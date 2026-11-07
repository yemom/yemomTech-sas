-- =============================================================
-- V6: websites — first vertical slice
-- Registered business websites, owned by exactly one organization.
--
-- domain is normalized by the application (lowercased host, no scheme,
-- no path, no trailing dot) and unique PER ORGANIZATION, so two tenants
-- may register the same domain while one tenant cannot duplicate it.
--
-- Registration is metadata-only: the backend never fetches the URL
-- (no crawler in this slice => no SSRF surface).
-- Deactivation is soft (status = 'INACTIVE'); the row is kept.
-- =============================================================
CREATE TABLE websites (
    id              UUID                        PRIMARY KEY,
    organization_id UUID                        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            VARCHAR(200)                NOT NULL,
    domain          VARCHAR(253)                NOT NULL,
    url             VARCHAR(2048)               NOT NULL,
    status          VARCHAR(32)                 NOT NULL DEFAULT 'ACTIVE',
    created_by      UUID                        REFERENCES users (id) ON DELETE SET NULL,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_websites_org_domain UNIQUE (organization_id, domain),
    CONSTRAINT ck_websites_status     CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_websites_name       CHECK (length(trim(name)) > 0),
    -- At least two labels, valid DNS characters, lowercase only (app normalizes first).
    CONSTRAINT ck_websites_domain     CHECK (domain ~ '^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$'),
    CONSTRAINT ck_websites_url        CHECK (url ~ '^https?://')
);

CREATE INDEX idx_websites_org_status ON websites (organization_id, status);
CREATE INDEX idx_websites_created_by ON websites (created_by);
