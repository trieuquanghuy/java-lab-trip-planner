-- itinerary_days table: docs/03-data-model.md §3.4.
-- Phase 5: one row per date in trip's start_date..end_date range.
CREATE TABLE trip.itinerary_days (
    id        UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id   UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    day_date  DATE NOT NULL,
    day_index INT  NOT NULL,
    CONSTRAINT itinerary_days_trip_date_uq UNIQUE (trip_id, day_date),
    CONSTRAINT itinerary_days_trip_index_uq UNIQUE (trip_id, day_index),
    CONSTRAINT itinerary_days_index_positive CHECK (day_index >= 1)
);

CREATE INDEX itinerary_days_trip_idx ON trip.itinerary_days (trip_id, day_index);
