package com.cryptovault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.cryptovault.entity.AuditLog;
import com.cryptovault.entity.User;
import com.cryptovault.repository.AuditLogRepository;
import com.cryptovault.repository.UserRepository;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditRepository;

    @Mock
    private UserRepository userRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditRepository, userRepository);
    }

    @Test
    void logSavesAuditEntryWithUser() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).email("admin@bank.com").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        auditService.log(userId, "KEY_ROTATE", "192.168.1.1");

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepository).save(logCaptor.capture());

        AuditLog savedLog = logCaptor.getValue();
        assertThat(savedLog).isNotNull();
        assertThat(savedLog.getUser()).isEqualTo(user);
        assertThat(savedLog.getAction()).isEqualTo("KEY_ROTATE");
        assertThat(savedLog.getIpAddress()).isEqualTo("192.168.1.1");
    }

    @Test
    void logSavesAuditEntryWithNullUserForFailedLogins() {
        auditService.log(null, "LOGIN_FAIL", "10.0.0.1");

        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditRepository).save(logCaptor.capture());

        AuditLog savedLog = logCaptor.getValue();
        assertThat(savedLog).isNotNull();
        assertThat(savedLog.getUser()).isNull();
        assertThat(savedLog.getAction()).isEqualTo("LOGIN_FAIL");
        assertThat(savedLog.getIpAddress()).isEqualTo("10.0.0.1");
    }

    @Test
    void getFilteredLogsCallsRepository() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> expectedPage = new PageImpl<>(new ArrayList<>());

        when(auditRepository.findFiltered(userId, "VAULT_READ", pageable)).thenReturn(expectedPage);

        Page<AuditLog> result = auditService.getFilteredLogs(userId, "VAULT_READ", pageable);

        assertThat(result).isEqualTo(expectedPage);
        verify(auditRepository).findFiltered(userId, "VAULT_READ", pageable);
    }
}
