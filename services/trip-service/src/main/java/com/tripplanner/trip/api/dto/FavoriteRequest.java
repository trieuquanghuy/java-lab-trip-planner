package com.tripplanner.trip.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FavoriteRequest(
        @NotBlank String destinationRef
) {}
