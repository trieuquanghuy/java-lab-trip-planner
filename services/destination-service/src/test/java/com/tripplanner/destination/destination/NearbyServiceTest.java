package com.tripplanner.destination.destination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.destination.provider.fsq.FoursquareClient;
import com.tripplanner.destination.provider.fsq.FoursquareVenue;
import com.tripplanner.destination.provider.otm.OtmClient;
import com.tripplanner.destination.provider.otm.OtmPlace;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NearbyServiceTest {

    @Mock DestinationsCacheRepository cacheRepository;
    @Mock OtmClient otmClient;
    @Mock FoursquareClient fsqClient;
    @Mock ProviderMapper mapper;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock CircuitBreakerRegistry cbRegistry;
    @Mock CircuitBreaker circuitBreaker;

    ObjectMapper objectMapper = new ObjectMapper();

    NearbyService nearbyService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(cbRegistry.circuitBreaker(anyString())).thenReturn(circuitBreaker);
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.CLOSED);
        nearbyService = new NearbyService(
                cacheRepository, otmClient, fsqClient, mapper,
                redisTemplate, objectMapper, cbRegistry);
    }

    @Test
    void redisL1HitReturnsCachedResponse() throws Exception {
        NearbyResponse expected = new NearbyResponse(List.of(), true, ProviderStatus.allOk());
        String json = objectMapper.writeValueAsString(expected);
        when(valueOps.get(anyString())).thenReturn(json);

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.items()).isEmpty();
        verify(otmClient, never()).fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt());
        verify(fsqClient, never()).searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void postgresL2HitSkipsProviders() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        DestinationsCacheEntity entity = createEntity("otm:N123", "Test Place");
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(entity, entity, entity, entity, entity, entity, entity, entity, entity, entity));
        when(mapper.toNearbyItem(any())).thenReturn(
                new NearbyItem("otm:N123", "Test", "arch", BigDecimal.ONE, null,
                        BigDecimal.valueOf(48.85), BigDecimal.valueOf(2.35)));

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.fromCache()).isTrue();
        verify(otmClient, never()).fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt());
        verify(fsqClient, never()).searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void cacheMissCallsOtmThenFsq() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        OtmPlace otmPlace = new OtmPlace("N123", "Test", 3, "architecture", new OtmPlace.OtmPoint(2.35, 48.85));
        when(otmClient.fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(otmPlace));
        when(mapper.fromOtm(any())).thenReturn(createEntity("otm:N123", "Test"));
        when(fsqClient.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(createEntity("otm:N123", "Test")));
        when(mapper.toNearbyItem(any())).thenReturn(
                new NearbyItem("otm:N123", "Test", "arch", BigDecimal.ONE, null,
                        BigDecimal.valueOf(48.85), BigDecimal.valueOf(2.35)));

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        verify(otmClient).fetchNearby(48.85, 2.35, 5000, 10);
        verify(fsqClient).searchNearby(48.85, 2.35, 5000, 10);
        assertThat(result.items()).hasSize(1);
    }

    @Test
    void otmCircuitOpenReturnsEmptyFromFallback() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(otmClient.fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList()); // fallback returns empty
        when(fsqClient.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.items()).isEmpty();
    }

    @Test
    void limitClampedToMax20() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(otmClient.fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(fsqClient.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        nearbyService.searchNearby(48.85, 2.35, 5000, 100);

        verify(otmClient).fetchNearby(48.85, 2.35, 5000, 20);
    }

    @Test
    void limitClampedToMin1() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(otmClient.fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(fsqClient.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        nearbyService.searchNearby(48.85, 2.35, 5000, 0);

        verify(otmClient).fetchNearby(48.85, 2.35, 5000, 1);
    }

    @Test
    void redisReadError_fallsToPostgres() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(otmClient.fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(fsqClient.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.items()).isEmpty();
        verify(otmClient).fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void lockNotAcquired_fallsToDirectQuery() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.items()).isEmpty();
        verify(otmClient, never()).fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void foursquareEnrichmentMatches() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        OtmPlace otmPlace = new OtmPlace("N123", "Eiffel Tower", 5, "architecture", new OtmPlace.OtmPoint(2.35, 48.85));
        when(otmClient.fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(otmPlace));
        when(mapper.fromOtm(any())).thenReturn(createEntity("otm:N123", "Eiffel Tower"));

        FoursquareVenue fsqVenue = new FoursquareVenue("fsq123", "Eiffel Tower", null, null, null, null);
        when(fsqClient.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(fsqVenue));

        DestinationsCacheEntity entity = createEntity("otm:N123", "Eiffel Tower");
        when(cacheRepository.findAll()).thenReturn(List.of(entity));
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(List.of(entity));
        when(mapper.toNearbyItem(any())).thenReturn(
                new NearbyItem("otm:N123", "Eiffel Tower", "arch", BigDecimal.ONE, null,
                        BigDecimal.valueOf(48.85), BigDecimal.valueOf(2.35)));

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.items()).hasSize(1);
        verify(mapper).enrichFromFoursquare(any(), any());
    }

    @Test
    void foursquareVenueWithNullNameSkipped() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(otmClient.fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        FoursquareVenue nullNameVenue = new FoursquareVenue("fsq1", null, null, null, null, null);
        when(fsqClient.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(nullNameVenue));
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.items()).isEmpty();
        // enrichFromFoursquare should NOT be called for null-named venues
        verify(mapper, never()).enrichFromFoursquare(any(), any());
    }

    @Test
    void circuitBreakerOpen_reportsStatusInResponse() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(otmClient.fetchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(fsqClient.searchNearby(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(cacheRepository.findNearby(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(circuitBreaker.getState()).thenReturn(CircuitBreaker.State.OPEN);

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.providerStatus().openTripMap()).isEqualTo("circuit_open");
        assertThat(result.providerStatus().foursquare()).isEqualTo("circuit_open");
    }

    @Test
    void redisWriteFailure_doesNotThrow() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        // Need enough fresh entries to trigger L2 hit path
        List<DestinationsCacheEntity> freshEntries = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            freshEntries.add(createEntity("otm:N" + i, "Place " + i));
        }
        when(cacheRepository.findNearbyFresh(anyDouble(), anyDouble(), anyDouble(), anyInt()))
                .thenReturn(freshEntries);
        when(mapper.toNearbyItem(any())).thenReturn(
                new NearbyItem("otm:N1", "Test", "arch", BigDecimal.ONE, null,
                        BigDecimal.valueOf(48.85), BigDecimal.valueOf(2.35)));
        org.mockito.Mockito.doThrow(new RuntimeException("Redis write failed"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        NearbyResponse result = nearbyService.searchNearby(48.85, 2.35, 5000, 10);

        assertThat(result.items()).hasSize(10);
        assertThat(result.fromCache()).isTrue();
    }

    private DestinationsCacheEntity createEntity(String providerRef, String name) {
        DestinationsCacheEntity entity = new DestinationsCacheEntity();
        entity.setProviderRef(providerRef);
        entity.setName(name);
        entity.setLat(BigDecimal.valueOf(48.85));
        entity.setLng(BigDecimal.valueOf(2.35));
        entity.setPhotos("[]");
        entity.setRaw("{}");
        entity.setFetchedAt(Instant.now());
        return entity;
    }
}
