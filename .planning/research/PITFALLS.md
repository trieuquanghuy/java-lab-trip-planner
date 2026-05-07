# Pitfalls Research

**Domain:** Trip-planning web application — Spring Boot 3 microservices + React 18
**Researched:** 2026-05-08
**Confidence:** HIGH (verified against official docs and known community issues)

> **Scope note:** Items already covered in `docs/10-risks.md` (R-1 through R-24) are
> intentionally excluded. This document surfaces *new* pitfalls or sharpens existing
> risk entries with concrete, code-level prevention steps. Every pitfall maps to at
> least one of the 11 phases in `docs/09-roadmap.md`.

---

## Critical Pitfalls

### Pitfall 1: X-User-Id Header Spoofing — Direct Downstream Access Bypasses Gateway Auth

**What goes wrong:**
The gateway injects `X-User-Id` from the validated JWT. Downstream services
(`trip-service`, `destination-service`) read that header to build the
`SecurityContext`. If those services are reachable on their raw ports (`:8082`,
`:8083`) from anything other than the gateway — including from the SPA during
dev, from Postman tests, or because `docker compose` exposes all ports — an
attacker (or an accidental integration test) can send any `X-User-Id` value and
impersonate any user without a valid JWT.

**Why it happens:**
The docs correctly specify that `jwt-common` re-validates the JWT for defense in
depth (§4.2), but this detail is easy to skip in Phase 1 when the gateway filter
is the obvious implementation site. Developers write the re-validation later,
or skip it with "we'll add it in Phase 2," and it never gets added. Additionally,
`docker-compose.yml` in the scaffold typically exposes all ports to `localhost`
for debugging convenience, which means the gap is invisible in dev.

**How to avoid:**
1. In Phase 1, wire `JwtCommonFilter` into `trip-service` and
   `destination-service` even before those services have real endpoints.
   The filter must reject requests that carry `X-User-Id` without a matching,
   valid `Authorization: Bearer` header.
2. In the filter, always derive `currentUserId` from the JWT `sub` claim — never
   from `X-User-Id` alone. Use `X-User-Id` only as a convenience read-through
   after JWT validation confirms it matches.
3. In `docker-compose.yml`, expose downstream service ports only on
   `127.0.0.1` (dev bound to loopback), and add a note that in production
   path those ports must not be published at all.
4. Add a mandatory security integration test:
   `DirectServiceAccessWithoutGatewayReturns401` — calls `:8082/api/trips`
   directly with a crafted `X-User-Id` header and no JWT; expects 401.

**Warning signs:**
- Integration tests reach trip-service via `localhost:8082` (not via the
  gateway on `:8080`).
- `JwtCommonFilter` is implemented but not registered in `SecurityFilterChain`
  of downstream services.
- `getHeader("X-User-Id")` appears in a controller or service method without
  a corresponding JWT validation step nearby.

**Phase to address:** Phase 1 (API Gateway) — must be complete before Phase 2
adds real auth data. Validate in Phase 2 security test suite.

---

### Pitfall 2: Optimistic Update Rollback Flicker with dnd-kit + TanStack Query

**What goes wrong:**
When a user drops an item, `onDragEnd` fires, the mutation starts, and TanStack
Query's optimistic update writes the reordered list to the cache. dnd-kit also
holds its own internal drag state until the drag cycle fully resolves. When
TanStack Query's `onSettled` triggers `invalidateQueries` after the mutation
completes, the resulting refetch overwrites the optimistic cache entry — but
dnd-kit is still mid-animation flush. The result is a visible one-frame snap-back
to the pre-drag position, or in multi-container dragging, a "too many re-renders"
crash from `setState` inside `onDragOver`.

**Why it happens:**
dnd-kit's `SortableContext` reads from React state for rendering; TanStack Query
is a separate cache layer. When both update in the same React commit, render
scheduling produces a transient state where dnd-kit's overlay and the query cache
disagree. The `invalidateQueries` call in `onSettled` is the specific trigger —
it causes a synchronous cache purge that dnd-kit's layout effect sees before the
new server data arrives.

**How to avoid:**
In Phase 8:
1. Maintain a **local ephemeral order state** (`useState`) inside
   `ItineraryBoard` for the drag session only — this is what dnd-kit reads.
   It is initialized from the query cache and discarded after drag resolves.
2. On `onDragEnd`, commit the mutation using TanStack Query's `mutate`, passing
   `onMutate` that writes the reorder to the *query cache* directly
   (`queryClient.setQueryData`). Do **not** call `invalidateQueries` on success
   — let the optimistic cache entry stand and only invalidate on *error* to
   trigger a re-fetch that rolls back.
3. Pattern:
   ```ts
   mutate(reorderPayload, {
     onMutate: async (vars) => {
       await queryClient.cancelQueries({ queryKey: ['trip', tripId] });
       const previous = queryClient.getQueryData(['trip', tripId]);
       queryClient.setQueryData(['trip', tripId], applyReorder(previous, vars));
       return { previous };
     },
     onError: (_err, _vars, ctx) => {
       queryClient.setQueryData(['trip', tripId], ctx.previous);
     },
     // NO onSuccess invalidation — server is authoritative only on error
   });
   ```
4. Debounce or queue concurrent drag mutations — if the user drags again before
   the first PATCH resolves, the second drag must `cancelQueries` first to avoid
   the race condition where `invalidateQueries` from mutation 1 wipes mutation 2's
   optimistic data.

**Warning signs:**
- `useQuery` invalidation fires immediately after every drag-drop in the network
  tab.
