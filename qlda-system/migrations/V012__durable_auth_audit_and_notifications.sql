SET client_encoding = 'UTF8';

-- Persist refresh-token state so sessions survive service restarts and work
-- consistently when auth-service is scaled horizontally. Only a SHA-256 hash
-- of the token is stored.
CREATE TABLE IF NOT EXISTS auth_refresh_token (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES nguoidung(id) ON DELETE CASCADE,
    username VARCHAR(150) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_refresh_token_user_active
    ON auth_refresh_token (user_id, revoked, expires_at);

-- Audit history must not disappear when auth-service restarts.
CREATE TABLE IF NOT EXISTS auth_audit_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    full_name VARCHAR(255),
    action VARCHAR(120) NOT NULL,
    object_name VARCHAR(160),
    object_id BIGINT,
    detail TEXT,
    ip_address VARCHAR(64),
    performed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_auth_audit_log_time
    ON auth_audit_log (performed_at DESC);
CREATE INDEX IF NOT EXISTS idx_auth_audit_log_user_time
    ON auth_audit_log (user_id, performed_at DESC);

-- Kafka consumers use this table to avoid creating duplicate notifications
-- after a restart or message redelivery.
CREATE TABLE IF NOT EXISTS processed_notification_event (
    event_id VARCHAR(120) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_processed_notification_event_time
    ON processed_notification_event (processed_at DESC);
