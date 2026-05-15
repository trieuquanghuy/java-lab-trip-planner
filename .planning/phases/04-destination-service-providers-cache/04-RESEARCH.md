# Phase 4: Destination Service — Providers + Cache — Research

**Researched:** 2026-05-15
**Phase:** 04-destination-service-providers-cache
**Requirements:** SRCH-02, DEST-01, DEST-02, DEST-03, NFR-03

---

## 1. OpenTripMap API (Free Tier)

### Base URL
`https://api.opentripmap.com/0.1/en/places/`

### Key Endpoints

**Radius search:** `GET /radius?radius={m}&lon={lng}&lat={lat}&rate={min_rate}&limit={n}&apikey={key}`

Response (array):
```json
[
  {
    "xid": "N12345",
    "name": "British Museum",
    "rate": 7,
    "osm": "relation:123456",
    "wikidata": "Q6373",
    "kinds": "museums,cultural,interesting_places",
    "point": { "lon": -0.1270, "lat": 51.5194 }
  }
]
```

**Detail:** `GET /xid/{xid}?apikey={key}`

Response:
```json
{
  "xid": "N12345",
  "name": "British Museum",
  "rate": 7,
  "kinds": "museums,cultural,interesting_places",
  "osm": "relation:123456",
  "wikidata": "Q6373",
  "sources": { "geometry": "osm", "attributes": ["osm", "wikidata"] },
  "bbox": { "lon_min": -0.128, "lat_min": 51.518, "lon_max": -0.125, "lat_max": 51.520 },
  "point": { "lon": -0.1270, "lat": 51.5194 },
  "address": { "road": "Great Russell Street", "city": "London", "state": "England", "country": "United Kingdom", "postcode": "WC1B 3DG" },
  "wikipedia": "https://en.wikipedia.org/wiki/British_Museum",
  "image": "https://commons.wikimedia.org/wiki/File:British_Museum.jpg",
  "preview": { "source": "https://upload.wikimedia.org/...", "width": 800, "height": 600 },
  "wikipedia_extracts": { "title": "British Museum", "text": "The British Museum...", "html": "<p>..." }
}
```

### Free Tier Constraints
- No API key signup cost (free registration)
- Rate limit: ~500 calls/day (undocumented soft limit)
- `rate` field: 1-3 (low), 4-6 (medium), 7 (high quality data)
- `kinds` field: comma-separated categories from OTM taxonomy
- `image` and `preview`: available only when Wikimedia Commons has a photo
- `address` object: structured, not always complete
- No opening hours in OTM responses

### Provider Ref Format
`otm:{xid}` — e.g., `otm:N12345`

---

## 2. Foursquare Places API (Free Tier)

### Base URL
`https://api.foursquare.com/v3/places/`

### Key Endpoints

**Nearby search:** `GET /search?ll={lat},{lng}&radius={m}&limit={n}&categories={ids}`
Headers: `Authorization: {API_KEY}`, `Accept: application/json`

Response:
```json
{
  "results": [
    {
      "fsq_id": "4b7c5e1af964a520d0e12fe3",
      "name": "British Museum",
      "categories": [
        { "id": 10027, "name": "Museum", "short_name": "Museum", "plural_name": "Museums", "icon": { "prefix": "https://...", "suffix": ".png" } }
      ],
      "geocodes": { "main": { "latitude": 51.5194, "longitude": -0.1270 } },
      "location": { "formatted_address": "Great Russell St, London WC1B 3DG", "locality": "London", "region": "England", "country": "GB" },
      "distance": 1200,
      "link": "/v3/places/4b7c5e1af964a520d0e12fe3"
    }
  ]
}
```

### Free Tier Constraints (CRITICAL — Pitfall 6)
- **Photos**: Premium field — silently absent on free tier. `photos` array is NOT returned.
- **Opening hours**: Premium field — `hours` object NOT returned on free tier.
- **Tips/reviews**: Premium field — NOT returned on free tier.
- **Rating**: NOT a field in Foursquare v3 API (was in v2). Use `popularity` or `rating` from tips (premium).
- Free tier: 50 regular API calls/day (as of 2026)
- Categories: numeric ID based (10027 = Museum, 16000 = Landmark, etc.)

