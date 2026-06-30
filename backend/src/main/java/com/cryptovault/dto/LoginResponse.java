package com.cryptovault.dto;

/**
 * Login result. Either the login completed and {@code token} is set, or the user has MFA enabled
 * and must complete a second step: {@code mfaRequired} is true and {@code mfaToken} carries the
 * short-lived challenge to submit alongside the TOTP code at {@code /api/auth/mfa/verify}.
 */
public record LoginResponse(String token, boolean mfaRequired, String mfaToken) {

    public static LoginResponse authenticated(String token) {
        return new LoginResponse(token, false, null);
    }

    public static LoginResponse mfaChallenge(String mfaToken) {
        return new LoginResponse(null, true, mfaToken);
    }
}
