package com.bizgrow.common.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Fail-fast validation of security configuration at application startup.
 *
 * <p>Configuration mistakes that would silently produce an insecure deployment
 * (weak JWT secret, wildcard CORS with credentials, dev-only invitation token
 * exposure) must crash the process instead of shipping. The validator is lenient
 * only for the {@code dev} and {@code test} profiles, whose values are by design
 * public and harmless on localhost.</p>
 */
@Component
public class StartupValidator {

    private static final int MIN_SECRET_BYTES = 32;

    private final AppProperties properties;
    private final Environment environment;

    public StartupValidator(AppProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @jakarta.annotation.PostConstruct
    public void validate() {
        // The dev profile is registered as a *default* (spring.profiles.default),
        // so it does not appear in getActiveProfiles() — include defaults or the
        // leniency would silently never apply to local development.
        String[] profiles = profilesInEffect();
        boolean lenient = Arrays.stream(profiles)
                .map(p -> p.toLowerCase(Locale.ROOT))
                .anyMatch(p -> p.equals("dev") || p.equals("test") || p.equals("local"));

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

        AppProperties.Cors cors = properties.getSecurity().getCors();
        if (cors.getAllowedOrigins().isEmpty()) {
            fail("CORS_ALLOWED_ORIGINS must list the exact frontend origin(s).");
        }
        boolean wildcard = cors.getAllowedOrigins().stream().anyMatch(o -> "*".equals(o.trim()) || o.isBlank());
        if (wildcard) {
            fail("CORS wildcard origins are forbidden because cookies are used for authentication. List exact origins.");
        }

        AppProperties.Cookie cookie = properties.getSecurity().getCookie();
        if ("None".equalsIgnoreCase(cookie.getSameSite()) && !cookie.isSecure()) {
            fail("SameSite=None cookies require cookie.secure=true (HTTPS) — browsers reject the combination.");
        }

        if (properties.getSecurity().getInvitation().isExposeDevToken() && !lenient) {
            fail("app.security.invitation.expose-dev-token must be false outside dev/test profiles "
                    + "(it returns raw invitation tokens).");
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
