-- =============================================================
-- V9: SEO audits, crawled pages, and detected issues
-- Supports comprehensive site crawling, Jsoup metadata inspection,
-- transparent scoring, and actionable remediation guidance.
-- =============================================================

CREATE TABLE seo_audits (
    id                      UUID                        PRIMARY KEY,
    organization_id         UUID                        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    website_id              UUID                        NOT NULL REFERENCES websites (id) ON DELETE CASCADE,
    status                  VARCHAR(32)                 NOT NULL,
    score                   INTEGER,
    technical_score         INTEGER,
    on_page_score           INTEGER,
    content_score           INTEGER,
    accessibility_score     INTEGER,
    pages_crawled           INTEGER                     NOT NULL DEFAULT 0,
    critical_issues_count   INTEGER                     NOT NULL DEFAULT 0,
    warning_issues_count    INTEGER                     NOT NULL DEFAULT 0,
    info_issues_count       INTEGER                     NOT NULL DEFAULT 0,
    error_message           VARCHAR(1000),
    started_at              TIMESTAMP WITH TIME ZONE,
    completed_at            TIMESTAMP WITH TIME ZONE,
    created_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_seo_audits_status CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_seo_audits_score  CHECK (score IS NULL OR (score >= 0 AND score <= 100))
);

CREATE INDEX idx_seo_audits_org             ON seo_audits (organization_id);
CREATE INDEX idx_seo_audits_website_created ON seo_audits (website_id, created_at DESC);

CREATE TABLE seo_crawled_pages (
    id                          UUID                        PRIMARY KEY,
    audit_id                    UUID                        NOT NULL REFERENCES seo_audits (id) ON DELETE CASCADE,
    organization_id             UUID                        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    website_id                  UUID                        NOT NULL REFERENCES websites (id) ON DELETE CASCADE,
    url                         VARCHAR(2048)               NOT NULL,
    status_code                 INTEGER,
    title                       VARCHAR(1000),
    meta_description            VARCHAR(2000),
    canonical_url               VARCHAR(2048),
    h1_count                    INTEGER                     NOT NULL DEFAULT 0,
    h2_count                    INTEGER                     NOT NULL DEFAULT 0,
    word_count                  INTEGER                     NOT NULL DEFAULT 0,
    internal_links_count        INTEGER                     NOT NULL DEFAULT 0,
    external_links_count        INTEGER                     NOT NULL DEFAULT 0,
    images_count                INTEGER                     NOT NULL DEFAULT 0,
    missing_alt_images_count    INTEGER                     NOT NULL DEFAULT 0,
    load_time_ms                BIGINT,
    has_viewport_meta           BOOLEAN                     NOT NULL DEFAULT FALSE,
    is_https                    BOOLEAN                     NOT NULL DEFAULT FALSE,
    content_hash                VARCHAR(64),
    created_at                  TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_crawled_pages_audit ON seo_crawled_pages (audit_id);
CREATE INDEX idx_crawled_pages_org   ON seo_crawled_pages (organization_id);

CREATE TABLE seo_issues (
    id                  UUID                        PRIMARY KEY,
    audit_id            UUID                        NOT NULL REFERENCES seo_audits (id) ON DELETE CASCADE,
    organization_id     UUID                        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    website_id          UUID                        NOT NULL REFERENCES websites (id) ON DELETE CASCADE,
    category            VARCHAR(32)                 NOT NULL,
    severity            VARCHAR(16)                 NOT NULL,
    title               VARCHAR(255)                NOT NULL,
    description         VARCHAR(2000)               NOT NULL,
    affected_url        VARCHAR(2048),
    evidence            VARCHAR(2000),
    explanation         VARCHAR(2000),
    recommendation      VARCHAR(2000),
    status              VARCHAR(16)                 NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_seo_issues_category CHECK (category IN ('TECHNICAL', 'ON_PAGE', 'CONTENT', 'ACCESSIBILITY', 'PERFORMANCE')),
    CONSTRAINT ck_seo_issues_severity CHECK (severity IN ('CRITICAL', 'WARNING', 'INFO')),
    CONSTRAINT ck_seo_issues_status   CHECK (status IN ('OPEN', 'RESOLVED', 'IGNORED'))
);

CREATE INDEX idx_seo_issues_audit        ON seo_issues (audit_id);
CREATE INDEX idx_seo_issues_org_severity ON seo_issues (organization_id, severity);
