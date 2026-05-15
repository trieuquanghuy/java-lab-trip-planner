package com.tripplanner.destination.destination;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DestinationDetailResponse(
        String providerRef,
        String name,
        String category,
        String shortDescription,
        BigDecimal rating,
        BigDecimal lat,
        BigDecimal lng,
        String address,
        String website,
        List<String> photos,
        Object openingHours,
        boolean fromCache,
        Instant fetchedAt
) {}
