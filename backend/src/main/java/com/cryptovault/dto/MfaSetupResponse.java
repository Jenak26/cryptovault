package com.cryptovault.dto;

/**
 * Enrollment payload: the Base32 secret (to show/enter manually) and the otpauth URI the frontend
 * renders as a QR code. MFA is not active until the user confirms a code via /api/mfa/enable.
 */
public record MfaSetupResponse(String secret, String otpAuthUri) {
}
