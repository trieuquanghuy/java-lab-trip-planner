package com.tripplanner.destination.provider.fsq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FoursquareVenue(
        @JsonProperty("fsq_id") String fsqId,
        String name,
        List<FsqCategory> categories,
        FsqGeocodes geocodes,
        FsqLocation location,
        Integer distance
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FsqCategory(int id, String name, @JsonProperty("short_name") String shortName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FsqGeocodes(FsqLatLng main) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FsqLatLng(double latitude, double longitude) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FsqLocation(
            @JsonProperty("formatted_address") String formattedAddress,
            String locality,
            String region,
            String country) {}
}
