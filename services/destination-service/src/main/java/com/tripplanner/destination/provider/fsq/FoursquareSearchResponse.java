package com.tripplanner.destination.provider.fsq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FoursquareSearchResponse(List<FoursquareVenue> results) {}
