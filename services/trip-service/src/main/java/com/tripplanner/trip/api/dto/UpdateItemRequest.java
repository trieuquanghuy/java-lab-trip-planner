package com.tripplanner.trip.api.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.UUID;

public record UpdateItemRequest(
        Integer position,
        UUID itineraryDayId,
        LocalTime timeSlot,
        @Size(max = 500) String note,
        String photoUrl
) {}
