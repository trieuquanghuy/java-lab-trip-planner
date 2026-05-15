package com.tripplanner.destination.destination;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.destination.provider.fsq.FoursquareVenue;
import com.tripplanner.destination.provider.otm.OtmPlace;
import com.tripplanner.destination.provider.otm.OtmPlaceDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProviderMapperTest {

    ProviderMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ProviderMapper(new ObjectMapper());
    }

    @Test
    void fromOtmMapsAllFields() {
        OtmPlace place = new OtmPlace("N123", "Eiffel Tower", 7,
                "architecture,historic", new OtmPlace.OtmPoint(2.2945, 48.8584));

        DestinationsCacheEntity entity = mapper.fromOtm(place);

        assertThat(entity.getProviderRef()).isEqualTo("otm:N123");
        assertThat(entity.getName()).isEqualTo("Eiffel Tower");
        assertThat(entity.getCategory()).isEqualTo("architecture");
        assertThat(entity.getRating()).isEqualByComparingTo(BigDecimal.valueOf(7));
        assertThat(entity.getLat()).isEqualByComparingTo(BigDecimal.valueOf(48.8584));
        assertThat(entity.getLng()).isEqualByComparingTo(BigDecimal.valueOf(2.2945));
        assertThat(entity.getPhotos()).isEqualTo("[]");
        assertThat(entity.getFetchedAt()).isNotNull();
    }

    @Test
    void mapFirstKindReturnsFirstKindWithUnderscoreReplacement() {
        assertThat(mapper.mapFirstKind("historic_object,architecture")).isEqualTo("historic object");
        assertThat(mapper.mapFirstKind("architecture")).isEqualTo("architecture");
        assertThat(mapper.mapFirstKind(null)).isNull();
        assertThat(mapper.mapFirstKind("")).isNull();
    }

    @Test
    void fromOtmDetailMapsAddressAndWikipedia() {
        OtmPlaceDetail detail = new OtmPlaceDetail("N123", "Eiffel Tower", 7,
                "architecture", new OtmPlace.OtmPoint(2.2945, 48.8584),
                new OtmPlaceDetail.OtmAddress("5 Avenue Anatole France", "Paris", null, "FR", null),
                "https://en.wikipedia.org/wiki/Eiffel_Tower",
                null,
                new OtmPlaceDetail.OtmPreview("https://example.com/photo.jpg", 800, 600),
                null);

        DestinationsCacheEntity entity = mapper.fromOtmDetail(detail);

        assertThat(entity.getAddress()).isEqualTo("5 Avenue Anatole France, Paris, FR");
        assertThat(entity.getWebsite()).isEqualTo("https://en.wikipedia.org/wiki/Eiffel_Tower");
        assertThat(entity.getPhotos()).contains("https://example.com/photo.jpg");
    }

    @Test
    void enrichFromFoursquareOverwritesCategoryAndFillsAddress() {
        DestinationsCacheEntity entity = new DestinationsCacheEntity();
        entity.setCategory("architecture");
        entity.setAddress(null);

        FoursquareVenue venue = new FoursquareVenue(
                "abc123", "Tour Eiffel",
                List.of(new FoursquareVenue.FsqCategory(1, "Tourist Attraction", "Attraction")),
                null,
                new FoursquareVenue.FsqLocation("5 Ave Anatole France", "Paris", "IDF", "FR"),
                100);

        mapper.enrichFromFoursquare(entity, venue);

        assertThat(entity.getCategory()).isEqualTo("Tourist Attraction");
        assertThat(entity.getAddress()).isEqualTo("5 Ave Anatole France");
    }

    @Test
    void toNearbyItemMapsEntityCorrectly() {
        DestinationsCacheEntity entity = new DestinationsCacheEntity();
        entity.setProviderRef("otm:N123");
        entity.setName("Test Place");
        entity.setCategory("museum");
        entity.setRating(BigDecimal.valueOf(5));
        entity.setPhotos("[\"https://example.com/photo.jpg\"]");
        entity.setLat(BigDecimal.valueOf(48.85));
        entity.setLng(BigDecimal.valueOf(2.35));

        NearbyItem item = mapper.toNearbyItem(entity);

        assertThat(item.providerRef()).isEqualTo("otm:N123");
        assertThat(item.name()).isEqualTo("Test Place");
        assertThat(item.category()).isEqualTo("museum");
        assertThat(item.photoUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void toDetailResponseMapsEntityCorrectly() {
        DestinationsCacheEntity entity = new DestinationsCacheEntity();
        entity.setProviderRef("otm:N123");
        entity.setName("Test Place");
        entity.setCategory("museum");
        entity.setRating(BigDecimal.valueOf(5));
        entity.setLat(BigDecimal.valueOf(48.85));
        entity.setLng(BigDecimal.valueOf(2.35));
        entity.setAddress("123 Test St");
        entity.setWebsite("https://example.com");
        entity.setPhotos("[\"https://example.com/photo.jpg\"]");
        entity.setFetchedAt(Instant.now());

        DestinationDetailResponse response = mapper.toDetailResponse(entity, true);

        assertThat(response.providerRef()).isEqualTo("otm:N123");
        assertThat(response.name()).isEqualTo("Test Place");
        assertThat(response.fromCache()).isTrue();
        assertThat(response.photos()).containsExactly("https://example.com/photo.jpg");
    }

    @Test
    void firstPhotoReturnsNullForEmptyArray() {
        assertThat(mapper.firstPhoto("[]")).isNull();
        assertThat(mapper.firstPhoto(null)).isNull();
    }
}
