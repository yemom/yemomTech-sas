package com.YemomBizGrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Boots the application with the <strong>production</strong> profile and asserts
 * the externally reachable surface.
 *
 * <p>Why this test exists: the security chain permitted {@code /v3/api-docs/**}
 * and {@code /swagger-ui/**}, so nothing in the source tree guaranteed that the
 * documentation stayed unpublished in production — only a deployment environment
 * variable would have. {@code application-prod.yml} now disables springdoc
 * explicitly and {@code StartupValidator} refuses to boot a production
 * deployment with the documentation switched on. This test verifies the
 * observable behaviour: the documentation endpoints answer 404.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ProductionProfileIT extends AbstractContainerTest {

    /**
     * The PostgreSQL/Redis containers are inherited from
     * {@link AbstractIntegrationTest} — the whole integration suite shares one
     * pair instead of starting a second database per profile.
     *
     * <p>Real (non-placeholder) secrets are supplied here: the production
     * profile validates strictly and refuses to boot with the development
     * defaults.</p>
     */
    @DynamicPropertySource
    static void productionSecrets(DynamicPropertyRegistry registry) {
        registry.add("app.security.jwt.secret",
                () -> "prod_profile_it_secret_0123456789abcdef0123456789abcdef");
        registry.add("app.security.encryption.key",
                () -> Base64.getEncoder()
                        .encodeToString("yemomProdAes256KeyBytesExactly32".getBytes(StandardCharsets.UTF_8)));
        registry.add("app.security.cors.allowed-origins", () -> "https://app.example.et");
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("the OpenAPI description is not published in production")
    void apiDocsAreNotPublished() {
        assertNotFound("/v3/api-docs");
        assertNotFound("/v3/api-docs/swagger-config");
    }

    @Test
    @DisplayName("the Swagger UI is not published in production")
    void swaggerUiIsNotPublished() {
        assertNotFound("/swagger-ui.html");
        assertNotFound("/swagger-ui/index.html");
        assertNotFound("/swagger-ui/");
    }

    @Test
    @DisplayName("protected endpoints stay protected in production")
    void protectedEndpointsStillRequireAuthentication() {
        ResponseEntity<String> response = rest.getForEntity("/api/v1/auth/me", String.class);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("health probes stay reachable in production")
    void healthProbesRemainAvailable() {
        // Readiness and liveness are the probes an orchestrator uses to decide
        // whether to route traffic to the instance (this is also what the
        // Docker healthcheck calls). The *aggregate* /actuator/health endpoint
        // additionally reports optional components such as the mail relay, so
        // it is deliberately not asserted here.
        ResponseEntity<String> readiness = rest.getForEntity("/actuator/health/readiness", String.class);
        org.assertj.core.api.Assertions.assertThat(readiness.getStatusCode())
                .as("the readiness probe must stay available in production")
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<String> liveness = rest.getForEntity("/actuator/health/liveness", String.class);
        org.assertj.core.api.Assertions.assertThat(liveness.getStatusCode())
                .as("the liveness probe must stay available in production")
                .isEqualTo(HttpStatus.OK);
    }

    private void assertNotFound(String path) {
        ResponseEntity<String> response = rest.getForEntity(path, String.class);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                .as("%s must not be served in production", path)
                .isEqualTo(HttpStatus.NOT_FOUND);
        org.assertj.core.api.Assertions.assertThat(response.getBody() == null
                        || !response.getBody().contains("openapi"))
                .as("%s must not leak an OpenAPI document", path)
                .isTrue();
    }
}
