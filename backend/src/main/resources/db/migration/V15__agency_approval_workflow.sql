-- ============================================================
-- V15: Update agency_client_relationships default status to PENDING
-- ============================================================

ALTER TABLE agency_client_relationships
    ALTER COLUMN status SET DEFAULT 'PENDING';
