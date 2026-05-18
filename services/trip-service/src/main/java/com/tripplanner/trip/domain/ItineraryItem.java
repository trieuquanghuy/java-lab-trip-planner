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

    @Column(name = "photo_url", length = 2048)
    private String photoUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ItineraryItem() {}

    public ItineraryItem(UUID id, UUID itineraryDayId, String destinationRef, int position,
                         LocalTime timeSlot, String note, String photoUrl) {
        this.id = id;
        this.itineraryDayId = itineraryDayId;
        this.destinationRef = destinationRef;
        this.position = position;
        this.timeSlot = timeSlot;
        this.note = note;
        this.photoUrl = photoUrl;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getItineraryDayId() { return itineraryDayId; }
    public String getDestinationRef() { return destinationRef; }
    public int getPosition() { return position; }
    public LocalTime getTimeSlot() { return timeSlot; }
    public String getNote() { return note; }
    public String getPhotoUrl() { return photoUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setItineraryDayId(UUID itineraryDayId) { this.itineraryDayId = itineraryDayId; }
    public void setPosition(int position) { this.position = position; }
    public void setTimeSlot(LocalTime timeSlot) { this.timeSlot = timeSlot; }
    public void setNote(String note) { this.note = note; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
