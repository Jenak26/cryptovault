package com.cryptovault.repository;

import com.cryptovault.entity.MfaBackupCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, Long> {

    /** Finds an unredeemed backup code matching the hash for a user (for login redemption). */
    Optional<MfaBackupCode> findByUserIdAndCodeHashAndUsedFalse(UUID userId, String codeHash);

    /** Clears a user's codes (on regeneration or when MFA is disabled). */
    void deleteByUserId(UUID userId);
}
