-- =============================================================
-- V7: audit_logs
-- Append-only trail of security-sensitive and organization-level
-- actions. Design rules:
--   * Never stores passwords, raw tokens, or secrets — the
--     application drops any metadata key that looks secret before insert.
--   * ON DELETE SET NULL keeps the trail if a user is purged while
--     losing the link (forensics over convenience).
--   * metadata JSONB only for genuinely flexible key/value context;
--     everything queryable is a real column.
-- =============================================================
CREATE TABLE audit_logs (
    id              UUID                        PRIMARY KEY,
    organization_id UUID                        REFERENCES organizations (id) ON DELETE SET NULL,
    actor_user_id   UUID                        REFERENCES users (id) ON DELETE SET NULL,
    action          VARCHAR(64)                 NOT NULL,
    target_type     VARCHAR(64),
    target_id       UUID,
    metadata        JSONB,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_action CHECK (action ~ '^[A-Z][A-Z0-9_]*$')
);

CREATE INDEX idx_audit_logs_org_created    ON audit_logs (organization_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor_created  ON audit_logs (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_logs_target         ON audit_logs (target_type, target_id);
