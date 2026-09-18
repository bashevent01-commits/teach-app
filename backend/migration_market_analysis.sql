-- ============================================================
-- Market Analysis migration — run manually against your database.
-- Self-contained: creates product_categories first (new tables are also
-- auto-created by Base.metadata.create_all() on app startup, but this
-- migration doesn't depend on startup order) then adds the new columns
-- on existing tables that create_all() can't add on its own.
-- ============================================================

CREATE TABLE IF NOT EXISTS product_categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE stock_items ADD COLUMN IF NOT EXISTS category_id INTEGER REFERENCES product_categories(id);
CREATE INDEX IF NOT EXISTS ix_stock_items_category_id ON stock_items (category_id);

ALTER TABLE institutions ADD COLUMN IF NOT EXISTS region VARCHAR(50);
