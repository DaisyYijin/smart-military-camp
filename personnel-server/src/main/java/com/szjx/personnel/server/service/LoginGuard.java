package com.szjx.personnel.server.service;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败防护:同一账号连续失败 N 次后锁定一段时间。
 * 记录带容量上限与不活动过期淘汰,防止匿名随机用户名把内存刷高;
 * 锁定中的记录不参与淘汰,容量压力不削弱防暴力破解。
 */
@Component
public class LoginGuard {

    public static final int MAX_FAILS = 5;
    public static final long LOCK_MILLIS = 10 * 60 * 1000L; // 10 分钟
    /** 失败记录容量上限:超过后淘汰最旧的未锁定记录 */
    static final int MAX_RECORDS = 10_000;
    /** 未锁定记录的不活动过期时间 */
    static final long IDLE_EXPIRE_MILLIS = 24L * 3600 * 1000;
    /** 每隔多少次失败顺带做一次过期清理(避免每次失败全表扫描) */
    static final int EVICT_INTERVAL = 256;

    private static class Record {
        int fails;
        long lockUntil;
        long lastAttempt;
    }

    private final Map<String, Record> records = new ConcurrentHashMap<String, Record>();
    private final java.time.Clock clock;
    private int failuresSinceEvict = 0;

    public LoginGuard() {
        this(java.time.Clock.systemDefaultZone());
    }

    /** 包私有:测试注入可控时钟 */
    LoginGuard(java.time.Clock clock) {
        this.clock = clock;
    }

    private long now() {
        return clock.millis();
    }

    /**
     * @return 锁定剩余毫秒数,未锁定返回 0
     */
    public synchronized long lockedRemainMillis(String username) {
        Record r = records.get(username);
        if (r == null) {
            return 0;
        }
        if (r.lockUntil > 0 && now() < r.lockUntil) {
            return r.lockUntil - now();
        }
        return 0;
    }

    /** synchronized:计数与锁定是读取-修改-写入的复合操作,并发登录失败不得丢失计数或提前锁定。 */
    public synchronized void recordFailure(String username) {
        long now = now();
        if (++failuresSinceEvict >= EVICT_INTERVAL) {
            failuresSinceEvict = 0;
            evictExpired(now);
        }
        Record r = records.get(username);
        if (r == null) {
            ensureCapacity(now);
            r = new Record();
            records.put(username, r);
        }
        r.lastAttempt = now;
        r.fails++;
        if (r.fails >= MAX_FAILS) {
            r.lockUntil = now + LOCK_MILLIS;
            r.fails = 0;
        }
    }

    public synchronized void clear(String username) {
        records.remove(username);
    }

    /** 包私有观察方法:测试与容量断言用 */
    synchronized int recordCount() {
        return records.size();
    }

    /** 移除超过不活动时限的未锁定记录;锁定中的记录保留(防止用海量用户名挤掉锁定绕过防护) */
    synchronized void evictExpired(long now) {
        Iterator<Map.Entry<String, Record>> it = records.entrySet().iterator();
        while (it.hasNext()) {
            Record r = it.next().getValue();
            boolean locked = r.lockUntil > 0 && now < r.lockUntil;
            if (!locked && now - r.lastAttempt >= IDLE_EXPIRE_MILLIS) {
                it.remove();
            }
        }
    }

    /** 容量保护:先清过期,仍满则淘汰最旧的未锁定记录;全部锁定中时不淘汰(防暴力破解优先,允许小幅超限) */
    private void ensureCapacity(long now) {
        if (records.size() < MAX_RECORDS) {
            return;
        }
        evictExpired(now);
        if (records.size() < MAX_RECORDS) {
            return;
        }
        String oldestKey = null;
        long oldest = Long.MAX_VALUE;
        for (Map.Entry<String, Record> e : records.entrySet()) {
            Record r = e.getValue();
            boolean locked = r.lockUntil > 0 && now < r.lockUntil;
            if (!locked && r.lastAttempt < oldest) {
                oldest = r.lastAttempt;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            records.remove(oldestKey);
        }
    }
}
