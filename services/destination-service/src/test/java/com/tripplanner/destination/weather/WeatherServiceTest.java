package com.tripplanner.destination.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WeatherServiceTest {

    @Mock OpenMeteoClient openMeteoClient;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    WeatherService weatherService;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        weatherService = new WeatherService(openMeteoClient, redis, new ObjectMapper());
    }

    // -----------------------------------------------------------------------
    // Icon mapping
    // -----------------------------------------------------------------------

    @Test
    void codeToIcon_clearSky() {
        assertThat(WeatherService.codeToIcon(0)).isEqualTo("☀️");
    }

    @Test
    void codeToIcon_partlyCloudy() {
        assertThat(WeatherService.codeToIcon(1)).isEqualTo("🌤️");
        assertThat(WeatherService.codeToIcon(3)).isEqualTo("🌤️");
    }

    @Test
    void codeToIcon_fog() {
        assertThat(WeatherService.codeToIcon(45)).isEqualTo("🌫️");
        assertThat(WeatherService.codeToIcon(48)).isEqualTo("🌫️");
    }

    @Test
    void codeToIcon_rain() {
        assertThat(WeatherService.codeToIcon(61)).isEqualTo("🌧️");
        assertThat(WeatherService.codeToIcon(67)).isEqualTo("🌧️");
    }

    @Test
    void codeToIcon_snow() {
        assertThat(WeatherService.codeToIcon(71)).isEqualTo("🌨️");
        assertThat(WeatherService.codeToIcon(77)).isEqualTo("🌨️");
    }

    @Test
    void codeToIcon_thunderstorm() {
        assertThat(WeatherService.codeToIcon(95)).isEqualTo("⛈️");
        assertThat(WeatherService.codeToIcon(96)).isEqualTo("⛈️");
        assertThat(WeatherService.codeToIcon(99)).isEqualTo("⛈️");
    }

    @Test
    void codeToIcon_unknownCodeReturnsThermometer() {
        assertThat(WeatherService.codeToIcon(999)).isEqualTo("🌡️");
        assertThat(WeatherService.codeToIcon(-1)).isEqualTo("🌡️");
    }

    // -----------------------------------------------------------------------
    // Date filtering — outside 16-day window
    // -----------------------------------------------------------------------

    @Test
    void getWeather_endDateInPast_returnsEmpty() {
        LocalDate today = LocalDate.now();
        WeatherResponse result = weatherService.getWeather(
                48.85, 2.35, today.minusDays(5), today.minusDays(1));
        assertThat(result.days()).isEmpty();
        verify(openMeteoClient, never()).fetchForecast(anyDouble(), anyDouble());
    }

    @Test
    void getWeather_startDateBeyondForecastWindow_returnsEmpty() {
        LocalDate today = LocalDate.now();
        WeatherResponse result = weatherService.getWeather(
                48.85, 2.35, today.plusDays(17), today.plusDays(20));
        assertThat(result.days()).isEmpty();
        verify(openMeteoClient, never()).fetchForecast(anyDouble(), anyDouble());
    }

    // -----------------------------------------------------------------------
    // Date filtering — within valid range
    // -----------------------------------------------------------------------

    @Test
    void getWeather_filtersToDatesInRange() {
        LocalDate today = LocalDate.now();
        when(valueOps.get(anyString())).thenReturn(null);
        when(openMeteoClient.fetchForecast(anyDouble(), anyDouble()))
                .thenReturn(buildMockResponse(today, 16));

        WeatherResponse result = weatherService.getWeather(
                48.85, 2.35, today.plusDays(2), today.plusDays(4));

        assertThat(result.days()).hasSize(3);
        assertThat(result.days().get(0).date()).isEqualTo(today.plusDays(2));
        assertThat(result.days().get(2).date()).isEqualTo(today.plusDays(4));
    }

    @Test
    void getWeather_startDateInPastButEndDateFuture_onlyShowsFutureDays() {
        LocalDate today = LocalDate.now();
        when(valueOps.get(anyString())).thenReturn(null);
        when(openMeteoClient.fetchForecast(anyDouble(), anyDouble()))
                .thenReturn(buildMockResponse(today, 16));

        // startDate yesterday, endDate tomorrow → only today and tomorrow returned
        WeatherResponse result = weatherService.getWeather(
                48.85, 2.35, today.minusDays(1), today.plusDays(1));

        assertThat(result.days()).hasSize(2);
        assertThat(result.days().get(0).date()).isEqualTo(today);
        assertThat(result.days().get(1).date()).isEqualTo(today.plusDays(1));
    }

    // -----------------------------------------------------------------------
    // Cache hit
    // -----------------------------------------------------------------------

    @Test
    void getWeather_cacheHit_doesNotCallClient() throws Exception {
        LocalDate today = LocalDate.now();
        WeatherResponse cached = buildWeatherResponse(today, 3);
        String json = new ObjectMapper()
                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                .writeValueAsString(cached);
        when(valueOps.get(anyString())).thenReturn(json);

        WeatherResponse result = weatherService.getWeather(
                48.85, 2.35, today, today.plusDays(2));

        assertThat(result.days()).hasSize(3);
        verify(openMeteoClient, never()).fetchForecast(anyDouble(), anyDouble());
    }

    // -----------------------------------------------------------------------
    // Circuit breaker fallback
    // -----------------------------------------------------------------------

    @Test
    void getWeather_clientReturnsNull_returnsEmpty() {
        LocalDate today = LocalDate.now();
        when(valueOps.get(anyString())).thenReturn(null);
        when(openMeteoClient.fetchForecast(anyDouble(), anyDouble())).thenReturn(null);

        WeatherResponse result = weatherService.getWeather(
                48.85, 2.35, today, today.plusDays(5));

        assertThat(result.days()).isEmpty();
    }

    @Test
    void getWeather_clientThrows_returnsEmpty() {
        LocalDate today = LocalDate.now();
        when(valueOps.get(anyString())).thenReturn(null);
        when(openMeteoClient.fetchForecast(anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("CB open"));

        WeatherResponse result = weatherService.getWeather(
                48.85, 2.35, today, today.plusDays(5));

        assertThat(result.days()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private OpenMeteoResponse buildMockResponse(LocalDate startDate, int days) {
        List<String> times = new java.util.ArrayList<>();
        List<Double> maxTemps = new java.util.ArrayList<>();
        List<Double> minTemps = new java.util.ArrayList<>();
        List<Double> precip = new java.util.ArrayList<>();
        List<Integer> codes = new java.util.ArrayList<>();

        for (int i = 0; i < days; i++) {
            times.add(startDate.plusDays(i).toString());
            maxTemps.add(25.0 + i);
            minTemps.add(15.0 + i);
            precip.add(i % 3 == 0 ? 0.0 : 2.5);
            codes.add(i % 4); // cycles through 0,1,2,3
        }
        return new OpenMeteoResponse(
                new OpenMeteoResponse.Daily(times, maxTemps, minTemps, precip, codes));
    }

    private WeatherResponse buildWeatherResponse(LocalDate startDate, int days) {
        List<WeatherResponse.DayWeather> dayList = new java.util.ArrayList<>();
        for (int i = 0; i < days; i++) {
            dayList.add(new WeatherResponse.DayWeather(
                    startDate.plusDays(i), 25.0 + i, 15.0 + i, 0.0, "☀️", "Clear sky"));
        }
        return new WeatherResponse(dayList);
    }
}
