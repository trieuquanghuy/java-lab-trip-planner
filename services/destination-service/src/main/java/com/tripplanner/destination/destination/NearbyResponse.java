package com.tripplanner.destination.destination;

import java.util.List;

public record NearbyResponse(
        List<NearbyItem> items,
        boolean fromCache,
        ProviderStatus providerStatus
) {
    public static NearbyResponse empty(ProviderStatus status) {
        return new NearbyResponse(List.of(), false, status);
    }
}
