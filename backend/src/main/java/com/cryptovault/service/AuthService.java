package com.cryptovault.service;

import com.cryptovault.dto.AuthResponse;
import com.cryptovault.dto.LoginRequest;
import com.cryptovault.dto.LoginResponse;
import com.cryptovault.dto.MfaSetupResponse;
import com.cryptovault.dto.RegisterRequest;
import com.cryptovault.entity.Role;
import com.cryptovault.entity.User;
import com.cryptovault.exception.EmailAlreadyExistsException;
import com.cryptovault.exception.InvalidCredentialsException;
import com.cryptovault.exception.RateLimitExceededException;
import com.cryptovault.repository.RoleRepository;
import com.cryptovault.repository.UserRepository;
import com.cryptovault.security.JwtService;
import com.cryptovault.security.MfaChallengeStore;
import com.cryptovault.security.RateLimiter;
import com.cryptovault.security.TokenBlacklist;
import com.cryptovault.security.TotpService;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication orchestration: register, login (incl. optional TOTP second factor), logout, and
 * MFA enrollment.
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
    private final TotpService totpService;
    private final MfaChallengeStore mfaChallengeStore;

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       JwtService jwt, TokenBlacklist blacklist, RateLimiter rateLimiter,
                       AuditService auditService, TotpService totpService,
                       MfaChallengeStore mfaChallengeStore) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.blacklist = blacklist;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.totpService = totpService;
        this.mfaChallengeStore = mfaChallengeStore;
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
    public LoginResponse login(LoginRequest request, String ipAddress) {
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

            // Password is correct — the brute-force window can reset regardless of the MFA step.
            rateLimiter.clearFailures(request.email(), ipAddress);

            if (user.isMfaEnabled()) {
                String challenge = mfaChallengeStore.create(user.getId());
                auditService.log(user.getId(), "LOGIN_MFA_CHALLENGE", ipAddress);
                return LoginResponse.mfaChallenge(challenge);
            }

            auditService.log(user.getId(), "LOGIN_SUCCESS", ipAddress);
            String token = jwt.generateToken(user.getId(), user.getRole().getName());
            return LoginResponse.authenticated(token);
        } catch (InvalidCredentialsException ex) {
            rateLimiter.recordFailure(request.email(), ipAddress);
            throw ex;
        }
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        return login(request, "127.0.0.1");
    }

    /**
     * Completes an MFA login: validates the challenge token and the TOTP code, then issues the JWT.
     * Returns the same generic {@link InvalidCredentialsException} on any failure (expired challenge,
     * wrong/replayed code) so the second step leaks nothing.
     */
    @Transactional(readOnly = true)
    public AuthResponse verifyMfa(String mfaToken, String code, String ipAddress) {
        UUID userId = mfaChallengeStore.consume(mfaToken);
        if (userId == null) {
            throw new InvalidCredentialsException();
        }
        User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!user.isMfaEnabled() || !totpService.verify(user.getMfaSecret(), code)) {
            auditService.log(userId, "LOGIN_MFA_FAIL", ipAddress);
            throw new InvalidCredentialsException();
        }
        auditService.log(userId, "LOGIN_SUCCESS", ipAddress);
        String token = jwt.generateToken(user.getId(), user.getRole().getName());
        return new AuthResponse(token);
    }

    /**
     * Begins MFA enrollment: generates and stores a secret (kept inactive) and returns the otpauth
     * URI for the authenticator app. The user must confirm a code via {@link #enableMfa} to activate.
     */
    @Transactional
    public MfaSetupResponse setupMfa(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.isMfaEnabled()) {
            throw new IllegalStateException("MFA is already enabled");
        }
        String secret = totpService.generateSecret();
        user.setMfaSecret(secret);
        users.save(user);
        return new MfaSetupResponse(secret, totpService.otpAuthUri(secret, user.getEmail()));
    }

    /** Activates MFA once the user proves they can produce a valid code from the new secret. */
    @Transactional
    public void enableMfa(UUID userId, String code) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getMfaSecret() == null) {
            throw new IllegalStateException("Start MFA setup before enabling");
        }
        if (!totpService.verify(user.getMfaSecret(), code)) {
            throw new InvalidCredentialsException();
        }
        user.setMfaEnabled(true);
        users.save(user);
        auditService.log(userId, "MFA_ENABLED", null);
    }

    /** Disables MFA; requires a valid current code so a hijacked session can't silently turn it off. */
    @Transactional
    public void disableMfa(UUID userId, String code) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!user.isMfaEnabled() || !totpService.verify(user.getMfaSecret(), code)) {
            throw new InvalidCredentialsException();
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        users.save(user);
        auditService.log(userId, "MFA_DISABLED", null);
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
