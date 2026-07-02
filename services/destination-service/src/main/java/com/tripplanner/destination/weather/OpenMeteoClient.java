package com.tripplanner.destination.weather;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    private static final String BASE_URL = "https://api.open-meteo.com";
    private static final String FORECAST_PATH =
            "/v1/forecast?latitude={lat}&longitude={lng}" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_sum,weathercode" +
            "&timezone=auto&forecast_days=16";

    private final RestClient restClient;

    public OpenMeteoClient() {
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    @CircuitBreaker(name = "openMeteo", fallbackMethod = "fallbackForecast")
    public OpenMeteoResponse fetchForecast(double lat, double lng) {
        return restClient.get()
                .uri(FORECAST_PATH, lat, lng)
                .retrieve()
                .body(OpenMeteoResponse.class);
    }

    @SuppressWarnings("unused")
    private OpenMeteoResponse fallbackForecast(double lat, double lng, Exception e) {
        log.warn("OpenMeteo fetchForecast fallback triggered for lat={} lng={}: {}", lat, lng, e.getMessage());
        return null;
    }
}
