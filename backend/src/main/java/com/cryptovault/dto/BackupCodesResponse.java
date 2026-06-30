package com.cryptovault.dto;

import java.util.List;

/**
 * The one-time recovery codes generated when MFA is enabled (or regenerated). Shown to the user
 * exactly once — only their hashes are stored server-side.
 */
public record BackupCodesResponse(List<String> backupCodes) {
}
