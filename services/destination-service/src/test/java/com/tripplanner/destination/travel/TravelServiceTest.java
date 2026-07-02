package com.tripplanner.destination.travel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.destination.travel.TravelRequest.Waypoint;
import java.time.Duration;
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
class TravelServiceTest {

    @Mock OsrmClient osrmClient;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    ObjectMapper objectMapper = new ObjectMapper();
    TravelService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // Default: no cache hit
        when(valueOps.get(anyString())).thenReturn(null);
        // valueOps.set returns void — no need to stub it
        service = new TravelService(osrmClient, redisTemplate, objectMapper);
    }

    @Test
    void returnsEmptyListForNullWaypoints() {
        TravelResponse response = service.getSegments(null);
        assertThat(response.segments()).isEmpty();
        verify(osrmClient, never()).fetchSegment(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void returnsEmptyListForSingleWaypoint() {
        TravelResponse response = service.getSegments(List.of(new Waypoint(35.68, 139.69)));
        assertThat(response.segments()).isEmpty();
        verify(osrmClient, never()).fetchSegment(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void convertsSecondsToMinutesAndMetersToKm() {
        // 750 seconds = 12.5 minutes, 3500 meters = 3.5 km
        when(osrmClient.fetchSegment(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OsrmClient.OsrmSegment(750.0, 3500.0));

        TravelResponse response = service.getSegments(List.of(
                new Waypoint(35.6812, 139.7671),
                new Waypoint(35.7090, 139.7320)
        ));

        assertThat(response.segments()).hasSize(1);
        TravelResponse.Segment seg = response.segments().get(0);
        assertThat(seg.durationMinutes()).isEqualTo(12.5);
        assertThat(seg.distanceKm()).isEqualTo(3.5);
    }

    @Test
    void returnsNullSegmentWhenOsrmClientReturnsNull() {
        when(osrmClient.fetchSegment(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(null); // simulates CB fallback

        TravelResponse response = service.getSegments(List.of(
                new Waypoint(35.6812, 139.7671),
                new Waypoint(35.7090, 139.7320)
        ));

        assertThat(response.segments()).hasSize(1);
        TravelResponse.Segment seg = response.segments().get(0);
        assertThat(seg.durationMinutes()).isNull();
        assertThat(seg.distanceKm()).isNull();
    }

    @Test
    void cacheKeyUsesRoundedCoordinates() {
        when(osrmClient.fetchSegment(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OsrmClient.OsrmSegment(300.0, 1000.0));

        // Coordinates with many decimal places
        service.getSegments(List.of(
                new Waypoint(35.681234567, 139.767123456),
                new Waypoint(35.709087654, 139.732098765)
        ));

        // Key should use 4 decimal places
        String expectedKey = "TRAVEL:35.6812:139.7671:35.7091:139.7321";
        verify(valueOps).get(expectedKey);
        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void returnsCachedSegmentWithoutCallingOsrm() throws Exception {
        TravelResponse.Segment cached = new TravelResponse.Segment(8.0, 2.5);
        String json = objectMapper.writeValueAsString(cached);
        when(valueOps.get(anyString())).thenReturn(json);

        TravelResponse response = service.getSegments(List.of(
                new Waypoint(35.6812, 139.7671),
                new Waypoint(35.7090, 139.7320)
        ));

        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).durationMinutes()).isEqualTo(8.0);
        assertThat(response.segments().get(0).distanceKm()).isEqualTo(2.5);
        verify(osrmClient, never()).fetchSegment(anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void producesCorrectNumberOfSegmentsForThreeWaypoints() {
        when(osrmClient.fetchSegment(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new OsrmClient.OsrmSegment(600.0, 2000.0));

        TravelResponse response = service.getSegments(List.of(
                new Waypoint(35.6812, 139.7671),
                new Waypoint(35.7090, 139.7320),
                new Waypoint(35.6900, 139.7000)
        ));

        assertThat(response.segments()).hasSize(2);
    }
}
