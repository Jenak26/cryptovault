package com.cryptovault.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed login rate limiter.
 * Tracks failed attempts per IP and per email separately to prevent:
 * 1. Password spraying attacks (one IP, multiple emails).
 * 2. Brute-force attacks (multiple IPs, one email).
 *
 * Blocks after 5 failures for 15 minutes.
 */
@Component
public class RateLimiter {

    private static final String FAIL_PREFIX_IP = "rate:login:fail:ip:";
    private static final String FAIL_PREFIX_EMAIL = "rate:login:fail:email:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public RateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Checks if either the IP or the email has exceeded the maximum failure limit.
     */
    public boolean isRateLimited(String email, String ipAddress) {
        String ipKey = FAIL_PREFIX_IP + ipAddress;
        String emailKey = FAIL_PREFIX_EMAIL + email;

        String ipAttempts = redis.opsForValue().get(ipKey);
        String emailAttempts = redis.opsForValue().get(emailKey);

        return (ipAttempts != null && Integer.parseInt(ipAttempts) >= MAX_ATTEMPTS) ||
               (emailAttempts != null && Integer.parseInt(emailAttempts) >= MAX_ATTEMPTS);
    }

    /**
     * Records a login failure. Increments the failure count and sets TTL if it is a new counter.
     */
    public void recordFailure(String email, String ipAddress) {
        String ipKey = FAIL_PREFIX_IP + ipAddress;
        String emailKey = FAIL_PREFIX_EMAIL + email;

        incrementKey(ipKey);
        incrementKey(emailKey);
    }

    /**
     * Clears login failures upon a successful authentication.
     */
    public void clearFailures(String email, String ipAddress) {
        redis.delete(FAIL_PREFIX_IP + ipAddress);
        redis.delete(FAIL_PREFIX_EMAIL + email);
    }

    private void incrementKey(String key) {
        Long attempts = redis.opsForValue().increment(key);
        if (attempts != null && attempts == 1) {
            redis.expire(key, BLOCK_DURATION);
        }
    }
}
