package com.cryptovault.service;

import com.cryptovault.dto.AuthResponse;
import com.cryptovault.dto.LoginRequest;
import com.cryptovault.dto.LoginResponse;
import com.cryptovault.dto.MfaSetupResponse;
import com.cryptovault.dto.RegisterRequest;
import com.cryptovault.entity.MfaBackupCode;
import com.cryptovault.entity.Role;
import com.cryptovault.entity.User;
import com.cryptovault.exception.EmailAlreadyExistsException;
import com.cryptovault.exception.InvalidCredentialsException;
import com.cryptovault.exception.RateLimitExceededException;
import com.cryptovault.repository.MfaBackupCodeRepository;
import com.cryptovault.repository.RoleRepository;
import com.cryptovault.repository.UserRepository;
import com.cryptovault.security.BackupCodes;
import com.cryptovault.security.JwtService;
import com.cryptovault.security.MfaChallengeStore;
import com.cryptovault.security.RateLimiter;
import com.cryptovault.security.TokenBlacklist;
import com.cryptovault.security.TotpService;
import io.jsonwebtoken.Claims;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final MfaBackupCodeRepository backupCodes;

    /** Number of one-time recovery codes issued per (re)generation. */
    private static final int BACKUP_CODE_COUNT = 10;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       JwtService jwt, TokenBlacklist blacklist, RateLimiter rateLimiter,
                       AuditService auditService, TotpService totpService,
                       MfaChallengeStore mfaChallengeStore, MfaBackupCodeRepository backupCodes) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.jwt = jwt;
        this.blacklist = blacklist;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.totpService = totpService;
        this.mfaChallengeStore = mfaChallengeStore;
        this.backupCodes = backupCodes;
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
     * Completes an MFA login: validates the challenge token, then either a TOTP code or a one-time
     * backup code, and issues the JWT. Returns the same generic {@link InvalidCredentialsException}
     * on any failure (expired challenge, wrong/replayed code) so the second step leaks nothing.
     */
    @Transactional
    public AuthResponse verifyMfa(String mfaToken, String code, String ipAddress) {
        UUID userId = mfaChallengeStore.consume(mfaToken);
        if (userId == null) {
            throw new InvalidCredentialsException();
        }
        User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!user.isMfaEnabled()) {
            auditService.log(userId, "LOGIN_MFA_FAIL", ipAddress);
            throw new InvalidCredentialsException();
        }

        if (totpService.verify(user.getMfaSecret(), code)) {
            auditService.log(userId, "LOGIN_SUCCESS", ipAddress);
        } else if (redeemBackupCode(userId, code)) {
            auditService.log(userId, "LOGIN_MFA_BACKUP", ipAddress);
        } else {
            auditService.log(userId, "LOGIN_MFA_FAIL", ipAddress);
            throw new InvalidCredentialsException();
        }

        String token = jwt.generateToken(user.getId(), user.getRole().getName());
        return new AuthResponse(token);
    }

    /** Consumes a matching unused backup code, returning true if one was redeemed. */
    private boolean redeemBackupCode(UUID userId, String code) {
        Optional<MfaBackupCode> match =
                backupCodes.findByUserIdAndCodeHashAndUsedFalse(userId, BackupCodes.hash(code));
        if (match.isEmpty()) {
            return false;
        }
        MfaBackupCode used = match.get();
        used.setUsed(true);
        backupCodes.save(used);
        return true;
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

    /**
     * Activates MFA once the user proves they can produce a valid code from the new secret, and
     * returns a fresh set of one-time recovery codes (shown to the user exactly once).
     */
    @Transactional
    public List<String> enableMfa(UUID userId, String code) {
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
        return issueBackupCodes(userId);
    }

    /** Re-issues recovery codes (invalidating the old set); requires a valid current TOTP code. */
    @Transactional
    public List<String> regenerateBackupCodes(UUID userId, String code) {
        User user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!user.isMfaEnabled() || !totpService.verify(user.getMfaSecret(), code)) {
            throw new InvalidCredentialsException();
        }
        auditService.log(userId, "MFA_BACKUP_REGENERATE", null);
        return issueBackupCodes(userId);
    }

    /** Clears any existing codes and stores hashes for a fresh set, returning the plaintext codes. */
    private List<String> issueBackupCodes(UUID userId) {
        backupCodes.deleteByUserId(userId);
        List<String> plaintext = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = BackupCodes.generate(random);
            plaintext.add(code);
            backupCodes.save(MfaBackupCode.builder()
                    .userId(userId)
                    .codeHash(BackupCodes.hash(code))
                    .used(false)
                    .build());
        }
        return plaintext;
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
        backupCodes.deleteByUserId(userId);
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