- Items visually snap back for ~100ms after drop, then settle.
- Console shows "Maximum update depth exceeded" or "Too many re-renders" when
  dragging quickly between days.

**Phase to address:** Phase 8 (Frontend: trip planner). Design this pattern
before writing any dnd-kit code — retrofitting it is painful.

---

### Pitfall 3: Flyway Shared `flyway_schema_history` Table Causes Service Startup Failures

**What goes wrong:**
All three services (`auth-service`, `trip-service`, `destination-service`) run
Flyway against the same Postgres instance. Flyway's default history table is
`flyway_schema_history` with no schema prefix. If all three services use the
default, the second service to start finds a `flyway_schema_history` entry for
`V1__init.sql` already present with a different checksum (from the first
service's migration file). Flyway throws
`FlywayException: Validate failed: Migration checksum mismatch` and the service
refuses to start.

**Why it happens:**
Developers scaffold three services from the same Spring Boot template, include
Flyway in all three, and never configure service-specific history table names.
In a single-schema deployment this is invisible; in a single-instance
multi-schema deployment it surfaces at first startup.

**How to avoid:**
In Phase 0 scaffolding, set **in each service's `application.yml`**:
```yaml
# auth-service/src/main/resources/application.yml
spring:
  flyway:
    schemas: auth
    default-schema: auth
    table: auth_flyway_schema_history

# trip-service
spring:
  flyway:
    schemas: trip
    default-schema: trip
    table: trip_flyway_schema_history

# destination-service
spring:
  flyway:
    schemas: destination
    default-schema: destination
    table: destination_flyway_schema_history
```
Also configure the Flyway `url`/`user`/`password` to use the per-service DB
user (`auth_svc`, `trip_svc`, `destination_svc`) so that a misconfigured service
cannot migrate a schema it doesn't own.

**Warning signs:**
- Running `docker compose up` with all three services produces a startup failure
  with `checksum mismatch` in one of the service logs.
- All three services have identical Flyway config blocks in `application.yml`.
- `psql -U postgres -c "\dt auth.*"` shows `flyway_schema_history` rather than
  `auth_flyway_schema_history`.

**Phase to address:** Phase 0 (Monorepo scaffolding) — must be correct in the
initial scaffold, not retrofitted.

---

### Pitfall 4: Day Materialization Not Wrapped in a Single Transaction — Partial Orphan State

**What goes wrong:**
`POST /api/trips/{id}` (set date range) calls a day-materialization service that
(a) inserts new `itinerary_days`, (b) optionally deletes removed days, and
(c) cascades deletion of `itinerary_items` on removed days. If steps (b) and (c)
are separate `@Transactional` calls — or if the confirmation check for shrinking
happens outside the transaction boundary — a crash between steps leaves the DB
in a partial state: some days deleted but their items still present as orphans,
or days present but items referencing a day that no longer exists.

A second failure mode: the `confirmShorten=true` check is done in the controller
before calling the service. If the controller's check and the service's delete
are in different transactions, a concurrent request on the same trip ID can slip
through and cause data corruption (two requests both think they have the
confirmation).

**Why it happens:**
Spring's `@Transactional` on a self-calling method inside the same class does NOT
create a new transaction (proxy is bypassed). Developers call
`materializeDays(trip)` from within `updateTrip(...)` without realizing that the
inner call falls outside the outer transaction's scope. Also, the confirmation
check is a natural "read, decide, act" pattern that developers write as three
separate DB round-trips.

**How to avoid:**
In Phase 5:
1. Place ALL of materialization — the confirmation count check, the insert of
   new days, and the delete of removed days + their items — inside a single
   `@Transactional(isolation = REPEATABLE_READ)` service method.
2. Never call `@Transactional` methods from within the same bean. If you need
   to split logic, move the inner method to a separate Spring bean so the proxy
   intercepts the call.
3. The cascade deletion of items when a day is deleted should be done via
   a `DELETE FROM itinerary_items WHERE day_id IN (...)` within the same
   transaction — not via JPA `CascadeType.REMOVE` (which issues N individual
   DELETE statements and can be slow).
4. Write an integration test that simulates a concurrent PATCH: two requests
   shrinking the same trip simultaneously must result in exactly one succeeding
   and one getting a conflict or 409, never a partial delete.

**Warning signs:**
- `materializeDays()` is a `private` or `protected` method called from within
  the same service class while marked `@Transactional`.
- Tests for date range changes use `@Transactional` on the test method — which
  rolls back state and masks failures from partial commits.
- `itinerary_items` table has orphan rows (items with `day_id` pointing to
  non-existent `itinerary_days`) visible via direct DB query.

**Phase to address:** Phase 5 (Trip service: trips + days).

---

### Pitfall 5: Position Integer Exhaustion and Collision Under Concurrent Inserts

**What goes wrong:**
The roadmap specifies gap-100, midpoint insert, lazy reindex. The midpoint
algorithm is: `newPosition = (prevPosition + nextPosition) / 2`. With integer
arithmetic, this eventually produces a gap of 1 between neighbors (e.g. 100 and
101 → midpoint = 100, collision). When this happens the reindex runs — but if two
concurrent requests both detect the same gap-too-small condition and both trigger
the reindex `UPDATE`, they produce a write-write conflict: one wins, the other
reads stale positions and assigns duplicates.

A second mode: Postgres integer overflow if positions are stored as `INT` (max
~2.1B) and a pathological sequence of MAX+100 appends runs for a long time.

**Why it happens:**
Integer midpoint compression is well known in collaborative tools. It is often
implemented without a SERIALIZABLE isolation level or a row-level lock on the
day's item list, meaning concurrent requests collide silently (no error — both
UPDATEs succeed, but the final order is determined by whichever commit arrives
last).

