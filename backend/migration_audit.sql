-- ============================================================
-- Audit feature migration — run manually against your database.
-- Scope: transactions table only (method, image_path, drop audit_id).
-- ============================================================

ALTER TABLE transactions ADD COLUMN IF NOT EXISTS method VARCHAR(10) NOT NULL DEFAULT 'cash';
ALTER TABLE transactions ADD COLUMN IF NOT EXISTS image_path VARCHAR(500);

-- Drop the old manual "attach to audit" link — an audit's transactions
-- are now computed from its period_start/period_end instead.
ALTER TABLE transactions DROP COLUMN IF EXISTS audit_id;

-- Optional but recommended: remove the DEFAULT once existing rows are
-- backfilled, so every future insert must specify a real method.
-- ALTER TABLE transactions ALTER COLUMN method DROP DEFAULT;