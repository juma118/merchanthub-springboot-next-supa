-- ─────────────────────────────────────────────────────────────────────────────
-- V2: Row-Level Security — the database-layer safety net.
--
-- The Spring Boot runtime connects as the non-superuser `merchanthub_app` role
-- and, at the start of every request transaction, runs:
--     SET LOCAL app.current_merchant_id = '<merchant uuid from JWT>';
-- These policies then constrain EVERY statement to that merchant's rows. If the
-- application layer ever forgets to scope a query, the database still refuses to
-- return or mutate another tenant's data.
--
-- current_setting(..., true) returns NULL (not an error) when the GUC is unset,
-- so an unscoped connection sees zero rows by default — fail closed.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION current_merchant_id()
RETURNS uuid AS $$
  SELECT NULLIF(current_setting('app.current_merchant_id', true), '')::uuid;
$$ LANGUAGE sql STABLE;

-- Helper: enable RLS + add a merchant-scoped policy for a table whose own
-- merchant_id column identifies the tenant.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['merchants','products','inventory','orders','order_items','sync_logs','alerts']
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
  END LOOP;
END
$$;

-- merchants: a tenant may only see/modify its own row.
CREATE POLICY merchants_isolation ON merchants
  USING (id = current_merchant_id())
  WITH CHECK (id = current_merchant_id());

-- All other tenant tables key off merchant_id.
CREATE POLICY products_isolation ON products
  USING (merchant_id = current_merchant_id())
  WITH CHECK (merchant_id = current_merchant_id());

CREATE POLICY inventory_isolation ON inventory
  USING (merchant_id = current_merchant_id())
  WITH CHECK (merchant_id = current_merchant_id());

CREATE POLICY orders_isolation ON orders
  USING (merchant_id = current_merchant_id())
  WITH CHECK (merchant_id = current_merchant_id());

CREATE POLICY order_items_isolation ON order_items
  USING (merchant_id = current_merchant_id())
  WITH CHECK (merchant_id = current_merchant_id());

CREATE POLICY sync_logs_isolation ON sync_logs
  USING (merchant_id = current_merchant_id())
  WITH CHECK (merchant_id = current_merchant_id());

CREATE POLICY alerts_isolation ON alerts
  USING (merchant_id = current_merchant_id())
  WITH CHECK (merchant_id = current_merchant_id());

-- The app role needs to read merchants by auth_user_id during login BEFORE a
-- merchant context exists (to resolve which merchant the JWT belongs to). That
-- single lookup is performed by the backend in a SECURITY DEFINER function so it
-- is not blocked by the merchants_isolation policy.
CREATE OR REPLACE FUNCTION resolve_merchant_by_auth_uid(p_auth_uid uuid)
RETURNS TABLE (id uuid, name text, email text, shop_api_key text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT m.id, m.name, m.email, m.shop_api_key
  FROM merchants m
  WHERE m.auth_user_id = p_auth_uid;
$$;

-- Resolve by email — used by the dev-token endpoint to find a seeded merchant's
-- auth uid so a dev login lands in the right tenant.
CREATE OR REPLACE FUNCTION resolve_merchant_by_email(p_email text)
RETURNS TABLE (id uuid, auth_user_id uuid, name text, email text, shop_api_key text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT m.id, m.auth_user_id, m.name, m.email, m.shop_api_key
  FROM merchants m
  WHERE lower(m.email) = lower(p_email);
$$;

-- Resolve by shop API key — used by the webhook receiver to map an inbound
-- signed payload to its owning tenant before any JWT context exists.
CREATE OR REPLACE FUNCTION resolve_merchant_by_api_key(p_key text)
RETURNS TABLE (id uuid, auth_user_id uuid, name text, email text, shop_api_key text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT m.id, m.auth_user_id, m.name, m.email, m.shop_api_key
  FROM merchants m
  WHERE m.shop_api_key = p_key;
$$;

-- List all merchants (id + key) — used by the scheduled pull-sync job, which
-- must fan out across every tenant. Returns only what the job needs.
CREATE OR REPLACE FUNCTION list_merchants_for_sync()
RETURNS TABLE (id uuid, shop_api_key text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT m.id, m.shop_api_key FROM merchants m;
$$;

-- Provisioning a brand-new merchant also happens before any context exists.
CREATE OR REPLACE FUNCTION provision_merchant(p_auth_uid uuid, p_name text, p_email text)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE new_id uuid;
BEGIN
  INSERT INTO merchants (auth_user_id, name, email)
  VALUES (p_auth_uid, p_name, p_email)
  ON CONFLICT (auth_user_id) DO UPDATE SET email = EXCLUDED.email
  RETURNING id INTO new_id;
  RETURN new_id;
END;
$$;

GRANT EXECUTE ON FUNCTION current_merchant_id() TO PUBLIC;
GRANT EXECUTE ON FUNCTION resolve_merchant_by_auth_uid(uuid) TO PUBLIC;
GRANT EXECUTE ON FUNCTION resolve_merchant_by_email(text) TO PUBLIC;
GRANT EXECUTE ON FUNCTION resolve_merchant_by_api_key(text) TO PUBLIC;
GRANT EXECUTE ON FUNCTION list_merchants_for_sync() TO PUBLIC;
GRANT EXECUTE ON FUNCTION provision_merchant(uuid, text, text) TO PUBLIC;
