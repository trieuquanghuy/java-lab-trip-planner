package com.tripplanner.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favorites", schema = "trip")
@IdClass(FavoriteId.class)
public class Favorite {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "destination_ref", nullable = false, length = 80)
    private String destinationRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Favorite() {}

    public Favorite(UUID userId, String destinationRef) {
        this.userId = userId;
        this.destinationRef = destinationRef;
        this.createdAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getDestinationRef() { return destinationRef; }
    public Instant getCreatedAt() { return createdAt; }
}
