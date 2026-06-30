package com.cryptovault.controller;

import com.cryptovault.service.KeyManager;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * Controller for key rotation. Restricted to administrators.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminKeyController {

    private final KeyManager keyManager;

    public AdminKeyController(KeyManager keyManager) {
        this.keyManager = keyManager;
    }

    /**
     * Triggers key rotation, generating a new data key and retiring the old one.
     */
    @PostMapping("/rotate-key")
    public Map<String, Object> rotateKey() {
        Integer newVersion = keyManager.rotateKey();
        return Map.of(
                "version", newVersion,
                "message", "Key rotated successfully"
        );
    }
}