**How to avoid:**
In Phase 6:
1. Store `position` as `BIGINT` (not `INT`) to push overflow further out.
2. Lock the parent day row during a reindex: use
   `SELECT ... FOR UPDATE` on `itinerary_days WHERE id = :dayId` before
   reading and writing positions for that day. This serializes concurrent
   reorder operations on the same day without requiring table-level locks.
3. Implement the reindex as a single SQL statement that assigns new positions
   via a window function, not N individual UPDATEs:
   ```sql
   UPDATE itinerary_items
   SET position = new_pos.rn * 100
   FROM (
     SELECT id, ROW_NUMBER() OVER (ORDER BY position) AS rn
     FROM itinerary_items WHERE day_id = :dayId
   ) new_pos
   WHERE itinerary_items.id = new_pos.id;
   ```
4. The existing plan to unit-test 50 random reorders is necessary but not
   sufficient — add a concurrent test: two threads each issue 25 reorders on
   the same day simultaneously; assert that after all commits, no two items
   share a position and the final order matches at least one valid permutation.

**Warning signs:**
- `position` column is `INT` in the migration script.
- The reorder service does `Math.round((a + b) / 2)` without a preceding
  `SELECT FOR UPDATE` on the parent day.
- After running the 50-random-reorder test, a `SELECT position, COUNT(*) FROM
  itinerary_items GROUP BY position HAVING COUNT(*) > 1` returns rows.

**Phase to address:** Phase 6 (Trip service: itinerary items).

---

### Pitfall 6: Foursquare Premium Fields Silently Not Returned on Free Tier — Schema Drift

**What goes wrong:**
Foursquare's free tier (10,000 calls/month) provides access to Pro endpoints
only. **Photos, tips, opening hours, and ratings are Premium fields** — they are
not returned on the free tier. The JSON response for a venue simply omits those
keys. If the `DestinationResponse` DTO maps `photos` and `hours` as required
non-null fields, Jackson throws a deserialization exception for every Foursquare
enrichment call, causing the circuit breaker to open immediately and all
destination lookups to fall back to the degraded path.

The second drift risk: Foursquare silently changed field names in v3 of their
Places API (e.g. `categories[].icon` restructured). WireMock stubs built from
an older API response will mask this until real network calls are made.

**How to avoid:**
In Phase 4:
1. Annotate all Foursquare DTO fields as `@JsonProperty` with
   `@JsonIgnoreProperties(ignoreUnknown = true)` on the class, and make
   photos/hours `Optional` or nullable in the DTO:
   ```java
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record FoursquareVenueDto(
       String fsqId,
       String name,
       @Nullable List<PhotoDto> photos,   // null on free tier
       @Nullable HoursDto hours           // null on free tier
   ) {}
   ```
2. In `EnrichingDestinationService`, treat null Foursquare fields as "not
   available" and populate the `DestinationView` accordingly — never throw on
   absence.
3. Capture WireMock stubs from the actual free-tier API response (not the full
   documented schema), so tests exercise the actual payload the app will receive.
4. Add a contract-diff integration test that fetches one real Foursquare response
   in CI (using the real key, limited to 1 call) and compares its top-level field
   names against the DTO; fails the build if a mapped field disappears.

**Warning signs:**
- Circuit breaker for Foursquare opens within the first 5 real API calls during
  development.
- `DestinationView.photos` is typed as `List<Photo>` (not `List<Photo>?` or
  nullable) in Java.
- WireMock stubs for Foursquare include `photos` and `hours` fields even though
  a real free-tier call omits them.

**Phase to address:** Phase 4 (Destination service: providers + cache).

---

### Pitfall 7: Trace Context Lost at Spring Cloud Gateway → Downstream Boundary

**What goes wrong:**
Spring Boot 3 with Micrometer Tracing defaults to W3C Trace Context propagation
(`traceparent` / `tracestate` headers). Spring Cloud Gateway is a **WebFlux
(reactive)** application. The downstream services are **Servlet (blocking)**
Spring Boot apps. The context propagation bridge (Micrometer's
`ObservationRegistry`) works differently in reactive vs. servlet contexts — if
the `spring-cloud-sleuth` / `micrometer-tracing-bridge-otel` dependency versions
are misaligned across modules, the reactive context from the gateway does not
carry into the downstream service's MDC, and the `traceId` in downstream logs is
a different value than in the gateway log, breaking Zipkin trace continuity.

A second failure: the `ServerHttpObservationFilter` was deprecated in Spring
Framework 6.1 (Spring Boot 3.2). Projects copied from pre-3.2 samples that
manually register this filter end up with duplicate spans or no-op tracing.

**How to avoid:**
In Phase 0:
1. Pin ALL services and shared libs to the same
   `micrometer-tracing-bom` version via the Gradle version catalog. Do not let
   individual service `build.gradle.kts` override it.
2. In the `observability` shared lib, configure the `ObservationRegistry` once
   and export it as a Spring auto-configuration so every service uses the same
   sampler probability and exporter config.
3. Do NOT manually register `ServerHttpObservationFilter` — Spring Boot 3.2+
   auto-configures it via `WebHttpHandlerBuilder`.
4. Validate in Phase 1 (not Phase 10): after routing one request through the
   gateway to any downstream stub endpoint, open Zipkin at `:9411` and assert
   a single trace ID spans both the gateway span and the downstream span.
