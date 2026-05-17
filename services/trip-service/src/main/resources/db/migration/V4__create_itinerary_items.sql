-- itinerary_items table: docs/03-data-model.md §3.5.
-- Phase 5 creates the table (empty); Phase 6 adds CRUD.
-- Needed now for DayMaterializationService shrink-conflict COUNT query (D-03).
CREATE TABLE trip.itinerary_items (
    id                UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    itinerary_day_id  UUID         NOT NULL REFERENCES trip.itinerary_days(id) ON DELETE CASCADE,
    destination_ref   VARCHAR(80)  NOT NULL,
    position          INT          NOT NULL,
    time_slot         TIME         NULL,
    note              VARCHAR(500) NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX itinerary_items_day_pos_idx ON trip.itinerary_items (itinerary_day_id, position);
