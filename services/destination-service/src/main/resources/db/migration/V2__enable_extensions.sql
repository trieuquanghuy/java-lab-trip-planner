-- Enable extensions required for full-text search with accent folding and trigram matching.
-- These are also in infra/postgres/init.sql but repeated here for standalone DB provisioning.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Immutable wrapper for unaccent() — required for use in GENERATED ALWAYS AS columns.
-- PostgreSQL's built-in unaccent() is STABLE (depends on dictionary), but for our use case
-- the dictionary never changes at runtime, so we can safely wrap it as IMMUTABLE.
CREATE OR REPLACE FUNCTION immutable_unaccent(text) RETURNS text AS $$
  SELECT unaccent($1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE;
