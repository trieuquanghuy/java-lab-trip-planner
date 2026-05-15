---
phase: 04-destination-service-providers-cache
plan: 04
status: complete
commits:
  - cd2187d  # feat(04-04): NearbyController, DetailController, SecurityConfig update
  - 755fe6e  # test(04-04): unit + integration tests
---

## What Was Done

### Task 1: Controllers + Security Config
- Created `NearbyController.java` — `GET /api/destinations` with lat/lng/radius/limit validation
  - lat: [-90, 90], lng: [-180, 180], radius: [1, 50000], limit clamped to [1, 20]
- Created `DetailController.java` — `GET /api/destinations/{providerRef}` with regex validation (`^(otm|fsq):[a-zA-Z0-9_-]+$`)
  - Returns 404 when DetailService returns null, 400 for invalid providerRef
- Updated `ServletSecurityConfig.java` — added `/api/destinations/**` to permitAll list

### Task 2: Unit Tests
- `NearbyServiceTest` — 5 tests: Redis L1 hit, Postgres L2 hit, cache miss pipeline calling OTM+FSQ, circuit open fallback, limit clamped to 20
- `DetailServiceTest` — 5 tests: fresh cache hit, stale triggers refresh, stale + provider down serves stale, no cache + provider down returns null, FSQ ref not supported
- `ProviderMapperTest` — 7 tests: fromOtm mapping, mapFirstKind with underscore replacement, fromOtmDetail with address/wiki/preview, enrichFromFoursquare overwrites category + fills address, toNearbyItem, toDetailResponse, firstPhoto edge cases

### Task 3: Integration Tests
- `NearbyControllerIntegrationTest` — 7 tests with @WebMvcTest: 200 with valid params, 400 for invalid lat/lng/radius, detail 200/404/400
- `OtmClientWireMockTest` — 3 tests with WireMock: fetchNearby parses array, fetchDetail parses full detail, empty response handling

## Key Decisions
- Used `@MockitoBean` (Spring Boot 3.5+) instead of deprecated `@MockBean`
- Excluded SecurityAutoConfiguration in controller integration test to avoid JWT filter dependency chain — endpoints are permitAll anyway
- WireMock test uses `@WireMockTest` JUnit5 extension (standalone) rather than `@EnableWireMock` spring integration

## Test Count
27 new tests total (5 + 5 + 7 + 7 + 3)

## Files Modified
- `services/destination-service/src/main/java/com/tripplanner/destination/destination/NearbyController.java` (new)
- `services/destination-service/src/main/java/com/tripplanner/destination/destination/DetailController.java` (new)
- `services/destination-service/src/main/java/com/tripplanner/destination/security/ServletSecurityConfig.java` (modified)
- `services/destination-service/src/test/java/com/tripplanner/destination/destination/NearbyServiceTest.java` (new)
- `services/destination-service/src/test/java/com/tripplanner/destination/destination/DetailServiceTest.java` (new)
- `services/destination-service/src/test/java/com/tripplanner/destination/destination/ProviderMapperTest.java` (new)
- `services/destination-service/src/test/java/com/tripplanner/destination/destination/NearbyControllerIntegrationTest.java` (new)
- `services/destination-service/src/test/java/com/tripplanner/destination/provider/otm/OtmClientWireMockTest.java` (new)
