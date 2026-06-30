package com.cryptovault.service;

import com.cryptovault.config.CryptoConfig;
import com.cryptovault.entity.CryptoKey;
import com.cryptovault.entity.KeyStatus;
import com.cryptovault.repository.CryptoKeyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages versioned data keys. Performs envelope encryption on keys at rest under a Master Key (KEK).
 * Caches decrypted key material in-memory for performance.
 */
@Service
public class KeyManager {

    private final CryptoKeyRepository keyRepository;
    private final CryptoConfig cryptoConfig;
    private final String masterSecret;
    private final AuditService auditService;

    // Cache to hold decrypted keys: version -> raw key material (32 bytes)
    private final Map<Integer, byte[]> decryptedKeysCache = new ConcurrentHashMap<>();

    private volatile Integer activeVersion = null;

    public KeyManager(CryptoKeyRepository keyRepository,
                      CryptoConfig cryptoConfig,
                      @Value("${cryptovault.crypto.master-secret}") String masterSecret,
                      AuditService auditService) {
        this.keyRepository = keyRepository;
        this.cryptoConfig = cryptoConfig;
        this.masterSecret = masterSecret;
        this.auditService = auditService;
    }

    /**
     * Seeds initial key on startup if none exists.
     */
    @EventListener(ContextRefreshedEvent.class)
    @Transactional
    public void initKeysOnStartup() {
        try {
            Optional<CryptoKey> activeKeyOpt = keyRepository.findByStatus(KeyStatus.ACTIVE);
            if (activeKeyOpt.isEmpty()) {
                // If there is no active key, check what the max version is.
                int nextVersion = keyRepository.findAll().stream()
                        .map(CryptoKey::getVersion)
                        .max(Integer::compareTo)
                        .orElse(0) + 1;

                String activeAlgo = cryptoConfig.getActiveAlgorithm();
                byte[] rawKey = generateRandomKey();
                byte[] encryptedKey = encryptKey(rawKey);

                CryptoKey newKey = CryptoKey.builder()
                        .version(nextVersion)
                        .algorithm(activeAlgo)
                        .status(KeyStatus.ACTIVE)
                        .encryptedKey(encryptedKey)
                        .build();

                keyRepository.save(newKey);
                decryptedKeysCache.put(nextVersion, rawKey);
                activeVersion = nextVersion;
            } else {
                CryptoKey activeKey = activeKeyOpt.get();
                activeVersion = activeKey.getVersion();
                decryptedKeysCache.put(activeVersion, decryptKey(activeKey.getEncryptedKey()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize cryptographic keys on startup", e);
        }
    }

    /**
     * Gets the decrypted active key material.
     */
    public byte[] getActiveKey() {
        if (activeVersion == null) {
            throw new IllegalStateException("Active key has not been initialized");
        }
        return getKey(activeVersion);
    }

    /**
     * Gets the active key version number.
     */
    public Integer getActiveVersion() {
        if (activeVersion == null) {
            throw new IllegalStateException("Active key has not been initialized");
        }
        return activeVersion;
    }

    /**
     * Gets the decrypted key material for a specific version.
     */
    public byte[] getKey(int version) {
        return decryptedKeysCache.computeIfAbsent(version, v -> {
            CryptoKey cryptoKey = keyRepository.findByVersion(v)
                    .orElseThrow(() -> new IllegalArgumentException("Crypto key version " + v + " not found"));
            try {
                return decryptKey(cryptoKey.getEncryptedKey());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to decrypt key version " + v, e);
            }
        });
    }

    /**
     * Rotates the key: generates a new version, marks the previous version retired, and sets the new version as active.
     */
    @Transactional
    public synchronized Integer rotateKey() {
        try {
            // Find current active key and retire it
            Optional<CryptoKey> currentActiveOpt = keyRepository.findByStatus(KeyStatus.ACTIVE);
            if (currentActiveOpt.isPresent()) {
                CryptoKey currentActive = currentActiveOpt.get();
                currentActive.setStatus(KeyStatus.RETIRED);
                keyRepository.save(currentActive);
            }

            int nextVersion = keyRepository.findAll().stream()
                    .map(CryptoKey::getVersion)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;

            String activeAlgo = cryptoConfig.getActiveAlgorithm();
            byte[] rawKey = generateRandomKey();
            byte[] encryptedKey = encryptKey(rawKey);

            CryptoKey newKey = CryptoKey.builder()
                    .version(nextVersion)
                    .algorithm(activeAlgo)
                    .status(KeyStatus.ACTIVE)
                    .encryptedKey(encryptedKey)
                    .build();

            keyRepository.save(newKey);
            decryptedKeysCache.put(nextVersion, rawKey);
            activeVersion = nextVersion;

            // Log key rotation under the current authenticated admin user
            java.util.UUID adminUserId = null;
            try {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof String) {
                    adminUserId = java.util.UUID.fromString((String) auth.getPrincipal());
                }
            } catch (Exception e) {
                // Ignore if called outside request context (e.g. tests)
            }
            auditService.log(adminUserId, "KEY_ROTATE", null);

            return nextVersion;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rotate key", e);
        }
    }

    private byte[] generateRandomKey() {
        byte[] key = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(key);
        return key;
    }

    // Domain-separation parameters for KEK derivation. These are non-secret by design:
    // HKDF's security comes from the high-entropy master secret (the IKM), while the salt
    // and info label provide the extract-then-expand construction and bind the derived key
    // to this specific purpose. Fixed (not random) so the KEK re-derives deterministically
    // on every startup without ever being stored.
    private static final byte[] KEK_SALT = "cryptovault-hkdf-kek-salt-v1".getBytes(StandardCharsets.UTF_8);
    private static final byte[] KEK_INFO = "cryptovault-master-kek/aes-256-gcm/v1".getBytes(StandardCharsets.UTF_8);

    /**
     * Derives the 256-bit Key-Encrypting-Key from the master secret using HKDF-SHA256.
     *
     * <p>HKDF (RFC 5869) is the right primitive here because the master secret is high-entropy:
     * a raw {@code SHA-256(secret)} has no salt and no domain separation. (If the master secret
     * were a human password instead, this would need a slow, memory-hard KDF such as Argon2id.)
     */
    private byte[] getKek() {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(masterSecret.getBytes(StandardCharsets.UTF_8), KEK_SALT, KEK_INFO));
        byte[] kek = new byte[32];
        hkdf.generateBytes(kek, 0, 32);
        return kek;
    }

    private byte[] encryptKey(byte[] rawKey) throws Exception {
        byte[] kek = getKek();
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        SecretKeySpec keySpec = new SecretKeySpec(kek, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);
        byte[] encrypted = cipher.doFinal(rawKey);

        byte[] result = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
        return result;
    }

    private byte[] decryptKey(byte[] encryptedKeyBlob) throws Exception {
        byte[] kek = getKek();
        byte[] iv = new byte[12];
        System.arraycopy(encryptedKeyBlob, 0, iv, 0, iv.length);

        int encryptedLen = encryptedKeyBlob.length - iv.length;
        byte[] encrypted = new byte[encryptedLen];
        System.arraycopy(encryptedKeyBlob, iv.length, encrypted, 0, encryptedLen);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        SecretKeySpec keySpec = new SecretKeySpec(kek, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);
        return cipher.doFinal(encrypted);
    }
}
