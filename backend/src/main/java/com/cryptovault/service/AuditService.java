package com.cryptovault.service;

import com.cryptovault.entity.AuditLog;
import com.cryptovault.entity.User;
import com.cryptovault.repository.AuditLogRepository;
import com.cryptovault.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Service managing audit logging, writing security-relevant actions to the database.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditRepository, UserRepository userRepository) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    /**
     * Writes a new audit log entry.
     */
    @Transactional
    public void log(UUID userId, String action, String ipAddress) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }

        String ip = ipAddress;
        if (ip == null) {
            ip = getIpFromRequest();
        }

        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .ipAddress(ip)
                .build();

        auditRepository.save(log);
    }

    /**
     * Gets a page of audit logs filtered by optional user ID and action.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getFilteredLogs(UUID userId, String action, Pageable pageable) {
        return auditRepository.findFiltered(userId, action, pageable);
    }

    private String getIpFromRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ipAddress = request.getRemoteAddr();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    ipAddress = xForwardedFor.split(",")[0].trim();
                }
                return ipAddress;
            }
        } catch (Exception e) {
            // Context might not be a request-bound thread
        }
        return "SYSTEM";
    }
}
