package com.tripplanner.destination.provider.fsq;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FoursquareClient {

    private static final Logger log = LoggerFactory.getLogger(FoursquareClient.class);

    private final RestClient restClient;

    public FoursquareClient(RestClient fsqRestClient) {
        this.restClient = fsqRestClient;
    }

    @CircuitBreaker(name = "foursquare", fallbackMethod = "fallbackSearch")
    public List<FoursquareVenue> searchNearby(double lat, double lng, int radiusMeters, int limit) {
        FoursquareSearchResponse response = restClient.get()
                .uri("/places/search?ll={ll}&radius={r}&limit={l}",
                        lat + "," + lng, radiusMeters, limit)
                .retrieve()
                .body(FoursquareSearchResponse.class);
        return response != null ? response.results() : Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private List<FoursquareVenue> fallbackSearch(double lat, double lng, int radiusMeters, int limit, Exception e) {
        log.warn("Foursquare searchNearby fallback triggered: {}", e.getMessage());
        return Collections.emptyList();
    }
}
