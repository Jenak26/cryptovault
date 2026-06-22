package com.cryptovault.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiter(redis);
    }

    @Test
    void isBlockedReturnsFalseWhenNoAttempts() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rl:ip:127.0.0.1")).thenReturn(null);
        when(valueOps.get("rl:email:a@b.com")).thenReturn(null);

        boolean blocked = rateLimiter.isBlocked("a@b.com", "127.0.0.1");

        assertThat(blocked).isFalse();
    }

    @Test
    void isBlockedReturnsTrueWhenIpAttemptsExceedLimit() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rl:ip:127.0.0.1")).thenReturn("5");
        when(valueOps.get("rl:email:a@b.com")).thenReturn("2");

        boolean blocked = rateLimiter.isBlocked("a@b.com", "127.0.0.1");

        assertThat(blocked).isTrue();
    }

    @Test
    void isBlockedReturnsTrueWhenEmailAttemptsExceedLimit() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rl:ip:127.0.0.1")).thenReturn("3");
        when(valueOps.get("rl:email:a@b.com")).thenReturn("5");

        boolean blocked = rateLimiter.isBlocked("a@b.com", "127.0.0.1");

        assertThat(blocked).isTrue();
    }

    @Test
    void recordFailureIncrementsAndSetsTtlOnFirstAttempt() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rl:ip:127.0.0.1")).thenReturn(1L);
        when(valueOps.increment("rl:email:a@b.com")).thenReturn(1L);

        rateLimiter.recordFailure("a@b.com", "127.0.0.1");

        verify(redis).expire(eq("rl:ip:127.0.0.1"), any(Duration.class));
        verify(redis).expire(eq("rl:email:a@b.com"), any(Duration.class));
    }

    @Test
    void resetDeletesKeys() {
        rateLimiter.reset("a@b.com", "127.0.0.1");

        verify(redis).delete("rl:ip:127.0.0.1");
        verify(redis).delete("rl:email:a@b.com");
    }
}
