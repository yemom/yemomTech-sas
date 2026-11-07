-- =============================================================
-- V8: user_tokens
-- One-time, secure tokens for email verification and password reset.
-- Tokens are opaque 256-bit random strings; only their SHA-256
-- hex digest is stored (64 chars). Once consumed_at is set, the
-- token cannot be reused.
-- =============================================================
CREATE TABLE user_tokens (
    id                  UUID                        PRIMARY KEY,
    user_id             UUID                        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_type          VARCHAR(32)                 NOT NULL,
    token_hash          VARCHAR(64)                 NOT NULL,
    expires_at          TIMESTAMP WITH TIME ZONE    NOT NULL,
    consumed_at         TIMESTAMP WITH TIME ZONE,
    created_at          TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_user_tokens_hash_len CHECK (length(token_hash) = 64),
    CONSTRAINT ck_user_tokens_type CHECK (token_type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET'))
);

CREATE INDEX idx_user_tokens_user ON user_tokens (user_id);
CREATE INDEX idx_user_tokens_lookup ON user_tokens (token_type, token_hash) WHERE consumed_at IS NULL;
