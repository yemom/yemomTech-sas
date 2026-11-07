-- =============================================================
-- V5: organization_invitations
-- The raw invitation token exists only in outbound e-mail links;
-- the database stores its SHA-256 digest (unique, indexed) so tokens
-- cannot be recovered from a database dump.
-- accepted_at/revoked_at make each invitation single-use and revocable.
-- =============================================================
CREATE TABLE organization_invitations (
    id                    UUID                        PRIMARY KEY,
    organization_id       UUID                        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    invited_email         VARCHAR(255)                NOT NULL,
    invited_by            UUID                        REFERENCES users (id) ON DELETE SET NULL,
    role                  VARCHAR(32)                 NOT NULL,
    invitation_token_hash VARCHAR(64)                 NOT NULL,
    expires_at            TIMESTAMP WITH TIME ZONE    NOT NULL,
    accepted_at           TIMESTAMP WITH TIME ZONE,
    accepted_by           UUID                        REFERENCES users (id) ON DELETE SET NULL,
    revoked_at            TIMESTAMP WITH TIME ZONE,
    created_at            TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_invitation_token_hash UNIQUE (invitation_token_hash),
    CONSTRAINT ck_invitation_role       CHECK (role IN ('BUSINESS_OWNER', 'BUSINESS_ADMIN', 'MARKETING_MANAGER',
                                                        'SALES_MANAGER', 'SALES_AGENT', 'ANALYST', 'VIEWER')),
    -- An invitation is either accepted or revoked, never both.
    CONSTRAINT ck_invitation_state      CHECK (accepted_at IS NULL OR revoked_at IS NULL),
    CONSTRAINT ck_invitation_email      CHECK (position('@' IN invited_email) > 1)
);

CREATE INDEX idx_invitations_org                 ON organization_invitations (organization_id);
CREATE INDEX idx_invitations_org_email           ON organization_invitations (organization_id, invited_email);
CREATE INDEX idx_invitations_pending             ON organization_invitations (organization_id) WHERE accepted_at IS NULL AND revoked_at IS NULL;
