package com.cryptovault.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Component;

/**
 * ChaCha20-Poly1305 implementation of CipherStrategy.
 * Uses the Bouncy Castle security provider.
 * Encrypted blob format: {@code nonce (12 bytes) || ciphertext (includes 16-byte auth tag)}.
 */
@Component
public class ChaCha20Poly1305Strategy implements CipherStrategy {

    private static final String ALGORITHM = "ChaCha20-Poly1305";
    private static final int KEY_SIZE_BYTES = 32;
    private static final int NONCE_SIZE_BYTES = 12;

    private final SecureRandom secureRandom = new SecureRandom();

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public byte[] encrypt(byte[] plaintext, byte[] key) throws Exception {
        if (key.length != KEY_SIZE_BYTES) {
            throw new IllegalArgumentException("Key must be 32 bytes (256 bits) for ChaCha20-Poly1305");
        }
        byte[] nonce = new byte[NONCE_SIZE_BYTES];
        secureRandom.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "ChaCha20");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] ciphertext = cipher.doFinal(plaintext);

        byte[] blob = new byte[nonce.length + ciphertext.length];
        System.arraycopy(nonce, 0, blob, 0, nonce.length);
        System.arraycopy(ciphertext, 0, blob, nonce.length, ciphertext.length);
        return blob;
    }

    @Override
    public byte[] decrypt(byte[] blob, byte[] key) throws Exception {
        if (key.length != KEY_SIZE_BYTES) {
            throw new IllegalArgumentException("Key must be 32 bytes (256 bits) for ChaCha20-Poly1305");
        }
        if (blob.length < NONCE_SIZE_BYTES + 16) {
            throw new IllegalArgumentException("Cipher blob too short");
        }
        byte[] nonce = new byte[NONCE_SIZE_BYTES];
        System.arraycopy(blob, 0, nonce, 0, NONCE_SIZE_BYTES);

        int ciphertextLen = blob.length - NONCE_SIZE_BYTES;
        byte[] ciphertext = new byte[ciphertextLen];
        System.arraycopy(blob, NONCE_SIZE_BYTES, ciphertext, 0, ciphertextLen);

        Cipher cipher = Cipher.getInstance(ALGORITHM, "BC");
        SecretKeySpec keySpec = new SecretKeySpec(key, "ChaCha20");
        IvParameterSpec ivSpec = new IvParameterSpec(nonce);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        return cipher.doFinal(ciphertext);
    }

    @Override
    public String name() {
        return "CHACHA20";
    }
}
