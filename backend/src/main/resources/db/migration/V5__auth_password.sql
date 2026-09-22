-- ─────────────────────────────────────────────────────────────────────────────
-- V5: Self-contained password auth (replaces the Supabase-Auth-or-dev-token
-- setup). `auth_user_id` stays as the stable JWT-subject identity — it's no
-- longer a mirror of a Supabase auth.users row, just an internal id minted at
-- registration — so MerchantResolver/JwtAuthFilter/RLS's SECURITY DEFINER
-- functions are unaffected.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE merchants ADD COLUMN password_hash text;

-- resolve_merchant_by_email now also returns password_hash for POST /api/auth/login.
-- The webhook/sync paths never call this function, so no other caller is affected.
DROP FUNCTION resolve_merchant_by_email(text);
CREATE FUNCTION resolve_merchant_by_email(p_email text)
RETURNS TABLE (id uuid, auth_user_id uuid, name text, email text, shop_api_key text, password_hash text)
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  SELECT m.id, m.auth_user_id, m.name, m.email, m.shop_api_key, m.password_hash
  FROM merchants m
  WHERE lower(m.email) = lower(p_email);
$$;
GRANT EXECUTE ON FUNCTION resolve_merchant_by_email(text) TO PUBLIC;

-- Registration path: create a merchant with a password in one call (distinct
-- from provision_merchant, which stays password-less for the tenant-isolation
-- test's direct provisioning and any future SSO-style flow).
CREATE OR REPLACE FUNCTION register_merchant(p_auth_uid uuid, p_name text, p_email text, p_password_hash text)
RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE new_id uuid;
BEGIN
  INSERT INTO merchants (auth_user_id, name, email, password_hash)
  VALUES (p_auth_uid, p_name, p_email, p_password_hash)
  RETURNING id INTO new_id;
  RETURN new_id;
END;
$$;
GRANT EXECUTE ON FUNCTION register_merchant(uuid, text, text, text) TO PUBLIC;

-- Demo accounts get a real password so the login form has something to log
-- into: both are "demo1234" (bcrypt, cost 10).
UPDATE merchants SET password_hash = '$2a$10$qysADQwVcasMG3hgU6phBeab5mQpLifO0AbTdM/vTeXbMZ3RjuGZW'
WHERE email IN ('demo@merchanthub.dev', 'rival@merchanthub.dev');
