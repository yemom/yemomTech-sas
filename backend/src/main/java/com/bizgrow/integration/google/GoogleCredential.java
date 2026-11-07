package com.YemomBizGrow.integration.google;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores per-organization, per-website Google OAuth credentials.
 *
 * <p>Both {@code accessTokenEnc} and {@code refreshTokenEnc} are stored
 * AES-256-GCM encrypted using {@link EncryptionService}. They are never
 * exposed through any API response.</p>
 */
@Entity
@Table(name = "google_oauth_credentials",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_google_cred_org_website",
                columnNames = {"organization_id", "website_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GoogleCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "website_id")
    private UUID websiteId;

    /** Google account identifier (sub or email) — not a secret. */
    @Column(name = "google_account_id", nullable = false)
    private String googleAccountId;

    /** AES-256-GCM encrypted access token. */
    @Column(name = "access_token_enc", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEnc;

    /** AES-256-GCM encrypted refresh token (may be absent on first auth). */
    @Column(name = "refresh_token_enc", columnDefinition = "TEXT")
    private String refreshTokenEnc;

    @Column(name = "token_expiry")
    private Instant tokenExpiry;

    @Column(name = "scopes", length = 2048)
    private String scopes;

    /** Short-lived CSRF state used only during the OAuth flow; cleared on completion. */
    @Column(name = "oauth_state", length = 128)
    private String oauthState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
