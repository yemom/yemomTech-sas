package com.bizgrow.auth;

import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.util.TokenHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the lifecycle of refresh tokens: issue → rotate → revoke family.
 *
 * <p>Rotation contract (the core of session security):
 * <ol>
 *   <li>Every login creates a new <b>family</b> (UUID).</li>
 *   <li>Each refresh marks the presented token revoked and issues a successor in
 *       the same family, inside ONE transaction (no state where both or neither
 *       exist).</li>
 *   <li>Presenting an already-revoked token means the token leaked (it was used
 *       after being replaced) → the whole family is revoked and the attacker AND
 *       the legitimate session must re-authenticate.</li>
 * </ol>
 * Raw tokens exist only in this class's return values and the client's cookie —
 * persistence is always the SHA-256 digest.</p>
 */
@Service
public class RefreshTokenService {

    public static final String DEVICE_UNKNOWN = "Unknown device";

    private final RefreshTokenRepository repository;
    private final AppProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Fresh session (login/registration): new family, new first token. */
    @Transactional
    public IssuedToken issueNewFamily(UUID userId, String deviceInformation) {
        return issue(userId, UUID.randomUUID(), deviceInformation);
    }

    /**
     * Rotation step: revoke {@code current}, insert its successor (same family).
     * Executed in one transaction so reuse detection stays race-free.
     */
    @Transactional
    public IssuedToken rotate(RefreshToken current, String deviceInformation) {
        IssuedToken successor = issue(current.getUserId(), current.getFamilyId(), deviceInformation);
        current.setRevokedAt(Instant.now());
        current.setReplacedBy(successor.id());
        repository.save(current);
        return successor;
    }

    /** Invalidates every token descended from one login — the reuse/theft response. */
    @Transactional
    public void revokeFamily(UUID familyId) {
        repository.revokeFamily(familyId, Instant.now());
    }

    public Optional<RefreshToken> findByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTokenHash(TokenHasher.sha256Hex(rawToken));
    }

    public long refreshTtlDays() {
        return properties.getSecurity().getJwt().getRefreshExpirationDays();
    }

    private IssuedToken issue(UUID userId, UUID familyId, String deviceInformation) {
        String raw = TokenHasher.newRawToken();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setFamilyId(familyId);
        token.setTokenHash(TokenHasher.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshTtlDays())));
        token.setDeviceInformation(truncate(deviceInformation));
        RefreshToken saved = repository.save(token);
        return new IssuedToken(saved.getId(), saved.getFamilyId(), raw, saved.getExpiresAt());
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return DEVICE_UNKNOWN;
        }
        String trimmed = value.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    /**
     * @param id           persisted token id
     * @param familyId     session family
     * @param rawToken     raw value for the cookie — NEVER persisted or logged
     * @param expiresAt    absolute expiry (UTC)
     */
    public record IssuedToken(UUID id, UUID familyId, String rawToken, Instant expiresAt) {
    }
}
