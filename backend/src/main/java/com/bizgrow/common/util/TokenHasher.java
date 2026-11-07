package com.bizgrow.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Cryptographic helpers shared by auth and invitations.
 *
 * <p>Design rule: raw bearer-style secrets (refresh tokens, invitation tokens)
 * are generated from a {@link SecureRandom} with 256 bits of entropy and are
 * persisted ONLY as SHA-256 digests. SHA-256 (rather than bcrypt) is correct
 * here because the inputs are already high-entropy random strings — there is
 * nothing to brute-force — and digests must be look-uppable by exact match.</p>
 */
public final class TokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TokenHasher() {
    }

    /** URL-safe random token, 256 bits of entropy, no padding (safe in cookies/links). */
    public static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lowercase hex SHA-256 of the input (64 characters, matching the DB columns). */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            char[] out = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) {
                out[i * 2] = HEX[(hash[i] >> 4) & 0xF];
                out[i * 2 + 1] = HEX[hash[i] & 0xF];
            }
            return new String(out);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java platform spec.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
