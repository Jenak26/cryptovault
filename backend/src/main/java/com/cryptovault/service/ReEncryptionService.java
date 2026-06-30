package com.cryptovault.service;

import com.cryptovault.crypto.CryptoEngine;
import com.cryptovault.entity.CryptoKey;
import com.cryptovault.entity.VaultRecord;
import com.cryptovault.repository.CryptoKeyRepository;
import com.cryptovault.repository.VaultRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service executing background/batch re-encryption migrations on stored secrets.
 * Elevates records to the current active data key and algorithm version.
 */
@Service
public class ReEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(ReEncryptionService.class);

    private final VaultRecordRepository vaultRepository;
    private final CryptoKeyRepository keyRepository;
    private final CryptoEngine cryptoEngine;
    private final KeyManager keyManager;
    private final AuditService auditService;

    public ReEncryptionService(VaultRecordRepository vaultRepository,
                               CryptoKeyRepository keyRepository,
                               CryptoEngine cryptoEngine,
                               KeyManager keyManager,
                               AuditService auditService) {
        this.vaultRepository = vaultRepository;
        this.keyRepository = keyRepository;
        this.cryptoEngine = cryptoEngine;
        this.keyManager = keyManager;
        this.auditService = auditService;
    }

    /**
     * Scans and re-encrypts all active records using the current active key version and strategy.
     * Retains old records' plaintexts while migrating them to the latest algorithm and key.
     * Runs in paginated chunks to avoid high heap utilization.
     */
    @Transactional
    public int reEncryptAll(UUID adminUserId) {
        int activeVersion = keyManager.getActiveVersion();
        String activeAlgo = cryptoEngine.getActiveStrategy().name();
        byte[] activeKeyMaterial = keyManager.getActiveKey();

        CryptoKey activeKeyEntity = keyRepository.findByVersion(activeVersion)
                .orElseThrow(() -> new IllegalStateException("Active key entity v" + activeVersion + " not found in DB"));

        int totalMigrated = 0;
        int pageNumber = 0;
        final int pageSize = 50;

        Page<VaultRecord> page;
        do {
            page = vaultRepository.findActivePage(PageRequest.of(pageNumber, pageSize));
            for (VaultRecord record : page.getContent()) {
                int recordKeyVersion = record.getCryptoKey().getVersion();
                String recordAlgo = record.getAlgorithmUsed();

                // If it already matches the active key and strategy, skip
                if (recordKeyVersion == activeVersion && recordAlgo.equalsIgnoreCase(activeAlgo)) {
                    continue;
                }

                try {
                    // Decrypt using the old historical key version
                    byte[] oldRawKey = keyManager.getKey(recordKeyVersion);
                    byte[] plaintext = cryptoEngine.decrypt(record.getEncryptedData(), oldRawKey, recordAlgo);

                    // Re-encrypt under the new active key version and strategy
                    byte[] newCiphertext = cryptoEngine.encrypt(plaintext, activeKeyMaterial);

                    // Update entity mappings
                    record.setEncryptedData(newCiphertext);
                    record.setAlgorithmUsed(activeAlgo);
                    record.setCryptoKey(activeKeyEntity);

                    vaultRepository.save(record);
                    totalMigrated++;
                } catch (Exception e) {
                    log.error("Failed to migrate vault record {} to key v{} (algo {}): {}", 
                            record.getId(), activeVersion, activeAlgo, e.getMessage());
                    throw new IllegalStateException("Re-encryption batch failed on record: " + record.getId(), e);
                }
            }
            pageNumber++;
        } while (page.hasNext());

        if (totalMigrated > 0) {
            auditService.log(adminUserId, "VAULT_RE_ENCRYPT", null);
        }

        return totalMigrated;
    }
}
