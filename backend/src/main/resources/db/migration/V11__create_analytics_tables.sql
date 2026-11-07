-- =============================================================
-- V11 — Google Integration Credentials & Analytics Snapshots
-- =============================================================
-- google_oauth_credentials : encrypted per-org, per-website Google tokens
-- google_analytics_snapshots: cached GA4 metrics (daily snapshots)
-- google_search_console_snapshots: cached GSC metrics (daily snapshots)
-- =============================================================

-- ---- Google OAuth credentials (org-scoped, AES-256-GCM encrypted) -----------

CREATE TABLE google_oauth_credentials (
    id                UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID                        NOT NULL,
    website_id        UUID                        REFERENCES websites(id) ON DELETE CASCADE,
    google_account_id TEXT                        NOT NULL,          -- Google sub/email, not secret
    access_token_enc  TEXT                        NOT NULL,          -- AES-256-GCM encrypted
    refresh_token_enc TEXT,                                          -- AES-256-GCM encrypted
    token_expiry      TIMESTAMP WITH TIME ZONE,
    scopes            TEXT,                                          -- space-separated
    oauth_state       TEXT,                                          -- CSRF state during flow
    created_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    CONSTRAINT uq_google_cred_org_website UNIQUE (organization_id, website_id)
);

CREATE INDEX idx_google_cred_org ON google_oauth_credentials (organization_id);

-- ---- GA4 daily snapshots -------------------------------------------------------

CREATE TABLE google_analytics_snapshots (
    id              UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID                        NOT NULL,
    website_id      UUID                        NOT NULL REFERENCES websites(id) ON DELETE CASCADE,
    snapshot_date   DATE                        NOT NULL,
    sessions        BIGINT                      NOT NULL DEFAULT 0,
    users           BIGINT                      NOT NULL DEFAULT 0,
    new_users       BIGINT                      NOT NULL DEFAULT 0,
    page_views      BIGINT                      NOT NULL DEFAULT 0,
    bounce_rate     NUMERIC(5, 2),
    avg_session_sec INTEGER,
    top_pages       JSONB,          -- [{page, views, avgTime}]
    devices         JSONB,          -- [{device, sessions}]
    countries       JSONB,          -- [{country, sessions}]
    channels        JSONB,          -- [{channel, sessions}]
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    CONSTRAINT uq_ga_snapshot_org_website_date UNIQUE (organization_id, website_id, snapshot_date)
);

CREATE INDEX idx_ga_snapshot_org_website_date ON google_analytics_snapshots (organization_id, website_id, snapshot_date DESC);

-- ---- Google Search Console daily snapshots -----------------------------------

CREATE TABLE google_search_console_snapshots (
    id              UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID                        NOT NULL,
    website_id      UUID                        NOT NULL REFERENCES websites(id) ON DELETE CASCADE,
    snapshot_date   DATE                        NOT NULL,
    total_clicks    BIGINT                      NOT NULL DEFAULT 0,
    total_impressions BIGINT                    NOT NULL DEFAULT 0,
    avg_ctr         NUMERIC(6, 4),
    avg_position    NUMERIC(7, 2),
    top_queries     JSONB,          -- [{query, clicks, impressions, ctr, position}]
    top_pages       JSONB,          -- [{page, clicks, impressions}]
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    CONSTRAINT uq_gsc_snapshot_org_website_date UNIQUE (organization_id, website_id, snapshot_date)
);

CREATE INDEX idx_gsc_snapshot_org_website_date ON google_search_console_snapshots (organization_id, website_id, snapshot_date DESC);
