-- ─────────────────────────────────────────────────────────────────────────────
-- V1: Core multi-tenant schema.
-- Every tenant-owned table carries merchant_id and is RLS-scoped in V2.
-- Run by Flyway as the postgres superuser.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- Keeps updated_at fresh on any UPDATE.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── merchants (tenants) ──────────────────────────────────────────────────────
CREATE TABLE merchants (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  auth_user_id  uuid UNIQUE,                       -- maps to Supabase auth uid (JWT sub)
  name          text NOT NULL,
  email         text,
  shop_api_key  text UNIQUE NOT NULL DEFAULT encode(gen_random_bytes(16), 'hex'),
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now()
);

-- ── products ─────────────────────────────────────────────────────────────────
CREATE TABLE products (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id  uuid NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
  sku          text NOT NULL,
  name         text NOT NULL,
  description  text,
  price        numeric(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
  image_url    text,
  external_id  text,                               -- id in the shop API
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (merchant_id, sku)
);
CREATE INDEX idx_products_merchant ON products(merchant_id);
CREATE UNIQUE INDEX idx_products_merchant_external ON products(merchant_id, external_id)
  WHERE external_id IS NOT NULL;

-- ── inventory (one row per product) ──────────────────────────────────────────
CREATE TABLE inventory (
  id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id          uuid NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
  product_id           uuid NOT NULL UNIQUE REFERENCES products(id) ON DELETE CASCADE,
  quantity             integer NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  low_stock_threshold  integer NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
  created_at           timestamptz NOT NULL DEFAULT now(),
  updated_at           timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_inventory_merchant ON inventory(merchant_id);

-- ── orders ───────────────────────────────────────────────────────────────────
CREATE TABLE orders (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id  uuid NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
  external_id  text,                               -- id in the shop API (idempotency key)
  total        numeric(12,2) NOT NULL DEFAULT 0 CHECK (total >= 0),
  currency     text NOT NULL DEFAULT 'USD',
  status       text NOT NULL DEFAULT 'created'
                 CHECK (status IN ('created','paid','fulfilled','cancelled','abandoned')),
  customer_email text,
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now(),
  UNIQUE (merchant_id, external_id)
);
CREATE INDEX idx_orders_merchant_created ON orders(merchant_id, created_at);
CREATE INDEX idx_orders_merchant_status ON orders(merchant_id, status);

-- ── order_items ──────────────────────────────────────────────────────────────
-- merchant_id is denormalized here so the row is directly RLS-scoped and joins
-- stay cheap (documented trade-off vs. the spec's minimal columns).
CREATE TABLE order_items (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id  uuid NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
  order_id     uuid NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  product_id   uuid REFERENCES products(id) ON DELETE SET NULL,
  sku          text,
  quantity     integer NOT NULL CHECK (quantity > 0),
  unit_price   numeric(12,2) NOT NULL CHECK (unit_price >= 0)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_merchant_product ON order_items(merchant_id, product_id);

-- ── sync_logs (audit trail for pull-sync / webhook ingestion) ────────────────
CREATE TABLE sync_logs (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id  uuid NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
  type         text NOT NULL CHECK (type IN ('pull_sync','webhook')),
  status       text NOT NULL DEFAULT 'running'
                 CHECK (status IN ('running','success','failed')),
  detail       text,
  records_processed integer NOT NULL DEFAULT 0,
  started_at   timestamptz NOT NULL DEFAULT now(),
  finished_at  timestamptz
);
CREATE INDEX idx_sync_logs_merchant ON sync_logs(merchant_id, started_at DESC);

-- ── alerts (drives realtime notifications) ───────────────────────────────────
CREATE TABLE alerts (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  merchant_id  uuid NOT NULL REFERENCES merchants(id) ON DELETE CASCADE,
  type         text NOT NULL CHECK (type IN ('new_order','low_stock','sync_complete','sync_failed')),
  payload      jsonb NOT NULL DEFAULT '{}'::jsonb,
  read         boolean NOT NULL DEFAULT false,
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_alerts_merchant_created ON alerts(merchant_id, created_at DESC);
CREATE INDEX idx_alerts_unread ON alerts(merchant_id) WHERE read = false;

-- ── updated_at triggers ──────────────────────────────────────────────────────
CREATE TRIGGER trg_merchants_updated  BEFORE UPDATE ON merchants  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_products_updated   BEFORE UPDATE ON products   FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_inventory_updated  BEFORE UPDATE ON inventory  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trg_orders_updated     BEFORE UPDATE ON orders     FOR EACH ROW EXECUTE FUNCTION set_updated_at();
