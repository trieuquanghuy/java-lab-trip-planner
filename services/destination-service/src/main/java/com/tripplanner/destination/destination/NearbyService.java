package com.tripplanner.destination.destination;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.destination.provider.fsq.FoursquareClient;
import com.tripplanner.destination.provider.fsq.FoursquareVenue;
import com.tripplanner.destination.provider.otm.OtmClient;
import com.tripplanner.destination.provider.otm.OtmPlace;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class NearbyService {

    private static final Logger log = LoggerFactory.getLogger(NearbyService.class);
    private static final int MAX_LIMIT = 20;
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final String CACHE_PREFIX = "POI:";
    private static final String LOCK_PREFIX = "lock:nearby:";

    private final DestinationsCacheRepository cacheRepository;
    private final OtmClient otmClient;
    private final FoursquareClient fsqClient;
    private final ProviderMapper mapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public NearbyService(DestinationsCacheRepository cacheRepository,
                         OtmClient otmClient,
                         FoursquareClient fsqClient,
                         ProviderMapper mapper,
                         StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper,
                         CircuitBreakerRegistry circuitBreakerRegistry) {
        this.cacheRepository = cacheRepository;
        this.otmClient = otmClient;
        this.fsqClient = fsqClient;
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    public NearbyResponse searchNearby(double lat, double lng, int radiusMeters, int limit) {
        limit = Math.min(Math.max(limit, 1), MAX_LIMIT);
        String cacheKey = CACHE_PREFIX + lat + ":" + lng + ":" + radiusMeters;

        // Step 1: Redis L1 check
        String cached = readRedisCache(cacheKey);
        if (cached != null) {
            NearbyResponse response = deserialize(cached);
            if (response != null) {
                log.debug("Redis L1 hit for key={}", cacheKey);
                return response;
            }
        }

        // Single-flight lock (same pattern as SearchService)
        String lockKey = LOCK_PREFIX + cacheKey;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);

        if (Boolean.TRUE.equals(acquired)) {
            try {
                // Double-check Redis after lock
                cached = readRedisCache(cacheKey);
                if (cached != null) {
                    NearbyResponse response = deserialize(cached);
                    if (response != null) return response;
                }

                // Step 2: Postgres L2 (fresh entries only)
                List<DestinationsCacheEntity> freshEntries =
                        cacheRepository.findNearbyFresh(lat, lng, radiusMeters, limit);
                if (freshEntries.size() >= limit) {
                    log.debug("Postgres L2 hit: {} fresh entries for key={}", freshEntries.size(), cacheKey);
                    NearbyResponse response = buildResponse(freshEntries, true, getProviderStatus());
                    writeRedisCache(cacheKey, response);
                    return response;
                }

                // Step 3: OTM provider call
                List<OtmPlace> otmPlaces = otmClient.fetchNearby(lat, lng, radiusMeters, limit);
                for (OtmPlace place : otmPlaces) {
                    DestinationsCacheEntity entity = mapper.fromOtm(place);
                    cacheRepository.upsert(
                            entity.getProviderRef(), entity.getName(), entity.getCategory(),
                            entity.getRating(), entity.getLat(), entity.getLng(),
                            entity.getAddress(), entity.getPhotos(), entity.getOpeningHours(),
                            entity.getWebsite(), entity.getRaw(), entity.getFetchedAt());
                }

                // Step 4: Foursquare enrichment
                List<FoursquareVenue> fsqVenues = fsqClient.searchNearby(lat, lng, radiusMeters, limit);
                enrichEntitiesFromFoursquare(fsqVenues);

                // Step 5: Re-query Postgres for final result set (now includes fresh data)
                List<DestinationsCacheEntity> results =
                        cacheRepository.findNearby(lat, lng, radiusMeters, limit);
                NearbyResponse response = buildResponse(results, false, getProviderStatus());

                // Step 6: Write Redis L1
                writeRedisCache(cacheKey, response);
                return response;
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            // Another thread holds lock — wait then read
            return waitForCacheOrDirect(cacheKey, lat, lng, radiusMeters, limit);
        }
    }

    private NearbyResponse buildResponse(List<DestinationsCacheEntity> entities,
                                          boolean fromCache, ProviderStatus status) {
        List<NearbyItem> items = entities.stream().map(mapper::toNearbyItem).toList();
        return new NearbyResponse(items, fromCache, status);
    }

    private ProviderStatus getProviderStatus() {
        String otmStatus = getCircuitState("openTripMap");
        String fsqStatus = getCircuitState("foursquare");
        return new ProviderStatus(otmStatus, fsqStatus);
    }

    private String getCircuitState(String name) {
        try {
            var cb = circuitBreakerRegistry.circuitBreaker(name);
            return switch (cb.getState()) {
                case CLOSED, HALF_OPEN -> "ok";
                case OPEN, DISABLED, FORCED_OPEN -> "circuit_open";
                default -> "ok";
            };
        } catch (Exception e) {
            return "ok";
        }
    }

    private void enrichEntitiesFromFoursquare(List<FoursquareVenue> venues) {
        for (FoursquareVenue venue : venues) {
            if (venue.name() == null) continue;
            // Match FSQ venue to cached OTM entry by name (case-insensitive)
            cacheRepository.findAll().stream()
                    .filter(e -> venue.name().equalsIgnoreCase(e.getName()))
                    .findFirst()
                    .ifPresent(entity -> {
                        mapper.enrichFromFoursquare(entity, venue);
                        cacheRepository.upsert(
                                entity.getProviderRef(), entity.getName(), entity.getCategory(),
                                entity.getRating(), entity.getLat(), entity.getLng(),
                                entity.getAddress(), entity.getPhotos(), entity.getOpeningHours(),
                                entity.getWebsite(), entity.getRaw(), entity.getFetchedAt());
                    });
        }
    }

    private NearbyResponse waitForCacheOrDirect(String cacheKey, double lat, double lng,
                                                 int radiusMeters, int limit) {
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String cached = readRedisCache(cacheKey);
            if (cached != null) {
                NearbyResponse response = deserialize(cached);
                if (response != null) return response;
            }
        }
        // Fallback: query Postgres directly
        List<DestinationsCacheEntity> results =
                cacheRepository.findNearby(lat, lng, radiusMeters, limit);
        return buildResponse(results, true, getProviderStatus());
    }

    private String readRedisCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeRedisCache(String key, NearbyResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for key={}: {}", key, e.getMessage());
        }
    }

    private NearbyResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, NearbyResponse.class);
        } catch (JsonProcessingException e) {
            log.warn("Cache deserialization failed: {}", e.getMessage());
            return null;
        }
    }
}
