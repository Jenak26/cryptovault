package com.cryptovault.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class BackupCodesTest {

    @Test
    void generatesDashedCodesFromAnUnambiguousAlphabet() {
        SecureRandom random = new SecureRandom();
        // Loop so an alphabet regression can't pass by luck of the draw.
        for (int i = 0; i < 500; i++) {
            String code = BackupCodes.generate(random);
            assertThat(code).matches("[A-HJKMNP-Z2-9]{5}-[A-HJKMNP-Z2-9]{5}");
            // Confusable characters must never appear.
            assertThat(code).doesNotContain("O", "I", "L", "0", "1", " ");
        }
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
