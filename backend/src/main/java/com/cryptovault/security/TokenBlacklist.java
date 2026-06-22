package com.cryptovault.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed JWT blacklist. Logout stores a token's {@code jti} here with a TTL equal to the
 * token's remaining lifetime, so the entry expires exactly when the token would have anyway.
 *
 * <p>Phase 3's JWT filter will consult {@link #isBlacklisted(String)} on every request.
 */
@Component
public class TokenBlacklist {

    private static final String PREFIX = "bl:jti:";

    private final StringRedisTemplate redis;

    public TokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void blacklist(String jti, Duration ttl) {
        redis.opsForValue().set(PREFIX + jti, "1", ttl);
    }

    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