5. Ensure `spring.application.name` is set in each service's `application.yml`
   (Zipkin service name is derived from this). Missing it groups all spans under
   the default name, making Zipkin traces unreadable.

**Warning signs:**
- Gateway log shows `traceId=abc123` but trip-service log for the same request
  shows a different `traceId`.
- Zipkin shows isolated single-service traces rather than multi-hop trees.
- A `ServerHttpObservationFilter` bean is explicitly defined in any
  `@Configuration` class.
- `management.tracing.sampling.probability` differs between services.

**Phase to address:** Phase 0 (scaffolding) for dependency pinning; Phase 1
(gateway) for first validation of cross-service trace continuity.

---

### Pitfall 8: Redis Cache Stampede on Cold Start or After TTL Expiry

**What goes wrong:**
`destination-service` caches city search results in Redis with a 1h TTL and
provider POI results with a 24h TTL. When the Redis instance restarts (or when
the Docker compose stack comes up cold), all cache keys are empty. If N
concurrent requests arrive for the same city search before the first request has
populated the cache, all N requests will miss the cache, all N will hit Postgres
FTS, and all N will write the result back to Redis simultaneously. For provider
calls, all N would call OpenTripMap — quickly exhausting the free-tier quota.

This "cache stampede" (thundering herd) is a distinct failure mode from the
circuit-breaker scenario already covered in R-3.

**How to avoid:**
In Phase 3 and Phase 4:
1. Use a **single-flight / mutex** pattern for cache population: before calling
   the provider or FTS, attempt to acquire a short-lived Redis lock
   (`SET lock:search:{key} 1 NX EX 5`). Only the request that acquires the lock
   populates the cache; all others wait (or return a 503 after timeout). In
   Spring, this can be done with Redisson's `RLock` or a manual Redis `SET NX`.
2. For the provider path: add a **probabilistic early expiry** — when remaining
   TTL drops below 10% of the original TTL, a small probability triggers a
   background refresh before expiry. This prevents a synchronized expiry burst.
3. Alternatively, for the city search path (lower stakes — Postgres is local),
   accept the stampede but ensure the Postgres query is idempotent and fast
   enough that N concurrent identical queries are tolerable (GIN index + LIMIT 5
   makes this acceptable at portfolio scale).
4. In the WireMock test for circuit-open scenarios (Phase 4), add a test that
   sends 5 concurrent requests to a cold cache path and asserts that OpenTripMap
   was called exactly once (not 5 times).

**Warning signs:**
- After `docker compose restart redis`, the first search for a popular city
  produces 5–10 simultaneous OpenTripMap HTTP calls in the log.
- Network tab shows N identical provider requests in-flight at the same time
  during a page load.

**Phase to address:** Phase 3 (city search caching) and Phase 4 (provider
caching).

---

### Pitfall 9: Axios 401 Retry Loop — Infinite Refresh Cycle on Expired Refresh Token

**What goes wrong:**
The Axios interceptor in the frontend is responsible for catching 401 responses,
calling `POST /api/auth/refresh` to obtain a new access token, and replaying the
failed request. If the refresh token itself is expired or has been revoked (e.g.
the user was inactive for >7 days), the refresh call also returns 401. A naive
interceptor implementation re-intercepts its own refresh 401 as a normal 401 and
attempts another refresh — triggering an infinite loop of failed refresh calls
until the browser tab freezes or the backend rate-limiter fires.

A second, subtler race condition: if two parallel requests both receive 401
simultaneously (e.g. after tab wake-up with an expired token), both enter the
interceptor and both call `/api/auth/refresh`. The first succeeds and rotates the
refresh token. The second call uses the (now-invalidated, rotated-away) old token
— per the refresh-token rotation design this causes the ENTIRE chain to be
revoked, logging the user out silently.

**Why it happens:**
Axios interceptors run for every request including the refresh request itself.
Developers add `if (error.config.url === '/api/auth/refresh') return`  but forget
to also skip retry for other known non-retryable 401s (e.g. the login endpoint
itself). The concurrent refresh problem is commonly called the "double-refresh
race" and requires a queuing mechanism.

**How to avoid:**
In Phase 7:
1. Implement a **refresh lock flag** (`isRefreshing: boolean`) and a **failed
   request queue** in the Axios interceptor module:
   ```ts
   let isRefreshing = false;
   let failedQueue: Array<{resolve: (t: string) => void; reject: (e: unknown) => void}> = [];

   axiosInstance.interceptors.response.use(undefined, async (error) => {
     const original = error.config;
     if (error.response?.status !== 401 || original._retry) return Promise.reject(error);
     if (original.url?.includes('/auth/refresh') || original.url?.includes('/auth/login'))
       return Promise.reject(error);  // never retry auth endpoints
     if (isRefreshing) {
       return new Promise((resolve, reject) => failedQueue.push({ resolve, reject }));
     }
     original._retry = true;
     isRefreshing = true;
     try {
       const { data } = await axiosInstance.post('/api/auth/refresh');
       failedQueue.forEach(p => p.resolve(data.accessToken));
       return axiosInstance(original);
     } catch (e) {
       failedQueue.forEach(p => p.reject(e));
       authStore.getState().clearSession();  // wipe Zustand auth state
       queryClient.clear();                  // wipe TanStack Query cache
       return Promise.reject(e);
     } finally {
       isRefreshing = false;
       failedQueue = [];
     }
   });
   ```
