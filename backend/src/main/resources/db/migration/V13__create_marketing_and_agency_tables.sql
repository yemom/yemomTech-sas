-- =============================================================
-- V13: Marketing Campaigns, Agency Client Relationships & Lead Attribution
-- =============================================================

-- Marketing Campaigns
CREATE TABLE marketing_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    objective VARCHAR(64) NOT NULL, -- LEAD_GENERATION, BRAND_AWARENESS, WEBSITE_TRAFFIC, SALES, EVENT_PROMOTION
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', -- DRAFT, ACTIVE, PAUSED, COMPLETED
    channel VARCHAR(64) NOT NULL, -- GOOGLE_ADS, META_ADS, TELEGRAM, TIKTOK, EMAIL, DIRECT, SMS
    budget_etb NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (budget_etb >= 0),
    spent_etb NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (spent_etb >= 0),
    start_date DATE,
    end_date DATE,
    utm_source VARCHAR(100),
    utm_medium VARCHAR(100),
    utm_campaign VARCHAR(100),
    utm_term VARCHAR(100),
    utm_content VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_campaigns_org_status ON marketing_campaigns(organization_id, status);
CREATE INDEX idx_campaigns_org_utm ON marketing_campaigns(organization_id, utm_campaign);

-- Add campaign attribution column to CRM leads
ALTER TABLE crm_leads ADD COLUMN campaign_id UUID REFERENCES marketing_campaigns(id) ON DELETE SET NULL;
CREATE INDEX idx_leads_campaign ON crm_leads(organization_id, campaign_id);

-- Agency Client Relationships (Agencies managing multiple client organizations)
CREATE TABLE agency_client_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agency_organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    client_organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PENDING, REVOKED
    access_level VARCHAR(32) NOT NULL DEFAULT 'FULL', -- FULL, READ_ONLY, MARKETING_ONLY
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_agency_client UNIQUE (agency_organization_id, client_organization_id),
    CONSTRAINT chk_different_orgs CHECK (agency_organization_id <> client_organization_id)
);

CREATE INDEX idx_agency_client_agency ON agency_client_relationships(agency_organization_id, status);
CREATE INDEX idx_agency_client_client ON agency_client_relationships(client_organization_id, status);
