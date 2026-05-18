package com.tripplanner.trip.api.dto;

import java.util.List;

public record FavoriteListResponse(
        List<FavoriteResponse> items
) {}
