package com.tripplanner.destination.destination;

public record ProviderStatus(String openTripMap, String foursquare) {

    public static ProviderStatus allOk() {
        return new ProviderStatus("ok", "ok");
    }
}
