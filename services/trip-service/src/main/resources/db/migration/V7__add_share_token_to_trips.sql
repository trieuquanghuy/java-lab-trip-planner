ALTER TABLE trip.trips
    ADD COLUMN share_token UUID NULL,
    ADD COLUMN share_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX trips_share_token_idx ON trip.trips (share_token)
    WHERE share_token IS NOT NULL;
