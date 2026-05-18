package com.tripplanner.trip.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class FavoriteId implements Serializable {

    private UUID userId;
    private String destinationRef;

    public FavoriteId() {}

    public FavoriteId(UUID userId, String destinationRef) {
        this.userId = userId;
        this.destinationRef = destinationRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FavoriteId that = (FavoriteId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(destinationRef, that.destinationRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, destinationRef);
    }
}
