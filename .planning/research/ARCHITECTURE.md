# Architecture Research

**Domain:** Trip-planning / travel-itinerary web application
**Researched:** 2026-05-08
**Confidence:** HIGH (architecture is locked and documented; research validates the decomposition and surfaces integration patterns)

---

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        React SPA (Vite :5173)                        │
│  TanStack Query (server state)  ·  Zustand (auth/UI state)           │
│  Axios + refresh-interceptor  ·  dnd-kit  ·  Leaflet + OSM          │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ HTTP /api/**
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  api-gateway :8080  (Spring Cloud Gateway)           │
│  JWT signature validation  ·  CORS  ·  Rate limiting (Redis-backed)  │
│  Header injection: X-User-Id, X-User-Email, X-Request-Id            │
└──────────┬──────────────────┬────────────────────┬──────────────────┘
           │ /api/auth/**     │ /api/trips/**       │ /api/search/**
           │                  │ /api/favorites/**   │ /api/destinations/**
           ▼                  ▼                     ▼
┌──────────────────┐  ┌──────────────────┐  ┌────────────────────────┐
│  auth-service    │  │  trip-service    │  │  destination-service   │
│  :8081           │  │  :8082           │  │  :8083                 │
│  schema: auth    │  │  schema: trip    │  │  schema: destination   │
│                  │  │                  │  │                        │
│  Users, tokens   │  │  Trips, days,    │  │  Cities (GeoNames),    │
│  JWT issuance    │  │  items, faves    │  │  POI cache, provider   │
│  SMTP verify     │  │  Position algo   │  │  gateway abstraction   │
└────────┬─────────┘  └────────┬─────────┘  └──────────┬─────────────┘
         │                     │                        │
         └─────────────────────┴────────────────────────┘
                               │
         ┌─────────────────────┼─────────────────────────────────────┐
         ▼                     ▼                                     ▼
  ┌──────────────┐     ┌──────────┐                      ┌──────────────────┐
  │ PostgreSQL 16│     │ Redis 7  │                      │ External APIs:   │
  │ 3 schemas    │     │          │                      │ OpenTripMap      │
  │ 3 DB users   │     │ city FTS │                      │ Foursquare       │
  └──────────────┘     │ POI cache│                      │ Mailhog/Resend   │
                       │ rate-lim │                      └──────────────────┘
                       └──────────┘

  ┌──────────────────┐
  │  eureka-server   │  All 4 services register; gateway resolves by name
  │  :8761           │
  └──────────────────┘

  ┌──────────────────┐
  │  Zipkin :9411    │  Distributed trace aggregation
  └──────────────────┘
```

### Component Boundaries

| Component | Responsibility | Trusts | Communicates With |
|-----------|----------------|--------|-------------------|
| api-gateway | Entry point, JWT validation, routing, CORS, rate limiting | Nothing — validates everything | Eureka (discovery), Redis (rate limit), all 3 services |
| auth-service | User accounts, JWT issuance, email verification, refresh-token rotation | X-User-Id from gateway (defense-in-depth re-validates JWT) | PostgreSQL `auth` schema, Mailhog/Resend SMTP |
| trip-service | Trip CRUD, day materialization, itinerary items, favorites, position arithmetic | X-User-Id from gateway (defense-in-depth re-validates JWT) | PostgreSQL `trip` schema |
| destination-service | City search, POI listing and detail, provider gateway orchestration, POI cache | Gateway-forwarded JWT (stateless — no user ownership checks needed) | PostgreSQL `destination` schema, Redis, OpenTripMap, Foursquare |
| eureka-server | Service registry for intra-cluster name resolution | All registered services | All services register on startup |
| shared libs | jwt-common, api-contracts, error-handling, observability | — | Compiled into each service |

**No service-to-service HTTP calls exist in v1.** Every request flows SPA → gateway → one service. The gateway is the only component that talks to multiple services (via routing, not aggregation).

---

## JWT Propagation: Explicit Trust Model

This is the most security-critical data flow in the system and must be precise.

### Flow (step by step)

```
1. SPA sends:
   Authorization: Bearer <access_token>

2. api-gateway AuthFilter:
   a. Extract token from Authorization header
   b. Verify HS256 signature (shared secret in gateway env var JWT_SECRET)
   c. Check exp claim — return 401 if expired
   d. Extract claims: sub (userId UUID), email
   e. Mutate request:
      - Remove original Authorization header from downstream request
      - Add X-User-Id: <userId>
      - Add X-User-Email: <email>
      - Add X-Request-Id: <UUID> (or propagate W3C traceparent)
   f. Forward to downstream service

3. Downstream service (trip-service / auth-service):
   a. jwt-common lib GatewayContextFilter reads X-User-Id and X-User-Email
   b. Defense-in-depth: also re-validates the Bearer token independently
      (guards against misconfigured or bypassed gateway in dev/test)
   c. Sets SecurityContext with userId principal
   d. Service layer asserts ownership: trip.user_id == SecurityContext.userId
      → 404 if mismatch (not 403; don't leak existence)

4. destination-service:
   - Reads X-User-Id only for rate-limiting context if needed
   - No ownership model; POI data is public
```

### What must NOT happen
- Downstream services must never accept X-User-Id injected by the SPA directly (only gateway can set it)
- Gateway must strip any incoming X-User-Id header from the client before injecting its own
- The JWT secret must never appear in application.yml — only as env var

### Confidence: HIGH
This pattern is validated by OWASP Microservices Security Cheat Sheet and multiple Spring Cloud Gateway reference implementations. The defense-in-depth re-validation at the service level is standard practice when running services on an open Docker network during development.

---

## Validated Decomposition Assessment

The 4-service split (auth / trip / destination / gateway) is well-aligned with how similar-scale travel systems decompose:

| Subdomain | Natural Bounded Context | Coupling Risk | Assessment |
|-----------|------------------------|---------------|------------|
| Identity (auth) | Separate — user management is stable, isolated | Low — no calls to other services | Correct split |
| Planning (trip) | Separate — owns all mutable user data | Low — references destinations by opaque ref, no FK | Correct split |
| Discovery (destination) | Separate — read-heavy, provider-coupled, stateless re: users | Low — no user state, replaces freely | Correct split |
| Gateway | Infrastructure, not domain | N/A | Correct placement |

**Verdict:** The decomposition matches the "decompose by subdomain" DDD pattern and avoids the common mistake of splitting too granularly (e.g., a separate service for favorites or map views). The choice to store `destination_ref` as an opaque `provider:id` string in trip-service (rather than a FK into destination-service's cache) correctly preserves service autonomy. This is the reference-by-id pattern common in microservices literature.

One deliberate design decision worth flagging: `trip.favorites` lives in trip-service rather than destination-service. This is correct — favorites are a user-owned association, not a property of the destination. Destination-service has no user concept.

---

## Architectural Patterns

### Pattern 1: Provider Gateway (Anti-Corruption Layer)

**What:** A single interface (`DestinationGateway`) abstracts all external POI providers (OpenTripMap, Foursquare). The service layer calls the interface; concrete clients implement it. An orchestrating service (`EnrichingDestinationService`) composes both providers — OpenTripMap supplies the POI list; Foursquare enriches it with photos and opening hours.

**When to use:** Any time you integrate multiple external APIs that serve similar data but have different schemas, rate limits, and reliability characteristics. Isolates provider churn from business logic.

**Implementation sketch:**

```java
// Interface — in destination-service, not in shared libs
public interface DestinationGateway {
    List<DestinationSummary> searchNearby(double lat, double lng, int radiusMeters);
    DestinationDetail getDetail(String providerId);
}

// Implementations
@Component("openTripMap")
public class OpenTripMapClient implements DestinationGateway { ... }

@Component("foursquare")
public class FoursquareClient implements DestinationGateway { ... }

// Orchestrator — primary source + enrichment
@Service
public class EnrichingDestinationService {
    // 1. Call OpenTripMap for list
    // 2. For each POI with a Foursquare match, enrich with photos/hours
    // 3. Write result to destinations_cache
    // 4. Return merged DestinationDetail
}
```

**Resilience4j wrapping per client (not per service):** Each `DestinationGateway` implementation wraps its `WebClient` calls with a named circuit breaker (`@CircuitBreaker(name = "openTripMap")`). This means OpenTripMap's circuit state is independent of Foursquare's. When OpenTripMap's circuit is open, the service falls back to cache-only; when Foursquare's circuit is open, photos/hours degrade but the POI list still works.

**Trade-offs:** Adds an interface layer. Worth it here because OpenTripMap and Foursquare have meaningfully different reliability profiles and the fallback behavior differs. Would be overkill if there were only one provider.

---

### Pattern 2: Cache-Aside (Read-Through with DB Persistence)

**What:** destination-service implements a two-level cache:
- **L1 (Redis, 1h TTL):** city search results keyed by `search:(q):(type)`. Pure read cache — no user data.
- **L2 (PostgreSQL `destinations_cache`, 24h staleness):** POI records persisted to DB. On miss, fetch from provider, write to DB, optionally warm Redis. On hit with stale row (`fetched_at < NOW() - 24h`), serve stale and schedule background refresh.

**When to use:** External APIs with free-tier rate limits, where latency matters (NFR-1: 500ms p95) and data changes infrequently (attraction hours/ratings update at most daily).

**Cache key strategy:**

```
City search:     "search:{q}:{type}"                  → Redis TTL 1h
POI list:        "pois:{lat}:{lng}:{radius}"           → Redis TTL 30min
POI detail:      "poi:{provider_ref}"                  → Redis TTL 1h
                 Also: destinations_cache row by provider_ref → DB TTL 24h
```

**Fallback chain on provider failure:**
```
Request → Redis hit? → return
       → Redis miss → destinations_cache hit (fresh)? → return + warm Redis
       → destinations_cache hit (stale)? → return stale + async refresh attempt
       → destinations_cache miss → call provider
         → provider OK? → write DB + warm Redis + return
         → provider circuit open? → return error with providerStatus flag
```

**Trade-offs:** The `destinations_cache` table grows unboundedly in v1 (no eviction). This is acceptable for portfolio scope (~thousands of POIs). In production, add a staleness sweep job. The stale-while-revalidate pattern (serve stale, refresh async) is best-practice for POI data but requires a `@Async` method or Spring's `CompletableFuture` — flag for Phase 4 implementation.

---

### Pattern 3: Gap-Based Position Algorithm (Midpoint Insertion)

**What:** `itinerary_items.position` uses integers spaced by 100 (positions: 100, 200, 300...). Drag-drop reorder computes the midpoint between neighbors and assigns it as the new position. When the gap falls below 2 (positions like 250, 251), a lazy reindex renumbers all items in the day by 100s in a single `UPDATE` batch.

**When to use:** Any ordered list where items reorder frequently via drag-drop, especially when optimistic updates are needed (SPA updates UI immediately, then confirms with backend).

**Algorithm specifics:**

```java
// Append: new item at end
int newPosition = maxExistingPosition + 100;  // first item: 100

// Insert between item A (pos=200) and item B (pos=300)
int newPosition = (200 + 300) / 2;  // = 250

// Move from another day: treat target day as fresh context
// (remove from source day, insert at target position using midpoint)

// Lazy reindex trigger
if (gapToNeighbor < 2) {
    // SELECT all items in day ORDER BY position
    // UPDATE each with 100, 200, 300...
    // Single transaction; one UPDATE per item
}
```

**Why integer gap-spacing over fractional indexing:** Fractional indexing (Figma's approach using lexicographic string keys) is superior for real-time multi-user collaboration because it eliminates the reindex operation entirely. For v1 (single user, no concurrency), integer gap-spacing is simpler, readable in SQL queries, and the lazy reindex is a rare operation (~50 reorders per day worst case before needing it).

**Frontend side:** dnd-kit fires `onDragEnd` with the source index, destination day ID, and neighbor item IDs. The frontend sends a `PATCH /api/trips/{tripId}/items/{itemId}` with `{ dayId, afterItemId, beforeItemId }`. Backend computes the integer midpoint. TanStack Query optimistic update reflects the new order immediately; rollback on 4xx.

---

### Pattern 4: Schema-Per-Service with Shared Postgres Cluster

**What:** A single PostgreSQL 16 instance hosts three schemas (`auth`, `trip`, `destination`). Each service connects with a dedicated DB user that has `ALL ON SCHEMA <its-schema>` and nothing else. Flyway migrations are scoped per service (`flyway.schemas: auth`). No cross-schema foreign keys.

**When to use:** Portfolio/local-dev context where the memory budget (2.5 GB total for all services) prevents running 3 separate DB containers. Preserves the logical ownership model of database-per-service without the operational overhead.

**Implementation invariant:** The `init.sql` runs once at compose startup and creates schemas + users. Per-service Flyway runs `createSchemas: false` (trusts `init.sql`) and uses `defaultSchema`. The `auth_svc` user literally cannot query `trip.*` — the DB enforces the boundary, not just the application layer.

**Trade-offs:**
- Soft isolation vs hard isolation (separate DB servers). Cross-service joins are impossible at the app level but technically possible by a superuser. Acceptable for portfolio; document the limitation.
- Single point of failure: Postgres down = all services down. Acceptable for v1 local-only. In production, use separate instances or PG schemas as a stepping stone toward full separation.
- Flyway `flyway_schema_history` table lives in each service's schema (not shared), so migration state is fully isolated.

---

### Pattern 5: Day Materialization on Date Range Change

**What:** When a trip's `start_date`/`end_date` is set or changed, trip-service runs an idempotent reconciliation that upserts one `itinerary_days` row per date in the range, then deletes rows outside the range.

**When to use:** Any domain where a resource has a derived set of child records tied to a date range (hotel bookings, event schedules, itineraries).

**Algorithm:**

```
Given: existing days D_existing, new range R_new = [start, end]
1. Compute D_add    = R_new - D_existing  → INSERT new days
2. Compute D_remove = D_existing - R_new  → query for items on these days
3. If D_remove has items AND confirmShorten=false:
   → Return 409 Conflict with list of orphaned items
4. If confirmShorten=true OR D_remove is empty:
   → DELETE D_remove days (CASCADE deletes their items)
   → INSERT D_add days
```

**Idempotency:** Safe to call twice — upsert on `(trip_id, day_date)` unique constraint. The `409 Conflict` confirmation gate is the key UX pattern here: never silently delete user data.

---

## Data Flow

### Primary Request Flows

**1. City Search (cold path)**
```
SPA input debounce (300ms)
  → GET /api/search?q=tokyo
  → api-gateway: rate-check, JWT optional (public), forward
  → destination-service SearchController
  → SearchService: Redis.get("search:tokyo:all")
    → Miss: Postgres FTS on cities.search_tsv
    → Write result to Redis (TTL 1h)
  → Return top-5 ranked by population
  → TanStack Query caches for 5min
```

**2. POI List (first request, provider call)**
```
SPA: city selected, lat/lng known
  → GET /api/destinations?lat=35.68&lng=139.69&radius=10000
  → api-gateway: JWT optional (public), forward
  → destination-service DestinationController
  → EnrichingDestinationService:
    → Redis.get("pois:35.68:139.69:10000") → Miss
    → destinations_cache query → Miss (or stale)
    → OpenTripMapClient.searchNearby(...) [Resilience4j CB]
    → FoursquareClient.enrich(poiIds)       [Resilience4j CB]
    → Write merged results to destinations_cache
    → Warm Redis (TTL 30min)
  → Return 20 destinations
```

**3. Add Item to Trip Day**
```
SPA: user clicks "Add to Trip Day 2"
  → POST /api/trips/{tripId}/days/{dayId}/items
     body: { destinationRef: "otm:abc123" }
     header: Authorization: Bearer <token>
  → api-gateway:
     JWT validation → extract userId
     Inject X-User-Id, X-User-Email
  → trip-service ItemController
     jwt-common filter: re-validate token, set SecurityContext
     TripOwnershipGuard: verify trip.user_id == SecurityContext.userId
     → 404 if mismatch
     ItemService:
       → SELECT MAX(position) FROM itinerary_items WHERE day_id = ?
       → INSERT item with position = MAX + 100
       → Return 201 with item DTO
  → SPA TanStack Query: invalidate trip cache, update UI
```

**4. Drag-Drop Reorder (optimistic update)**
```
SPA dnd-kit onDragEnd:
  → Optimistic: mutate TanStack Query cache immediately (reorder items array)
  → PATCH /api/trips/{tripId}/items/{itemId}
     body: { dayId, afterItemId: "uuid-before", beforeItemId: "uuid-after" }
  → trip-service:
     Load neighbor positions from DB
     newPosition = (afterPos + beforePos) / 2
     If gap < 2: trigger lazy reindex for that day
     UPDATE itinerary_items SET position = newPosition, day_id = dayId
     → Return 200
  → On 2xx: TanStack Query confirms (no UI change needed)
  → On 4xx: TanStack Query rollback (restore original order)
```

### Cross-Service Data Reference (no-join pattern)
```
trip.itinerary_items.destination_ref = "otm:12345"
                        ↓ (opaque to trip-service)
destination-service:  GET /api/destinations/otm:12345
                        ↓
Returns full DTO: name, photos, opening_hours, lat/lng
```

The SPA is responsible for resolving `destination_ref` values by calling destination-service. In TripDetailPage, the frontend fetches the trip (gets item list with `destination_ref` strings), then batch-fetches destination details. TanStack Query deduplicates these calls across days.

---

## Build / Deploy Order

The existing 11-phase roadmap in `docs/09-roadmap.md` is sound. The dependency graph is validated below with notes on the rationale:

```
P0 ─→ P1 ─→ P2 ─→ P3 ─→ P4
                 │
                 ├─→ P5 ─→ P6
                 │
                 └─→ P7 (can start after P2; search uses WireMock stubs until P3 is wired)
                          │
                          └─→ P8 (needs P5 + P6 backend fully complete)
                                  │
                                  └─→ P9 ─→ P10
```

### Phase-by-phase build rationale

| Phase | Key Deliverable | Why This Order |
|-------|----------------|----------------|
| **P0** — Monorepo scaffolding | All services boot, register in Eureka, Docker Compose healthy | Infrastructure must exist before any feature work. jwt-common, error-handling, observability libs scaffold here to avoid retrofitting later. |
| **P1** — API Gateway | JWT validation, header injection, routing, rate limiting | Gateway is the security perimeter. Building it before auth-service means it can be tested with synthetic JWTs. Also forces jwt-common lib to be real from day 1. |
| **P2** — Auth service | Full signup → verify → login → refresh → logout | JWT issuance must exist before any protected endpoint is tested end-to-end. Auth is also the highest-risk service (security surface); shipping it early means more time for testing. |
| **P3** — Destination search | City/country search with GeoNames seed + Redis cache | Foundational data for everything else. destination-service's cities are used by the frontend before trips exist. GeoNames seed in Flyway V3 — run once and done. Also proves the cache-aside pattern works before P4 adds provider complexity. |
| **P4** — Destination providers + POI cache | OpenTripMap + Foursquare + circuit breakers + destinations_cache | Builds on P3's infrastructure. Provider gateway pattern + Resilience4j. Can run in parallel with P5 (no dependency between trip-service and destination-service data). |
| **P5** — Trip CRUD + day materialization | Trip lifecycle, date range, day materialization, 409 shrink guard | Core planning data model. Depends only on auth (for JWT). Day materialization is the trickiest business logic in trip-service — ship it isolated before adding items. |
| **P6** — Itinerary items + favorites | Position algorithm, drag-drop backend, notes, favorites | Depends on P5 (itinerary_days must exist). Position algorithm and cross-day move are the complex operations; ship separately from trip CRUD to keep each phase small. |
| **P7** — Frontend: auth + discovery | SPA routing, auth pages, search UI, destination detail | First frontend phase. Can start after P2 (backend auth) and P3 (search). Uses WireMock/MSW stubs for destination detail until P4 is live. Axios interceptors for JWT refresh go here — foundational for all subsequent frontend phases. |
| **P8** — Frontend: trip planner | Itinerary editor, dnd-kit drag-drop, map view, favorites page | Depends on P5+P6 (backend items API) and P7 (auth/routing). The most complex frontend phase. dnd-kit setup and optimistic update wiring should be done last in the phase after static CRUD works. |
| **P9** — Polish | Loading skeletons, error boundaries, a11y, mobile | Correctness before quality. Polish only makes sense on a feature-complete base. |
| **P10** — Observability + perf | Zipkin traces, Micrometer metrics, k6 load test, final security audit | Must be last — validates the system under realistic load and traces the full call path. k6 against P3/P4 (search + POI) is the key SLA proof. |

### Parallelism opportunities (single developer context)

These phases have no technical dependency between them and can switch context freely:

- **P3 and P5** can proceed in parallel after P2 (different services, different schemas)
- **P4** can proceed after P3 is done (same service, but pure additions)
- **P7** can start as soon as P2 backend is working; no need to wait for P4 — use WireMock

---

## Scaling Considerations

This is a portfolio project targeting local Docker Compose, not production scale. The architecture choices reflect that. The notes below explain what would change at each scale tier.

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 0-100 users (current target) | Single Postgres, single Redis, single instances of each service. Docker Compose. No horizontal scaling needed. |
| 1k-10k users | destination-service is the first bottleneck (external API rate limits + DB reads). Scale out destination-service replicas behind a load balancer. Redis cluster for shared cache across replicas. Separate Postgres instances per service. |
| 10k-100k users | auth-service refresh-token volume may require Redis-backed session store instead of DB. trip-service becomes write-heavy under concurrent itinerary edits; consider read replicas. Message broker (RabbitMQ/Kafka) for user-deletion cascades. |
| 100k+ users | Fractional indexing over integer gap-spacing for position (eliminates reindex contention). Separate DB instances per service as hard isolation. CDN for static frontend assets. Consider replacing Eureka with Kubernetes service discovery. |

### First bottleneck: External API rate limits (OpenTripMap free tier)

OpenTripMap free tier has strict rate limits (~1000 req/day on some endpoints). The `destinations_cache` write-through pattern directly mitigates this: once a POI is cached, it never hits the provider again for 24h. The Redis L1 cache further reduces DB reads. If this bottleneck hits before destinations_cache warms up, add a seeding script that pre-populates popular cities on startup.

---

## Anti-Patterns

### Anti-Pattern 1: Service Mesh Coupling via Shared Database User

**What people do:** All services connect to Postgres as the same superuser, or one service runs queries that touch another service's schema.

**Why it's wrong:** Defeats service isolation. Any service can corrupt another's data. Makes schema evolution a coordination nightmare. Violates the core constraint of schema-per-service.

**Do this instead:** Per-service DB users with GRANT limited to their own schema, as specified in `infra/postgres/init.sql`. Enforce at DB level, not just at application level.

---

### Anti-Pattern 2: Calling Auth-Service to Validate User Identity

**What people do:** trip-service calls `GET /api/auth/users/{id}` to verify the user exists before processing a trip request.

**Why it's wrong:** Creates synchronous coupling between trip-service and auth-service. auth-service becomes a dependency of every protected endpoint. If auth-service is down, trip-service is down.

**Do this instead:** JWT is proof of identity. The gateway validated the signature. trip-service trusts the `X-User-Id` header (plus its own defense-in-depth JWT re-validation). If the user was deleted after token issuance, the token will expire in 15 minutes anyway. This is the standard microservices pattern for identity propagation.

---

### Anti-Pattern 3: Hydrating Destination Details Inside Trip-Service

**What people do:** trip-service calls destination-service to resolve `destination_ref` into full destination data before returning the trip response. Returns a single merged DTO.

**Why it's wrong:** Creates synchronous coupling. If destination-service is slow or circuit-open, trip CRUD becomes slow. Violates the principle of service self-containment. Also means trip-service must understand the destination data model.

**Do this instead:** trip-service returns items with opaque `destination_ref` strings. The SPA resolves them by calling destination-service separately. TanStack Query deduplicates: if the same `destination_ref` appears in multiple items/trips, it is fetched once and cached. This is the "composition at the UI layer" pattern.

---

### Anti-Pattern 4: Putting Rate Limiting Only on the Gateway

**What people do:** Add a Redis rate limiter on the gateway for login, then assume downstream services are protected.

**Why it's wrong:** If the gateway is bypassed in dev (Docker network), or if a misconfiguration exposes service ports directly, the rate limit is gone. Also, the gateway rate limiter limits by IP, but the login brute-force threat is per email+IP combination.

**Do this instead:** Gateway rate limiting is the primary line of defense (5 req/15min per IP on `/api/auth/login`). auth-service independently tracks failed attempts per email and enforces a secondary lockout at the service layer. The `observability` lib emits a metric on lockout events.

---

### Anti-Pattern 5: Storing Position as an Auto-Increment Column

**What people do:** Use an auto-increment `position` column and update all sibling rows on every reorder (UPDATE WHERE position > X).

**Why it's wrong:** N-item reorder requires N-1 UPDATE statements. Produces lock contention on large days. Optimistic update on the frontend conflicts with the batch update confirmation.

**Do this instead:** Gap-based integer positions (100, 200, 300) with midpoint insertion. Only one row is updated per drag-drop. Lazy reindex (runs once per day when gap < 2) is a bounded N-update only triggered rarely.

---

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| OpenTripMap | `DestinationGateway` implementation using WebClient; Resilience4j CB name `"openTripMap"` | Free tier: check `xid` parameter for detail requests. Rate limit: buffer with 24h cache before first real hit. |
| Foursquare | `DestinationGateway` implementation using WebClient; CB name `"foursquare"` | Used for enrichment (photos, hours) only. If Foursquare CB is open, return OpenTripMap data without enrichment — partial degradation acceptable. |
| Mailhog (dev) / Resend (prod) | Spring Boot Mail (`JavaMailSender`); single `@Service EmailService` in auth-service | SMTP only; no async in v1. If SMTP fails, signup returns 503 with retryable error code. |
| GeoNames `cities15000.txt` | Flyway seed migration V3; COPY from TSV file in Docker volume | Seeded once. Never call GeoNames API at runtime in v1. |
| OpenStreetMap tiles | Leaflet default tile layer URL in frontend | No API key. No backend involvement. Frontend-only. |
| Zipkin | Micrometer Tracing OTEL exporter → Zipkin HTTP endpoint; configured via `observability` shared lib | All 4 services + gateway. W3C `traceparent` propagated in HTTP headers. |

### Internal Boundaries

| Boundary | Communication | Protocol | Notes |
|----------|---------------|----------|-------|
| SPA → api-gateway | HTTP/JSON | REST | All /api/** traffic. JWT in Authorization header. |
| api-gateway → auth-service | HTTP/JSON | REST via Eureka lb://auth-service | Route: /api/auth/** |
| api-gateway → trip-service | HTTP/JSON | REST via Eureka lb://trip-service | Routes: /api/trips/**, /api/favorites/** |
| api-gateway → destination-service | HTTP/JSON | REST via Eureka lb://destination-service | Routes: /api/search/**, /api/destinations/** |
| Services → PostgreSQL | JDBC (HikariCP) | Per-service connection pool, size 10 | Each service uses its own DB user and schema |
| Services → Redis | Spring Data Redis (Lettuce) | destination-service: POI cache + city search; gateway: rate limiting | Separate key namespaces by service |
| Services → Eureka | Spring Cloud Netflix Eureka client | Heartbeat every 30s. Gateway uses `lb://` scheme for routing | eureka-server must start before other services in compose |
| Services → Zipkin | Micrometer OTEL exporter (HTTP, not gRPC) | All services push spans to Zipkin :9411/api/v2/spans | Trace context propagated via W3C headers |

---

## Sources

- Spring Cloud Gateway JWT filter pattern: [Securing Services with Spring Cloud Gateway — Spring.io](https://spring.io/blog/2019/08/16/securing-services-with-spring-cloud-gateway/)
- JWT defense-in-depth at service layer: [OWASP Microservices Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Microservices_Security_Cheat_Sheet.html)
- JWT token propagation patterns: [Tokens At The Microservices Context Boundary — FusionAuth](https://fusionauth.io/articles/tokens/tokens-microservices-boundaries)
- Cache-aside pattern with Redis: [Cache-Aside Pattern with Redis — Redis.io](https://redis.io/tutorials/howtos/solutions/microservices/caching/)
- Gap-based / fractional indexing for drag-drop: [Reordering Part 2: Tables and Fractional Indexing — Steve Ruiz](https://www.steveruiz.me/posts/reordering-fractional-indices)
- Fractional indexing explained: [Fractional Indexing Explained — hollos.dev](https://hollos.dev/blog/fractional-indexing-a-solution-to-sorting/)
- Flyway multiple schemas in Spring Boot: [Multiple Databases With Flyway — Baeldung](https://www.baeldung.com/spring-boot-flyway-multiple-databases)
- Schema-per-service Flyway handling: [Handling multiple schemas — Redgate](https://www.red-gate.com/blog/handling-multiple-schemas-in-the-same-database-with-flyway/)
- DDD subdomain decomposition: [Decomposition of Microservices Architecture — microservices.io](https://microservices.io/patterns/microservices.html)
- Circuit breaker with Resilience4j + WebClient: [Spring Cloud Circuit Breaker — spring.io](https://spring.io/projects/spring-cloud-circuitbreaker/)

---
*Architecture research for: trip-planning / travel-itinerary web application (java-lab portfolio)*
*Researched: 2026-05-08*
