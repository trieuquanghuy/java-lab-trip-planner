package com.tripplanner.destination.weather;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(3);
    private static final int FORECAST_DAYS = 16;

    private final OpenMeteoClient openMeteoClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public WeatherService(OpenMeteoClient openMeteoClient, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.openMeteoClient = openMeteoClient;
        this.redis = redis;
        // Ensure Java 8 time types (LocalDate) are handled
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
    }

    public WeatherResponse getWeather(double lat, double lng, LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        LocalDate forecastEnd = today.plusDays(FORECAST_DAYS - 1);

        // If requested range is entirely in the past or beyond the forecast window, skip the call
        if (endDate.isBefore(today) || startDate.isAfter(forecastEnd)) {
            return new WeatherResponse(List.of());
        }

        String cacheKey = buildCacheKey(lat, lng, today);
        WeatherResponse cached = tryGetFromCache(cacheKey);
        if (cached != null) {
            return filterToDates(cached, startDate, endDate, today, forecastEnd);
        }

        OpenMeteoResponse raw;
        try {
            raw = openMeteoClient.fetchForecast(lat, lng);
        } catch (Exception e) {
            log.warn("OpenMeteo fetchForecast failed for lat={} lng={}: {}", lat, lng, e.getMessage());
            return new WeatherResponse(List.of());
        }
        if (raw == null || raw.daily() == null) {
            return new WeatherResponse(List.of());
        }

        WeatherResponse full = mapToResponse(raw);
        tryPutInCache(cacheKey, full);

        return filterToDates(full, startDate, endDate, today, forecastEnd);
    }

    private String buildCacheKey(double lat, double lng, LocalDate today) {
        return String.format("WEATHER:%.6f:%.6f:%s", lat, lng, today);
    }

    private WeatherResponse tryGetFromCache(String key) {
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, WeatherResponse.class);
            }
        } catch (Exception e) {
            log.warn("Weather cache read failed for key {}: {}", key, e.getMessage());
        }
        return null;
    }

    private void tryPutInCache(String key, WeatherResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(key, json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Weather cache write failed for key {}: {}", key, e.getMessage());
        }
    }

    private WeatherResponse mapToResponse(OpenMeteoResponse raw) {
        OpenMeteoResponse.Daily daily = raw.daily();
        List<WeatherResponse.DayWeather> days = new ArrayList<>();

        List<String> times = daily.time();
        List<Double> maxTemps = daily.temperatureMax();
        List<Double> minTemps = daily.temperatureMin();
        List<Double> precip = daily.precipitationSum();
        List<Integer> codes = daily.weathercode();

        for (int i = 0; i < times.size(); i++) {
            LocalDate date = LocalDate.parse(times.get(i));
            int code = (codes != null && i < codes.size()) ? codes.get(i) : -1;
            String icon = codeToIcon(code);
            String description = codeToDescription(code);

            days.add(new WeatherResponse.DayWeather(
                    date,
                    safeGet(maxTemps, i),
                    safeGet(minTemps, i),
                    safeGet(precip, i),
                    icon,
                    description
            ));
        }
        return new WeatherResponse(days);
    }

    private WeatherResponse filterToDates(WeatherResponse full, LocalDate startDate, LocalDate endDate,
                                          LocalDate today, LocalDate forecastEnd) {
        List<WeatherResponse.DayWeather> filtered = full.days().stream()
                .filter(d -> !d.date().isBefore(today) && !d.date().isAfter(forecastEnd))
                .filter(d -> !d.date().isBefore(startDate) && !d.date().isAfter(endDate))
                .toList();
        return new WeatherResponse(filtered);
    }

    private Double safeGet(List<Double> list, int index) {
        if (list == null || index >= list.size()) return null;
        return list.get(index);
    }

    static String codeToIcon(int code) {
        if (code == 0) return "☀️";
        if (code >= 1 && code <= 3) return "🌤️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 51 && code <= 57) return "🌦️";
        if (code >= 61 && code <= 67) return "🌧️";
        if (code >= 71 && code <= 77) return "🌨️";
        if (code >= 80 && code <= 82) return "🌦️";
        if (code == 85 || code == 86) return "🌨️";
        if (code == 95) return "⛈️";
        if (code == 96 || code == 99) return "⛈️";
        return "🌡️";
    }

    static String codeToDescription(int code) {
        if (code == 0) return "Clear sky";
        if (code >= 1 && code <= 3) return "Partly cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code == 95) return "Thunderstorm";
        if (code == 96 || code == 99) return "Thunderstorm with hail";
        return "Unknown";
    }
}
