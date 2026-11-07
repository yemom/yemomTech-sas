-- =============================================================
-- V1: users
-- Every account in the platform. Email is stored already normalized
-- (trimmed + lowercased) by the application, and the UNIQUE constraint
-- is the final defense against duplicate registrations racing each other.
-- Passwords are BCrypt hashes (60 chars); plaintext never touches the DB.
-- =============================================================
CREATE TABLE users (
    id              UUID                        PRIMARY KEY,
    first_name      VARCHAR(100)                NOT NULL,
    last_name       VARCHAR(100)                NOT NULL,
    email           VARCHAR(255)                NOT NULL,
    password_hash   VARCHAR(100)                NOT NULL,
    phone_number    VARCHAR(32),
    status          VARCHAR(32)                 NOT NULL DEFAULT 'ACTIVE',
    email_verified  BOOLEAN                     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_users_email          UNIQUE (email),
    CONSTRAINT ck_users_status         CHECK (status IN ('ACTIVE', 'PENDING_VERIFICATION', 'SUSPENDED', 'DEACTIVATED')),
    CONSTRAINT ck_users_first_name     CHECK (length(trim(first_name)) > 0),
    CONSTRAINT ck_users_last_name      CHECK (length(trim(last_name)) > 0),
    CONSTRAINT ck_users_email_shape    CHECK (position('@' IN email) > 1)
);

CREATE INDEX idx_users_status ON users (status);
