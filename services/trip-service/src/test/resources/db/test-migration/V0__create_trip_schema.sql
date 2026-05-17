-- Schema bootstrap for Testcontainers Postgres (which lacks infra/postgres/init.sql).
-- Must run before V1__init.sql.
CREATE SCHEMA IF NOT EXISTS trip;
