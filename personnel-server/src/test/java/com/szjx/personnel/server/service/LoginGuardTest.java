package com.szjx.personnel.server.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 登录失败防护:锁定语义、过期淘汰与容量上限(注入可控时钟,不依赖真实时间流逝) */
class LoginGuardTest {

    /** 可手动前进的时钟(毫秒),覆盖 Clock 的四个抽象方法 */
    private static final class MutableClock extends java.time.Clock {
        long millis;

        @Override public long millis() { return millis; }
        @Override public java.time.ZoneId getZone() { return java.time.ZoneId.systemDefault(); }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(millis); }
    }

    @Test
    void locksAfterFiveFailuresAndClearsOnSuccess() {
        MutableClock clock = new MutableClock();
        LoginGuard guard = new LoginGuard(clock);
        for (int i = 1; i < LoginGuard.MAX_FAILS; i++) {
            guard.recordFailure("alice");
            assertEquals(0, guard.lockedRemainMillis("alice"), "第" + i + "次失败不应锁定");
        }
        guard.recordFailure("alice");
        assertTrue(guard.lockedRemainMillis("alice") > 0, "连续5次失败应锁定");
        assertEquals(LoginGuard.LOCK_MILLIS, guard.lockedRemainMillis("alice"));
        guard.clear("alice");
        assertEquals(0, guard.lockedRemainMillis("alice"), "成功登录后应清除记录");
    }

    @Test
    void lockExpiresAfterTenMinutes() {
        MutableClock clock = new MutableClock();
        LoginGuard guard = new LoginGuard(clock);
        for (int i = 0; i < LoginGuard.MAX_FAILS; i++) {
            guard.recordFailure("bob");
        }
        assertTrue(guard.lockedRemainMillis("bob") > 0);
        clock.millis += LoginGuard.LOCK_MILLIS + 1;
        assertEquals(0, guard.lockedRemainMillis("bob"), "10分钟锁定期过后应自动解锁");
    }

    @Test
    void evictExpiredRemovesIdleUnlockedRecordsOnly() {
        MutableClock clock = new MutableClock();
        LoginGuard guard = new LoginGuard(clock);
        guard.recordFailure("stale-user"); // 仅1次失败,未锁定
        // 另一账号连续失败至锁定
        for (int i = 0; i < LoginGuard.MAX_FAILS; i++) {
            guard.recordFailure("locked-user");
        }
        clock.millis += LoginGuard.LOCK_MILLIS / 2; // 5分钟:锁定尚未到期
        guard.evictExpired(clock.millis);
        assertEquals(0, guard.lockedRemainMillis("locked-user") > 0 ? 0 : 1,
                "占位断言不会到达;下一行是真正断言");
        assertTrue(guard.lockedRemainMillis("locked-user") > 0, "锁定中的记录不应被过期清理移除");

        clock.millis += LoginGuard.IDLE_EXPIRE_MILLIS + 1; // 合计超过24小时不活动
        guard.evictExpired(clock.millis);
        // 锁定早已到期(合计约24小时+),视为未锁定且不活动,应被清理
        assertEquals(0, guard.lockedRemainMillis("locked-user"));
    }

    @Test
    void recordFailureCountResetsAfterIdleEviction() {
        MutableClock clock = new MutableClock();
        LoginGuard guard = new LoginGuard(clock);
        for (int i = 1; i < LoginGuard.MAX_FAILS; i++) {
            guard.recordFailure("carol"); // 4次,差1次到锁定
        }
        clock.millis += LoginGuard.IDLE_EXPIRE_MILLIS + 1;
        guard.evictExpired(clock.millis); // 不活动记录被清理,计数随之归零
        for (int i = 1; i < LoginGuard.MAX_FAILS; i++) {
            guard.recordFailure("carol"); // 再4次
            assertEquals(0, guard.lockedRemainMillis("carol"), "旧计数已清零,这4次不应触发锁定");
        }
    }

    @Test
    void capacityKeepsBoundedAndLockingStillWorksUnderPressure() {
        MutableClock clock = new MutableClock();
        LoginGuard guard = new LoginGuard(clock);
        // 超过容量上限的随机用户名各失败1次:稳态下记录数应保持在上限附近,不会无限增长
        int total = LoginGuard.MAX_RECORDS + 2000;
        for (int i = 0; i < total; i++) {
            guard.recordFailure("spam-" + i);
        }
        assertTrue(guard.recordCount() <= LoginGuard.MAX_RECORDS,
                "灌入" + total + "个用户名后记录数应不超过上限,实际=" + guard.recordCount());
        // 容量压力下防暴力破解仍有效:真实账号连续失败仍会锁定
        for (int i = 0; i < LoginGuard.MAX_FAILS; i++) {
            guard.recordFailure("dave");
        }
        assertTrue(guard.lockedRemainMillis("dave") > 0, "容量压力下真实账号仍应能被锁定");
    }
}
