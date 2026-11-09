package com.YemomBizGrow.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class ChapaClient {

    private static final Logger log = LoggerFactory.getLogger(ChapaClient.class);
    private static final String CHAPA_BASE_URL = "https://api.chapa.co/v1";

    private final String secretKey;
    private final String webhookSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ChapaClient(
            @Value("${bizgrow.billing.chapa.secret-key:}") String secretKey,
            @Value("${bizgrow.billing.chapa.webhook-secret:}") String webhookSecret,
            ObjectMapper objectMapper
    ) {
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isLiveConfigured() {
        return !secretKey.isBlank() && !secretKey.equalsIgnoreCase("mock") && !secretKey.equalsIgnoreCase("test");
    }

    /**
     * Initializes a transaction with Chapa or returns a simulated sandbox checkout URL.
     */
    public ChapaInitializeResult initialize(
            BigDecimal amount,
            String currency,
            String email,
            String firstName,
            String lastName,
            String txRef,
            String callbackUrl,
            String returnUrl,
            String title,
            String description
    ) {
        if (!isLiveConfigured()) {
            log.info("Chapa secret key not configured or set to mock. Returning simulated checkout URL for txRef: {}", txRef);
            String mockCheckoutUrl = (returnUrl != null && !returnUrl.isBlank())
                    ? returnUrl + (returnUrl.contains("?") ? "&" : "?") + "tx_ref=" + txRef + "&status=success"
                    : "/workspace/billing?tx_ref=" + txRef + "&status=success";
            return new ChapaInitializeResult(true, mockCheckoutUrl, txRef, "Sandbox checkout initialized");
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("amount", amount.toPlainString());
            body.put("currency", currency != null ? currency : "ETB");
            body.put("email", email);
            body.put("first_name", firstName != null ? firstName : "Customer");
            body.put("last_name", lastName != null ? lastName : "");
            body.put("tx_ref", txRef);
            if (callbackUrl != null) body.put("callback_url", callbackUrl);
            if (returnUrl != null) body.put("return_url", returnUrl);
            body.put("customization[title]", title != null ? title : "BizGrow Subscription");
            body.put("customization[description]", description != null ? description : "Payment for BizGrow SaaS Plan");

            String jsonPayload = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAPA_BASE_URL + "/transaction/initialize"))
                    .header("Authorization", "Bearer " + secretKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String status = root.path("status").asText("");
                if ("success".equalsIgnoreCase(status)) {
                    String checkoutUrl = root.path("data").path("checkout_url").asText("");
                    return new ChapaInitializeResult(true, checkoutUrl, txRef, "Payment initialized successfully");
                }
            }

            log.error("Chapa initialize failed: status={}, body={}", response.statusCode(), response.body());
            return new ChapaInitializeResult(false, null, txRef, "Chapa initialize error: HTTP " + response.statusCode());

        } catch (IOException | InterruptedException e) {
            log.error("Chapa network exception during initialize for txRef: {}", txRef, e);
            Thread.currentThread().interrupt();
            return new ChapaInitializeResult(false, null, txRef, "Network error communicating with Chapa: " + e.getMessage());
        }
    }

    /**
     * Verifies a transaction status with Chapa server-side.
     */
    public ChapaVerifyResult verify(String txRef) {
        if (!isLiveConfigured()) {
            log.info("Chapa live credentials not active; sandbox verification succeeded for txRef: {}", txRef);
            return new ChapaVerifyResult(true, "success", txRef, "MOCK_CHAPA_" + txRef, "Sandbox verified");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(CHAPA_BASE_URL + "/transaction/verify/" + txRef))
                    .header("Authorization", "Bearer " + secretKey)
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String status = root.path("status").asText("");
                JsonNode data = root.path("data");
                String txStatus = data.path("status").asText("");

                if ("success".equalsIgnoreCase(status) && "success".equalsIgnoreCase(txStatus)) {
                    String chapaRef = data.path("reference").asText(txRef);
                    return new ChapaVerifyResult(true, "success", txRef, chapaRef, "Transaction verified successfully");
                } else {
                    return new ChapaVerifyResult(false, txStatus.isBlank() ? "failed" : txStatus, txRef, null, "Transaction not successful");
                }
            }

            log.warn("Chapa verify returned HTTP {}: {}", response.statusCode(), response.body());
            return new ChapaVerifyResult(false, "failed", txRef, null, "Verification failed with status " + response.statusCode());

        } catch (IOException | InterruptedException e) {
            log.error("Chapa verify exception for txRef: {}", txRef, e);
            Thread.currentThread().interrupt();
            return new ChapaVerifyResult(false, "error", txRef, null, "Verification exception: " + e.getMessage());
        }
    }

    public boolean validateWebhookSignature(String signatureHeader) {
        if (webhookSecret.isBlank()) {
            return true; // No secret configured in dev/sandbox
        }
        return signatureHeader != null && signatureHeader.equals(webhookSecret);
    }

    public record ChapaInitializeResult(boolean success, String checkoutUrl, String txRef, String message) {}
    public record ChapaVerifyResult(boolean success, String status, String txRef, String chapaTransactionId, String message) {}
}
