-- Phase 0 scaffold: 3 schemas + 3 service users with schema-scoped grants.
-- Idempotent: safe to re-run after `docker compose down -v` (mounted via
-- /docker-entrypoint-initdb.d/init.sql which only runs on FIRST container init
-- for the postgres-data volume — but the IF NOT EXISTS guards make manual
-- re-execution against an already-populated DB safe as well).
-- Source: 00-CONTEXT.md D-08 + 00-RESEARCH.md Pattern 6.

-- Schemas
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS trip;
CREATE SCHEMA IF NOT EXISTS destination;

-- Users (literal hard-coded dev passwords — match defaults in .env.example;
-- NOT real secrets. PostgreSQL has no `CREATE USER ... IF NOT EXISTS` syntax;
-- the DO $$ ... pg_roles WHERE rolname='X' guard pattern below is the standard
-- idempotency idiom.)
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='auth_svc') THEN
    CREATE ROLE auth_svc LOGIN PASSWORD 'auth_svc';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='trip_svc') THEN
    CREATE ROLE trip_svc LOGIN PASSWORD 'trip_svc';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='destination_svc') THEN
    CREATE ROLE destination_svc LOGIN PASSWORD 'destination_svc';
  END IF;
END $$;

-- Schema-scoped grants — per-service user has USAGE + CREATE on its own schema
-- only (D-08 cross-schema denial). No grants on other services' schemas.
GRANT USAGE, CREATE ON SCHEMA auth TO auth_svc;
GRANT USAGE, CREATE ON SCHEMA trip TO trip_svc;
GRANT USAGE, CREATE ON SCHEMA destination TO destination_svc;

-- search_path defaults — each user lands in its own schema by default so
-- unqualified table references resolve correctly. JDBC URLs in each service's
-- application.yml also set ?currentSchema=<svc> as the primary lock; this
-- ALTER ROLE is defense-in-depth.
ALTER ROLE auth_svc SET search_path = auth;
ALTER ROLE trip_svc SET search_path = trip;
ALTER ROLE destination_svc SET search_path = destination;
