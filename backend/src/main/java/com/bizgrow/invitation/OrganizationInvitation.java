package com.bizgrow.invitation;

import com.bizgrow.membership.MembershipRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A pending or consumed invitation to join an organization.
 *
 * <p>Single-use: acceptance sets {@code accepted_at}, revocation sets
 * {@code revoked_at}; the DB check constraint forbids both. The raw token is
 * emailed as a one-time link and never stored or returned by production APIs.</p>
 */
@Entity
@Table(name = "organization_invitations",
        uniqueConstraints = @UniqueConstraint(name = "uq_invitation_token_hash", columnNames = "invitation_token_hash"))
@Getter
@Setter
@NoArgsConstructor
public class OrganizationInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Normalized (lowercase) e-mail the invitation is bound to. */
    @Column(name = "invited_email", nullable = false, length = 255)
    private String invitedEmail;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private MembershipRole role;

    /** SHA-256 hex of the raw token; unique so a token can be found exactly once. */
    @Column(name = "invitation_token_hash", nullable = false, length = 64)
    private String invitationTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by")
    private UUID acceptedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isPending() {
        return acceptedAt == null && revokedAt == null;
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    /** Pending and inside its validity window — the only state that may be accepted. */
    public boolean isAcceptable() {
        return isPending() && !isExpired();
    }
}
