package com.tripplanner.destination.search;

import java.util.List;

public record SearchResponse(
        List<CitySearchItem> items
) {
    public static SearchResponse empty() {
        return new SearchResponse(List.of());
    }
}
