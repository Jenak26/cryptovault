package com.cryptovault.dto;

import jakarta.validation.constraints.NotBlank;

public record VaultStoreRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Secret payload is required")
    String secret
) {}
