package com.cryptovault.controller;

import com.cryptovault.dto.VaultDecryptResponse;
import com.cryptovault.dto.VaultMetadataResponse;
import com.cryptovault.dto.VaultStoreRequest;
import com.cryptovault.service.VaultService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Controller exposing REST API endpoints for user secrets vault management.
 */
@RestController
@RequestMapping("/api/vault")
public class VaultController {

    private final VaultService vaultService;

    public VaultController(VaultService vaultService) {
        this.vaultService = vaultService;
    }

    /**
     * Encrypts and stores a new secret.
     */
    @PostMapping("/store")
    @ResponseStatus(HttpStatus.CREATED)
    public VaultMetadataResponse store(@Valid @RequestBody VaultStoreRequest request, Principal principal) {
        return vaultService.store(request, getUserId(principal));
    }

    /**
     * Lists active metadata records for the current user.
     */
    @GetMapping
    public List<VaultMetadataResponse> list(Principal principal) {
        return vaultService.list(getUserId(principal));
    }

    /**
     * Decrypts and retrieves a specific secret.
     */
    @GetMapping("/{id}")
    public VaultDecryptResponse retrieve(@PathVariable("id") UUID id, Principal principal) {
        return vaultService.retrieve(id, getUserId(principal));
    }

    /**
     * Soft-deletes a specific secret record.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") UUID id, Principal principal) {
        vaultService.delete(id, getUserId(principal));
    }

    private UUID getUserId(Principal principal) {
        if (principal == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not authenticated");
        }
        return UUID.fromString(principal.getName());
    }
}
