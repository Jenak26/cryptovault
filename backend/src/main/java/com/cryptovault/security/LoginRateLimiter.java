package com.cryptovault.security;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis-backed rate limiter for tracking failed login attempts.
 */
@Component
public class LoginRateLimiter {

    private static final String IP_PREFIX = "rl:ip:";
    private static final String EMAIL_PREFIX = "rl:email:";
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Checks if the rate limit has been exceeded for either the email or the IP address.
     */
    public boolean isBlocked(String email, String ip) {
        int ipAttempts = getAttempts(IP_PREFIX + ip);
        int emailAttempts = getAttempts(EMAIL_PREFIX + email);
        return ipAttempts >= MAX_ATTEMPTS || emailAttempts >= MAX_ATTEMPTS;
    }

    /**
     * Records a login failure for the email and the IP address.
     */
    public void recordFailure(String email, String ip) {
        increment(IP_PREFIX + ip);
        increment(EMAIL_PREFIX + email);
    }

    /**
     * Resets the failure counters for the email and the IP address upon a successful login.
     */
    public void reset(String email, String ip) {
        redis.delete(IP_PREFIX + ip);
        redis.delete(EMAIL_PREFIX + email);
    }

    private int getAttempts(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, WINDOW);
        }
    }
}
