package com.tripplanner.destination.destination;

import java.math.BigDecimal;

public record NearbyItem(
        String providerRef,
        String name,
        String category,
        BigDecimal rating,
        String photoUrl,
        BigDecimal lat,
        BigDecimal lng
) {}
