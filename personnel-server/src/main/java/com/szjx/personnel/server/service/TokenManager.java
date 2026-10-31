package com.szjx.personnel.server.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录令牌管理(内存态,重启后需重新登录)
 */
@Component
public class TokenManager {

    public static class TokenEntry {
        public final String username;
        public final long expireAt;

        public TokenEntry(String username, long expireAt) {
            this.username = username;
            this.expireAt = expireAt;
        }
    }

    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<String, TokenEntry>();
    private volatile long expireMillis = 24L * 3600 * 1000;
    private final Map<String, Long> versions = new java.util.HashMap<String, Long>();
    private long globalVersion;

    public synchronized long version(String username) {
        Long value = versions.get(username);
        return globalVersion + (value == null ? 0L : value);
    }

    public synchronized void revokeAll() {
        globalVersion++;
        tokens.clear();
    }

    public synchronized String create(String username) {
        return create(username, version(username));
    }

    /** Reject a login whose identity/password checks overlapped a session revocation. */
    public synchronized String create(String username, long expectedVersion) {
        if (version(username) != expectedVersion) {
            throw new com.szjx.personnel.server.auth.ForbiddenException("账号授权已更新，请重新登录");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenEntry(username, System.currentTimeMillis() + expireMillis));
        return token;
    }

    public synchronized void revokeUsers(java.util.Collection<String> usernames) {
        java.util.Set<String> names = new java.util.HashSet<String>(usernames);
        for (String username : names) {
            Long previous = versions.get(username);
            versions.put(username, (previous == null ? 0L : previous) + 1L);
        }
        tokens.entrySet().removeIf(entry -> names.contains(entry.getValue().username));
    }

    /**
     * 校验令牌,有效返回用户名,无效返回 null
     */
    public String validate(String token) {
        if (token == null) {
            return null;
        }
        TokenEntry entry = tokens.get(token);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAt) {
            tokens.remove(token);
            return null;
        }
        return entry.username;
    }

    public void remove(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    public void setExpireMillis(long expireMillis) {
        this.expireMillis = expireMillis;
    }

    /** 定期清理过期令牌:不再只在被动 validate 时移除,避免长期运行下内存缓慢增长(WebConfig 已开启 @EnableScheduling)。 */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30 * 60 * 1000L)
    public void pruneExpired() {
        long now = System.currentTimeMillis();
        synchronized (this) {
            tokens.entrySet().removeIf(entry -> now > entry.getValue().expireAt);
        }
    }
}
