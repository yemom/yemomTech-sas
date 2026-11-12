-- V19: Add PKCE code verifier column to Google OAuth credentials

ALTER TABLE google_oauth_credentials
    ADD COLUMN IF NOT EXISTS code_verifier VARCHAR(256);

COMMENT ON COLUMN google_oauth_credentials.code_verifier IS
    'PKCE code verifier generated during OAuth flow, stored for exchange at callback.';
