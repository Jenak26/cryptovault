package com.cryptovault.repository;

import com.cryptovault.entity.CryptoKey;
import com.cryptovault.entity.KeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CryptoKeyRepository extends JpaRepository<CryptoKey, Long> {
    Optional<CryptoKey> findByVersion(Integer version);
    Optional<CryptoKey> findByStatus(KeyStatus status);
}
