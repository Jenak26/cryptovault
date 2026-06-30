package com.cryptovault.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

/**
 * Short-lived, single-use challenge tokens issued between the password step and the TOTP step of an
 * MFA login. The token maps to a user id in Redis with a 5-minute TTL, so a half-finished login
 * can't be resumed indefinitely, and consuming it deletes it (one verification attempt per challenge).
 */
@Component
public class MfaChallengeStore {

    private static final String PREFIX = "mfa:challenge:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();

    public MfaChallengeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Creates a challenge bound to the given user and returns the opaque token. */
    public String create(UUID userId) {
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        redis.opsForValue().set(PREFIX + token, userId.toString(), TTL);
        return token;
    }

    /** Returns the bound user id and deletes the challenge, or {@code null} if missing/expired. */
    public UUID consume(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String key = PREFIX + token;
        String userId = redis.opsForValue().get(key);
        if (userId == null) {
            return null;
        }
        redis.delete(key);
        return UUID.fromString(userId);
    }
}
