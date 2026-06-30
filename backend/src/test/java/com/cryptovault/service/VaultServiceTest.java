package com.cryptovault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.cryptovault.crypto.CipherStrategy;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class VaultServiceTest {

    @Mock
    private VaultRecordRepository vaultRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CryptoKeyRepository keyRepository;
    @Mock
    private CryptoEngine cryptoEngine;
    @Mock
    private KeyManager keyManager;
    @Mock
    private AuditService auditService;

    private VaultService vaultService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        vaultService = new VaultService(vaultRepository, userRepository, keyRepository, cryptoEngine, keyManager, auditService);
        userId = UUID.randomUUID();
        user = User.builder().id(userId).email("user@bank.com").build();
    }

    @Test
    void storeEncryptsAndSavesRecord() throws Exception {
        VaultStoreRequest request = new VaultStoreRequest("my-secret", "sensitive-payload");
        byte[] activeKey = new byte[32];
        byte[] encryptedPayload = "encrypted-payload-bytes".getBytes(StandardCharsets.UTF_8);

        CryptoKey cryptoKey = CryptoKey.builder().version(1).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(keyManager.getActiveKey()).thenReturn(activeKey);
        when(keyManager.getActiveVersion()).thenReturn(1);
        when(keyRepository.findByVersion(1)).thenReturn(Optional.of(cryptoKey));

        CipherStrategy mockStrategy = mock(CipherStrategy.class);
        when(mockStrategy.name()).thenReturn("AES");
        when(cryptoEngine.getActiveStrategy()).thenReturn(mockStrategy);
        when(cryptoEngine.encrypt(any(), eq(activeKey))).thenReturn(encryptedPayload);

        VaultRecord savedRecord = VaultRecord.builder()
                .id(UUID.randomUUID())
                .name("my-secret")
                .encryptedData(encryptedPayload)
                .algorithmUsed("AES")
                .cryptoKey(cryptoKey)
                .user(user)
                .build();
        when(vaultRepository.save(any(VaultRecord.class))).thenReturn(savedRecord);

        VaultMetadataResponse response = vaultService.store(request, userId);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("my-secret");
        verify(vaultRepository).save(any(VaultRecord.class));
        verify(auditService).log(userId, "VAULT_STORE", null);
    }

    @Test
    void retrieveDecryptsOwnerRecord() throws Exception {
        UUID recordId = UUID.randomUUID();
        byte[] encryptedData = "encrypted".getBytes(StandardCharsets.UTF_8);
        byte[] decryptedData = "decrypted-secret".getBytes(StandardCharsets.UTF_8);
        byte[] rawKey = new byte[32];

        CryptoKey cryptoKey = CryptoKey.builder().version(2).build();
        VaultRecord record = VaultRecord.builder()
                .id(recordId)
                .name("my-secret")
                .user(user)
                .cryptoKey(cryptoKey)
                .encryptedData(encryptedData)
                .algorithmUsed("AES")
                .build();

        when(vaultRepository.findActiveById(recordId)).thenReturn(Optional.of(record));
        when(keyManager.getKey(2)).thenReturn(rawKey);
        when(cryptoEngine.decrypt(encryptedData, rawKey, "AES")).thenReturn(decryptedData);

        VaultDecryptResponse response = vaultService.retrieve(recordId, userId);

        assertThat(response).isNotNull();
        assertThat(response.secret()).isEqualTo("decrypted-secret");
        verify(auditService).log(userId, "VAULT_READ", null);
    }

    @Test
    void retrieveRejectsNonOwnerWithAccessDenied() {
        UUID recordId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder().id(otherUserId).build();

        VaultRecord record = VaultRecord.builder()
                .id(recordId)
                .user(otherUser)
                .build();

        when(vaultRepository.findActiveById(recordId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> vaultService.retrieve(recordId, userId))
                .isInstanceOf(AccessDeniedException.class);

        verify(auditService, never()).log(any(), eq("VAULT_READ"), any());
    }

    @Test
    void deleteRejectsNonOwnerWithAccessDenied() {
        UUID recordId = UUID.randomUUID();
        User otherUser = User.builder().id(UUID.randomUUID()).build();
        VaultRecord record = VaultRecord.builder()
                .id(recordId)
                .user(otherUser)
                .build();

        when(vaultRepository.findActiveById(recordId)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> vaultService.delete(recordId, userId))
                .isInstanceOf(AccessDeniedException.class);

        // The record must not be soft-deleted and no audit/save should happen on a denied attempt.
        assertThat(record.getDeletedAt()).isNull();
        verify(vaultRepository, never()).save(any(VaultRecord.class));
        verify(auditService, never()).log(any(), eq("VAULT_DELETE"), any());
    }

    @Test
    void deleteSoftDeletesRecord() {
        UUID recordId = UUID.randomUUID();
        VaultRecord record = VaultRecord.builder()
                .id(recordId)
                .user(user)
                .build();

        when(vaultRepository.findActiveById(recordId)).thenReturn(Optional.of(record));

        vaultService.delete(recordId, userId);

        assertThat(record.getDeletedAt()).isNotNull();
        verify(vaultRepository).save(record);
        verify(auditService).log(userId, "VAULT_DELETE", null);
    }
}
