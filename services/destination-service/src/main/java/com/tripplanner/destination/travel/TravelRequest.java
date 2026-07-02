package com.tripplanner.destination.travel;

import java.util.List;

public record TravelRequest(List<Waypoint> waypoints) {
    public record Waypoint(double lat, double lng) {}
}
