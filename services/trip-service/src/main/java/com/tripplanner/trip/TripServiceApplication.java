// Phase 0 scaffold: empty Spring Boot servlet application — trip-service.
//
// Package per D-28 / Convention C1: com.tripplanner.trip.
// Phase 5 lands the real trip domain (Trip / ItineraryDay / ItineraryItem entities, /api/trips/*
// controllers, V2+ Flyway migrations).
package com.tripplanner.trip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TripServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TripServiceApplication.class, args);
    }
}
