package com.cryptovault.crypto;

/**
 * Interface defining a cryptographic algorithm strategy (AES-GCM, ChaCha20-Poly1305, etc.).
 * Enables pluggable crypto agility.
 */
public interface CipherStrategy {

    /**
     * Encrypts the plaintext using the provided raw key.
     * Generates a fresh random nonce internally.
     *
     * @return The encrypted blob formatted as {@code nonce || ciphertext}.
     */
    byte[] encrypt(byte[] plaintext, byte[] key) throws Exception;

    /**
     * Decrypts the cipher blob formatted as {@code nonce || ciphertext} using the provided raw key.
     */
    byte[] decrypt(byte[] blob, byte[] key) throws Exception;

    /**
     * The unique name of the algorithm (e.g. "AES", "CHACHA20").
     */
    String name();
}
