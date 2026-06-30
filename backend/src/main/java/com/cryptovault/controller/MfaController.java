package com.cryptovault.controller;

import com.cryptovault.dto.BackupCodesResponse;
import com.cryptovault.dto.MfaCodeRequest;
import com.cryptovault.dto.MfaSetupResponse;
import com.cryptovault.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * Authenticated MFA enrollment endpoints (not under {@code /api/auth/**}, so they require a valid
 * JWT). The user id comes from the authenticated principal, never the request body.
 */
@RestController
@RequestMapping("/api/mfa")
public class MfaController {

    private final AuthService auth;

    public MfaController(AuthService auth) {
        this.auth = auth;
    }

    /** Begin enrollment: returns the secret + otpauth URI to render as a QR code. */
    @PostMapping("/setup")
    public MfaSetupResponse setup(Principal principal) {
        return auth.setupMfa(currentUserId(principal));
    }

    /** Confirm enrollment by proving a valid code; returns one-time recovery codes (shown once). */
    @PostMapping("/enable")
    public BackupCodesResponse enable(@Valid @RequestBody MfaCodeRequest request, Principal principal) {
        return new BackupCodesResponse(auth.enableMfa(currentUserId(principal), request.code()));
    }

    /** Re-issue recovery codes, invalidating the previous set (requires a valid current code). */
    @PostMapping("/backup-codes/regenerate")
    public BackupCodesResponse regenerateBackupCodes(@Valid @RequestBody MfaCodeRequest request, Principal principal) {
        return new BackupCodesResponse(auth.regenerateBackupCodes(currentUserId(principal), request.code()));
    }

    /** Turn MFA off (requires a valid current code). */
    @PostMapping("/disable")
    @ResponseStatus(HttpStatus.OK)
    public void disable(@Valid @RequestBody MfaCodeRequest request, Principal principal) {
        auth.disableMfa(currentUserId(principal), request.code());
    }

    private UUID currentUserId(Principal principal) {
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        return UUID.fromString(principal.getName());
    }
}
