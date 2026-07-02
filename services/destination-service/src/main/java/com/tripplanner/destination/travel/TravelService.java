package com.tripplanner.destination.travel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.destination.travel.TravelRequest.Waypoint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TravelService {

    private static final Logger log = LoggerFactory.getLogger(TravelService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String CACHE_PREFIX = "TRAVEL:";

    private final OsrmClient osrmClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public TravelService(OsrmClient osrmClient, StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper) {
        this.osrmClient = osrmClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public TravelResponse getSegments(List<Waypoint> waypoints) {
        if (waypoints == null || waypoints.size() < 2) {
            return new TravelResponse(List.of());
        }

        List<TravelResponse.Segment> segments = new ArrayList<>();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            Waypoint from = waypoints.get(i);
            Waypoint to = waypoints.get(i + 1);
            segments.add(computeSegment(from.lat(), from.lng(), to.lat(), to.lng()));
        }
        return new TravelResponse(segments);
    }

    private TravelResponse.Segment computeSegment(double lat1, double lng1,
                                                   double lat2, double lng2) {
        String cacheKey = buildCacheKey(lat1, lng1, lat2, lng2);

        // Check Redis cache first
        String cached = readCache(cacheKey);
        if (cached != null) {
            TravelResponse.Segment segment = deserialize(cached);
            if (segment != null) {
                log.debug("Travel cache hit for key={}", cacheKey);
                return segment;
            }
        }

        // Call OSRM (CB handles fallback to null)
        OsrmClient.OsrmSegment raw = osrmClient.fetchSegment(lat1, lng1, lat2, lng2);

        TravelResponse.Segment segment;
        if (raw == null) {
            segment = new TravelResponse.Segment(null, null);
        } else {
            double minutes = roundTo1(raw.durationSeconds() / 60.0);
            double km = roundTo2(raw.distanceMeters() / 1000.0);
            segment = new TravelResponse.Segment(minutes, km);
        }

        writeCache(cacheKey, segment);
        return segment;
    }

    /**
     * Cache key uses coordinates rounded to 4 decimal places (~11m precision).
     */
    String buildCacheKey(double lat1, double lng1, double lat2, double lng2) {
        return CACHE_PREFIX
                + round4(lat1) + ":" + round4(lng1) + ":"
                + round4(lat2) + ":" + round4(lng2);
    }

    private double round4(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private double roundTo1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private double roundTo2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String readCache(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis read failed for key={}: {}", key, e.getMessage());
            return null;
        }
    }

    private void writeCache(String key, TravelResponse.Segment segment) {
        try {
            String json = objectMapper.writeValueAsString(segment);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Redis write failed for key={}: {}", key, e.getMessage());
        }
    }

    private TravelResponse.Segment deserialize(String json) {
        try {
            return objectMapper.readValue(json, TravelResponse.Segment.class);
        } catch (JsonProcessingException e) {
            log.warn("Travel cache deserialization failed: {}", e.getMessage());
            return null;
        }
    }
}
