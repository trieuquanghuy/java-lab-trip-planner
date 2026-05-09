-- Source: docs/03-data-model.md §3.1; .planning/phases/02-auth-service/02-RESEARCH.md V2 migration.
-- Phase 2 / 02-CONTEXT.md D-09 (lower-case email storage), D-19 (bcrypt 60-72 char hash).
CREATE TABLE auth.users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(254) NOT NULL UNIQUE,
    password_hash   VARCHAR(72)  NOT NULL,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
