-- Phase 6 D-11: denormalized photo_url for cover image fallback.
ALTER TABLE trip.itinerary_items ADD COLUMN photo_url VARCHAR(2048) NULL;
