package com.bizgrow.auth;

import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rotation is the core session-security contract; these tests pin the chain
 * behaviour without a database (the same invariants are re-checked end-to-end
 * by the Testcontainers integration suite).
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private AppProperties properties;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        properties = new AppProperties();
        properties.getSecurity().getJwt().setRefreshExpirationDays(30);
        service = new RefreshTokenService(repository, properties);

        // Simulate persistence assigning ids (lenient: not every test saves).
        lenient().when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
            }
            return token;
        });
    }

    @Test
    @DisplayName("only the SHA-256 digest of a new token is ever persisted")
    void persistsHashNotRawToken() {
        RefreshTokenService.IssuedToken issued =
                service.issueNewFamily(UUID.randomUUID(), "Chrome on Windows");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getTokenHash()).isEqualTo(TokenHasher.sha256Hex(issued.rawToken()));
        assertThat(saved.getTokenHash()).hasSize(64);
        assertThat(issued.rawToken()).doesNotContain(saved.getTokenHash());
        assertThat(saved.getTokenHash()).isNotEqualTo(issued.rawToken());
    }

    @Test
    @DisplayName("a new login starts a fresh family expiring in the configured window")
    void newFamilyAndExpiry() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        RefreshTokenService.IssuedToken issued = service.issueNewFamily(userId, null);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getFamilyId()).isEqualTo(issued.familyId());
        assertThat(saved.getExpiresAt()).isBetween(before.plusSeconds(29 * 86_400),
                Instant.now().plusSeconds(31 * 86_400));
        assertThat(saved.getDeviceInformation()).isEqualTo(RefreshTokenService.DEVICE_UNKNOWN);
        assertThat(saved.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("rotation revokes the presented token and issues its successor in the same family")
    void rotateChainsWithinFamily() {
        RefreshToken current = activeToken();

        RefreshTokenService.IssuedToken successor = service.rotate(current, "Firefox on Android");

        assertThat(current.isRevoked()).isTrue();
        assertThat(current.getReplacedBy()).isEqualTo(successor.id());
        assertThat(successor.familyId()).isEqualTo(current.getFamilyId());
        assertThat(successor.rawToken()).isNotBlank();
    }

    @Test
    @DisplayName("device labels longer than 255 characters are truncated to the column width")
    void truncatesLongDeviceInformation() {
        service.issueNewFamily(UUID.randomUUID(), "x".repeat(400));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDeviceInformation()).hasSize(255);
    }

    @Test
    @DisplayName("lookup by raw token hashes before touching the repository")
    void lookupHashesInput() {
        String raw = "raw-token-value";
        when(repository.findByTokenHash(TokenHasher.sha256Hex(raw))).thenReturn(Optional.empty());

        assertThat(service.findByRawToken(raw)).isEmpty();
        verify(repository).findByTokenHash(TokenHasher.sha256Hex(raw));
    }

    @Test
    @DisplayName("blank or missing cookies never reach the repository")
    void blankTokenIsEmpty() {
        assertThat(service.findByRawToken(null)).isEmpty();
        assertThat(service.findByRawToken("")).isEmpty();
        assertThat(service.findByRawToken("   ")).isEmpty();
        verify(repository, never()).findByTokenHash(any());
    }

    @Test
    @DisplayName("family revocation delegates to a single bulk update")
    void revokeFamilyDelegates() {
        UUID familyId = UUID.randomUUID();

        service.revokeFamily(familyId);

        verify(repository).revokeFamily(eq(familyId), any(Instant.class));
    }

    private RefreshToken activeToken() {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUserId(UUID.randomUUID());
        token.setFamilyId(UUID.randomUUID());
        token.setTokenHash(TokenHasher.sha256Hex("current"));
        token.setExpiresAt(Instant.now().plusSeconds(86_400));
        return token;
    }
}
