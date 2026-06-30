package com.cryptovault.dto;

import java.util.UUID;

public record VaultDecryptResponse(
    UUID id,
    String name,
    String secret
) {}
