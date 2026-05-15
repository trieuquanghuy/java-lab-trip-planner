package com.tripplanner.destination.destination;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/destinations")
public class NearbyController {

    private final NearbyService nearbyService;

    public NearbyController(NearbyService nearbyService) {
        this.nearbyService = nearbyService;
    }

    @GetMapping
    public ResponseEntity<NearbyResponse> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5000") int radius,
            @RequestParam(defaultValue = "20") int limit) {

        if (lat < -90 || lat > 90) {
            return ResponseEntity.badRequest().build();
        }
        if (lng < -180 || lng > 180) {
            return ResponseEntity.badRequest().build();
        }
        if (radius < 1 || radius > 50000) {
            return ResponseEntity.badRequest().build();
        }
        if (limit < 1 || limit > 20) {
            limit = 20;
        }

        NearbyResponse response = nearbyService.searchNearby(lat, lng, radius, limit);
        return ResponseEntity.ok(response);
    }
}
