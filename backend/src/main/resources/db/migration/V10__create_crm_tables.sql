-- =============================================================
-- V10: CRM leads, sales pipeline stages, and lead activities
-- Multi-tenant lead management with stage progression, ETB valuation,
-- activity timelines, and secure public lead capture.
-- =============================================================

CREATE TABLE crm_leads (
    id                      UUID                        PRIMARY KEY,
    organization_id         UUID                        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    first_name              VARCHAR(100)                NOT NULL,
    last_name               VARCHAR(100),
    email                   VARCHAR(255),
    phone_number            VARCHAR(50),
    company_name            VARCHAR(200),
    source                  VARCHAR(32)                 NOT NULL DEFAULT 'DIRECT',
    stage                   VARCHAR(32)                 NOT NULL DEFAULT 'NEW',
    status                  VARCHAR(32)                 NOT NULL DEFAULT 'ACTIVE',
    assigned_user_id        UUID                        REFERENCES users (id) ON DELETE SET NULL,
    estimated_value_etb     NUMERIC(15, 2),
    notes                   TEXT,
    created_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_crm_leads_stage  CHECK (stage IN ('NEW', 'CONTACTED', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
    CONSTRAINT ck_crm_leads_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_crm_leads_source CHECK (source IN ('DIRECT', 'WEBSITE_FORM', 'CHATBOT', 'CAMPAIGN', 'REFERRAL', 'IMPORT', 'OTHER'))
);

CREATE INDEX idx_crm_leads_org_stage   ON crm_leads (organization_id, stage);
CREATE INDEX idx_crm_leads_org_created ON crm_leads (organization_id, created_at DESC);
CREATE INDEX idx_crm_leads_assigned    ON crm_leads (assigned_user_id);
CREATE INDEX idx_crm_leads_org_status  ON crm_leads (organization_id, status);

CREATE TABLE crm_lead_activities (
    id                      UUID                        PRIMARY KEY,
    organization_id         UUID                        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    lead_id                 UUID                        NOT NULL REFERENCES crm_leads (id) ON DELETE CASCADE,
    actor_user_id           UUID                        REFERENCES users (id) ON DELETE SET NULL,
    activity_type           VARCHAR(32)                 NOT NULL,
    description             VARCHAR(2000)               NOT NULL,
    metadata                JSONB,
    created_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_crm_activities_type CHECK (activity_type IN ('STAGE_CHANGE', 'NOTE', 'CALL', 'EMAIL', 'MEETING', 'FORM_SUBMISSION', 'ASSIGNMENT'))
);

CREATE INDEX idx_crm_activities_lead ON crm_lead_activities (lead_id, created_at DESC);
CREATE INDEX idx_crm_activities_org  ON crm_lead_activities (organization_id);
