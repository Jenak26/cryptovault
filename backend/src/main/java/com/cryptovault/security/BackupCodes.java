package com.cryptovault.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generation and hashing of one-time MFA recovery codes.
 *
 * <p>Codes are high-entropy random strings (so a fast hash is appropriate — unlike passwords, they
 * don't need a slow KDF). Only the SHA-256 hash is stored; comparison normalises case and dashes so
 * the user can type the code however it's displayed.
 */
public final class BackupCodes {

    // Crockford-style alphabet: no 0/O/1/I to avoid transcription errors.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int GROUP = 5;

    private BackupCodes() {
    }

    /** Returns a display code like {@code AB3CD-EFG9H} (two groups of five). */
    public static String generate(SecureRandom random) {
        StringBuilder sb = new StringBuilder(GROUP * 2 + 1);
        for (int i = 0; i < GROUP * 2; i++) {
            if (i == GROUP) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** SHA-256 hex of the normalised code (uppercased, dashes/whitespace stripped). */
    public static String hash(String code) {
        String normalised = code.trim().toUpperCase().replace("-", "").replace(" ", "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalised.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
