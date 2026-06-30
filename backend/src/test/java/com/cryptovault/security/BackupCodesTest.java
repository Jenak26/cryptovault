package com.cryptovault.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class BackupCodesTest {

    @Test
    void generatesDashedTenCharacterCodes() {
        String code = BackupCodes.generate(new SecureRandom());

        assertThat(code).matches("[A-Z2-9]{5}-[A-Z2-9]{5}");
        // Crockford-style alphabet excludes easily-confused characters.
        assertThat(code).doesNotContainAnyWhitespaces();
        assertThat(code.replace("-", "")).doesNotContain("O", "I", "L", "0", "1");
    }

    @Test
    void hashIsStableAndIgnoresDashesAndCase() {
        String hashed = BackupCodes.hash("ABCDE-FGHJK");

        assertThat(hashed).hasSize(64); // SHA-256 hex
        assertThat(BackupCodes.hash("abcde-fghjk")).isEqualTo(hashed);
        assertThat(BackupCodes.hash("abcdefghjk")).isEqualTo(hashed);
        assertThat(BackupCodes.hash("ZZZZZ-ZZZZZ")).isNotEqualTo(hashed);
    }
}
