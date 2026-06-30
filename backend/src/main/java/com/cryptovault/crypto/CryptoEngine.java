package com.cryptovault.crypto;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.cryptovault.config.CryptoConfig;

/**
 * Orchestrates encryption and decryption by dispatching to pluggable strategies.
 * Implements crypto-agility.
 */
@Component
public class CryptoEngine {

    private final Map<String, CipherStrategy> strategies;
    private final CryptoConfig config;

    public CryptoEngine(List<CipherStrategy> strategyList, CryptoConfig config) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        strategy -> strategy.name().toUpperCase(),
                        strategy -> strategy
                ));
        this.config = config;
    }

    /**
     * Gets the currently active encryption strategy.
     */
    public CipherStrategy getActiveStrategy() {
        String activeName = config.getActiveAlgorithm().toUpperCase();
        CipherStrategy strategy = strategies.get(activeName);
        if (strategy == null) {
            throw new IllegalStateException("No CipherStrategy registered for active algorithm: " + activeName);
        }
        return strategy;
    }

    /**
     * Gets a strategy by name (supporting flexible naming mapping).
     */
    public CipherStrategy getStrategy(String name) {
        String lookupName = name.toUpperCase();
        if (lookupName.contains("CHACHA")) {
            lookupName = "CHACHA20";
        } else if (lookupName.contains("AES")) {
            lookupName = "AES";
        }
        CipherStrategy strategy = strategies.get(lookupName);
        if (strategy == null) {
            throw new IllegalArgumentException("No CipherStrategy registered for algorithm: " + name);
        }
        return strategy;
    }

    /**
     * Encrypts the plaintext using the active strategy and key.
     */
    public byte[] encrypt(byte[] plaintext, byte[] key) throws Exception {
        return getActiveStrategy().encrypt(plaintext, key);
    }

    /**
     * Decrypts the blob using the strategy corresponding to the algorithm used during encryption.
     */
    public byte[] decrypt(byte[] blob, byte[] key, String algorithmName) throws Exception {
        return getStrategy(algorithmName).decrypt(blob, key);
    }
}
