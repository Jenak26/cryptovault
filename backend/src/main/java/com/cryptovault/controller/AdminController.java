package com.cryptovault.controller;

import com.cryptovault.entity.AuditLog;
import com.cryptovault.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.UUID;

import com.cryptovault.service.ReEncryptionService;
import org.springframework.web.bind.annotation.PostMapping;
import java.security.Principal;

/**
 * Controller for admin-only operations. Protected at class level using RBAC.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditService auditService;
    private final ReEncryptionService reEncryptionService;

    public AdminController(AuditService auditService, ReEncryptionService reEncryptionService) {
        this.auditService = auditService;
        this.reEncryptionService = reEncryptionService;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "pong", "message", "Admin authority verified");
    }

    @GetMapping("/audit")
    public Page<AuditLog> getAuditLogs(
            @RequestParam(name = "userId", required = false) UUID userId,
            @RequestParam(name = "action", required = false) String action,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return auditService.getFilteredLogs(
                userId,
                action,
                PageRequest.of(page, size, Sort.by("timestamp").descending())
        );
    }

    @PostMapping("/re-encrypt")
    public Map<String, Object> triggerReEncryption(Principal principal) {
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        UUID adminUserId = UUID.fromString(principal.getName());
        int count = reEncryptionService.reEncryptAll(adminUserId);
        return Map.of("status", "success", "migratedRecords", count);
    }
}
