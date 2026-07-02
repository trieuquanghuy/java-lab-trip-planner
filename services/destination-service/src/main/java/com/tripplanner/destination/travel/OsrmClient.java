package com.tripplanner.destination.travel;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OsrmClient {

    private static final Logger log = LoggerFactory.getLogger(OsrmClient.class);

    private final RestClient restClient;

    public OsrmClient(RestClient osrmRestClient) {
        this.restClient = osrmRestClient;
    }

    /**
     * Fetches a driving route segment between two points from OSRM.
     *
     * @return OsrmSegment with raw duration (seconds) and distance (meters), or null on failure
     */
    @CircuitBreaker(name = "osrm", fallbackMethod = "fallbackSegment")
    public OsrmSegment fetchSegment(double lat1, double lng1, double lat2, double lng2) {
        OsrmResponse response = restClient.get()
                .uri("/route/v1/driving/{lng1},{lat1};{lng2},{lat2}?overview=false",
                        lng1, lat1, lng2, lat2)
                .retrieve()
                .body(OsrmResponse.class);

        if (response == null || response.routes() == null || response.routes().isEmpty()) {
            log.warn("OSRM returned empty routes for ({},{}) → ({},{})", lat1, lng1, lat2, lng2);
            return null;
        }
        OsrmResponse.Route route = response.routes().get(0);
        return new OsrmSegment(route.duration(), route.distance());
    }

    @SuppressWarnings("unused")
    private OsrmSegment fallbackSegment(double lat1, double lng1, double lat2, double lng2,
                                         Exception e) {
        log.warn("OSRM fetchSegment fallback ({},{})→({},{}): {}", lat1, lng1, lat2, lng2,
                e.getMessage());
        return null;
    }

    // Internal response records for JSON parsing
    record OsrmResponse(List<Route> routes) {
        record Route(double duration, double distance) {}
    }

    record OsrmSegment(double durationSeconds, double distanceMeters) {}
}