### Provider Ref Format
`fsq:{fsq_id}` — e.g., `fsq:4b7c5e1af964a520d0e12fe3`

### WireMock Stub Implications
- Stubs must NOT include `photos`, `hours`, `tips`, or `rating` fields
- DTOs must use `@JsonIgnoreProperties(ignoreUnknown = true)` for future-proofing
- `photos` and `opening_hours` in `destinations_cache` will be NULL for Foursquare-only entries

---

## 3. Resilience4j Circuit Breaker (Spring Boot 3)

### Dependencies Required
```kotlin
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.4.0")
implementation("org.springframework.boot:spring-boot-starter-aop")  // REQUIRED for annotations
implementation("org.springframework.boot:spring-boot-starter-actuator")  // Already present
```

### YAML Configuration
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowType: COUNT_BASED
        minimumNumberOfCalls: 5
        registerHealthIndicator: true
        eventConsumerBufferSize: 10
    instances:
      openTripMap:
        baseConfig: default
      foursquare:
        baseConfig: default
```

### Annotation Usage
```java
@CircuitBreaker(name = "openTripMap", fallbackMethod = "fallbackNearby")
public List<OtmPlace> fetchNearby(double lat, double lng, int radius) {
    // HTTP call to OpenTripMap
}

private List<OtmPlace> fallbackNearby(double lat, double lng, int radius, Exception e) {
    log.warn("OTM circuit open, returning empty", e);
    return Collections.emptyList();
}
```

### Key Behaviors
- Separate CB instances per provider (per CONTEXT.md D-06)
- Fallback method MUST be in the same class with same signature + Exception param
- Aspect order: Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead
- Health indicator: CLOSED=UP, OPEN=DOWN, HALF_OPEN=UNKNOWN
- Metrics auto-published to `/actuator/metrics/resilience4j.circuitbreaker.*`

---

## 4. PostgreSQL earth_distance Module

### Approach: Cube-Based (Recommended)

The `earthdistance` module requires `cube` extension. Both must be installed.

```sql
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;
```

### Key Functions
- `ll_to_earth(lat, lng)` → earth point from lat/lng degrees
- `earth_distance(earth, earth)` → great circle distance in **meters**
- `earth_box(earth, distance_meters)` → bounding cube for index search

### Nearby Query Pattern
```sql
SELECT d.*,
       earth_distance(ll_to_earth(d.lat, d.lng), ll_to_earth(:lat, :lng)) AS distance_m
FROM destinations_cache d
WHERE earth_box(ll_to_earth(:lat, :lng), :radius) @> ll_to_earth(d.lat, d.lng)
  AND earth_distance(ll_to_earth(d.lat, d.lng), ll_to_earth(:lat, :lng)) <= :radius
ORDER BY distance_m ASC
LIMIT :limit
```

### Index Strategy
The `@>` operator on `earth_box` uses a GiST index on `cube` type:
```sql
-- Convert lat/lng to cube for indexing
ALTER TABLE destinations_cache ADD COLUMN earth_loc cube;
-- Update: earth_loc = ll_to_earth(lat, lng)
CREATE INDEX destinations_cache_earth_idx ON destinations_cache USING GIST (earth_loc);
```

**Alternative (simpler):** Skip the stored cube column. Use a functional index or compute at query time. For portfolio scale (<10K rows), the bounding-box pre-filter with `earth_box @>` is optional — direct `earth_distance()` with a sequential scan is fast enough.

### Comparison: cube-based vs point-based `<@>`
| Aspect | cube + earth_distance | point <@> |
|--------|----------------------|-----------|
| Units | meters (native) | statute miles (hardwired) |
| Accuracy | Great circle | Great circle |
| Edge cases | Handles poles/antimeridian | Problems near ±180° longitude |
| Index | GiST on cube | GiST on point |
| Conversion | Need ll_to_earth() | Direct point(lng, lat) |

**Decision:** Use cube-based approach for meter-native radius queries (API uses meters).

---

## 5. WireMock Spring Boot Integration

### Dependency
```kotlin
testImplementation("org.wiremock.integrations:wiremock-spring-boot:3.9.0")
```

Note: This replaces the old `com.github.tomakehurst:wiremock` artifact.

### Usage Pattern
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableWireMock({
    @ConfigureWireMock(name = "otm-service", property = "otm.base-url"),
    @ConfigureWireMock(name = "fsq-service", property = "fsq.base-url")
})
class DestinationNearbyIT {

    @InjectWireMock("otm-service")
    private WireMockServer otmServer;

    @InjectWireMock("fsq-service")
    private WireMockServer fsqServer;

    @Test
    void nearbyReturnsAttractionsFromProviders() {
        otmServer.stubFor(get(urlPathEqualTo("/0.1/en/places/radius"))
            .withQueryParam("lat", equalTo("51.5074"))
            .willReturn(okJson("[{\"xid\":\"N12345\",\"name\":\"British Museum\",...}]")));

        // test logic
    }
}
```

