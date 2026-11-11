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

    /** Expiration timestamp for the short-lived CSRF state. */
    @Column(name = "oauth_state_expires_at")
    private Instant oauthStateExpiresAt;

    /** ID of user who initiated the OAuth flow. */
    @Column(name = "initiating_user_id")
    private UUID initiatingUserId;

    /** Tenant-selected GA4 Property ID (e.g. "properties/123456"). */
    @Column(name = "ga4_property_id", length = 128)
    private String ga4PropertyId;

    /** Tenant-selected Google Search Console site URL. */
    @Column(name = "gsc_site_url", length = 2048)
    private String gscSiteUrl;

    /** Timestamp of last successful GA4 metric sync. */
    @Column(name = "last_ga4_sync_at")
    private Instant lastGa4SyncAt;

    /** Timestamp of last successful Search Console metric sync. */
    @Column(name = "last_gsc_sync_at")
    private Instant lastGscSyncAt;

    /** PKCE code verifier from the OAuth flow (stored for callback exchange). */
    @Column(name = "code_verifier", length = 256)
    private String codeVerifier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
