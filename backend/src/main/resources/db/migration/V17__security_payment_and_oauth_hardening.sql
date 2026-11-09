-- V17: Security Hardening: Payment Transaction Concurrency & Google OAuth State Initiator

ALTER TABLE payment_transactions
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE google_oauth_credentials
    ADD COLUMN IF NOT EXISTS initiating_user_id UUID;
