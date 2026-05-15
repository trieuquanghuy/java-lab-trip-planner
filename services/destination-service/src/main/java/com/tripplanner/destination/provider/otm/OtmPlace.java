package com.tripplanner.destination.provider.otm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OtmPlace(
        String xid,
        String name,
        int rate,
        String kinds,
        OtmPoint point
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtmPoint(double lon, double lat) {}
}
