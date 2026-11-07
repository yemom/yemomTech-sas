package com.bizgrow.common.security;

import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit_test_secret_0a1b2c3d4e5f6789abcdef_0123456789";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getJwt().setSecret(SECRET);
        properties.getSecurity().getJwt().setAccessExpirationSeconds(900);
        jwtService = new JwtService(properties);
    }

    @Test
    @DisplayName("a freshly issued token resolves back to the same user id")
    void roundTripsUserId() {
        UUID userId = UUID.randomUUID();

        assertThat(jwtService.parseUserId(jwtService.createAccessToken(userId))).isEqualTo(userId);
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void rejectsTokenFromAnotherKey() {
        AppProperties other = new AppProperties();
        other.getSecurity().getJwt().setSecret("a_totally_different_secret_9876543210_abcdef");
        JwtService otherService = new JwtService(other);

        String forged = otherService.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtService.parseUserId(forged))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("tampering with the payload invalidates the signature")
    void rejectsTamperedToken() {
        String token = jwtService.createAccessToken(UUID.randomUUID());
        String[] parts = token.split("\\.");
        String payloadJson = "{\"sub\":\"" + UUID.randomUUID() + "\"}";
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String forged = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.parseUserId(forged))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("garbage and empty tokens are rejected with one generic message")
    void rejectsGarbage() {
        for (String bad : new String[]{null, "", "not.a.jwt", "abc"}) {
            assertThatThrownBy(() -> jwtService.parseUserId(bad))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid or expired access token.");
        }
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpiredToken() throws InterruptedException {
        AppProperties shortLived = new AppProperties();
        shortLived.getSecurity().getJwt().setSecret(SECRET);
        shortLived.getSecurity().getJwt().setAccessExpirationSeconds(1);
        JwtService expiring = new JwtService(shortLived);
        String token = expiring.createAccessToken(UUID.randomUUID());

        Thread.sleep(1500);

        assertThatThrownBy(() -> expiring.parseUserId(token))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("reported TTL matches the configured lifetime")
    void reportsConfiguredTtl() {
        assertThat(jwtService.accessTokenTtlSeconds()).isEqualTo(900);
    }
}
