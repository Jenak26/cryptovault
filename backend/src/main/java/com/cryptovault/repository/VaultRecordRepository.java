package com.cryptovault.repository;

import com.cryptovault.entity.VaultRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaultRecordRepository extends JpaRepository<VaultRecord, UUID> {
    
    @Query("SELECT vr FROM VaultRecord vr WHERE vr.id = :id AND vr.deletedAt IS NULL")
    Optional<VaultRecord> findActiveById(UUID id);

    @Query("SELECT vr FROM VaultRecord vr WHERE vr.user.id = :userId AND vr.deletedAt IS NULL")
    List<VaultRecord> findActiveByUserId(UUID userId);

    @Query("SELECT vr FROM VaultRecord vr WHERE vr.deletedAt IS NULL")
    org.springframework.data.domain.Page<VaultRecord> findActivePage(org.springframework.data.domain.Pageable pageable);
}
