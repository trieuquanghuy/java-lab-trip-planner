# Phase 4: Destination Service — Providers + Cache - Context

**Gathered:** 2026-05-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 4 ships the nearby attractions endpoint (`GET /api/destinations?lat=…&lng=…&radius=…&limit=…`) and the destination detail endpoint (`GET /api/destinations/{providerRef}`) inside destination-service. The endpoints are backed by two external providers (OpenTripMap as primary, Foursquare as enrichment), protected by per-provider Resilience4j circuit breakers, and cached in a two-tier system: Redis L1 (1h) + Postgres `destinations_cache` L2 (24h lazy refresh). All provider interactions are fully stubbed via WireMock for v1.

**In scope:**
- Flyway migration: `destinations_cache` table with GIST index on `point(lng, lat)` for radial queries
- OpenTripMap provider client (WireMock-stubbed) — returns nearby attractions + basic detail
- Foursquare provider client (WireMock-stubbed) — enriches with category and rating
- Separate provider DTOs (OtmPlace, FoursquareVenue) mapped to unified DestinationCacheEntity
- Resilience4j circuit breakers: separate per provider (50% failure rate / 10 calls / 30s wait)
- Fallback behavior: return cached data or empty items + `providerStatus` object
- Two-tier cache: Redis L1 (`POI:{lat}:{lng}:{radius}`, 1h TTL) + Postgres L2 (24h staleness threshold)
- Write-through: every successful provider response upserted immediately to `destinations_cache`
- Lazy staleness refresh: rows > 24h refetched on access when provider is available
- Deduplication: `ON CONFLICT (provider_ref) DO UPDATE` with newer-wins semantics
- Radial geo query: `point()` + `earth_distance` (no PostGIS extension)
- Nearby pipeline: Redis → Postgres → OTM → Foursquare enrichment → cache write
- Detail endpoint: cache lookup → provider fetch if miss/stale → cache write → return
- WireMock stubs for both providers (capturing realistic free-tier responses)
- Integration tests validating all 5 success criteria

**Out of scope (deferred to other phases):**
- Real API keys / live provider calls (v2+)
- PostGIS extension (unnecessary for portfolio scale)
- Background cache refresh jobs
- Frontend destination UI — Phase 7/8

</domain>

<decisions>
## Implementation Decisions

### Provider Integration
- **D-01 (Stub-only v1):** All provider interactions are WireMock-stubbed. No real API keys needed for local dev or tests. WireMock stubs represent realistic free-tier response shapes.
- **D-02 (Provider roles):** OpenTripMap is the primary source (nearby search + basic detail). Foursquare is enrichment only (adds category, rating). If Foursquare is down, OTM data alone is sufficient.
- **D-03 (Response mapping):** Separate DTO per provider (e.g., `OtmPlace`, `FoursquareVenue`) mapped to a unified `DestinationCacheEntity`. Clean provider boundary — each client has its own response model.
- **D-04 (Foursquare free-tier):** Photos and opening hours are Premium fields silently absent on free tier. DTOs use `@JsonIgnoreProperties(ignoreUnknown = true)`. `photos`/`hours` are nullable. WireMock stubs must NOT include these fields (per ROADMAP Pitfall 6).

