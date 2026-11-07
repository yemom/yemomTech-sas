-- =============================================================
-- V4: organization_members
-- Membership is the ONLY proof that a user may touch a tenant.
-- The JWT never proves organization access; every tenant operation
-- re-reads this table through the application's access-control layer.
--
-- A user can belong to many organizations with different roles
-- (UNIQUE is on the pair, not on user_id alone).
-- =============================================================
CREATE TABLE organization_members (
    id                UUID                        PRIMARY KEY,
    organization_id   UUID                        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id           UUID                        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role              VARCHAR(32)                 NOT NULL,
    membership_status VARCHAR(32)                 NOT NULL DEFAULT 'ACTIVE',
    joined_at         TIMESTAMP WITH TIME ZONE,
    created_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_org_member_user   UNIQUE (organization_id, user_id),
    CONSTRAINT ck_org_member_role   CHECK (role IN ('BUSINESS_OWNER', 'BUSINESS_ADMIN', 'MARKETING_MANAGER',
                                                    'SALES_MANAGER', 'SALES_AGENT', 'ANALYST', 'VIEWER')),
    CONSTRAINT ck_org_member_status CHECK (membership_status IN ('ACTIVE', 'SUSPENDED', 'REMOVED'))
);

CREATE INDEX idx_org_members_org  ON organization_members (organization_id);
CREATE INDEX idx_org_members_user ON organization_members (user_id);
-- Listing pages filter by status constantly
CREATE INDEX idx_org_members_org_status ON organization_members (organization_id, membership_status);