2. TanStack Query's default `retry: 3` must be disabled for auth-related queries:
   add `retry: false` to any `useQuery` that calls auth endpoints, or configure
   the `QueryClient` defaults with a `retry` function that returns `false` for
   401 errors.

**Warning signs:**
- The network tab shows `/api/auth/refresh` being called repeatedly in a tight
  loop after the session expires.
- Two simultaneous requests both trigger a refresh and the second causes the user
  to be silently logged out.
- TanStack Query's retry spinner appears on an auth failure page instead of
  redirecting to login.

**Phase to address:** Phase 7 (Frontend: auth + discovery). Must be correct before
Phase 8 adds the itinerary pages that issue multiple parallel queries.

---

### Pitfall 10: Eureka Registration Lag Causes Gateway 503 on `docker compose up`

**What goes wrong:**
When `docker compose up` starts all services simultaneously, each service
registers with Eureka after its Spring context loads. Spring Cloud Gateway
resolves backend routes by service name via Eureka. The gateway starts routing
requests within seconds of startup, but Eureka's default registry refresh cache
(30s) means the gateway may not yet have a live registration for
`destination-service` or `trip-service`. Any request arriving in the first ~60s
(30s for the service to register + 30s for the gateway to fetch the update)
receives a 503 `No instances available for destination-service`.

This is distinct from R-14 (Eureka single-instance failure) — this is the normal
cold-start window, not a fault.

**Why it happens:**
Developers run `docker compose up` and immediately open the browser, hit search,
and see 503 errors. This creates confusion about whether the system is broken or
just slow to start. It also causes Phase 0's acceptance criterion
(`docker compose up` → healthy in <60s) to be falsely failed.

**How to avoid:**
In Phase 0:
1. In `docker-compose.yml`, add a `healthcheck` for Eureka that waits for
   `/actuator/health` to return UP, and use `depends_on: condition:
   service_healthy` on all downstream services and the gateway.
2. Tune Eureka's cache refresh interval in the gateway's `application.yml` for
   dev:
   ```yaml
   eureka:
     client:
       registry-fetch-interval-seconds: 5  # default 30; reduce for dev
     instance:
       lease-renewal-interval-in-seconds: 5
       lease-expiration-duration-in-seconds: 10
   ```
3. Add a `wait-on` or `docker compose wait` step in any script that runs
   integration tests against the live compose stack — do not rely on wall-clock
   sleeps.
4. Document the 30–60s warm-up in the README so reviewers testing the live demo
   know to wait.

**Warning signs:**
- `curl localhost:8080/api/search?q=lon` returns 503 immediately after
  `docker compose up` but succeeds after a 60s wait.
- The gateway log shows `No instances available for [service]` repeatedly during
  the first minute.
- Integration tests that spin up via `docker compose` fail non-deterministically
  in CI based on startup timing.

**Phase to address:** Phase 0 (Monorepo scaffolding) for compose `depends_on`
and Eureka tuning; Phase 1 for validating the routing accepts first request
without delay.

---

### Pitfall 11: Postgres FTS Returns Wrong City Due to Missing Population-Weight Ranking

**What goes wrong:**
Postgres `tsvector` full-text search ranks results by lexeme frequency and
document weight — not by city population. A search for "London" returns results
in order of which row has the highest `ts_rank`, which may be "London, Ontario"
(if it has a longer description in the seeded data) before "London, UK". The
GeoNames `cities-15000` dataset contains ~23,000 cities with a `population`
field; without incorporating it into the ranking, search quality is poor for
ambiguous queries.

**Why it happens:**
Developers use `ORDER BY ts_rank(search_tsv, query) DESC` because that is the
standard Postgres FTS example. The `population` field exists in the seeded data
but isn't used in the ORDER BY. NFR-1 (`GET /api/search?q=lon` returns London
first) will silently fail if the seed contains ambiguous "lon*" entries.

**How to avoid:**
In Phase 3:
1. Store `population` as an integer column in the `cities` table.
2. Add a computed ranking column or use a weighted ORDER BY:
   ```sql
   ORDER BY
     ts_rank(search_tsv, plainto_tsquery('english', :q)) * LOG(population + 1) DESC
   LIMIT 5;
   ```
   Multiplying the lexeme rank by `LOG(population)` biases toward globally
   significant cities without ignoring exact-match relevance.
3. Add the Phase 3 acceptance criterion explicitly: seed includes at least 3
   cities matching "lon" prefix; assert that the first result's `name` is
   "London" and `country_code` is "GB".
4. Store a pre-computed `search_tsv` tsvector column (updated by a Flyway
   migration) rather than computing `to_tsvector()` at query time — this lets
   the GIN index be used and avoids a sequential scan.

**Warning signs:**
- `GET /api/search?q=lon` returns "London, Ontario" or "Longjumeau" before
  "London, UK" in the test suite or manual testing.
- The `cities` table `ORDER BY` clause uses only `ts_rank` with no population
  component.
- GIN index exists but `EXPLAIN ANALYZE` shows a sequential scan because
  `to_tsvector()` is computed inline at query time rather than using the indexed
  column.

**Phase to address:** Phase 3 (Destination service: search).

---

### Pitfall 12: OpenTripMap `radius` Parameter Returns Duplicate POI IDs Across Calls — Cache Key Collision

**What goes wrong:**
`GET /api/destinations?lat=51.5&lng=-0.1&radius=5000` and
`GET /api/destinations?lat=51.5&lng=-0.1&radius=10000` are two different API
calls but their results overlap significantly. If the Redis cache key is
`destinations:{lat}:{lng}:{radius}`, both keys are cached separately, meaning
the same POI is stored twice in `destinations_cache` under different
`provider_ref` entries. When a user adds a destination from the 5km search and
then re-searches at 10km, `destination-service` cannot deduplicate, and
`trip-service` may hold two `destination_ref` values that map to the same physical
place. The FavoritesPage shows the same place twice.

