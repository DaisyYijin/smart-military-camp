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
    private long expireMillis = 24L * 3600 * 1000;

    public String create(String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenEntry(username, System.currentTimeMillis() + expireMillis));
        return token;
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
}
