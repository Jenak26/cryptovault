package com.cryptovault.service;

import com.cryptovault.dto.AuthResponse;
import com.cryptovault.dto.LoginRequest;
import com.cryptovault.dto.RegisterRequest;
import com.cryptovault.entity.Role;
import com.cryptovault.entity.User;
import com.cryptovault.exception.EmailAlreadyExistsException;
import com.cryptovault.exception.InvalidCredentialsException;
import com.cryptovault.exception.RateLimitExceededException;
import com.cryptovault.repository.RoleRepository;
import com.cryptovault.repository.UserRepository;
import com.cryptovault.security.JwtService;
import com.cryptovault.security.RateLimiter;
import com.cryptovault.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication orchestration: register, login, logout.
 */
@Service
public class AuthService {

    /** Default role assigned to new users; matches the V2 Flyway seed (uppercase). */
    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final TokenBlacklist blacklist;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       JwtService jwt, TokenBlacklist blacklist, RateLimiter rateLimiter,
                       AuditService auditService) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.blacklist = blacklist;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }
        Role userRole = roles.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Default role " + DEFAULT_ROLE + " is not seeded"));

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.email())
                .passwordHash(encoder.encode(request.password()))
                .role(userRole)
                .isActive(true)
                .build();
        users.save(user);

        auditService.log(user.getId(), "REGISTER", null);

        String token = jwt.generateToken(user.getId(), userRole.getName());
        return new AuthResponse(token);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        if (rateLimiter.isRateLimited(request.email(), ipAddress)) {
            throw new RateLimitExceededException();
        }

        try {
            User user = users.findByEmail(request.email())
                    .orElseThrow(() -> {
                        auditService.log(null, "LOGIN_FAIL", ipAddress);
                        return new InvalidCredentialsException();
                    });
            if (!encoder.matches(request.password(), user.getPasswordHash())) {
                auditService.log(user.getId(), "LOGIN_FAIL", ipAddress);
                throw new InvalidCredentialsException();
            }

            rateLimiter.clearFailures(request.email(), ipAddress);
            auditService.log(user.getId(), "LOGIN_SUCCESS", ipAddress);

            String token = jwt.generateToken(user.getId(), user.getRole().getName());
            return new AuthResponse(token);
        } catch (InvalidCredentialsException ex) {
            rateLimiter.recordFailure(request.email(), ipAddress);
            throw ex;
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, "127.0.0.1");
    }

    /**
     * Invalidates a token by blacklisting its {@code jti} in Redis until its natural expiry.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, tampered, or expired
     */
    public void logout(String token) {
        Claims claims = jwt.parseClaims(token);
        String jti = claims.getId();
        String userIdStr = claims.getSubject();
        Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (!remaining.isNegative() && !remaining.isZero()) {
            blacklist.blacklist(jti, remaining);
        }
        if (userIdStr != null) {
            auditService.log(UUID.fromString(userIdStr), "LOGOUT", null);
        }
    }
}
