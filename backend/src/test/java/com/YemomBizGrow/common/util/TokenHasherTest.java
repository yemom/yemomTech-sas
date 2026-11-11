package com.YemomBizGrow.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    @DisplayName("raw tokens are URL-safe, high-entropy and non-repeating")
    void generatesUniqueUrlSafeTokens() {
        String first = TokenHasher.newRawToken();
        String second = TokenHasher.newRawToken();

        assertThat(first).isNotEqualTo(second);
        // 32 bytes -> 43 base64url characters without padding.
        assertThat(first).hasSize(43).matches("^[A-Za-z0-9_-]+$");
        assertThat(second).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    @DisplayName("sha256Hex produces the canonical lowercase digest")
    void hashesDeterministically() {
        // Known SHA-256 vector for "abc".
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TokenHasher.sha256Hex("abc")).isEqualTo(TokenHasher.sha256Hex("abc"));
        assertThat(TokenHasher.sha256Hex("abc")).hasSize(64);
    }

    @Test
    @DisplayName("digests are case- and content-sensitive")
    void differsForDifferentInput() {
        assertThat(TokenHasher.sha256Hex("token-a")).isNotEqualTo(TokenHasher.sha256Hex("token-b"));
        assertThat(TokenHasher.sha256Hex("Token")).isNotEqualTo(TokenHasher.sha256Hex("token"));
    }
}
