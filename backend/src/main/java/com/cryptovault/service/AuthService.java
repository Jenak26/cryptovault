package com.cryptovault.service;

import com.cryptovault.dto.AuthResponse;
import com.cryptovault.dto.LoginRequest;
import com.cryptovault.dto.RegisterRequest;
import com.cryptovault.entity.Role;
import com.cryptovault.entity.User;
import com.cryptovault.exception.EmailAlreadyExistsException;
import com.cryptovault.exception.InvalidCredentialsException;
import com.cryptovault.repository.RoleRepository;
import com.cryptovault.repository.UserRepository;
import com.cryptovault.security.JwtService;
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
 *
 * <p>Auth is performed here directly (not via Spring Security's {@code AuthenticationManager}) — the
 * JWT validation filter and RBAC arrive in Phase 3.
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

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       JwtService jwt, TokenBlacklist blacklist) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.blacklist = blacklist;
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

        String token = jwt.generateToken(user.getId(), userRole.getName());
        return new AuthResponse(token);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // Same exception for unknown email and wrong password — no existence leak.
        // @Transactional keeps the persistence context open so the lazy Role can be read.
        User user = users.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwt.generateToken(user.getId(), user.getRole().getName());
        return new AuthResponse(token);
    }

    /**
     * Invalidates a token by blacklisting its {@code jti} in Redis until its natural expiry.
     *
     * @throws io.jsonwebtoken.JwtException if the token is malformed, tampered, or expired
     */
    public void logout(String token) {
        Claims claims = jwt.parseClaims(token);
        String jti = claims.getId();
        Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (!remaining.isNegative() && !remaining.isZero()) {
            blacklist.blacklist(jti, remaining);
        }
    }
}
