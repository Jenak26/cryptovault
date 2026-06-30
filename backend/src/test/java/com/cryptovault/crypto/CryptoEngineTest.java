package com.cryptovault.crypto;

import com.cryptovault.config.CryptoConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CryptoEngineTest {

    private CryptoEngine cryptoEngine;
    private CryptoConfig cryptoConfig;
    private byte[] rawKey;

    @BeforeEach
    void setUp() {
        cryptoConfig = Mockito.mock(CryptoConfig.class);
        CipherStrategy aes = new AesGcmStrategy();
        CipherStrategy chacha = new ChaCha20Poly1305Strategy();
        cryptoEngine = new CryptoEngine(List.of(aes, chacha), cryptoConfig);

        rawKey = new byte[32];
        new SecureRandom().nextBytes(rawKey);
    }

    @Test
    void testAesGcmRoundTrip() throws Exception {
        when(cryptoConfig.getActiveAlgorithm()).thenReturn("AES");

        String plaintext = "Sensitive data for AES";
        byte[] ciphertextBlob = cryptoEngine.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), rawKey);

        assertNotNull(ciphertextBlob);
        assertTrue(ciphertextBlob.length > 12); // Nonce (12) + ciphertext + tag

        byte[] decrypted = cryptoEngine.decrypt(ciphertextBlob, rawKey, "AES");
        assertEquals(plaintext, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    void testChaCha20Poly1305RoundTrip() throws Exception {
        when(cryptoConfig.getActiveAlgorithm()).thenReturn("CHACHA20");

        String plaintext = "Sensitive data for ChaCha20";
        byte[] ciphertextBlob = cryptoEngine.encrypt(plaintext.getBytes(StandardCharsets.UTF_8), rawKey);

        assertNotNull(ciphertextBlob);
        assertTrue(ciphertextBlob.length > 12); // Nonce (12) + ciphertext + tag

        byte[] decrypted = cryptoEngine.decrypt(ciphertextBlob, rawKey, "CHACHA20");
        assertEquals(plaintext, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    void testTamperDetectionAes() throws Exception {
        when(cryptoConfig.getActiveAlgorithm()).thenReturn("AES");

        byte[] plaintext = "Hello World".getBytes(StandardCharsets.UTF_8);
        byte[] blob = cryptoEngine.encrypt(plaintext, rawKey);

        // Tamper with the last byte
        blob[blob.length - 1] ^= 0x01;

        assertThrows(Exception.class, () -> cryptoEngine.decrypt(blob, rawKey, "AES"));
    }

    @Test
    void testTamperDetectionChaCha() throws Exception {
        when(cryptoConfig.getActiveAlgorithm()).thenReturn("CHACHA20");

        byte[] plaintext = "Hello World".getBytes(StandardCharsets.UTF_8);
        byte[] blob = cryptoEngine.encrypt(plaintext, rawKey);

        // Tamper with the last byte
        blob[blob.length - 1] ^= 0x01;

        assertThrows(Exception.class, () -> cryptoEngine.decrypt(blob, rawKey, "CHACHA20"));
    }
}
