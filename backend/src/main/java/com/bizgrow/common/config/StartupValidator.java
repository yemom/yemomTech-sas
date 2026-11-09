package com.YemomBizGrow.common.config;

import com.YemomBizGrow.integration.google.EncryptionProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * Fail-fast validation of security configuration at application startup.
 *
 * <p>Configuration mistakes that would silently produce an insecure deployment
 * (weak JWT secret, wildcard CORS with credentials, dev-only invitation token
 * exposure, or known development AES keys) must crash the process instead of shipping.
 * The validator is lenient only for the {@code dev} and {@code test} profiles.</p>
 */
@Component
public class StartupValidator {

    private static final int MIN_SECRET_BYTES = 32;
    private static final String KNOWN_DEV_ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final AppProperties properties;
    private final EncryptionProperties encryptionProperties;
    private final Environment environment;

    public StartupValidator(
            AppProperties properties,
            EncryptionProperties encryptionProperties,
            Environment environment
    ) {
        this.properties = properties;
        this.encryptionProperties = encryptionProperties;
        this.environment = environment;
    }

    @jakarta.annotation.PostConstruct
    public void validate() {
        String[] profiles = profilesInEffect();
        boolean lenient = Arrays.stream(profiles)
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("dev") || p.equals("test") || p.equals("local"));

        // 1. JWT Secret Validation
        AppProperties.Jwt jwt = properties.getSecurity().getJwt();
        String secret = jwt.getSecret() == null ? "" : jwt.getSecret();

        if (secret.isBlank()) {
            fail("JWT secret is not configured. Set JWT_SECRET (minimum 32 bytes of entropy).");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            fail("JWT secret must be at least " + MIN_SECRET_BYTES + " bytes. Generate one with "
                    + "'openssl rand -base64 64'.");
        }
        String lower = secret.toLowerCase(Locale.ROOT);
        boolean looksLikePlaceholder = lower.contains("change_me") || lower.contains("dev_only") || lower.contains("test_only");
        if (looksLikePlaceholder && !lenient) {
            fail("JWT_SECRET still contains a placeholder/dev value. Generate a real secret before starting this profile.");
        }

        if (jwt.getAccessExpirationSeconds() <= 0 || jwt.getRefreshExpirationDays() <= 0) {
            fail("Token expirations must be positive (JWT_ACCESS_EXPIRATION_SECONDS / JWT_REFRESH_EXPIRATION_DAYS).");
        }

        // 2. CORS Validation
        AppProperties.Cors cors = properties.getSecurity().getCors();
        if (cors.getAllowedOrigins().isEmpty()) {
            fail("CORS_ALLOWED_ORIGINS must list the exact frontend origin(s).");
        }
        boolean wildcard = cors.getAllowedOrigins().stream().anyMatch(o -> "*".equals(o.trim()) || o.isBlank());
        if (wildcard) {
            fail("CORS wildcard origins are forbidden because cookies are used for authentication. List exact origins.");
        }

        // 3. Cookie SameSite/Secure Validation
        AppProperties.Cookie cookie = properties.getSecurity().getCookie();
        if ("None".equalsIgnoreCase(cookie.getSameSite()) && !cookie.isSecure()) {
            fail("SameSite=None cookies require cookie.secure=true (HTTPS) — browsers reject the combination.");
        }

        if (properties.getSecurity().getInvitation().isExposeDevToken() && !lenient) {
            fail("app.security.invitation.expose-dev-token must be false outside dev/test profiles "
                    + "(it returns raw invitation tokens).");
        }

        // 4. AES-256-GCM Encryption Key Validation
        String encKey = encryptionProperties.getKey() != null ? encryptionProperties.getKey().trim() : "";
        if (encKey.isBlank()) {
            fail("app.security.encryption.key is not configured. Set ENCRYPTION_KEY to a Base64-encoded 32-byte AES key (openssl rand -base64 32).");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encKey);
            if (decoded.length != 32) {
                fail("Encryption key must decode to exactly 32 bytes (256-bit AES). Got " + decoded.length + " bytes.");
            }
        } catch (IllegalArgumentException e) {
            fail("Encryption key is not valid Base64: " + e.getMessage());
        }

        if (!lenient && KNOWN_DEV_ENCRYPTION_KEY.equals(encKey)) {
            fail("ENCRYPTION_KEY is using the publicly known development default key! Generate a secure production key.");
        }

        // 5. Chapa Production Validation
        String chapaKey = environment.getProperty("bizgrow.billing.chapa.secret-key", "");
        if (!lenient && !chapaKey.isBlank()) {
            if (chapaKey.equalsIgnoreCase("mock") || chapaKey.equalsIgnoreCase("test") || chapaKey.contains("CHANGE_ME")) {
                fail("CHAPA_SECRET_KEY is set to a placeholder/mock value in production.");
            }
        }
    }

    private String[] profilesInEffect() {
        String[] active = environment.getActiveProfiles();
        if (active.length > 0) {
            return active;
        }
        return environment.getDefaultProfiles();
    }

    private void fail(String message) {
        throw new IllegalStateException("Configuration validation failed: " + message);
    }
}