**Why it happens:**
The `destinations_cache` table is keyed by `provider_ref` (the POI's OpenTripMap
ID), which is stable. But the Redis search cache is keyed by `(lat, lng, radius)`
— so the same provider fetch is not reused for overlapping search areas.

**How to avoid:**
In Phase 4:
1. Key the Redis search cache on `(lat, lng, radius)` as designed, but key
   `destinations_cache` on `provider_ref` only. The enrichment step must
   upsert into `destinations_cache` using `ON CONFLICT (provider_ref) DO UPDATE`
   so that a POI fetched via a different radius reuses the existing cached row.
2. In `destination-service`, the search endpoint returns items from
   `destinations_cache` that match the bounding box — not directly from the
   provider. This means provider calls only populate the cache; reads always come
   from the DB layer.
3. Integration test: search radius 5km → search radius 10km → assert
   `destinations_cache` has no duplicate `provider_ref` values.

**Warning signs:**
- `SELECT COUNT(*), provider_ref FROM destinations_cache GROUP BY provider_ref
  HAVING COUNT(*) > 1` returns rows after running two overlapping radius searches.
- The Favorites page shows the same attraction twice for a user who added it from
  different search contexts.

**Phase to address:** Phase 4 (Destination service: providers + cache).

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| HS256 JWT secret shared across all 5 services via single env var | Zero key distribution complexity | Single secret compromise exposes all services; rotation requires redeploying all 5 services simultaneously | v1 only — must be replaced with RS256 + key endpoint before any multi-instance deployment |
| `@Transactional` on controller methods instead of service methods | Quick to write | Transaction spans HTTP serialization; if Jackson throws, rollback fails silently; impossible to unit-test without Spring context | Never |
| Using `spring.jpa.hibernate.ddl-auto=update` in any profile | Schema changes apply without migration | Silently drops and re-creates columns; breaks Flyway history; inconsistent across environments | Never — `validate` for dev, Flyway-managed for all others |
| Hardcoding `localhost:8081` in service config instead of Eureka service names | Works immediately without Eureka running | Breaks in Docker Compose because container names differ from `localhost`; defeats service discovery entirely | Never in compose environment |
| Skipping `@JsonIgnoreProperties(ignoreUnknown = true)` on provider DTOs | Slightly cleaner DTOs | Provider adds a new field → Jackson throws UnrecognizedPropertyException → circuit breaker opens | Never for external API DTOs |
| Storing raw JWT access token in `localStorage` instead of memory | Survives page refresh | XSS vulnerability — any injected script can read the token; design already uses httpOnly cookie for refresh, so access token should live in memory | Never |
| Testing optimistic update correctness only with a single user / single thread | Tests are simpler | Misses the concurrent reorder race condition described in Pitfall 5 | Only in phase stubs before the position algorithm is implemented |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| OpenTripMap | Using `xid` (OpenTripMap internal ID) as `provider_ref` stored in `trip-service` | Use the composite `"otm:{xid}"` string so the namespace is explicit and a future second provider doesn't collide in the same column |
| Foursquare Places v3 | Fetching all fields (including Premium) on every enrichment call | Only request Pro fields; mark Premium fields as optional and handle absence gracefully |
| Mailhog (dev SMTP) | Not setting `spring.mail.properties.mail.smtp.auth=false` for Mailhog (which requires no auth) | Add a `application-dev.yml` override that disables SMTP auth; keep `application-docker.yml` consistent with the compose `mailhog` service host |
| Spring Cloud Gateway + Eureka | Constructing the route predicate with a full URL (`http://auth-service:8081`) instead of a service name | Use `lb://auth-service` (load-balanced URI) so Eureka resolution applies; hardcoded URLs bypass Eureka and break in compose networking |
| Redis (Spring Data) | Using `@Cacheable` with the default key serializer (JDK serialization) for cache keys shared across services | Configure `RedisTemplate` with `StringRedisSerializer` for keys and `GenericJackson2JsonRedisSerializer` for values; otherwise deserialization fails if the reading service's classpath differs |
| dnd-kit keyboard sensor | Never registering `KeyboardSensor` in the `sensors` array | Call `useSensor(KeyboardSensor)` alongside `PointerSensor`; omitting it fails WCAG keyboard navigation requirement (NFR-7) |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Lazy-loading `itinerary_items` inside a loop (N+1 query) | Trip detail page takes >2s; `TRACE` SQL log shows dozens of identical `SELECT FROM itinerary_items WHERE day_id=?` queries | Use `@EntityGraph` or a JOIN FETCH query to load trip → days → items in one query | With >3 days / >10 items |
| `to_tsvector()` computed inline at city search query time | Cold FTS query takes 200–400ms despite GIN index existing | Store `search_tsv` as a generated column in the migration and index it; computed inline disables the index | With >15,000 city rows (the full seed) |
| Redis key scan (`KEYS pattern*`) for cache invalidation | Redis CPU spikes; blocks event loop | Use `SCAN` cursor-based iteration or structured key naming that avoids global scans | Even with a small key set — `KEYS` is O(N) against all keys |
| Leaflet `fitBounds` called on every render of `TripMap` | Map jerks and re-centers on every state update | Call `fitBounds` only on mount and when the set of marker coordinates actually changes (use `useEffect` with a stable dependency array) | With >2 destinations on the map |
| WireMock returning 200 synchronously for all provider calls in tests | Tests pass but never exercise the circuit-breaker timeout path | Add at least one WireMock scenario with a `fixedDelay` of 4000ms (> the 3s read timeout) to confirm Resilience4j's timeout fires | During Phase 4 provider integration testing |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Returning `403 Forbidden` instead of `404 Not Found` for cross-user resource access | Confirms to an attacker that the resource exists; leaks user data shape | Per docs/05-auth-security.md §5: always `findByIdAndUserId` — if not found for that user, throw a not-found exception that maps to 404 |
| Including `userId` in the URL path (`/api/users/{userId}/trips`) and trusting it | User A calls `/api/users/B_id/trips` and reads User B's trips if path param isn't validated against JWT | Never use `userId` as a path parameter for ownership-guarded routes; read it exclusively from the `SecurityContext` populated by `JwtCommonFilter` |
| Logging `Authorization: Bearer ...` headers in gateway request/response logs | Full JWT token in log files; extractable by log-reader | Configure gateway's `AccessLogFilter` to redact the `Authorization` header; use Logback's masking converter for `token`, `secret`, `password` field names |
| Using `bcrypt` cost factor <12 in any non-test profile | Weaker password hashing | Set cost to 12 in `application.yml`; in tests, override with `bcrypt.cost=4` via `application-test.yml` to keep test speed acceptable without touching prod config |
| Returning the full stack trace in error responses in the `docker` profile | Leaks internal class names and Hibernate schema details | Add `server.error.include-stacktrace=never` and `server.error.include-message=never` to `application-docker.yml` and `application-prod.yml` |
| `SameSite=Lax` instead of `SameSite=Strict` on refresh cookie | CSRF vulnerability on top-level navigation from attacker site | Set `SameSite=Strict` in the `ResponseCookie` builder in `auth-service` logout/refresh endpoints |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Confirming trip shrink via a query parameter (`?confirmShorten=true`) forces the frontend to re-issue the PATCH silently | User clicks "confirm" but has no visual confirmation the operation completed; if the re-request fails silently, items are orphaned with no feedback | Surface the 409 response as a modal dialog with an explicit "Remove days and their items" CTA; the confirm re-PATCH should show a loading state and a success/failure toast |
| Showing the dnd-kit drag overlay but not hiding the original item during drag | User sees a ghost AND the original item — confusing which is the "real" item | Use dnd-kit's `DragOverlay` component with `opacity: 0` on the original item via CSS `data-dragging` attribute |
| Persisting note edits on every keystroke (debounced PATCH) without an "unsaved" indicator | Network tab shows a PATCH every 300ms; on slow connections, consecutive debounced patches arrive out of order at the server | PATCH on blur, not on keystroke; show a subtle "Saved" / "Saving..." indicator driven by the TanStack Query mutation state |
| Deferred login flow (FR-10) losing the "add to trip" intent after auth redirect | User logs in, lands on home page, destination is gone from context | Persist the pending `{destinationRef, tripId}` intent to `sessionStorage` before redirecting to login; restore and execute it in `useEffect` on the redirect-back target page |

