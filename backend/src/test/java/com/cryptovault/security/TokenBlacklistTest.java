package com.cryptovault.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private TokenBlacklist blacklist;

    @Test
    void blacklistStoresPrefixedKeyWithTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        blacklist.blacklist("jti-1", Duration.ofMinutes(30));

        verify(valueOps).set("bl:jti:jti-1", "1", Duration.ofMinutes(30));
    }

    @Test
    void isBlacklistedTrueWhenKeyPresent() {
        when(redis.hasKey("bl:jti:jti-1")).thenReturn(true);

        assertThat(blacklist.isBlacklisted("jti-1")).isTrue();
    }

    @Test
    void isBlacklistedFalseWhenKeyAbsent() {
        when(redis.hasKey("bl:jti:jti-2")).thenReturn(false);

        assertThat(blacklist.isBlacklisted("jti-2")).isFalse();
    }
}
