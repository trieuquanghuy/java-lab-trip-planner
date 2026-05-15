package com.tripplanner.destination.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tripplanner.destination.provider.otm.OtmClient;
import com.tripplanner.destination.provider.otm.OtmPlace;
import com.tripplanner.destination.provider.otm.OtmPlaceDetail;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DetailServiceTest {

    @Mock DestinationsCacheRepository cacheRepository;
    @Mock OtmClient otmClient;
    @Mock ProviderMapper mapper;

    DetailService detailService;

    @BeforeEach
    void setUp() {
        detailService = new DetailService(cacheRepository, otmClient, mapper);
    }

    @Test
    void freshCacheHitReturnsCachedDetail() {
        DestinationsCacheEntity entity = createEntity("otm:N123", Instant.now());
        when(cacheRepository.findById("otm:N123")).thenReturn(Optional.of(entity));
        DestinationDetailResponse expected = createDetailResponse("otm:N123", true);
        when(mapper.toDetailResponse(entity, true)).thenReturn(expected);

        DestinationDetailResponse result = detailService.getDetail("otm:N123");

        assertThat(result).isNotNull();
        assertThat(result.fromCache()).isTrue();
        verify(otmClient, never()).fetchDetail(anyString());
    }

    @Test
    void staleCacheTriggersProviderRefresh() {
        DestinationsCacheEntity staleEntity = createEntity("otm:N123", Instant.now().minusSeconds(90000));
        when(cacheRepository.findById("otm:N123")).thenReturn(Optional.of(staleEntity));

        OtmPlaceDetail detail = new OtmPlaceDetail("N123", "Test", 3, "arch",
                new OtmPlace.OtmPoint(2.35, 48.85), null, null, null, null, null);
        when(otmClient.fetchDetail("N123")).thenReturn(detail);
        DestinationsCacheEntity refreshed = createEntity("otm:N123", Instant.now());
        when(mapper.fromOtmDetail(detail)).thenReturn(refreshed);
        DestinationDetailResponse expected = createDetailResponse("otm:N123", false);
        when(mapper.toDetailResponse(refreshed, false)).thenReturn(expected);

        DestinationDetailResponse result = detailService.getDetail("otm:N123");

        assertThat(result).isNotNull();
        assertThat(result.fromCache()).isFalse();
        verify(otmClient).fetchDetail("N123");
    }

    @Test
    void staleCacheProviderDownServesStale() {
        DestinationsCacheEntity staleEntity = createEntity("otm:N123", Instant.now().minusSeconds(90000));
        when(cacheRepository.findById("otm:N123")).thenReturn(Optional.of(staleEntity));
        when(otmClient.fetchDetail("N123")).thenReturn(null);
        DestinationDetailResponse expected = createDetailResponse("otm:N123", true);
        when(mapper.toDetailResponse(staleEntity, true)).thenReturn(expected);

        DestinationDetailResponse result = detailService.getDetail("otm:N123");

        assertThat(result).isNotNull();
        assertThat(result.fromCache()).isTrue();
    }

    @Test
    void noCacheProviderDownReturnsNull() {
        when(cacheRepository.findById("otm:N999")).thenReturn(Optional.empty());
        when(otmClient.fetchDetail("N999")).thenReturn(null);

        DestinationDetailResponse result = detailService.getDetail("otm:N999");

        assertThat(result).isNull();
    }

    @Test
    void fsqProviderRefNotSupportedReturnsNullWhenNotCached() {
        when(cacheRepository.findById("fsq:abc123")).thenReturn(Optional.empty());

        DestinationDetailResponse result = detailService.getDetail("fsq:abc123");

        assertThat(result).isNull();
        verify(otmClient, never()).fetchDetail(anyString());
    }

    private DestinationsCacheEntity createEntity(String providerRef, Instant fetchedAt) {
        DestinationsCacheEntity entity = new DestinationsCacheEntity();
        entity.setProviderRef(providerRef);
        entity.setName("Test Place");
        entity.setLat(BigDecimal.valueOf(48.85));
        entity.setLng(BigDecimal.valueOf(2.35));
        entity.setPhotos("[]");
        entity.setRaw("{}");
        entity.setFetchedAt(fetchedAt);
        return entity;
    }

    private DestinationDetailResponse createDetailResponse(String providerRef, boolean fromCache) {
        return new DestinationDetailResponse(
                providerRef, "Test Place", "arch", null,
                BigDecimal.ONE, BigDecimal.valueOf(48.85), BigDecimal.valueOf(2.35),
                null, null, java.util.List.of(), null, fromCache, Instant.now());
    }
}
