package com.tripplanner.trip.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripListResponse(
        List<TripSummaryResponse> content,
        long totalElements, int totalPages, int page, int size
) {
    public record TripSummaryResponse(
            UUID id, String name, LocalDate startDate,
            LocalDate endDate, String coverImageUrl,
            Instant createdAt, Instant updatedAt
    ) {}
}
