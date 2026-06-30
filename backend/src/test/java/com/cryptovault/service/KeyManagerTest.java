package com.cryptovault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cryptovault.config.CryptoConfig;
import com.cryptovault.entity.CryptoKey;
import com.cryptovault.entity.KeyStatus;
import com.cryptovault.repository.CryptoKeyRepository;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeyManagerTest {

    @Mock
    private CryptoKeyRepository keyRepository;

    @Mock
    private CryptoConfig cryptoConfig;

    @Mock
    private AuditService auditService;

    private KeyManager keyManager;

    private static final String MASTER_SECRET = "test-master-secret-key-must-be-32-bytes";

    @BeforeEach
    void setUp() {
        keyManager = new KeyManager(keyRepository, cryptoConfig, MASTER_SECRET, auditService);
    }

    @Test
    void initKeysOnStartupCreatesNewKeyIfNoneExist() {
        when(keyRepository.findByStatus(KeyStatus.ACTIVE)).thenReturn(Optional.empty());
        when(keyRepository.findAll()).thenReturn(new ArrayList<>());
        when(cryptoConfig.getActiveAlgorithm()).thenReturn("AES");

        keyManager.initKeysOnStartup();

        verify(keyRepository).save(any(CryptoKey.class));
        assertThat(keyManager.getActiveVersion()).isEqualTo(1);
        assertNotNull(keyManager.getActiveKey());
    }

    @Test
    void initKeysOnStartupLoadsExistingActiveKey() throws Exception {
        // Prepare an encrypted key using KeyManager's internals (to make decryption succeed)
        KeyManager tempManager = new KeyManager(keyRepository, cryptoConfig, MASTER_SECRET, auditService);
        byte[] rawKey = new byte[32];
        java.security.SecureRandom.getInstanceStrong().nextBytes(rawKey);
        
        // Use reflection or just helper method to encrypt
        byte[] encryptedKey = encryptWithTestSecret(rawKey);

        CryptoKey existingKey = CryptoKey.builder()
                .version(3)
                .algorithm("AES")
                .status(KeyStatus.ACTIVE)
                .encryptedKey(encryptedKey)
                .build();

        when(keyRepository.findByStatus(KeyStatus.ACTIVE)).thenReturn(Optional.of(existingKey));

        keyManager.initKeysOnStartup();

        assertThat(keyManager.getActiveVersion()).isEqualTo(3);
        assertThat(keyManager.getActiveKey()).isEqualTo(rawKey);
    }

    @Test
    void rotateKeyRetiresOldActiveKeyAndCreatesNewActiveKey() {
        CryptoKey oldActiveKey = CryptoKey.builder()
                .version(1)
                .algorithm("AES")
                .status(KeyStatus.ACTIVE)
                .encryptedKey(new byte[44])
                .build();

        when(keyRepository.findByStatus(KeyStatus.ACTIVE)).thenReturn(Optional.of(oldActiveKey));
        when(keyRepository.findAll()).thenReturn(new ArrayList<>());
        when(cryptoConfig.getActiveAlgorithm()).thenReturn("CHACHA20");

        Integer newVersion = keyManager.rotateKey();

        verify(keyRepository).save(oldActiveKey); // Verify retired save
        verify(keyRepository, times(2)).save(any(CryptoKey.class)); // Verify retired and new save
        assertThat(oldActiveKey.getStatus()).isEqualTo(KeyStatus.RETIRED);
        assertThat(newVersion).isEqualTo(1);
    }

    private byte[] encryptWithTestSecret(byte[] rawKey) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] kek = digest.digest(MASTER_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);

        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        javax.crypto.spec.GCMParameterSpec spec = new javax.crypto.spec.GCMParameterSpec(128, iv);
        javax.crypto.spec.SecretKeySpec aesKeySpec = new javax.crypto.spec.SecretKeySpec(kek, "AES");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, aesKeySpec, spec);
        byte[] encrypted = cipher.doFinal(rawKey);

        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return result;
    }
}
