-- Double-entry ledger tables. create_all builds these on startup; this file is for manual runs and RLS.
-- Existing transactions are posted lazily the first time an institution's books are read.

DO $$ BEGIN
    CREATE TYPE accounttype AS ENUM ('asset', 'liability', 'equity', 'income', 'expense');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE IF NOT EXISTS accounts (
    id SERIAL PRIMARY KEY,
    institution_id INTEGER NOT NULL REFERENCES institutions(id),
    key VARCHAR(120) NOT NULL,
    code INTEGER NOT NULL,
    name VARCHAR(120) NOT NULL,
    type accounttype NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now(),
    CONSTRAINT uq_accounts_institution_key UNIQUE (institution_id, key),
    CONSTRAINT uq_accounts_institution_code UNIQUE (institution_id, code)
);

CREATE TABLE IF NOT EXISTS journal_entries (
    id SERIAL PRIMARY KEY,
    institution_id INTEGER NOT NULL REFERENCES institutions(id),
    transaction_id INTEGER UNIQUE REFERENCES transactions(id) ON DELETE CASCADE,
    entry_date TIMESTAMPTZ NOT NULL,
    memo TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS journal_lines (
    id SERIAL PRIMARY KEY,
    entry_id INTEGER NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id INTEGER NOT NULL REFERENCES accounts(id),
    debit NUMERIC(12, 2) NOT NULL DEFAULT 0,
    credit NUMERIC(12, 2) NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS ix_accounts_institution_id ON accounts (institution_id);
CREATE INDEX IF NOT EXISTS ix_journal_entries_institution_id ON journal_entries (institution_id);
CREATE INDEX IF NOT EXISTS ix_journal_entries_entry_date ON journal_entries (entry_date);
CREATE INDEX IF NOT EXISTS ix_journal_lines_entry_id ON journal_lines (entry_id);
CREATE INDEX IF NOT EXISTS ix_journal_lines_account_id ON journal_lines (account_id);

ALTER TABLE accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE journal_lines ENABLE ROW LEVEL SECURITY;
