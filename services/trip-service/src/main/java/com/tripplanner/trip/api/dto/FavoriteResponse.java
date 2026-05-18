package com.tripplanner.trip.api.dto;

import com.tripplanner.trip.domain.Favorite;
import java.time.Instant;

public record FavoriteResponse(
        String destinationRef,
        Instant createdAt
) {
    public static FavoriteResponse from(Favorite favorite) {
        return new FavoriteResponse(
                favorite.getDestinationRef(),
                favorite.getCreatedAt()
        );
    }
}
