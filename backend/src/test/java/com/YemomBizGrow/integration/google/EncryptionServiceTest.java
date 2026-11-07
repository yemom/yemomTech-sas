package com.YemomBizGrow.integration.google;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        EncryptionProperties properties = new EncryptionProperties();
        properties.setKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        encryptionService = new EncryptionService(properties);
    }

    @Test
    void encryptAndDecrypt_recoversOriginalPlaintext() {
        String original = "ya29.a0AXooCgs_super_secret_google_refresh_token_12345";
        String encrypted = encryptionService.encrypt(original);

        assertThat(encrypted).isNotBlank();
        assertThat(encrypted).isNotEqualTo(original);

        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void encrypt_producesDifferentCiphertextEachTimeDueToRandomIv() {
        String token = "same_token_value";
        String enc1 = encryptionService.encrypt(token);
        String enc2 = encryptionService.encrypt(token);

        assertThat(enc1).isNotEqualTo(enc2);
        assertThat(encryptionService.decrypt(enc1)).isEqualTo(token);
        assertThat(encryptionService.decrypt(enc2)).isEqualTo(token);
    }

    @Test
    void decrypt_withTamperedCiphertext_throwsException() {
        String original = "sensitive_data";
        String encrypted = encryptionService.encrypt(original);

        // Tamper with the base64 string
        char lastChar = encrypted.charAt(encrypted.length() - 2);
        char swapped = (lastChar == 'A') ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 2) + swapped + "=";

        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidKeyLength_throwsIllegalStateException() {
        EncryptionProperties shortProps = new EncryptionProperties();
        shortProps.setKey("c2hvcnRfa2V5"); // "short_key" (too short)

        assertThatThrownBy(() -> new EncryptionService(shortProps))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32-byte");
    }
}