### Key Features
- `@ConfigureWireMock(property = "...")` auto-sets Spring property to WireMock URL
- Multiple WireMock instances (one per provider) — recommended pattern
- `@InjectWireMock` gives direct server access for stubbing
- Auto port allocation, no conflicts
- Stubs can be loaded from JSON files in `src/test/resources/wiremock/`

### Stub File Organization
```
src/test/resources/wiremock/otm-service/mappings/
  nearby-london.json
  detail-british-museum.json
src/test/resources/wiremock/fsq-service/mappings/
  search-london.json
```

---

## 6. Spring RestClient (Spring Boot 3.5)

Spring Boot 3.5 ships `RestClient` as the recommended synchronous HTTP client (replaces `RestTemplate`).

```java
@Configuration
public class ProviderClientConfig {
    @Bean
    public RestClient otmRestClient(@Value("${otm.base-url}") String baseUrl) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Accept", "application/json")
            .build();
    }
}
```

Usage:
```java
List<OtmPlace> places = otmRestClient.get()
    .uri("/0.1/en/places/radius?lat={lat}&lon={lng}&radius={r}&limit={l}&apikey={k}",
         lat, lng, radius, limit, apiKey)
    .retrieve()
    .body(new ParameterizedTypeReference<List<OtmPlace>>() {});
```

### Why RestClient over WebClient
- Synchronous is simpler (no reactive chain complexity)
- The `@CircuitBreaker` annotation works directly with synchronous return types
- WebClient requires `resilience4j-reactor` module for reactive CB integration
- Phase 3 SearchService uses synchronous patterns — maintain consistency

---

## 7. Provider Client Architecture

### Recommended Package Structure
```
com.tripplanner.destination/
  provider/
    ProviderClient.java              // Interface
    ProviderException.java           // Custom exception for CB to catch
    otm/
      OtmClient.java                // RestClient-based OTM calls
      OtmPlace.java                  // DTO for radius response
      OtmPlaceDetail.java           // DTO for detail response
    fsq/
      FoursquareClient.java         // RestClient-based FSQ calls
      FoursquareVenue.java          // DTO for search response
  destination/
    DestinationsCacheEntity.java    // JPA entity for destinations_cache
    DestinationsCacheRepository.java // Spring Data JPA repo with earth_distance
    NearbyService.java              // Orchestrates pipeline: cache → providers → cache write
    DetailService.java              // Single-destination detail lookup
    NearbyController.java           // GET /api/destinations
    DetailController.java           // GET /api/destinations/{providerRef}
    NearbyResponse.java             // Response DTO with items + providerStatus
    DestinationDetailResponse.java  // Response DTO for single destination
```

### Mapping Layer
```java
// OtmPlace → DestinationsCacheEntity (core fields)
entity.setProviderRef("otm:" + otmPlace.xid());
entity.setName(otmPlace.name());
entity.setLat(otmPlace.point().lat());
entity.setLng(otmPlace.point().lon());
entity.setCategory(mapOtmKindsToCategory(otmPlace.kinds()));

// FoursquareVenue → enrich existing entity (category, formatted address)
entity.setCategory(venue.categories().get(0).name());  // Override OTM category
entity.setAddress(venue.location().formattedAddress());
```

---

## 8. Destinations Cache Table Migration

