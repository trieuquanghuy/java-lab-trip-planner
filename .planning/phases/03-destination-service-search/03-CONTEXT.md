# Phase 3: Destination Service — Search - Context

**Gathered:** 2026-05-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 ships the city/country search endpoint (`GET /api/search?q=…&type=…&limit=…`) inside destination-service. The endpoint is backed by a GeoNames seed (~25k cities) loaded via Flyway, queried with Postgres full-text search (population-weighted ranking), and cached in Redis with single-flight stampede protection. The route is already public-permitted at the gateway (Phase 1 D-03).

**In scope:**
- Flyway migration: `unaccent` + `pg_trgm` extensions, `destination.cities` table with TSVECTOR generated column + GIN indexes
- Seed data: pre-processed GeoNames `cities15000.txt` committed as SQL INSERT/COPY migration
- `GET /api/search` controller, service, repository (Spring Data JPA + native query)
- Population-weighted FTS ranking: `ts_rank(search_tsv, query) * LOG(population + 1) DESC`
- FTS prefix matching via `:*` operator with `'simple'` text search config
- Accent-folding at both query and index level via `unaccent()`
- Redis caching: key `SEARCH:{q}:{type}:{limit}`, 1h TTL, no manual invalidation
- Single-flight lock: `SET lock:search:{key} 1 NX EX 5` per Pitfall 8
- Type filtering (city/country from cities table)
- Empty/short query handling: return 200 with empty array
- Integration tests validating all 5 success criteria

**Out of scope (deferred to other phases):**
- `GET /api/destinations?lat=…&lng=…` nearby endpoint (Phase 4)
- `GET /api/destinations/{providerRef}` detail endpoint (Phase 4)
- Provider integrations (OpenTripMap, Foursquare) — Phase 4
- `destinations_cache` table — Phase 4
- Frontend search UI — Phase 7

</domain>

<decisions>
## Implementation Decisions

### GeoNames seed ingestion
- **D-01 (Load method):** Flyway SQL migration. The cities15000 dataset is pre-processed into SQL (INSERT or COPY statements) and committed to the repo as a versioned migration file. No network dependency at runtime; fully reproducible.
- **D-02 (Extensions):** `CREATE EXTENSION IF NOT EXISTS unaccent` and `CREATE EXTENSION IF NOT EXISTS pg_trgm` in an early Flyway migration (or `infra/postgres/init.sql` if superuser required). These are prerequisites for the TSVECTOR generated column and trigram index.
- **D-03 (Table schema):** Exactly as `docs/03-data-model.md §3.7` — `id`, `geoname_id` (UNIQUE), `name`, `country`, `country_code`, `lat`, `lng`, `population`, `search_tsv` (GENERATED ALWAYS AS `to_tsvector('simple', unaccent(name) || ' ' || unaccent(country))` STORED). Indexes: GIN on `search_tsv`, GIN trigram on `name`, btree on `country`.

### Search ranking + query strategy
- **D-04 (Ranking):** Population-weighted FTS: `ORDER BY ts_rank(search_tsv, query) * LOG(population + 1) DESC`. Ensures London UK (9M) ranks above London Ontario (400K) for prefix 'lon' (Pitfall 11).
- **D-05 (Prefix matching):** Convert user input to FTS prefix query: `to_tsquery('simple', unaccent(trim(q)) || ':*')`. Leverages the GIN index on `search_tsv`.
- **D-06 (Text search config):** `'simple'` — no stemming, no stop words. Predictable for proper nouns (city/country names).
- **D-07 (Accent handling):** `unaccent()` applied to both the stored TSVECTOR (via generated column) and the query input. "München" and "Munich" both match. Required by success criterion #5.

### Redis caching layer
- **D-08 (Cache key):** `SEARCH:{normalized_q}:{type}:{limit}` — normalize = lowercase + trim. Deterministic, debuggable.
- **D-09 (TTL):** 1 hour. TTL-only eviction; no manual invalidation. GeoNames data is static (seeded at boot), so cache is always consistent with DB.
- **D-10 (Stampede prevention):** Redis SETNX single-flight lock per Pitfall 8: `SET lock:search:{key} 1 NX EX 5`. First request acquires lock → queries Postgres → writes cache → deletes lock. Concurrent requests retry/wait briefly then read from cache.
- **D-11 (Serialization):** Store search results as JSON string in Redis via Jackson. RedisTemplate<String, String> with manual serialize/deserialize (avoids class metadata in Redis values).

### Response shape + edge cases
- **D-12 (Empty/short query):** Empty string, whitespace-only, or q < 1 meaningful char → return `{"items": []}` with HTTP 200. No 400 error. Matches API spec and success criterion #4.
- **D-13 (Type filter):** `type` parameter is optional CSV (default `city,country`). `type=city` returns only city-type rows; `type=country` searches the country field for direct country matches. Both filter from the same `cities` table.
- **D-14 (Limit):** Max 5 results per SRCH-01. Default 5, cap at 5 regardless of requested limit.
- **D-15 (Response shape):** Per `docs/04-api-spec.md §4`: `{"items": [{"type": "city"|"country", "name": "...", "country": "...", "lat": ..., "lng": ...}]}`. Each item has type, name, country, lat, lng.

### Agent's Discretion
- Repository layer: native query vs. Spring Data JPA `@Query` — agent chooses based on complexity
- Service layer organization (single SearchService vs. split CitySearchService + CacheService)
- RedisTemplate configuration approach (reuse existing bean from observability or declare service-specific)
- Test approach for single-flight lock (integration vs. unit with mock)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Data Model
- `docs/03-data-model.md` §3.7 — `destination.cities` table schema, indexes, generated column definition

### API Contract
- `docs/04-api-spec.md` §4 — `GET /api/search` request/response contract, query params, error codes

### Architecture
- `docs/02-architecture.md` — service boundaries, destination-service placement

### Prior Phase Context
- `.planning/phases/01-api-gateway/01-CONTEXT.md` — D-05/D-06 rate limit topology, D-03 public route allowlist
- `.planning/phases/00-monorepo-scaffolding/00-CONTEXT.md` — DB schema setup, Flyway conventions

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `libs/error-handling` — `ProblemDetailFactory`, `ErrorCode` enum for consistent error responses
- `libs/api-contracts` — `UserContext` record (not needed for this public endpoint but available)
- `infra/postgres/init.sql` — `destination` schema + `destination_svc` user already provisioned
- `services/destination-service/build.gradle.kts` — Spring Data JPA, Flyway, Redis starters already declared

### Established Patterns
- Flyway migrations in `src/main/resources/db/migration/` (convention from Phase 2 auth-service)
- Spring Boot auto-configured `ObjectMapper` for JSON serialization (convention from Phase 1 BL-01 fix)
- `@RestController` + service layer + repository pattern (standard Spring structure)

### Integration Points
- Gateway route `/api/search/**` → `http://destination-service:8083` (already configured in Phase 1)
- Redis connection shared with gateway rate-limiter (same Redis 7 instance in docker-compose)
- Actuator health endpoint at `/__health` already wired (Phase 1)

</code_context>

<specifics>
## Specific Ideas

- ROADMAP explicitly calls out: `ts_rank(search_tsv, query) * LOG(population + 1) DESC` as the ranking formula
- Single-flight lock key: `lock:search:{cache_key}` with NX and 5-second expiry
- GeoNames source: `cities15000.txt` from geonames.org (population ≥ 15,000, ~25k rows)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 03-destination-service-search*
*Context gathered: 2026-05-10*
