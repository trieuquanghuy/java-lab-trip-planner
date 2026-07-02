package com.tripplanner.destination.travel;

import java.util.List;

public record TravelResponse(List<Segment> segments) {
    // durationMinutes and distanceKm are null when OSRM is unavailable for a segment
    public record Segment(Double durationMinutes, Double distanceKm) {}
}
