package com.tripplanner.destination.provider.otm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OtmPlaceDetail(
        String xid,
        String name,
        int rate,
        String kinds,
        OtmPlace.OtmPoint point,
        OtmAddress address,
        String wikipedia,
        String image,
        OtmPreview preview,
        @JsonProperty("wikipedia_extracts") OtmWikipediaExtracts wikipediaExtracts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtmAddress(String road, String city, String state, String country, String postcode) {
        public String formatted() {
            StringBuilder sb = new StringBuilder();
            if (road != null) sb.append(road);
            if (city != null) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(city);
            }
            if (postcode != null) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(postcode);
            }
            if (country != null) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(country);
            }
            return sb.toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtmPreview(String source, int width, int height) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OtmWikipediaExtracts(String title, String text) {}
}
