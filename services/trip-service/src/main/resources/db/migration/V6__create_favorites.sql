-- Phase 6 D-06/D-08: favorites table with composite PK.
CREATE TABLE trip.favorites (
    user_id          UUID         NOT NULL,
    destination_ref  VARCHAR(80)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, destination_ref)
);

CREATE INDEX favorites_user_created_idx ON trip.favorites (user_id, created_at DESC);