```sql
-- V5__create_destinations_cache.sql

-- Required extensions (cube + earthdistance already need unaccent from V2)
CREATE EXTENSION IF NOT EXISTS cube;
CREATE EXTENSION IF NOT EXISTS earthdistance;

CREATE TABLE destination.destinations_cache (
    provider_ref   VARCHAR(80) PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    category       VARCHAR(80),
    rating         NUMERIC(3,1),
    lat            NUMERIC(9,6) NOT NULL,
    lng            NUMERIC(9,6) NOT NULL,
    address        VARCHAR(400),
    photos         JSONB NOT NULL DEFAULT '[]'::jsonb,
    opening_hours  JSONB,
    website        VARCHAR(2048),
    raw            JSONB NOT NULL,
    fetched_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Earth-distance index for radial queries (cube-based)
-- Using functional index: ll_to_earth(lat, lng) produces a cube
CREATE INDEX destinations_cache_earth_idx
    ON destination.destinations_cache
    USING GIST (ll_to_earth(CAST(lat AS float8), CAST(lng AS float8)));

-- Staleness sweep index
CREATE INDEX destinations_cache_fetched_at_idx
    ON destination.destinations_cache (fetched_at);
```

### Upsert Pattern (Dedup per D-14)
```sql
INSERT INTO destination.destinations_cache (provider_ref, name, category, rating, lat, lng, address, photos, opening_hours, raw, fetched_at)
VALUES (:ref, :name, :cat, :rating, :lat, :lng, :addr, :photos, :hours, :raw, NOW())
ON CONFLICT (provider_ref) DO UPDATE SET
    name = EXCLUDED.name,
    category = EXCLUDED.category,
    rating = EXCLUDED.rating,
    lat = EXCLUDED.lat,
    lng = EXCLUDED.lng,
    address = EXCLUDED.address,
    photos = EXCLUDED.photos,
    opening_hours = EXCLUDED.opening_hours,
    raw = EXCLUDED.raw,
    fetched_at = EXCLUDED.fetched_at
WHERE destinations_cache.fetched_at < EXCLUDED.fetched_at;
```

---

## 9. Pitfalls & Gotchas

### Pitfall 6 (Foursquare Free Tier)
Photos, hours, and tips are Premium-only fields. WireMock stubs and DTOs must reflect this — never include these fields in test fixtures.

### Pitfall 8 (Cache Stampede)
Reuse Phase 3 pattern: Redis SETNX single-flight lock. Key: `lock:nearby:{lat}:{lng}:{radius}`.

### Pitfall 12 (Overlapping Radius Dedup)
`ON CONFLICT (provider_ref) DO UPDATE` with `WHERE fetched_at < EXCLUDED.fetched_at` — newer data wins, no duplicates.

### Resilience4j + AOP
`spring-boot-starter-aop` MUST be on the classpath for `@CircuitBreaker` to work. Without it, annotations are silently ignored (no error, just no circuit breaking).

### earth_distance + NUMERIC columns
`ll_to_earth()` takes `float8` arguments. NUMERIC(9,6) columns need explicit CAST to `float8` in the index definition and queries.

### RestClient Error Handling
RestClient throws `HttpClientErrorException` / `HttpServerErrorException`. The circuit breaker should record `HttpServerErrorException` (5xx) as failures, but NOT `HttpClientErrorException` (4xx — like 404 for missing attraction).

```yaml
resilience4j.circuitbreaker.instances.openTripMap:
  recordExceptions:
    - org.springframework.web.client.HttpServerErrorException
    - java.io.IOException
    - java.net.ConnectException
  ignoreExceptions:
    - org.springframework.web.client.HttpClientErrorException
```

---

## 10. Security Considerations

- Provider API keys stored in environment variables, not in code
- For WireMock-only v1: placeholder keys in `application.yml`, real keys in env overrides
- Rate limiting on `/api/destinations*` (60 req/min per IP, per API spec §7)
- Input validation: lat (-90 to 90), lng (-180 to 180), radius (1 to 50000), limit (1 to 20)
- `raw` JSONB column stores unmodified provider response — contains no user PII
- No authentication required for destination endpoints (public, read-only data)
