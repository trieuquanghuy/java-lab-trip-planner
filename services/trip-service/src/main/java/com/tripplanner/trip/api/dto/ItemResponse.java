package com.tripplanner.trip.api.dto;

import com.tripplanner.trip.domain.ItineraryItem;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        UUID itineraryDayId,
        String destinationRef,
        int position,
        LocalTime timeSlot,
        String note,
        String photoUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static ItemResponse from(ItineraryItem item) {
        return new ItemResponse(
                item.getId(),
                item.getItineraryDayId(),
                item.getDestinationRef(),
                item.getPosition(),
                item.getTimeSlot(),
                item.getNote(),
                item.getPhotoUrl(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
