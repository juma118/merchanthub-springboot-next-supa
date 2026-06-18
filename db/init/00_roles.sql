-- ─────────────────────────────────────────────────────────────────────────────
-- Cluster-level setup that runs ONCE on first database init (empty data dir),
-- before the backend starts and before Flyway runs. Creates the restricted
-- application role that the Spring Boot runtime connects as. Because this role is
-- NOT a superuser and does NOT have BYPASSRLS, every query it issues is subject
-- to Row-Level Security — the database-layer safety net described in PROJECT.md.
--
-- Flyway connects separately as the `postgres` superuser to run migrations.
--
-- NOTE: psql does not interpolate :variables inside dollar-quoted ($$) blocks, so
-- the role is created with plain statements using identifier/literal interpolation.
-- This script only runs on a fresh volume, so the role cannot pre-exist.
-- ─────────────────────────────────────────────────────────────────────────────

\set app_user `echo "$APP_DB_USER"`
\set app_password `echo "$APP_DB_PASSWORD"`

CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password'
  NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

-- Allow the app role to use the public schema.
GRANT USAGE ON SCHEMA public TO :"app_user";

-- Tables/sequences are created later by Flyway (as postgres). Default privileges
-- ensure the app role automatically receives DML rights on those future objects.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";
