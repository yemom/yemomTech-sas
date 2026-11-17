package com.YemomBizGrow.common.config;

import com.YemomBizGrow.integration.google.EncryptionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Behavioural coverage for {@link StartupValidator}: the checks it relaxes in
 * development must stay relaxed there, and must be enforced everywhere else.
 */
class StartupValidatorTest {

    private static final String REAL_SECRET =
            "a-production-grade-jwt-secret-value-with-plenty-of-entropy-0123456789";
    private static final String DEV_ENCRYPTION_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    /** The key inlined in the development docker-compose.yml. */
    private static final String COMPOSE_DEV_ENCRYPTION_KEY = "EydsM2kgwpuNGxgEPhOGI50mdGFXRw3HgzUVqMKYLiE=";
    private static final String DEV_JWT_SECRET =
            "dev_only_secret_do_not_use_in_production_0a1b2c3d4e5f6789abcdef";
    /** A real, non-default AES key: exactly 32 bytes, Base64-encoded. */
    private static final String PRODUCTION_ENCRYPTION_KEY =
            Base64.getEncoder().encodeToString("yemomProdAes256KeyBytesExactly32".getBytes(StandardCharsets.UTF_8));

    @Test
    @DisplayName("dev profile tolerates the documented development placeholders")
    void devProfileAllowsDevelopmentPlaceholders() {
        assertThatCode(() -> validator(DEV_JWT_SECRET, DEV_ENCRYPTION_KEY, "dev").validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod profile rejects the development JWT placeholder")
    void prodProfileRejectsDevelopmentJwtSecret() {
        assertThatThrownBy(() -> validator(DEV_JWT_SECRET, DEV_ENCRYPTION_KEY, "prod").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET still contains a placeholder");
    }

    @Test
    @DisplayName("prod profile rejects the publicly known development encryption key")
    void prodProfileRejectsKnownDevEncryptionKey() {
        assertThatThrownBy(() -> validator(REAL_SECRET, DEV_ENCRYPTION_KEY, "prod").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicly known development default key");
    }

    @Test
    @DisplayName("prod profile also rejects the dev docker-compose encryption key")
    void prodProfileRejectsComposeDevEncryptionKey() {
        // The key inlined in the development docker-compose.yml is public
        // (it ships in the repository), so a strict profile must refuse it.
        assertThatThrownBy(() -> validator(REAL_SECRET, COMPOSE_DEV_ENCRYPTION_KEY, "prod").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("publicly known development default key");
    }

    @Test
    @DisplayName("dev profile tolerates the dev docker-compose encryption key")
    void devProfileAllowsComposeDevEncryptionKey() {
        assertThatCode(() -> validator(DEV_JWT_SECRET, COMPOSE_DEV_ENCRYPTION_KEY, "dev").validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("mixed prod+dev is rejected instead of silently becoming lenient")
    void mixedProdAndDevProfilesAreRejected() {
        assertThatThrownBy(() -> validator(DEV_JWT_SECRET, DEV_ENCRYPTION_KEY, "prod", "dev").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mixed Spring profiles");
    }

    @Test
    @DisplayName("mixed prod+test is rejected instead of silently becoming lenient")
    void mixedProdAndTestProfilesAreRejected() {
        assertThatThrownBy(() -> validator(DEV_JWT_SECRET, DEV_ENCRYPTION_KEY, "prod", "test").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mixed Spring profiles");
    }

    @Test
    @DisplayName("mixed prod+local is rejected instead of silently becoming lenient")
    void mixedProdAndLocalProfilesAreRejected() {
        assertThatThrownBy(() -> validator(DEV_JWT_SECRET, DEV_ENCRYPTION_KEY, "prod", "local").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mixed Spring profiles");
    }

    @Test
    @DisplayName("a strict production configuration passes validation")
    void prodProfileAcceptsRealSecrets() {
        assertThatCode(() -> validator(REAL_SECRET, PRODUCTION_ENCRYPTION_KEY, "prod").validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("invitation dev-token exposure is rejected outside dev/test")
    void exposeDevTokenIsRejectedInProduction() {
        AppProperties properties = strictProperties();
        properties.getSecurity().getInvitation().setExposeDevToken(true);

        StartupValidator validator = new StartupValidator(
                properties, encryptionProperties(PRODUCTION_ENCRYPTION_KEY),
                environment("prod"));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expose-dev-token");
    }

    @Test
    @DisplayName("production refuses to boot with the API documentation enabled")
    void apiDocsMustBeDisabledInProduction() {
        MockEnvironment environment = environment("prod");
        environment.setProperty("springdoc.api-docs.enabled", "true");

        StartupValidator validator = new StartupValidator(
                strictProperties(), encryptionProperties(PRODUCTION_ENCRYPTION_KEY), environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API documentation must be disabled in production");
    }

    @Test
    @DisplayName("production refuses to boot with the Swagger UI enabled")
    void swaggerUiMustBeDisabledInProduction() {
        MockEnvironment environment = environment("prod");
        environment.setProperty("springdoc.swagger-ui.enabled", "true");

        StartupValidator validator = new StartupValidator(
                strictProperties(), encryptionProperties(PRODUCTION_ENCRYPTION_KEY), environment);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API documentation must be disabled in production");
    }

    @Test
    @DisplayName("development keeps the API documentation available")
    void devProfileKeepsApiDocsAvailable() {
        MockEnvironment environment = environment("dev");
        environment.setProperty("springdoc.api-docs.enabled", "true");

        StartupValidator validator = new StartupValidator(
                strictProperties(DEV_JWT_SECRET), encryptionProperties(DEV_ENCRYPTION_KEY), environment);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    private StartupValidator validator(String jwtSecret, String encryptionKey, String... profiles) {
        return new StartupValidator(
                strictProperties(jwtSecret),
                encryptionProperties(encryptionKey),
                environment(profiles));
    }

    private MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private EncryptionProperties encryptionProperties(String key) {
        EncryptionProperties encryption = new EncryptionProperties();
        encryption.setKey(key);
        return encryption;
    }

    private AppProperties strictProperties() {
        return strictProperties(REAL_SECRET);
    }

    private AppProperties strictProperties(String jwtSecret) {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getJwt().setSecret(jwtSecret);
        properties.getSecurity().getCors()
                .setAllowedOrigins(List.of("https://app.example.et"));
        properties.getSecurity().getCookie().setSameSite("None");
        properties.getSecurity().getCookie().setSecure(true);
        return properties;
    }
}
