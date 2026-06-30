package com.cryptovault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.cryptovault.crypto.CipherStrategy;
import com.cryptovault.crypto.CryptoEngine;
import com.cryptovault.entity.CryptoKey;
import com.cryptovault.entity.VaultRecord;
import com.cryptovault.repository.CryptoKeyRepository;
import com.cryptovault.repository.VaultRecordRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ReEncryptionServiceTest {

    @Mock
    private VaultRecordRepository vaultRepository;
    @Mock
    private CryptoKeyRepository keyRepository;
    @Mock
    private CryptoEngine cryptoEngine;
    @Mock
    private KeyManager keyManager;
    @Mock
    private AuditService auditService;

    private ReEncryptionService reEncryptionService;

    private UUID adminUserId;

    @BeforeEach
    void setUp() {
        reEncryptionService = new ReEncryptionService(vaultRepository, keyRepository, cryptoEngine, keyManager, auditService);
        adminUserId = UUID.randomUUID();
    }

    @Test
    void reEncryptAllSkipsUpToDateRecords() {
        int activeVersion = 2;
        byte[] activeKey = new byte[32];

        when(keyManager.getActiveVersion()).thenReturn(activeVersion);
        when(keyManager.getActiveKey()).thenReturn(activeKey);

        CipherStrategy mockStrategy = mock(CipherStrategy.class);
        when(mockStrategy.name()).thenReturn("AES");
        when(cryptoEngine.getActiveStrategy()).thenReturn(mockStrategy);

        CryptoKey activeKeyEntity = CryptoKey.builder().version(2).build();
        when(keyRepository.findByVersion(2)).thenReturn(Optional.of(activeKeyEntity));

        VaultRecord upToDateRecord = VaultRecord.builder()
                .algorithmUsed("AES")
                .cryptoKey(activeKeyEntity)
                .build();

        Page<VaultRecord> page = new PageImpl<>(List.of(upToDateRecord));
        when(vaultRepository.findActivePage(PageRequest.of(0, 50))).thenReturn(page);

        int migrated = reEncryptionService.reEncryptAll(adminUserId);

        assertThat(migrated).isZero();
        verify(vaultRepository, never()).save(any());
        verify(auditService, never()).log(any(), any(), any());
    }

    @Test
    void reEncryptAllMigratesOutdatedRecords() throws Exception {
        int activeVersion = 2;
        byte[] activeKey = new byte[32];
        byte[] oldKey = new byte[32];
        byte[] oldEncryptedData = "old-ciphertext".getBytes(StandardCharsets.UTF_8);
        byte[] decryptedPlaintext = "plaintext".getBytes(StandardCharsets.UTF_8);
        byte[] newCiphertext = "new-ciphertext".getBytes(StandardCharsets.UTF_8);

        when(keyManager.getActiveVersion()).thenReturn(activeVersion);
        when(keyManager.getActiveKey()).thenReturn(activeKey);

        CipherStrategy mockStrategy = mock(CipherStrategy.class);
        when(mockStrategy.name()).thenReturn("CHACHA20");
        when(cryptoEngine.getActiveStrategy()).thenReturn(mockStrategy);

        CryptoKey activeKeyEntity = CryptoKey.builder().version(2).build();
        when(keyRepository.findByVersion(2)).thenReturn(Optional.of(activeKeyEntity));

        CryptoKey oldKeyEntity = CryptoKey.builder().version(1).build();
        VaultRecord outdatedRecord = VaultRecord.builder()
                .id(UUID.randomUUID())
                .algorithmUsed("AES")
                .cryptoKey(oldKeyEntity)
                .encryptedData(oldEncryptedData)
                .build();

        Page<VaultRecord> page = new PageImpl<>(List.of(outdatedRecord));
        when(vaultRepository.findActivePage(PageRequest.of(0, 50))).thenReturn(page);

        when(keyManager.getKey(1)).thenReturn(oldKey);
        when(cryptoEngine.decrypt(oldEncryptedData, oldKey, "AES")).thenReturn(decryptedPlaintext);
        when(cryptoEngine.encrypt(decryptedPlaintext, activeKey)).thenReturn(newCiphertext);

        int migrated = reEncryptionService.reEncryptAll(adminUserId);

        assertThat(migrated).isEqualTo(1);
        assertThat(outdatedRecord.getAlgorithmUsed()).isEqualTo("CHACHA20");
        assertThat(outdatedRecord.getCryptoKey()).isEqualTo(activeKeyEntity);
        assertThat(outdatedRecord.getEncryptedData()).isEqualTo(newCiphertext);

        verify(vaultRepository).save(outdatedRecord);
        verify(auditService).log(adminUserId, "VAULT_RE_ENCRYPT", null);
    }
}
