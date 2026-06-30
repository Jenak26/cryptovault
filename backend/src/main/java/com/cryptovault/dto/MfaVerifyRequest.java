package com.cryptovault.dto;

import jakarta.validation.constraints.NotBlank;

/** Second step of an MFA login: the challenge token from /login plus the TOTP code. */
public record MfaVerifyRequest(
        @NotBlank String mfaToken,
        @NotBlank String code) {
}
