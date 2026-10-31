-- Branches can each hold their own item of the same name; the old rule allowed one per business and broke stock exchange
ALTER TABLE stock_items DROP CONSTRAINT IF EXISTS stock_items_institution_id_name_key;
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_items_branch_name ON stock_items (institution_id, branch_id, name) WHERE branch_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_items_shared_name ON stock_items (institution_id, name) WHERE branch_id IS NULL;
