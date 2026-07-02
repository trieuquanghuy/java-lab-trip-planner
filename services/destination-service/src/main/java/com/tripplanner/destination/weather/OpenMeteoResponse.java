package com.tripplanner.destination.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Raw deserialization target for the Open-Meteo /v1/forecast response. */
public record OpenMeteoResponse(
        @JsonProperty("daily") Daily daily
) {
    public record Daily(
            @JsonProperty("time") List<String> time,
            @JsonProperty("temperature_2m_max") List<Double> temperatureMax,
            @JsonProperty("temperature_2m_min") List<Double> temperatureMin,
            @JsonProperty("precipitation_sum") List<Double> precipitationSum,
            @JsonProperty("weathercode") List<Integer> weathercode
    ) {}
}
