-- =============================================================
-- V12 — AI Recommendations & Intelligent Chatbot Tables
-- =============================================================
-- ai_recommendations: actionable growth & optimization suggestions
-- chatbot_configs: per-organization website chatbot customization & knowledge base
-- chatbot_conversations: visitor chat sessions
-- chatbot_messages: individual user and AI assistant messages
-- =============================================================

-- ---- AI Recommendations ---------------------------------------------------

CREATE TABLE ai_recommendations (
    id                    UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID                        NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    website_id            UUID                        REFERENCES websites(id) ON DELETE CASCADE,
    category              VARCHAR(32)                 NOT NULL,
    priority              VARCHAR(16)                 NOT NULL,
    title                 VARCHAR(255)                NOT NULL,
    problem               TEXT                        NOT NULL,
    evidence              TEXT,
    expected_benefit      TEXT,
    action_recommendation TEXT                        NOT NULL,
    status                VARCHAR(16)                 NOT NULL DEFAULT 'OPEN',
    created_at            TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_recommendation_status CHECK (status IN ('OPEN', 'APPLIED', 'DISMISSED')),
    CONSTRAINT ck_ai_recommendation_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW'))
);

CREATE INDEX idx_ai_rec_org_status ON ai_recommendations (organization_id, status);
CREATE INDEX idx_ai_rec_org_website ON ai_recommendations (organization_id, website_id);

-- ---- Chatbot Configurations -----------------------------------------------

CREATE TABLE chatbot_configs (
    id                    UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID                        NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    bot_name              VARCHAR(128)                NOT NULL DEFAULT 'BizGrow Assistant',
    greeting_message      TEXT                        NOT NULL DEFAULT 'Hello! How can we assist your business today?',
    prompt_instructions   TEXT,
    primary_color         VARCHAR(32)                 NOT NULL DEFAULT '#0f766e',
    faq_items             JSONB,
    is_enabled            BOOLEAN                     NOT NULL DEFAULT true,
    created_at            TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now()
);

CREATE INDEX idx_chatbot_configs_org ON chatbot_configs (organization_id);

-- ---- Chatbot Conversations ------------------------------------------------

CREATE TABLE chatbot_conversations (
    id                    UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id       UUID                        NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    session_id            VARCHAR(128)                NOT NULL,
    visitor_name          VARCHAR(128),
    visitor_email         VARCHAR(255),
    visitor_phone         VARCHAR(64),
    created_at            TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    CONSTRAINT uq_chatbot_conv_org_session UNIQUE (organization_id, session_id)
);

CREATE INDEX idx_chatbot_conv_org_created ON chatbot_conversations (organization_id, created_at DESC);

-- ---- Chatbot Messages -----------------------------------------------------

CREATE TABLE chatbot_messages (
    id                    UUID                        PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id       UUID                        NOT NULL REFERENCES chatbot_conversations(id) ON DELETE CASCADE,
    role                  VARCHAR(16)                 NOT NULL,
    content               TEXT                        NOT NULL,
    created_at            TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT now(),
    CONSTRAINT ck_chatbot_message_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_chatbot_msg_conv_created ON chatbot_messages (conversation_id, created_at ASC);
