package com.cryptovault.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Reads cryptographic settings from configuration properties.
 */
@Configuration
public class CryptoConfig {

    @Value("${cryptovault.crypto.active-algorithm:AES-256-GCM}")
    private String activeAlgorithm;

    /**
     * Standardizes and returns the active algorithm name (e.g. "AES", "CHACHA20").
     */
    public String getActiveAlgorithm() {
        String algo = activeAlgorithm.toUpperCase();
        if (algo.contains("CHACHA")) {
            return "CHACHA20";
        }
        return "AES";
    }
}
