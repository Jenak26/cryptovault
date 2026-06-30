package com.cryptovault.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record VaultMetadataResponse(
    UUID id,
    String name,
    String algorithmUsed,
    Integer keyVersion,
    LocalDateTime createdAt
) {}
