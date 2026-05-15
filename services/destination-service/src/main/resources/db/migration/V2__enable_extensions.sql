-- Enable extensions required for full-text search with accent folding and trigram matching.
-- These are also in infra/postgres/init.sql but repeated here for standalone DB provisioning.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
