package com.tripplanner.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "itinerary_items", schema = "trip")
public class ItineraryItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "itinerary_day_id", nullable = false)
    private UUID itineraryDayId;

    @Column(name = "destination_ref", nullable = false, length = 80)
    private String destinationRef;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "time_slot")
    private LocalTime timeSlot;

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ItineraryItem() {}

    // No public constructor needed in Phase 5 — entity exists only for JPQL COUNT query
    public UUID getId() { return id; }
    public UUID getItineraryDayId() { return itineraryDayId; }
}
