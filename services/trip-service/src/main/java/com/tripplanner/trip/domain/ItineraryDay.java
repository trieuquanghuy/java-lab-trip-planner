package com.tripplanner.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "itinerary_days", schema = "trip")
public class ItineraryDay {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    protected ItineraryDay() {}

    public ItineraryDay(UUID id, UUID tripId, LocalDate dayDate, int dayIndex) {
        this.id = id;
        this.tripId = tripId;
        this.dayDate = dayDate;
        this.dayIndex = dayIndex;
    }

    public UUID getId() { return id; }
    public UUID getTripId() { return tripId; }
    public LocalDate getDayDate() { return dayDate; }
    public int getDayIndex() { return dayIndex; }
}
