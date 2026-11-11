package com.YemomBizGrow.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One issued refresh token. Only the SHA-256 digest of the raw token is stored,
 * so the raw value exists solely in the client's HttpOnly cookie.
 *
 * <p>{@code familyId} groups every rotation of a single login session. If a
 * previously rotated (already replaced) token is replayed, the service revokes
 * the whole family — the standard defense against stolen-cookie replay.</p>
 */
@Entity
@Table(name = "refresh_tokens", uniqueConstraints = @UniqueConstraint(name = "uq_refresh_tokens_hash", columnNames = "token_hash"))
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    /** 64-char lowercase hex SHA-256 of the raw token. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** The successor issued when this token was rotated (chain evidence for reuse detection). */
    @Column(name = "replaced_by")
    private UUID replacedBy;

    /** Coarse label only ("Chrome on Windows") — no IP address is collected. */
    @Column(name = "device_information", length = 255)
    private String deviceInformation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isActive() {
        return !isRevoked() && !isExpired();
    }
}
