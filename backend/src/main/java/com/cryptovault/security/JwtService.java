package com.cryptovault.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates and validates JWTs (HMAC-SHA256).
 *
 * <p>Tokens carry: subject = userId, a {@code role} claim, a unique {@code jti}, an issued-at and an
 * expiry. The {@code jti} is what logout blacklists in Redis.
 *
 * <p>The constructor takes the secret and lifetime directly so the service can be unit-tested
 * without a Spring context: {@code new JwtService(secret, 60)}.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtService(
            @Value("${cryptovault.jwt.secret}") String secret,
            @Value("${cryptovault.jwt.expiration-minutes}") long expirationMinutes) {
        // HS256 requires a key of at least 256 bits (32 bytes).
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMinutes * 60_000L;
    }

    /** Mints a signed token for the given user and role. Each call gets a fresh random {@code jti}. */
    public String generateToken(UUID userId, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMillis);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Verifies the signature and expiry, returning the claims.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, tampered, or expired
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
