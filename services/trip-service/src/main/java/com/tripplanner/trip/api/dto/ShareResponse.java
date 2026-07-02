package com.tripplanner.trip.api.dto;

import java.util.UUID;

public record ShareResponse(UUID shareToken, String shareUrl) {}
