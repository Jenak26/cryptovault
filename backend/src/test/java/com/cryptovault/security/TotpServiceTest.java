package com.cryptovault.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    // Fixed instant so the test is deterministic regardless of when it runs.
    private static final long FIXED_MILLIS = 1_700_000_000_000L;
    private static final long STEP = FIXED_MILLIS / 1000L / 30L;

    @Test
    void verifiesACodeGeneratedForTheCurrentStep() {
        String secret = totp.generateSecret();
        String code = totp.generateCode(TotpService.Base32.decode(secret), STEP);

        assertThat(totp.verifyAt(secret, code, FIXED_MILLIS)).isTrue();
    }

    @Test
    void acceptsAdjacentStepsWithinSkewWindow() {
        String secret = totp.generateSecret();
        byte[] key = TotpService.Base32.decode(secret);

        assertThat(totp.verifyAt(secret, totp.generateCode(key, STEP - 1), FIXED_MILLIS)).isTrue();
        assertThat(totp.verifyAt(secret, totp.generateCode(key, STEP + 1), FIXED_MILLIS)).isTrue();
    }

    @Test
    void rejectsCodesOutsideSkewWindow() {
        String secret = totp.generateSecret();
        byte[] key = TotpService.Base32.decode(secret);

        assertThat(totp.verifyAt(secret, totp.generateCode(key, STEP + 5), FIXED_MILLIS)).isFalse();
    }

    @Test
    void rejectsMalformedCodes() {
        String secret = totp.generateSecret();

        assertThat(totp.verify(secret, "12345")).isFalse();    // too short
        assertThat(totp.verify(secret, "abcdef")).isFalse();   // non-digit
        assertThat(totp.verify(secret, null)).isFalse();
    }

    @Test
    void base32RoundTrips() {
        byte[] data = "the quick brown fox".getBytes();
        assertThat(TotpService.Base32.decode(TotpService.Base32.encode(data))).isEqualTo(data);
    }

    @Test
    void otpAuthUriCarriesSecretAndIssuer() {
        String uri = totp.otpAuthUri("ABC234", "user@bank.com");

        assertThat(uri).startsWith("otpauth://totp/");
        assertThat(uri).contains("secret=ABC234");
        assertThat(uri).contains("issuer=CryptoVault");
    }
}