---

## "Looks Done But Isn't" Checklist

- [ ] **JWT re-validation in downstream services:** `JwtCommonFilter` is wired in
  `SecurityFilterChain` of `trip-service` AND `destination-service`, not just the
  gateway. Verify: `curl -X GET localhost:8082/api/trips -H "X-User-Id: some-uuid"`
  (no JWT) returns 401.

- [ ] **Flyway per-service history tables:** `psql -c "\dt auth.*"` shows
  `auth_flyway_schema_history`; same for `trip` and `destination` schemas.
  No shared `flyway_schema_history` table in `public` schema.

- [ ] **Foursquare nullable fields:** Send a real (or stubbed free-tier-accurate)
  Foursquare response with no `photos` key; assert that `DestinationView.photos`
  is an empty list, not a NullPointerException.

- [ ] **Position collision test:** After 50 random reorders on the same day via
  the service layer, `SELECT COUNT(*) FROM itinerary_items WHERE day_id=:id GROUP
  BY position HAVING COUNT(*)>1` returns zero rows.

- [ ] **Concurrent day-shrink transaction:** Two simultaneous PATCH requests
  shrinking the same trip both complete without leaving orphan `itinerary_items`
  (items with `day_id` pointing to a deleted day).

- [ ] **Axios 401 loop guard:** After the refresh token expires, the browser
  network tab shows exactly one failed `POST /api/auth/refresh` — not a loop.
  The user is redirected to login.

- [ ] **Trace continuity:** A single request through
  `gateway → trip-service` produces one trace ID visible in both services' JSON
  logs. Confirmed in Zipkin before Phase 10 (not discovered in Phase 10).

- [ ] **dnd-kit keyboard sensor:** Tab to an itinerary item, press Space to
  activate drag, press arrow keys to move, press Enter to drop — works without
  a mouse. Required for NFR-7.

- [ ] **Foursquare WireMock stubs match free-tier reality:** WireMock stubs do
  NOT include `photos`/`hours` fields (because the free tier omits them). Stubs
  built from full API docs will mask the Pitfall 6 deserialization bug.

