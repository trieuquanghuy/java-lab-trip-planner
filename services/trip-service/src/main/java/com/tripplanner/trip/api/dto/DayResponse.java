package com.tripplanner.trip.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DayResponse(UUID id, LocalDate dayDate, int dayIndex) {}
