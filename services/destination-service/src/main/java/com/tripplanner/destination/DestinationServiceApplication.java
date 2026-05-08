// Phase 0 scaffold: empty Spring Boot servlet application — destination-service.
//
// Package per D-28 / Convention C1: com.tripplanner.destination.
// Phase 3 lands the real destination domain (City / Destination entities, OpenTripMap +
// Foursquare clients, /api/destinations/* + /api/search/* controllers, V2+ Flyway migrations).
package com.tripplanner.destination;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DestinationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DestinationServiceApplication.class, args);
    }
}
