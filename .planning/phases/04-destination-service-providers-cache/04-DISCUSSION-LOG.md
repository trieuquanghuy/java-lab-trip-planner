# Phase 4: Destination Service — Providers + Cache - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-15
**Phase:** 04-destination-service-providers-cache
**Areas discussed:** Provider integration approach, Circuit breaker & fallback behavior, Cache population & staleness, Nearby endpoint pipeline

---

## Provider Integration Approach

### Q1: Provider call strategy for v1 local development

| Option | Description | Selected |
|--------|-------------|----------|
| Fully stubbed (WireMock only) | No real API keys. WireMock returns canned responses. Tests and local dev use the same stubs. | ✓ |
| Real API keys + WireMock for tests | Local dev hits real APIs. Tests use WireMock. Requires API key signup. | |
| Configurable (stub by default, real optional) | Default to WireMock, allow switching to real providers via Spring profile. | |

**User's choice:** Fully stubbed (WireMock only)
**Notes:** Fastest to implement, no external dependencies for v1 portfolio project.

### Q2: Provider relationship

| Option | Description | Selected |
|--------|-------------|----------|
| OTM primary, Foursquare enrichment | OpenTripMap = primary (nearby + detail). Foursquare = enrichment (category, rating). OTM alone is sufficient. | ✓ |
| Both equal, merge results | Both searched in parallel, merged by proximity dedup. | |
| OpenTripMap only (drop Foursquare) | Only OTM for v1. Foursquare deferred. | |

**User's choice:** OTM primary, Foursquare enrichment
**Notes:** Clear hierarchy — OTM failure is critical, Foursquare failure is gracefully degraded.

### Q3: Response mapping strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Separate provider DTOs → unified entity | Separate DTO per provider mapped to unified DestinationCacheEntity. Clean boundary. | ✓ |
| Generic ProviderResponse | Single generic response parsed differently per provider. | |

**User's choice:** Separate provider DTOs → unified entity
**Notes:** Clean provider boundary with explicit mapping layer.

---

## Circuit Breaker & Fallback Behavior

### Q1: Circuit breaker configuration

| Option | Description | Selected |
|--------|-------------|----------|
| Standard (50% / 10 calls / 30s) | Standard resilience4j defaults. | ✓ |
| Aggressive (30% / 10 calls / 60s) | Lower threshold, longer wait. More aggressive opening. | |
| Tolerant (70% / 10 calls / 15s) | Higher threshold, shorter wait. | |

**User's choice:** Standard (50% / 10 calls / 30s)

### Q2: Fallback when circuit is open

| Option | Description | Selected |
|--------|-------------|----------|
| Return cached or empty + status | Return whatever is in destinations_cache. If nothing cached, return empty items + providerStatus. | ✓ |
| Return cached or 503 | Return cached data if available. 503 if no cache. | |
| Async retry (202) | Queue and retry when circuit closes. | |

**User's choice:** Return cached or empty + status
**Notes:** Never return 503 for nearby. Always 200 with degraded mode indicator.

### Q3: Separate circuit breakers per provider

| Option | Description | Selected |
|--------|-------------|----------|
| Separate per provider | Each provider has own CB. Open/close independently. | ✓ |
| Single shared CB | One CB for all external calls. | |

**User's choice:** Separate per provider
**Notes:** Matches success criterion #4 — provider failures don't cascade.

---

## Cache Population & Staleness

### Q1: When data gets written to destinations_cache

| Option | Description | Selected |
|--------|-------------|----------|
| Write-through on every provider call | Every successful response upserted immediately. ON CONFLICT DO UPDATE. | ✓ |
| Write after full pipeline completes | Only write when both providers responded. | |
| Background cache population | Background job writes cache. Requests always go to providers. | |

**User's choice:** Write-through on every provider call

### Q2: Stale cache entries (>24h) handling

| Option | Description | Selected |
|--------|-------------|----------|
| Lazy refresh on access (24h threshold) | Refetch on access when provider available. Serve stale if provider down. | ✓ |
| Background scheduled refresh | Scheduled job refreshes all stale rows. | |
| No expiry (append-only) | Cache never expires. | |

**User's choice:** Lazy refresh on access (24h threshold)

### Q3: Redis L1 for nearby endpoint

| Option | Description | Selected |
|--------|-------------|----------|
| Redis L1 (1h) + Postgres L2 (24h) | Redis caches full nearby response. Same pattern as Phase 3 search. | ✓ |
| Postgres only (no Redis L1) | Only Postgres destinations_cache. | |

**User's choice:** Redis L1 (1h) + Postgres L2 (24h)

---

## Nearby Endpoint Pipeline

### Q1: Radial geo query approach

| Option | Description | Selected |
|--------|-------------|----------|
| point() + earth_distance (no PostGIS) | Native Postgres point + GIST index with earth_distance. No extra extension. | ✓ |
| PostGIS ST_DWithin | PostGIS extension for proper geodesic distance. Heavier dependency. | |
| Bounding box (approximate) | Simple lat/lng range filter. Fast but not circular. | |

**User's choice:** point() + earth_distance (no PostGIS)

### Q2: Pipeline step order

| Option | Description | Selected |
|--------|-------------|----------|
| Redis → Postgres → OTM → Foursquare → cache write | Full tiered pipeline with cache layers first. | ✓ |
| Provider-first, cache-merge after | Always call providers, then merge with cache. | |

**User's choice:** Redis → Postgres → OTM → Foursquare → cache write

### Q3: Deduplication strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Upsert with newer-wins (PK dedup) | ON CONFLICT (provider_ref) DO UPDATE WHERE fetched_at < EXCLUDED.fetched_at. | ✓ |
| Check-then-insert | Check existence before insert. Skip duplicates. | |

**User's choice:** Upsert with newer-wins (PK dedup)

---

## Agent's Discretion

- Provider client implementation approach (RestClient vs WebClient vs RestTemplate)
- WireMock stub organization (per-test vs shared fixtures)
- CircuitBreaker annotation vs programmatic API
- Detail endpoint cache-miss handling
- earth_distance vs cube extension choice

## Deferred Ideas

- Real API key configuration (v2+)
- PostGIS extension (unnecessary at portfolio scale)
- Background cache refresh jobs
- Provider response schema validation / alerting
