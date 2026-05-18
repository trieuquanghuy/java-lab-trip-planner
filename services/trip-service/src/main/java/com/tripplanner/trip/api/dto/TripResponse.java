package com.tripplanner.trip.api.dto;

import com.tripplanner.trip.domain.ItineraryDay;
import com.tripplanner.trip.domain.ItineraryItem;
import com.tripplanner.trip.domain.Trip;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TripResponse(
        UUID id, String name, LocalDate startDate, LocalDate endDate,
        String coverImageUrl, Instant createdAt, Instant updatedAt,
        List<DayResponse> days
) {
    /** List endpoint — days without items. */
    public static TripResponse from(Trip trip, List<ItineraryDay> days) {
        List<DayResponse> dayResponses = days == null ? List.of()
                : days.stream()
                    .map(d -> new DayResponse(d.getId(), d.getDayDate(), d.getDayIndex()))
                    .toList();
        return new TripResponse(
                trip.getId(), trip.getName(), trip.getStartDate(), trip.getEndDate(),
                trip.getCoverImageUrl(), trip.getCreatedAt(), trip.getUpdatedAt(),
                dayResponses
        );
    }

    /** Detail endpoint — days with items and resolved cover image fallback. */
    public static TripResponse from(Trip trip, List<ItineraryDay> days,
                                    Map<UUID, List<ItineraryItem>> itemsByDay,
                                    String resolvedCoverImage) {
        List<DayResponse> dayResponses = days == null ? List.of()
                : days.stream()
                    .map(d -> new DayResponse(d.getId(), d.getDayDate(), d.getDayIndex(),
                            itemsByDay.getOrDefault(d.getId(), List.of()).stream()
                                    .map(ItemResponse::from).toList()))
                    .toList();
        String coverImage = trip.getCoverImageUrl() != null ? trip.getCoverImageUrl() : resolvedCoverImage;
        return new TripResponse(
                trip.getId(), trip.getName(), trip.getStartDate(), trip.getEndDate(),
                coverImage, trip.getCreatedAt(), trip.getUpdatedAt(),
                dayResponses
        );
    }
}
