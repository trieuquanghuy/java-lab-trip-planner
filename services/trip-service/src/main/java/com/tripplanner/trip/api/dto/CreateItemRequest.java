package com.tripplanner.trip.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record CreateItemRequest(
        @NotBlank String destinationRef,
        LocalTime timeSlot,
        @Size(max = 500) String note,
        String photoUrl
) {}