- [ ] **`spring.application.name` set per service:** Each service's
  `application.yml` has a unique `spring.application.name`. Check: Zipkin UI
  service list shows `api-gateway`, `auth-service`, `trip-service`,
  `destination-service` as separate entries.

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| X-User-Id spoofing discovered after deploy | HIGH | Add `JwtCommonFilter` to downstream services; run mandatory security test suite; patch must deploy all 3 downstream services simultaneously |
| Flyway checksum mismatch on startup | LOW | Add `spring.flyway.table=<service>_flyway_schema_history` to application.yml; restart affected service |
| Position collision corruption | MEDIUM | Write a one-time migration script that re-assigns positions using `ROW_NUMBER()` for all days; run as a `V_fix__reorder_positions.sql` Flyway script |
| Day-shrink orphan items | MEDIUM | Run `DELETE FROM itinerary_items WHERE day_id NOT IN (SELECT id FROM itinerary_days)` in a transaction; add the concurrent-lock fix to the service layer |
| Foursquare Premium fields deserialization crash | LOW | Add `@JsonIgnoreProperties(ignoreUnknown = true)` and nullable field types; no data migration required |
| Axios 401 infinite refresh loop in production | LOW | Deploy interceptor fix with failed-queue pattern; users who are stuck in the loop can clear cookies to escape immediately |
| Trace IDs not propagating | LOW | Align `micrometer-tracing-bom` version across all modules in `libs.versions.toml`; redeploy; no data changes needed |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| X-User-Id header spoofing (Pitfall 1) | Phase 1 | `DirectServiceAccessWithoutGatewayReturns401` test passes |
| dnd-kit optimistic update flicker/rollback (Pitfall 2) | Phase 8 | No snap-back visible in Playwright recording; no re-render errors under rapid drag |
| Flyway shared history table (Pitfall 3) | Phase 0 | All 3 services start clean; per-service history table names verified in psql |
| Day materialization partial transaction (Pitfall 4) | Phase 5 | Concurrent shrink test passes; no orphan items in DB after test |
| Position collision under concurrent inserts (Pitfall 5) | Phase 6 | 50-random-reorder test + concurrent reorder test; zero duplicate positions |
| Foursquare Premium fields drift (Pitfall 6) | Phase 4 | Free-tier accurate WireMock stub; NPE test on absent photos/hours field |
| Trace context lost at gateway boundary (Pitfall 7) | Phase 0 + Phase 1 | Single trace ID spans gateway and downstream in Zipkin before Phase 10 |
| Redis cache stampede (Pitfall 8) | Phase 3 + Phase 4 | Concurrent cold-cache test asserts single provider call for N parallel requests |
| Axios 401 infinite refresh loop (Pitfall 9) | Phase 7 | Expired refresh token test: network shows exactly one refresh attempt then redirect |
| Eureka registration lag 503 (Pitfall 10) | Phase 0 | `docker compose up` + immediate test; 503 rate = 0 after healthcheck depends_on |
| FTS wrong city ranking (Pitfall 11) | Phase 3 | `GET /api/search?q=lon` returns "London, UK" as first result with population-weighted ORDER BY |
| POI duplicate cache entries (Pitfall 12) | Phase 4 | Overlapping radius search test; zero duplicate `provider_ref` in destinations_cache |

---

## Sources

- dnd-kit / TanStack Query optimistic update flicker: [GitHub Discussion #1522](https://github.com/clauderic/dnd-kit/discussions/1522), [TanStack Query concurrent optimistic updates](https://tkdodo.eu/blog/concurrent-optimistic-updates-in-react-query)
- Foursquare rate limits and Premium field restrictions: [Foursquare Rate Limits Docs](https://docs.foursquare.com/developer/reference/personalization-apis-rate-limits), [Foursquare Pricing](https://foursquare.com/pricing/)
- Flyway per-service schema history table configuration: [Flyway in Spring Boot Microservices](https://shekhargulati.com/2020/06/24/using-flyway-to-manage-database-migration-in-spring-boot-microservices-application/)
- Spring Cloud Gateway reactive context propagation: [Tracing in Spring Boot 3 WebFlux](https://betterprogramming.pub/tracing-in-spring-boot-3-webflux-d432d0c78d3e), [Spring Boot 3 Observability](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3/)
- Eureka startup race conditions: [Netflix Eureka Issue #1103](https://github.com/Netflix/eureka/issues/1103), [Spring Cloud Netflix Issues](https://github.com/spring-cloud/spring-cloud-netflix/issues/3941)
- Axios JWT 401 retry loop and double-refresh race: [TanStack Query Discussion #3653](https://github.com/TanStack/query/discussions/3653), [Production-Grade React Auth Starter](https://dev.to/hkarimi/building-a-production-grade-react-auth-starter-jwt-refresh-tokens-zustand-tanstack-query-3pk3)
- Postgres FTS ranking and GIN index: [Postgres Full Text Search – The Gnar Company](https://www.thegnar.com/blog/postgres-full-text-search), [Speed Up PG FTS with Persistent TSVectors](https://danielabaron.me/blog/speed-up-pg-fts-with-persistent-ts-vectors/)
- Redis cache stampede prevention: [Cache Stampede Protection in Spring Boot](https://medium.com/@AlexanderObregon/cache-stampede-protection-in-spring-boot-applications-341f87b37649)
- Fractional indexing and position arithmetic pitfalls: [PostgreSQL Collation Trap with Fractional Indexing](https://www.solberg.is/fractional-indexing-gotcha)
- Spring Security filter chain and header injection: [Header Injection Prevention in Spring Boot APIs](https://medium.com/@AlexanderObregon/header-injection-prevention-in-spring-boot-apis-772522ff35d8)

---
*Pitfalls research for: Trip Planner — Spring Boot 3 microservices + React 18*
*Researched: 2026-05-08*
