-- ============================================================
-- Security hardening migration — run manually against your database.
-- Scope: account lockout + session revocation columns on users,
-- plus the new activity_logs table.
-- ============================================================

ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS activity_logs (
    id SERIAL PRIMARY KEY,
    actor_id INTEGER REFERENCES users(id),
    actor_username VARCHAR(80),
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40),
    target_id INTEGER,
    detail VARCHAR(500),
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_activity_logs_action ON activity_logs (action);
CREATE INDEX IF NOT EXISTS ix_activity_logs_created_at ON activity_logs (created_at);