### Circuit Breaker & Fallback
- **D-05 (CB config):** Resilience4j standard settings: 50% failure rate threshold, sliding window of 10 calls, 30s wait duration in half-open state, 3 permitted calls in half-open.
- **D-06 (Separate CBs):** Each provider has its own circuit breaker instance. They open/close independently — Foursquare being down does not affect OTM calls (success criterion #4).
- **D-07 (Fallback behavior):** When circuit is open: return whatever is in `destinations_cache` for the requested area. If nothing cached, return empty `items` array with HTTP 200 + `providerStatus` showing which provider is down. Never return 503 for nearby (per API spec).

### Cache Population & Staleness
- **D-08 (Write-through):** Every successful provider response is upserted to `destinations_cache` immediately. `ON CONFLICT (provider_ref) DO UPDATE` prevents duplicates from overlapping radius searches. Newer `fetched_at` wins.
- **D-09 (Staleness):** Cache rows with `fetched_at > 24h` are refetched lazily on next access when the relevant provider circuit is closed. If provider is down, serve stale data (any data is better than no data).
- **D-10 (Redis L1):** Nearby endpoint has Redis cache layer. Key: `POI:{lat}:{lng}:{radius}`, 1h TTL. Redis serves as L1 (fast), Postgres `destinations_cache` as L2 (durable). Same pattern as Phase 3 search cache.
- **D-11 (Single-flight):** Same stampede protection pattern as Phase 3 — Redis SETNX lock for concurrent nearby requests to same coordinates.

### Nearby Pipeline
- **D-12 (Step order):** 1) Check Redis L1 → 2) Check Postgres L2 for non-stale rows in radius → 3) If insufficient: call OTM → upsert cache → call Foursquare enrichment → upsert cache → 4) Write Redis L1 → 5) Return merged results + `providerStatus`.
- **D-13 (Geo query):** Use native Postgres `point()` + `earth_distance` module with GIST index. No PostGIS needed. Index: `GIST(point(lng, lat))` on `destinations_cache`.
- **D-14 (Dedup):** `provider_ref` is PK. Upsert with `WHERE fetched_at < EXCLUDED.fetched_at` ensures newer data wins. Overlapping radius searches naturally deduplicate.
- **D-15 (Limit):** Max 20 results per API spec. Default 20, capped at 20.

### Agent's Discretion
- Provider client implementation: RestClient vs WebClient vs RestTemplate (agent chooses based on Spring Boot 3.5 best practices)
- WireMock stub organization (per-test vs shared fixtures)
- Whether to use `@CircuitBreaker` annotation or programmatic Resilience4j API
- Detail endpoint cache-miss handling (synchronous fetch vs. 404-then-background)
- earth_distance vs cube extension for distance calculation

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Data Model
- `docs/03-data-model.md` §3.8 — `destination.destinations_cache` table schema, indexes, GIST index definition
- `docs/03-data-model.md` §2.3 — ERD showing `destinations_cache` structure

### API Contract
- `docs/04-api-spec.md` §4 — `GET /api/destinations` nearby endpoint contract (params, response shape, providerStatus)
- `docs/04-api-spec.md` §4 — `GET /api/destinations/{providerRef}` detail endpoint contract

### Architecture
- `docs/02-architecture.md` — service boundaries, destination-service placement, provider integration layer

### Prior Phase Context
- `.planning/phases/03-destination-service-search/03-CONTEXT.md` — Redis caching pattern, JPA entity pattern, service layering (reuse same patterns)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SearchService.java` — Redis cache-aside + single-flight lock pattern (reuse for nearby cache)
- `City.java` — JPA entity pattern (protected no-arg constructor, getters only)
- `CityRepository.java` — Native query pattern with `@Query`
- `ServletSecurityConfig.java` — Security permit pattern (add `/api/destinations/**`)
- `StringRedisTemplate` + `ObjectMapper` — already configured for JSON serialization

### Established Patterns
- Controller → Service → Repository layering
- Records for DTOs (`CitySearchItem`, `SearchResponse`)
- Flyway versioned migrations in `db/migration/`
- Testcontainers for integration tests
- `application-docker.yml` for Docker Compose profile overrides

### Integration Points
- `destination` schema — new table `destinations_cache` alongside existing `cities`
- Security config — add `/api/destinations/**` to `permitAll()`
- Redis — reuse existing connection for L1 cache
- `build.gradle.kts` — add `resilience4j-spring-boot3` + `wiremock-spring-boot` dependencies

</code_context>

<specifics>
## Specific Ideas

- WireMock stubs should represent realistic free-tier responses (not full-schema documentation)
- `providerStatus` field in response must show per-provider status (`"ok"`, `"circuit_open"`, `"degraded"`)
- Foursquare enrichment is best-effort — missing enrichment data is acceptable
- The `raw` JSONB column stores unmodified provider response for debugging/future use

</specifics>

<deferred>
## Deferred Ideas

- Real API key configuration and live provider calls (v2+)
- PostGIS extension for more accurate geodesic queries (unnecessary at portfolio scale)
- Background cache refresh scheduled jobs
- Provider response schema validation / alerting on shape changes

</deferred>

---

*Phase: 04-destination-service-providers-cache*
*Context gathered: 2026-05-15*
