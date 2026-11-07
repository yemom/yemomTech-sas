package com.YemomBizGrow.integration.google;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM encryption/decryption for sensitive data at rest.
 *
 * <p>Format: Base64(IV_12_bytes || ciphertext_with_16_byte_tag)</p>
 *
 * <p>The 32-byte key is supplied as a Base64-encoded string via
 * {@code app.security.encryption-key}. Keys must be rotated by
 * re-encrypting all stored tokens and updating the environment variable.</p>
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptionService(EncryptionProperties props) {
        byte[] keyBytes = Base64.getDecoder().decode(props.getKey());
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "app.security.encryption-key must be a Base64-encoded 32-byte (256-bit) AES key");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts {@code plaintext} and returns a Base64 encoded string
     * containing the IV prepended to the ciphertext+tag.
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(IV_LENGTH + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);

            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts a value previously produced by {@link #encrypt(String)}.
     */
    public String decrypt(String encoded) {
        try {
            byte[] data = Base64.getDecoder().decode(encoded);
            ByteBuffer buf = ByteBuffer.wrap(data);

            byte[] iv = new byte[IV_LENGTH];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
