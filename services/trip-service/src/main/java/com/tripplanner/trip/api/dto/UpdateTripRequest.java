package com.tripplanner.trip.api.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateTripRequest(
        @Size(min = 1, max = 120) String name,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 2048) String coverImageUrl
) {}
