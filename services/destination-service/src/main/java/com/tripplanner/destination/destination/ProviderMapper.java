package com.tripplanner.destination.destination;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.destination.provider.fsq.FoursquareVenue;
import com.tripplanner.destination.provider.otm.OtmPlace;
import com.tripplanner.destination.provider.otm.OtmPlaceDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProviderMapper {

    private static final Logger log = LoggerFactory.getLogger(ProviderMapper.class);
    private static final int MAX_DESCRIPTION_LENGTH = 300;

    private final ObjectMapper objectMapper;

    public ProviderMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DestinationsCacheEntity fromOtm(OtmPlace place) {
        DestinationsCacheEntity entity = new DestinationsCacheEntity();
        entity.setProviderRef("otm:" + place.xid());
        entity.setName(place.name() != null ? place.name() : "");
        entity.setCategory(mapFirstKind(place.kinds()));
        entity.setRating(BigDecimal.valueOf(place.rate()));
        entity.setLat(BigDecimal.valueOf(place.point().lat()));
        entity.setLng(BigDecimal.valueOf(place.point().lon()));
        entity.setPhotos("[]");
        entity.setRaw(serializeToJson(place));
        entity.setFetchedAt(Instant.now());
        return entity;
    }

    public DestinationsCacheEntity fromOtmDetail(OtmPlaceDetail detail) {
        DestinationsCacheEntity entity = new DestinationsCacheEntity();
        entity.setProviderRef("otm:" + detail.xid());
        entity.setName(detail.name() != null ? detail.name() : "");
        entity.setCategory(mapFirstKind(detail.kinds()));
        entity.setRating(BigDecimal.valueOf(detail.rate()));
        if (detail.point() != null) {
            entity.setLat(BigDecimal.valueOf(detail.point().lat()));
            entity.setLng(BigDecimal.valueOf(detail.point().lon()));
        }
        if (detail.address() != null) {
            entity.setAddress(detail.address().formatted());
        }
        entity.setWebsite(detail.wikipedia());
        // Photos from preview source
        if (detail.preview() != null && detail.preview().source() != null) {
            entity.setPhotos("[\"" + detail.preview().source().replace("\"", "\\\"") + "\"]");
        } else {
            entity.setPhotos("[]");
        }
        entity.setRaw(serializeToJson(detail));
        entity.setFetchedAt(Instant.now());
        return entity;
    }

    public void enrichFromFoursquare(DestinationsCacheEntity entity, FoursquareVenue venue) {
        if (venue.categories() != null && !venue.categories().isEmpty()) {
            entity.setCategory(venue.categories().get(0).name());
        }
        if (entity.getAddress() == null && venue.location() != null
                && venue.location().formattedAddress() != null) {
            entity.setAddress(venue.location().formattedAddress());
        }
    }

    public NearbyItem toNearbyItem(DestinationsCacheEntity entity) {
        return new NearbyItem(
                entity.getProviderRef(),
                entity.getName(),
                entity.getCategory(),
                entity.getRating(),
                firstPhoto(entity.getPhotos()),
                entity.getLat(),
                entity.getLng()
        );
    }

    public DestinationDetailResponse toDetailResponse(DestinationsCacheEntity entity, boolean fromCache) {
        return new DestinationDetailResponse(
                entity.getProviderRef(),
                entity.getName(),
                entity.getCategory(),
                null, // shortDescription — extracted from raw if needed
                entity.getRating(),
                entity.getLat(),
                entity.getLng(),
                entity.getAddress(),
                entity.getWebsite(),
                parsePhotos(entity.getPhotos()),
                parseJson(entity.getOpeningHours()),
                fromCache,
                entity.getFetchedAt()
        );
    }

    String mapFirstKind(String kinds) {
        if (kinds == null || kinds.isBlank()) return null;
        String first = kinds.split(",")[0].trim();
        return first.replace("_", " ");
    }

    String firstPhoto(String photosJson) {
        if (photosJson == null || photosJson.equals("[]")) return null;
        try {
            List<String> photos = objectMapper.readValue(photosJson, new TypeReference<>() {});
            return photos.isEmpty() ? null : photos.get(0);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse photos JSON: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parsePhotos(String photosJson) {
        if (photosJson == null || photosJson.equals("[]")) return List.of();
        try {
            return objectMapper.readValue(photosJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse photos JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private Object parseJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON: {}", e.getMessage());
            return null;
        }
    }

    private String serializeToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}
