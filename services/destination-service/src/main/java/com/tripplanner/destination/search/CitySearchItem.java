package com.tripplanner.destination.search;

import java.math.BigDecimal;

public record CitySearchItem(
        String type,
        String name,
        String country,
        BigDecimal lat,
        BigDecimal lng
) {
}
