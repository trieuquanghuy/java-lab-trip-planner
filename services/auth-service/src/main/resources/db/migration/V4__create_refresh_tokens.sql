-- Source: docs/05-auth-security.md §2; .planning/phases/02-auth-service/02-RESEARCH.md V4 migration.
-- Phase 2 / 02-CONTEXT.md D-10 (replay revocation walks chain via rotated_to reverse-lookup),
-- D-13 (SELECT FOR UPDATE on token_hash), D-17 (cleanup uses expires_at index).
CREATE TABLE auth.refresh_tokens (
    token_hash   CHAR(64)    PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ NOT NULL,
    rotated_to   CHAR(64)    NULL,
    revoked_at   TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX rt_rotated_to_idx
    ON auth.refresh_tokens (rotated_to)
    WHERE rotated_to IS NOT NULL;
CREATE INDEX rt_expires_at_idx
    ON auth.refresh_tokens (expires_at);
