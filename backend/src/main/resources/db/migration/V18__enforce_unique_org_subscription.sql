-- V18: Enforce one subscription per organization

-- Deduplicate any existing duplicates (retaining the latest updated row)
DELETE FROM organization_subscriptions os1
WHERE os1.id NOT IN (
    SELECT id FROM (
        SELECT DISTINCT ON (organization_id) id
        FROM organization_subscriptions
        ORDER BY organization_id, updated_at DESC, created_at DESC
    ) latest
);

-- Drop existing non-unique index and create unique index
DROP INDEX IF EXISTS idx_org_subscriptions_org_id;
CREATE UNIQUE INDEX idx_org_subscriptions_org_id_unique ON organization_subscriptions(organization_id);
