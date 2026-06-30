package com.cryptovault.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private RateLimiter rateLimiter;

    @Test
    void isRateLimitedReturnsTrueIfAttemptsExceedMax() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rate:login:fail:ip:192.168.1.1")).thenReturn("5");
        when(valueOps.get("rate:login:fail:email:test@example.com")).thenReturn("2");

        assertThat(rateLimiter.isRateLimited("test@example.com", "192.168.1.1")).isTrue();
    }

    @Test
    void isRateLimitedReturnsTrueIfEmailAttemptsExceedMax() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rate:login:fail:ip:192.168.1.1")).thenReturn("2");
        when(valueOps.get("rate:login:fail:email:test@example.com")).thenReturn("6");

        assertThat(rateLimiter.isRateLimited("test@example.com", "192.168.1.1")).isTrue();
    }

    @Test
    void isRateLimitedReturnsFalseIfAttemptsAreBelowMax() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("rate:login:fail:ip:192.168.1.1")).thenReturn("4");
        when(valueOps.get("rate:login:fail:email:test@example.com")).thenReturn("3");

        assertThat(rateLimiter.isRateLimited("test@example.com", "192.168.1.1")).isFalse();
    }

    @Test
    void recordFailureIncrementsCountersAndSetsTtlOnFirstAttempt() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate:login:fail:ip:192.168.1.1")).thenReturn(1L);
        when(valueOps.increment("rate:login:fail:email:test@example.com")).thenReturn(1L);

        rateLimiter.recordFailure("test@example.com", "192.168.1.1");

        verify(redis).expire("rate:login:fail:ip:192.168.1.1", Duration.ofMinutes(15));
        verify(redis).expire("rate:login:fail:email:test@example.com", Duration.ofMinutes(15));
    }

    @Test
    void recordFailureDoesNotSetTtlOnSubsequentAttempts() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("rate:login:fail:ip:192.168.1.1")).thenReturn(2L);
        when(valueOps.increment("rate:login:fail:email:test@example.com")).thenReturn(2L);

        rateLimiter.recordFailure("test@example.com", "192.168.1.1");

        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void clearFailuresDeletesKeys() {
        rateLimiter.clearFailures("test@example.com", "192.168.1.1");

        verify(redis).delete("rate:login:fail:ip:192.168.1.1");
        verify(redis).delete("rate:login:fail:email:test@example.com");
    }
}
