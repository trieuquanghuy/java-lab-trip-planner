-- Enable cube and earthdistance extensions for radial geo-queries.
-- Safe to re-run: IF NOT EXISTS guards each extension.
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;

-- Destinations cache: provider-agnostic attraction data with automatic dedup.
-- provider_ref format: "otm:{xid}" or "fsq:{fsq_id}"
CREATE TABLE destination.destinations_cache (
    provider_ref   VARCHAR(80) PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    category       VARCHAR(80),
    rating         NUMERIC(3,1),
    lat            NUMERIC(9,6) NOT NULL,
    lng            NUMERIC(9,6) NOT NULL,
    address        VARCHAR(400),
    photos         JSONB NOT NULL DEFAULT '[]'::jsonb,
    opening_hours  JSONB,
    website        VARCHAR(2048),
    raw            JSONB NOT NULL,
    fetched_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- GIST index on earth point for radial bounding-box queries via earth_box @>.
CREATE INDEX destinations_cache_earth_idx
    ON destination.destinations_cache
    USING GIST (ll_to_earth(CAST(lat AS float8), CAST(lng AS float8)));

-- B-tree index on fetched_at for staleness filtering.
CREATE INDEX destinations_cache_fetched_at_idx
    ON destination.destinations_cache (fetched_at);
