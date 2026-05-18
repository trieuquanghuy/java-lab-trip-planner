package com.tripplanner.trip.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DayResponse(UUID id, LocalDate dayDate, int dayIndex, List<ItemResponse> items) {
    public DayResponse(UUID id, LocalDate dayDate, int dayIndex) {
        this(id, dayDate, dayIndex, List.of());
    }
}
