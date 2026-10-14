package com.campus.lostfound.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已吊销 token 的内存集合。v1 无 Redis，退出登录后在此登记使 token 失效；
 * 每次读写时顺带清理已过期的条目，避免无限增长。
 */
@Component
public class TokenBlacklist {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String token, Instant expiresAt) {
        cleanupExpired();
        revoked.put(token, expiresAt);
    }

    public boolean isRevoked(String token) {
        Instant expiresAt = revoked.get(token);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            revoked.remove(token);
            return false;
        }
        return true;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
