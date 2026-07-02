package com.tripplanner.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "trips", schema = "trip")
public class Trip {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "share_token")
    private UUID shareToken;

    @Column(name = "share_enabled", nullable = false)
    private boolean shareEnabled = false;

    protected Trip() {}   // JPA

    public Trip(UUID id, UUID userId, String name, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public UUID getShareToken() { return shareToken; }
    public boolean isShareEnabled() { return shareEnabled; }

    public void setShareToken(UUID shareToken) { this.shareToken = shareToken; }
    public void setShareEnabled(boolean shareEnabled) { this.shareEnabled = shareEnabled; }

    public void enableShare() {
        this.shareToken = UUID.randomUUID();
        this.shareEnabled = true;
        this.updatedAt = Instant.now();
    }

    public void revokeShare() {
        this.shareToken = null;
        this.shareEnabled = false;
        this.updatedAt = Instant.now();
    }

    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public void setStartDate(LocalDate d) { this.startDate = d; this.updatedAt = Instant.now(); }
    public void setEndDate(LocalDate d) { this.endDate = d; this.updatedAt = Instant.now(); }
    public void setCoverImageUrl(String u) { this.coverImageUrl = u; this.updatedAt = Instant.now(); }
}
