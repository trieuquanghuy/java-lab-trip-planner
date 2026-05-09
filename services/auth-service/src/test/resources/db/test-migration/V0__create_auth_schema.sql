-- Source: 02-06-PLAN.md Task 6.1(c) bootstrap migration.
-- The Testcontainer Postgres image does NOT carry infra/postgres/init.sql, so the `auth` schema
-- doesn't pre-exist. This V0 migration bootstraps it before V1__init.sql runs.
-- (In compose deployments, init.sql creates the schema; this file is test-only.)
CREATE SCHEMA IF NOT EXISTS auth;
