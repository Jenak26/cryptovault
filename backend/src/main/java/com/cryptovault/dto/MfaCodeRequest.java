package com.cryptovault.dto;

import jakarta.validation.constraints.NotBlank;

/** A TOTP code, used to confirm enabling or disabling MFA. */
public record MfaCodeRequest(
        @NotBlank String code) {
}
