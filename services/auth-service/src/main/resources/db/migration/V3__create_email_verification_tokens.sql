-- Source: docs/03-data-model.md §3.2; .planning/phases/02-auth-service/02-RESEARCH.md V3 migration.
-- Phase 2 / 02-CONTEXT.md D-23 re-signup branch (markAllUnconsumedAsConsumedFor uses the
-- partial unconsumed index for fast lookup); D-04 (24h TTL).
CREATE TABLE auth.email_verification_tokens (
    token        CHAR(64)    PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX evt_unconsumed_idx
    ON auth.email_verification_tokens (user_id)
    WHERE consumed_at IS NULL;
