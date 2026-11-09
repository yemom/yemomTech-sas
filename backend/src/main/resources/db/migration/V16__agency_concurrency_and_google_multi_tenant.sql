-- V16: Concurrency locking for agency relationships and multi-tenant property selection for Google integration

-- 1. Optimistic locking version for agency_client_relationships
ALTER TABLE agency_client_relationships
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- 2. Multi-tenant Google Analytics 4 property and Search Console site selection
ALTER TABLE google_oauth_credentials
    ADD COLUMN IF NOT EXISTS ga4_property_id          VARCHAR(128),
    ADD COLUMN IF NOT EXISTS gsc_site_url             VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS last_ga4_sync_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_gsc_sync_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS oauth_state_expires_at   TIMESTAMPTZ;

COMMENT ON COLUMN google_oauth_credentials.ga4_property_id IS
    'Org-selected GA4 property ID (e.g., properties/123456). Null until selected by user.';
COMMENT ON COLUMN google_oauth_credentials.gsc_site_url IS
    'Org-selected Google Search Console site URL. Null until selected by user.';
COMMENT ON COLUMN google_oauth_credentials.oauth_state_expires_at IS
    'Expiry timestamp for one-time OAuth state parameter to prevent replay attacks.';
