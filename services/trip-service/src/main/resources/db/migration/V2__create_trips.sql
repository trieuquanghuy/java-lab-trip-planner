-- trips table: docs/03-data-model.md §3.3.
-- Phase 5 D-13: name VARCHAR(120) NOT NULL, dates nullable, CHECK end >= start.
CREATE TABLE trip.trips (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    name            VARCHAR(120) NOT NULL,
    start_date      DATE         NULL,
    end_date        DATE         NULL,
    cover_image_url VARCHAR(2048) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT trips_dates_check CHECK (end_date >= start_date)
);

CREATE INDEX trips_user_id_idx ON trip.trips (user_id, created_at DESC);
