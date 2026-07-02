package com.tripplanner.destination.travel;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/travel")
public class TravelController {

    private final TravelService travelService;

    public TravelController(TravelService travelService) {
        this.travelService = travelService;
    }

    /**
     * Accepts an ordered list of waypoints and returns travel time/distance
     * for each consecutive pair. Segments list length = waypoints.length - 1.
     * Segment values are null when OSRM is unavailable.
     */
    @PostMapping("/segments")
    public ResponseEntity<TravelResponse> getSegments(@RequestBody TravelRequest request) {
        TravelResponse response = travelService.getSegments(request.waypoints());
        return ResponseEntity.ok(response);
    }
}
