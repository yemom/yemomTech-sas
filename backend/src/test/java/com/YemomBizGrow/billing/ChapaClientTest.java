package com.YemomBizGrow.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChapaClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("validateWebhookSignature accepts valid HMAC-SHA256 signature")
    void validateWebhookSignature_validHmac_returnsTrue() throws Exception {
        String secret = "test_webhook_secret_key_123";
        String payload = "{\"event\":\"charge.success\",\"tx_ref\":\"TX-12345\"}";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        String validSig = hex.toString();

        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        ChapaClient client = new ChapaClient("CHASECK_TEST-xxx", secret, env, objectMapper);

        assertThat(client.validateWebhookSignature(validSig, payload)).isTrue();
    }

    @Test
    @DisplayName("validateWebhookSignature rejects invalid signature, blank header, or missing secret")
    void validateWebhookSignature_invalidOrMissing_returnsFalse() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        ChapaClient client = new ChapaClient("CHASECK_TEST-xxx", "test_secret", env, objectMapper);

        assertThat(client.validateWebhookSignature("invalid_sig", "payload")).isFalse();
        assertThat(client.validateWebhookSignature("", "payload")).isFalse();
        assertThat(client.validateWebhookSignature(null, "payload")).isFalse();
        assertThat(client.validateWebhookSignature("sig", null)).isFalse();

        ChapaClient clientNoSecret = new ChapaClient("CHASECK_TEST-xxx", "", env, objectMapper);
        assertThat(clientNoSecret.validateWebhookSignature("sig", "payload")).isFalse();
    }

    @Test
    @DisplayName("isLiveConfigured correctly identifies live vs test/mock configuration")
    void isLiveConfigured_checksSecretKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        ChapaClient liveClient = new ChapaClient("CHASECK_LIVE-real-key-123", "secret", env, objectMapper);
        assertThat(liveClient.isLiveConfigured()).isTrue();

        ChapaClient mockClient = new ChapaClient("mock", "secret", env, objectMapper);
        assertThat(mockClient.isLiveConfigured()).isFalse();

        ChapaClient blankClient = new ChapaClient("", "secret", env, objectMapper);
        assertThat(blankClient.isLiveConfigured()).isFalse();
    }
}
