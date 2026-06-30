package com.cryptovault.service;

import com.cryptovault.crypto.CryptoEngine;
import com.cryptovault.dto.VaultDecryptResponse;
import com.cryptovault.dto.VaultMetadataResponse;
import com.cryptovault.dto.VaultStoreRequest;
import com.cryptovault.entity.CryptoKey;
import com.cryptovault.entity.User;
import com.cryptovault.entity.VaultRecord;
import com.cryptovault.repository.CryptoKeyRepository;
import com.cryptovault.repository.UserRepository;
import com.cryptovault.repository.VaultRecordRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service orchestrating vault store, list, retrieve (decrypt), and delete operations.
 */
@Service
public class VaultService {

    private final VaultRecordRepository vaultRepository;
    private final UserRepository userRepository;
    private final CryptoKeyRepository keyRepository;
    private final CryptoEngine cryptoEngine;
    private final KeyManager keyManager;
    private final AuditService auditService;

    public VaultService(VaultRecordRepository vaultRepository,
                        UserRepository userRepository,
                        CryptoKeyRepository keyRepository,
                        CryptoEngine cryptoEngine,
                        KeyManager keyManager,
                        AuditService auditService) {
        this.vaultRepository = vaultRepository;
        this.userRepository = userRepository;
        this.keyRepository = keyRepository;
        this.cryptoEngine = cryptoEngine;
        this.keyManager = keyManager;
        this.auditService = auditService;
    }

    /**
     * Encrypts and stores a new secret payload under the active algorithm and key version.
     */
    @Transactional
    public VaultMetadataResponse store(VaultStoreRequest request, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        byte[] activeKey = keyManager.getActiveKey();
        Integer activeVersion = keyManager.getActiveVersion();

        CryptoKey cryptoKey = keyRepository.findByVersion(activeVersion)
                .orElseThrow(() -> new IllegalStateException("Crypto key version " + activeVersion + " not found in DB"));

        String activeAlgo = cryptoEngine.getActiveStrategy().name();

        try {
            byte[] ciphertextBlob = cryptoEngine.encrypt(request.secret().getBytes(StandardCharsets.UTF_8), activeKey);

            VaultRecord record = VaultRecord.builder()
                    .user(user)
                    .name(request.name())
                    .encryptedData(ciphertextBlob)
                    .algorithmUsed(activeAlgo)
                    .cryptoKey(cryptoKey)
                    .build();

            VaultRecord saved = vaultRepository.save(record);

            auditService.log(userId, "VAULT_STORE", null);

            return new VaultMetadataResponse(
                    saved.getId(),
                    saved.getName(),
                    saved.getAlgorithmUsed(),
                    saved.getCryptoKey().getVersion(),
                    saved.getCreatedAt()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt and store secret", e);
        }
    }

    /**
     * Lists active (non-soft-deleted) secrets metadata for the current user.
     */
    @Transactional(readOnly = true)
    public List<VaultMetadataResponse> list(UUID userId) {
        return vaultRepository.findActiveByUserId(userId).stream()
                .map(record -> new VaultMetadataResponse(
                        record.getId(),
                        record.getName(),
                        record.getAlgorithmUsed(),
                        record.getCryptoKey().getVersion(),
                        record.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Decrypts and retrieves a specific secret. Restricted to owner.
     */
    @Transactional(readOnly = true)
    public VaultDecryptResponse retrieve(UUID recordId, UUID userId) {
        VaultRecord record = vaultRepository.findActiveById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Record not found or deleted: " + recordId));

        if (!record.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You are not authorized to view this secret");
        }

        int keyVersion = record.getCryptoKey().getVersion();
        byte[] rawKey = keyManager.getKey(keyVersion);

        try {
            byte[] plaintextBytes = cryptoEngine.decrypt(record.getEncryptedData(), rawKey, record.getAlgorithmUsed());
            String secret = new String(plaintextBytes, StandardCharsets.UTF_8);

            auditService.log(userId, "VAULT_READ", null);

            return new VaultDecryptResponse(
                    record.getId(),
                    record.getName(),
                    secret
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    /**
     * Performs a soft delete by marking deletedAt timestamp. Restricted to owner.
     */
    @Transactional
    public void delete(UUID recordId, UUID userId) {
        VaultRecord record = vaultRepository.findActiveById(recordId)
                .orElseThrow(() -> new IllegalArgumentException("Record not found or already deleted: " + recordId));

        if (!record.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You are not authorized to delete this secret");
        }

        record.setDeletedAt(LocalDateTime.now());
        vaultRepository.save(record);

        auditService.log(userId, "VAULT_DELETE", null);
    }
}
