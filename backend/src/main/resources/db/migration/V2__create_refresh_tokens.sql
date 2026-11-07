-- =============================================================
-- V2: refresh_tokens
-- Refresh tokens are opaque 256-bit random strings; only their
-- SHA-256 hex digest is stored, so a database leak cannot be replayed.
--
-- family_id links every token issued in one login session so that
-- detecting reuse of a rotated token lets us revoke the whole family
-- (classic refresh-token rotation anti-replay design).
--
-- ip_address is intentionally NOT stored: not needed for the first
-- slice, and collecting IP addresses creates privacy obligations.
-- device_information is a coarse, non-identifying label ("Chrome on Windows").
-- =============================================================
CREATE TABLE refresh_tokens (
    id                   UUID                        PRIMARY KEY,
    user_id              UUID                        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id            UUID                        NOT NULL,
    token_hash           VARCHAR(64)                 NOT NULL,
    expires_at           TIMESTAMP WITH TIME ZONE    NOT NULL,
    revoked_at           TIMESTAMP WITH TIME ZONE,
    replaced_by          UUID                        REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    device_information   VARCHAR(255),
    created_at           TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_refresh_tokens_hash     UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_hash_len CHECK (length(token_hash) = 64)
);

CREATE INDEX idx_refresh_tokens_user   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);
-- Hot path: "find the active token for this cookie"
CREATE INDEX idx_refresh_tokens_active ON refresh_tokens (family_id) WHERE revoked_at IS NULL;
