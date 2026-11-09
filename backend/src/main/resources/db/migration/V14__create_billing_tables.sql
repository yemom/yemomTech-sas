-- ============================================================
-- V14: Billing — subscription plans, org subscriptions,
--      payment transactions, billing history
-- ============================================================

-- ----------------------------------------------------------------
-- 1. Subscription plans (seed data included)
-- ----------------------------------------------------------------
CREATE TABLE subscription_plans (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(100)    NOT NULL,
    slug                VARCHAR(60)     NOT NULL UNIQUE,
    description         TEXT,
    price_etb           NUMERIC(12, 2)  NOT NULL DEFAULT 0,
    billing_interval    VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY',   -- MONTHLY | YEARLY | ONE_TIME
    max_websites        INT             NOT NULL DEFAULT 1,
    max_members         INT             NOT NULL DEFAULT 3,
    max_leads           INT             NOT NULL DEFAULT 500,
    max_campaigns       INT             NOT NULL DEFAULT 2,
    has_ai_insights     BOOLEAN         NOT NULL DEFAULT FALSE,
    has_agency_workspace BOOLEAN        NOT NULL DEFAULT FALSE,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    sort_order          INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Seed plans
INSERT INTO subscription_plans
  (name, slug, description, price_etb, billing_interval, max_websites, max_members, max_leads,
   max_campaigns, has_ai_insights, has_agency_workspace, sort_order)
VALUES
  ('Free',    'free',       'Get started with 1 website and basic SEO audits.',
   0,       'MONTHLY', 1,  3,    200,  1, FALSE, FALSE, 0),
  ('Starter', 'starter',    'For growing businesses that need deeper insights.',
   999,     'MONTHLY', 3,  10,   2000, 5, FALSE, FALSE, 1),
  ('Growth',  'growth',     'Full AI-powered growth suite for serious marketers.',
   2499,    'MONTHLY', 10, 25,   10000, 20, TRUE,  FALSE, 2),
  ('Agency',  'agency',     'Multi-client workspace for digital marketing agencies.',
   5999,    'MONTHLY', 50, 100,  50000, 100, TRUE,  TRUE,  3);

-- ----------------------------------------------------------------
-- 2. Organization subscriptions
-- ----------------------------------------------------------------
CREATE TABLE organization_subscriptions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID            NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    plan_id             UUID            NOT NULL REFERENCES subscription_plans(id),
    status              VARCHAR(30)     NOT NULL DEFAULT 'ACTIVE',
        -- ACTIVE | TRIALING | PAST_DUE | CANCELLED | EXPIRED
    current_period_start TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    current_period_end  TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN        NOT NULL DEFAULT FALSE,
    chapa_subscription_id VARCHAR(255),              -- future recurring billing ref
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_org_subscriptions_org_id  ON organization_subscriptions(organization_id);
CREATE INDEX idx_org_subscriptions_status  ON organization_subscriptions(status);

-- Bootstrap: give every existing org a free subscription
INSERT INTO organization_subscriptions (organization_id, plan_id, status, current_period_start)
SELECT o.id, (SELECT id FROM subscription_plans WHERE slug = 'free'), 'ACTIVE', NOW()
FROM organizations o
WHERE NOT EXISTS (
    SELECT 1 FROM organization_subscriptions s WHERE s.organization_id = o.id
);

-- ----------------------------------------------------------------
-- 3. Payment transactions  (Chapa-specific fields included)
-- ----------------------------------------------------------------
CREATE TABLE payment_transactions (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID            NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    subscription_id     UUID            REFERENCES organization_subscriptions(id),
    plan_id             UUID            REFERENCES subscription_plans(id),
    tx_ref              VARCHAR(255)    NOT NULL UNIQUE,   -- idempotency key sent to Chapa
    chapa_transaction_id VARCHAR(255),                    -- returned by Chapa after success
    amount_etb          NUMERIC(12, 2)  NOT NULL,
    currency            VARCHAR(10)     NOT NULL DEFAULT 'ETB',
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
        -- PENDING | SUCCESS | FAILED | REFUNDED | CANCELLED
    provider            VARCHAR(50)     NOT NULL DEFAULT 'CHAPA',
    checkout_url        TEXT,                             -- redirect URL given to customer
    callback_url        TEXT,                             -- our /api/v1/billing/chapa/callback
    return_url          TEXT,                             -- frontend success page
    initiated_by_user_id UUID           REFERENCES users(id),
    verified_at         TIMESTAMPTZ,
    failure_reason      TEXT,
    raw_provider_response JSONB         NOT NULL DEFAULT '{}',
    idempotency_key     VARCHAR(255)    NOT NULL UNIQUE,  -- prevents duplicate charges
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_tx_org_id   ON payment_transactions(organization_id);
CREATE INDEX idx_payment_tx_tx_ref   ON payment_transactions(tx_ref);
CREATE INDEX idx_payment_tx_status   ON payment_transactions(status);
CREATE INDEX idx_payment_tx_created  ON payment_transactions(created_at DESC);

-- ----------------------------------------------------------------
-- 4. Billing event history  (append-only audit trail)
-- ----------------------------------------------------------------
CREATE TABLE billing_history (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID            NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    transaction_id      UUID            REFERENCES payment_transactions(id),
    subscription_id     UUID            REFERENCES organization_subscriptions(id),
    event_type          VARCHAR(60)     NOT NULL,
        -- SUBSCRIPTION_CREATED | SUBSCRIPTION_UPGRADED | SUBSCRIPTION_DOWNGRADED |
        -- SUBSCRIPTION_CANCELLED | PAYMENT_SUCCESS | PAYMENT_FAILED |
        -- PLAN_CHANGED | TRIAL_STARTED | TRIAL_ENDED
    description         TEXT            NOT NULL,
    amount_etb          NUMERIC(12, 2),
    metadata            JSONB           NOT NULL DEFAULT '{}',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_billing_history_org_id ON billing_history(organization_id);
CREATE INDEX idx_billing_history_created ON billing_history(created_at DESC);
