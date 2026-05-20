package com.tripplanner.destination.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.destination.city.City;
import com.tripplanner.destination.city.CityRepository;
import java.math.BigDecimal;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceTest {

    @Mock
    CityRepository cityRepository;

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    ObjectMapper objectMapper = new ObjectMapper();

    SearchService searchService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        searchService = new SearchService(cityRepository, redisTemplate, objectMapper);
    }

    @Test
    void blankQueryReturnsEmpty() {
        SearchResponse result = searchService.search("", "city,country", 5);

        assertThat(result.items()).isEmpty();
        verify(cityRepository, never()).searchByPrefix(any(), any(int.class));
    }

    @Test
    void nullQueryReturnsEmpty() {
        SearchResponse result = searchService.search(null, "city,country", 5);

        assertThat(result.items()).isEmpty();
        verify(cityRepository, never()).searchByPrefix(any(), any(int.class));
    }

    @Test
    void whitespaceQueryReturnsEmpty() {
        SearchResponse result = searchService.search("   ", "city,country", 5);

        assertThat(result.items()).isEmpty();
        verify(cityRepository, never()).searchByPrefix(any(), any(int.class));
    }

    @Test
    void limitCappedAtFive() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cityRepository.searchByPrefix("lon", 5)).thenReturn(List.of());

        searchService.search("lon", "city", 100);

        verify(cityRepository).searchByPrefix("lon", 5);
    }

    @Test
    void cacheHitSkipsDatabase() throws Exception {
        SearchResponse cached = new SearchResponse(List.of(
                new CitySearchItem("city", "London", "United Kingdom",
                        new BigDecimal("51.507400"), new BigDecimal("-0.127800"))));
        String json = objectMapper.writeValueAsString(cached);

        when(valueOps.get("SEARCH:london:city,country:5")).thenReturn(json);

        SearchResponse result = searchService.search("london", "city,country", 5);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("London");
        verify(cityRepository, never()).searchByPrefix(any(), any(int.class));
    }

    @Test
    void cacheMissQueriesDatabaseAndWritesCache() throws Exception {
        when(valueOps.get("SEARCH:lon:city,country:5")).thenReturn(null);
        when(valueOps.setIfAbsent(eq("lock:search:SEARCH:lon:city,country:5"), eq("1"), any(Duration.class)))
                .thenReturn(true);

        City london = createCity("London", "United Kingdom", "GB", "51.507400", "-0.127800", 8982000L);
        when(cityRepository.searchByPrefix("lon", 5)).thenReturn(List.of(london));

        SearchResponse result = searchService.search("lon", "city,country", 5);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("London");
        verify(valueOps).set(eq("SEARCH:lon:city,country:5"), anyString(), eq(Duration.ofHours(1)));
        verify(redisTemplate).delete("lock:search:SEARCH:lon:city,country:5");
    }

    @Test
    void cacheKeyNormalizesToLowercase() {
        when(valueOps.get("SEARCH:london:city,country:5")).thenReturn(null);
        when(valueOps.setIfAbsent(eq("lock:search:SEARCH:london:city,country:5"), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(cityRepository.searchByPrefix("LONDON", 5)).thenReturn(List.of());

        searchService.search("LONDON", "city,country", 5);

        // Verify the cache key uses lowercase (called at least once for initial read)
        verify(valueOps, org.mockito.Mockito.atLeastOnce()).get("SEARCH:london:city,country:5");
    }

    @Test
    void redisReadError_fallsToDatabase() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("Redis connection refused"));
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cityRepository.searchByPrefix("par", 5)).thenReturn(List.of());

        SearchResponse result = searchService.search("par", "city,country", 5);

        assertThat(result.items()).isEmpty();
        verify(cityRepository).searchByPrefix("par", 5);
    }

    @Test
    void cacheDeserializationFails_queriesDatabase() {
        when(valueOps.get("SEARCH:tok:city:3")).thenReturn("{invalid json");
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cityRepository.searchByPrefix("tok", 3)).thenReturn(List.of());

        SearchResponse result = searchService.search("tok", "city", 3);

        assertThat(result.items()).isEmpty();
        verify(cityRepository).searchByPrefix("tok", 3);
    }

    @Test
    void lockNotAcquired_retriesAndFallsToDatabase() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        when(cityRepository.searchByPrefix("ber", 5)).thenReturn(List.of());

        SearchResponse result = searchService.search("ber", "city,country", 5);

        assertThat(result.items()).isEmpty();
        verify(cityRepository).searchByPrefix("ber", 5);
    }

    @Test
    void limitClampsMinToOne() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(cityRepository.searchByPrefix("lon", 1)).thenReturn(List.of());

        searchService.search("lon", "city", 0);

        verify(cityRepository).searchByPrefix("lon", 1);
    }

    @Test
    void redisWriteError_doesNotThrow() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        City city = createCity("Paris", "France", "FR", "48.8566", "2.3522", 11000000L);
        when(cityRepository.searchByPrefix("par", 5)).thenReturn(List.of(city));
        org.mockito.Mockito.doThrow(new RuntimeException("Redis write failed"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        SearchResponse result = searchService.search("par", "city,country", 5);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("Paris");
    }

    private City createCity(String name, String country, String countryCode,
                            String lat, String lng, long population) {
        try {
            var ctor = City.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            City city = ctor.newInstance();
            ReflectionTestUtils.setField(city, "name", name);
            ReflectionTestUtils.setField(city, "country", country);
            ReflectionTestUtils.setField(city, "countryCode", countryCode);
            ReflectionTestUtils.setField(city, "lat", new BigDecimal(lat));
            ReflectionTestUtils.setField(city, "lng", new BigDecimal(lng));
            ReflectionTestUtils.setField(city, "population", population);
            return city;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
