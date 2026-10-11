package com.szjx.personnel.server.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败防护:同一账号连续失败 N 次后锁定一段时间
 */
@Component
public class LoginGuard {

    public static final int MAX_FAILS = 5;
    public static final long LOCK_MILLIS = 10 * 60 * 1000L; // 10 分钟

    private static class Record {
        int fails;
        long lockUntil;
    }

    private final Map<String, Record> records = new ConcurrentHashMap<String, Record>();

    /**
     * @return 锁定剩余毫秒数,未锁定返回 0
     */
    public long lockedRemainMillis(String username) {
        Record r = records.get(username);
        if (r == null) {
            return 0;
        }
        if (r.lockUntil > 0 && System.currentTimeMillis() < r.lockUntil) {
            return r.lockUntil - System.currentTimeMillis();
        }
        return 0;
    }

    public void recordFailure(String username) {
        Record r = records.get(username);
        if (r == null) {
            r = new Record();
            records.put(username, r);
        }
        r.fails++;
        if (r.fails >= MAX_FAILS) {
            r.lockUntil = System.currentTimeMillis() + LOCK_MILLIS;
            r.fails = 0;
        }
    }

    public void clear(String username) {
        records.remove(username);
    }
}
