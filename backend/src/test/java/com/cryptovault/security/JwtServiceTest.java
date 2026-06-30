package com.cryptovault.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    // HS256 needs >= 32 bytes.
    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    private final JwtService jwt = new JwtService(SECRET, 60);

    @Test
    void generatesAndParsesToken() {
        UUID userId = UUID.randomUUID();

        String token = jwt.generateToken(userId, "USER");
        Claims claims = jwt.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.getId()).isNotBlank();
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void rejectsExpiredToken() {
        // Negative lifetime => token's expiry is already in the past.
        JwtService expiringSvc = new JwtService(SECRET, -1);
        String token = expiringSvc.generateToken(UUID.randomUUID(), "USER");

        assertThatThrownBy(() -> jwt.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.generateToken(UUID.randomUUID(), "USER");
        // Flip the FIRST character of the signature segment, not the last: the trailing
        // base64url char carries bits that don't always change the decoded signature bytes,
        // which made the old last-char flip occasionally still verify (a flaky test).
        int sigStart = token.lastIndexOf('.') + 1;
        char first = token.charAt(sigStart);
        String tampered = token.substring(0, sigStart)
                + (first == 'A' ? 'B' : 'A')
                + token.substring(sigStart + 1);

        assertThatThrownBy(() -> jwt.parseClaims(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService otherSvc = new JwtService("a-totally-different-secret-key-32bytes!!", 60);
        String token = otherSvc.generateToken(UUID.randomUUID(), "USER");

        assertThatThrownBy(() -> jwt.parseClaims(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void eachTokenHasUniqueJti() {
        Claims first = jwt.parseClaims(jwt.generateToken(UUID.randomUUID(), "USER"));
        Claims second = jwt.parseClaims(jwt.generateToken(UUID.randomUUID(), "USER"));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }
}
