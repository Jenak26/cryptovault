package com.cryptovault.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Time-based One-Time Password (TOTP) engine, implemented from scratch per RFC 6238 (TOTP) on top
 * of RFC 4226 (HOTP). Compatible with Google Authenticator, Authy, 1Password, etc.
 *
 * <p>Deliberately dependency-free: a 6-digit code is HMAC-SHA1 over the 30-second time counter,
 * dynamically truncated and reduced mod 10^6. Verification accepts a ±1 step window to tolerate
 * clock skew between the server and the authenticator app.
 */
@Service
public class TotpService {

    private static final int SECRET_BYTES = 20;     // 160-bit shared secret (RFC 4226 recommendation)
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int SKEW_STEPS = 1;        // accept previous/current/next step
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final String ISSUER = "CryptoVault";

    private final SecureRandom random = new SecureRandom();

    /** Generates a fresh Base32-encoded TOTP secret for a new enrollment. */
    public String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTES];
        random.nextBytes(buffer);
        return Base32.encode(buffer);
    }

    /**
     * Builds the {@code otpauth://} URI an authenticator app scans (typically rendered as a QR code).
     */
    public String otpAuthUri(String base32Secret, String accountEmail) {
        String label = url(ISSUER) + ":" + url(accountEmail);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + url(ISSUER)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    /** Verifies a user-supplied code against the secret, allowing for clock skew. */
    public boolean verify(String base32Secret, String code) {
        return verifyAt(base32Secret, code, System.currentTimeMillis());
    }

    /** Time-injectable verification (package-visible for deterministic tests). */
    boolean verifyAt(String base32Secret, String code, long epochMillis) {
        if (base32Secret == null || code == null) {
            return false;
        }
        String trimmed = code.trim();
        if (trimmed.length() != DIGITS || !trimmed.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] key = Base32.decode(base32Secret);
        long currentStep = epochMillis / 1000L / PERIOD_SECONDS;
        for (int offset = -SKEW_STEPS; offset <= SKEW_STEPS; offset++) {
            String candidate = generateCode(key, currentStep + offset);
            if (constantTimeEquals(candidate, trimmed)) {
                return true;
            }
        }
        return false;
    }

    /** HOTP code for a given counter value (RFC 4226 §5.3). Package-visible for tests. */
    String generateCode(byte[] key, long counter) {
        byte[] counterBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xff);
            counter >>>= 8;
        }
        byte[] hash;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            hash = mac.doFinal(counterBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute TOTP", e);
        }
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String url(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Minimal RFC 4648 Base32 (no padding) — what authenticator apps expect for the secret. */
    static final class Base32 {
        private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

        static String encode(byte[] data) {
            StringBuilder out = new StringBuilder();
            int buffer = 0;
            int bitsLeft = 0;
            for (byte b : data) {
                buffer = (buffer << 8) | (b & 0xff);
                bitsLeft += 8;
                while (bitsLeft >= 5) {
                    int index = (buffer >> (bitsLeft - 5)) & 0x1f;
                    bitsLeft -= 5;
                    out.append(ALPHABET.charAt(index));
                }
            }
            if (bitsLeft > 0) {
                int index = (buffer << (5 - bitsLeft)) & 0x1f;
                out.append(ALPHABET.charAt(index));
            }
            return out.toString();
        }

        static byte[] decode(String s) {
            String clean = s.trim().replace("=", "").toUpperCase();
            int buffer = 0;
            int bitsLeft = 0;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (char c : clean.toCharArray()) {
                int val = ALPHABET.indexOf(c);
                if (val < 0) {
                    throw new IllegalArgumentException("Invalid Base32 character: " + c);
                }
                buffer = (buffer << 5) | val;
                bitsLeft += 5;
                if (bitsLeft >= 8) {
                    out.write((buffer >> (bitsLeft - 8)) & 0xff);
                    bitsLeft -= 8;
                }
            }
            return out.toByteArray();
        }
    }
}
