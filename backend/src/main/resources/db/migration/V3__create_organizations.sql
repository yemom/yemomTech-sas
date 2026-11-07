-- =============================================================
-- V3: organizations
-- The tenant. Every tenant-owned row in the platform carries
-- organization_id (shared-table multi-tenancy).
--
-- Ethiopian market defaults are column-level defaults so a row can
-- never exist without them, but they remain per-organization and
-- configurable: nothing in business logic hardcodes ET/ETB.
-- slug is the URL-safe identity of the org (unique, immutable once set).
-- Deletion is soft (status = 'ARCHIVED'): rows are never removed by
-- the API, which preserves auditability and enables recovery.
-- =============================================================
CREATE TABLE organizations (
    id             UUID                        PRIMARY KEY,
    name           VARCHAR(200)                NOT NULL,
    slug           VARCHAR(220)                NOT NULL,
    description    VARCHAR(2000),
    industry       VARCHAR(100),
    website_url    VARCHAR(500),
    country_code   VARCHAR(2)                  NOT NULL DEFAULT 'ET',
    currency_code  VARCHAR(3)                  NOT NULL DEFAULT 'ETB',
    timezone       VARCHAR(64)                 NOT NULL DEFAULT 'Africa/Addis_Ababa',
    status         VARCHAR(32)                 NOT NULL DEFAULT 'ACTIVE',
    created_by     UUID                        REFERENCES users (id) ON DELETE SET NULL,
    created_at     TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_organizations_slug     UNIQUE (slug),
    CONSTRAINT ck_organizations_status   CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    CONSTRAINT ck_organizations_country  CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_organizations_currency CHECK (currency_code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_organizations_name     CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_organizations_slug     CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE INDEX idx_organizations_status    ON organizations (status);
CREATE INDEX idx_organizations_created_by ON organizations (created_by);
