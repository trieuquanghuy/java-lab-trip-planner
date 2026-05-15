package com.tripplanner.destination.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.destination.city.City;
import com.tripplanner.destination.city.CityRepository;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int MAX_LIMIT = 5;
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final String CACHE_PREFIX = "SEARCH:";
    private static final String LOCK_PREFIX = "lock:search:";

    private final CityRepository cityRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SearchService(CityRepository cityRepository,
                         StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper) {
        this.cityRepository = cityRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public SearchResponse search(String q, String type, int limit) {
        q = q == null ? "" : q.trim();
        if (q.isBlank()) {
            return SearchResponse.empty();
        }

        limit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        String cacheKey = CACHE_PREFIX + q.toLowerCase() + ":" + type + ":" + limit;

        // Cache read
        String cached = readCache(cacheKey);
        if (cached != null) {
            SearchResponse response = deserialize(cached);
            if (response != null) {
                log.debug("Cache hit for key={}", cacheKey);
                return response;
            }
        }

        // Single-flight lock to prevent cache stampede
        String lockKey = LOCK_PREFIX + cacheKey;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            try {
                // Double-check cache after acquiring lock
                cached = readCache(cacheKey);
                if (cached != null) {
                    SearchResponse response = deserialize(cached);
                    if (response != null) {
                        return response;
                    }
                }
                // Query Postgres
                List<City> cities = cityRepository.searchByPrefix(q, limit);
                SearchResponse response = toResponse(cities);
                writeCache(cacheKey, response);
                return response;
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // Another thread holds the lock — brief retry then query directly
            return waitForCacheOrQuery(cacheKey, q, limit);
        }
    }

    private SearchResponse waitForCacheOrQuery(String cacheKey, String q, int limit) {
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String cached = readCache(cacheKey);
            if (cached != null) {
                SearchResponse response = deserialize(cached);
                if (response != null) {
                    return response;
                }
            }
        }
        // Fallback: query Postgres directly
        List<City> cities = cityRepository.searchByPrefix(q, limit);
        return toResponse(cities);
    }

    private String readCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, SearchResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for key={}: {}", key, e.getMessage());
        }
    }

    private SearchResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, SearchResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Cache deserialization failed: {}", e.getMessage());
            return null;
        }
    }

    private SearchResponse toResponse(List<City> cities) {
        List<CitySearchItem> items = cities.stream()
                .map(c -> new CitySearchItem("city", c.getName(), c.getCountry(), c.getLat(), c.getLng()))
                .toList();
        return new SearchResponse(items);
    }
}
