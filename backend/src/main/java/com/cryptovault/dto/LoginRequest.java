package com.cryptovault.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Login request body. No min-length on the password here — wrong credentials are rejected generically. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
