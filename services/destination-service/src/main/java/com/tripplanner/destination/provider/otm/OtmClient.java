package com.tripplanner.destination.provider.otm;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OtmClient {

    private static final Logger log = LoggerFactory.getLogger(OtmClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public OtmClient(RestClient otmRestClient, @Value("${otm.api-key}") String apiKey) {
        this.restClient = otmRestClient;
        this.apiKey = apiKey;
    }

    @CircuitBreaker(name = "openTripMap", fallbackMethod = "fallbackNearby")
    public List<OtmPlace> fetchNearby(double lat, double lng, int radiusMeters, int limit) {
        return restClient.get()
                .uri("/0.1/en/places/radius?lat={lat}&lon={lng}&radius={r}&limit={l}&apikey={k}",
                        lat, lng, radiusMeters, limit, apiKey)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    @CircuitBreaker(name = "openTripMap", fallbackMethod = "fallbackDetail")
    public OtmPlaceDetail fetchDetail(String xid) {
        return restClient.get()
                .uri("/0.1/en/places/xid/{xid}?apikey={k}", xid, apiKey)
                .retrieve()
                .body(OtmPlaceDetail.class);
    }

    @SuppressWarnings("unused")
    private List<OtmPlace> fallbackNearby(double lat, double lng, int radiusMeters, int limit, Exception e) {
        log.warn("OTM fetchNearby fallback triggered: {}", e.getMessage());
        return Collections.emptyList();
    }

    @SuppressWarnings("unused")
    private OtmPlaceDetail fallbackDetail(String xid, Exception e) {
        log.warn("OTM fetchDetail fallback triggered for xid={}: {}", xid, e.getMessage());
        return null;
    }
}
