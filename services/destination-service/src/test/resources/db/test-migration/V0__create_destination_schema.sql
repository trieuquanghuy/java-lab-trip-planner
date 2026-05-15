-- Test migration: create destination schema and required extensions.
-- Runs before the production migrations (V2+) in Testcontainers Postgres.
CREATE SCHEMA IF NOT EXISTS destination;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
