# Project Research Summary

**Project:** Trip Planner
**Domain:** Travel itinerary web application — Spring Boot microservices + React SPA
**Researched:** 2026-05-08
**Confidence:** MEDIUM-HIGH

---

## Executive Summary

This is a portfolio-grade travel itinerary planner demonstrating Spring Boot microservices, JWT-authenticated REST APIs, distributed tracing, and production-quality React. The design package in `docs/` is well-formed and covers the right scope — 22 functional requirements, a 4-service decomposition (api-gateway / auth-service / trip-service / destination-service), and an 11-phase roadmap. Research validated every major architectural decision and found the feature set competitive with Wanderlog's free-tier core while remaining deliberately narrow enough to build solo in focused phases.

The single most important research outcome is that the locked stack has several version pins that are already out of date or carry active CVEs as of May 2026. Spring Boot 3.3.x reached EOL on June 30, 2025; Axios < 1.15.0 has two unpatched CVEs; react-leaflet v5 requires React 19 which is incompatible with the locked React 18 and Tailwind v3 choices; Flyway 10 needs an explicit `flyway-database-postgresql` dependency that is not pulled transitively. These must be corrected in `docs/02-architecture.md` and `gradle/libs.versions.toml` before Phase 0 scaffolding begins. See **Required Updates to docs/** below for the full list.

The 12 pitfalls identified cover every phase from P0 through P8. Three are critical-severity: X-User-Id header spoofing via direct downstream port access (must be addressed in Phase 1, not deferred), Flyway shared history table collision at startup (must be set correctly in Phase 0), and the dnd-kit + TanStack Query optimistic update pattern that requires a specific no-invalidation-on-success shape to avoid visible snap-back flicker. The other pitfalls are concrete and preventable with the explicit code patterns documented in PITFALLS.md.

---

## Key Findings

### Recommended Stack

The locked stack is validated as the correct choice for 2026, with version corrections. Java 21 LTS + Spring Boot **3.5.x** (not 3.3.x) + Spring Cloud **2024.0.x** is the supported chain; all three Spring Boot 3.x minor versions before 3.5 are EOL or reach EOL by December 2025. On the frontend, React 18.3.x + Tailwind 3.4.x + react-leaflet **4.2.x** is the stable combination; jumping to react-leaflet v5 or Tailwind v4 would require React 19, which in turn breaks the dnd-kit 6.x stable API and the shadcn/ui v3-compatible component generation. The Zod v3 pin is intentional — Zod v4 has breaking API changes that would create friction during Phase 7/8 form work; plan a v4 migration for v2. jjwt should be pinned to 0.13.0 (latest), and for v2 a migration to Spring Security's native Nimbus JOSE support is worth planning since jjwt activity has slowed.

**Core technologies (corrected pins):**

**Backend**
- Java 21 LTS: virtual threads GA, LTS until 2031 — no change needed
- Spring Boot **3.5.x** (was 3.3.x, EOL Jun 2025): upgrade to 3.5.14; low-friction, same Spring Framework 6 base
- Spring Cloud **2024.0.x** / Gateway **4.2.x**: release train must match Spring Boot minor; resolves CVE-2025-41235, CVE-2025-41253
- Flyway 10 + explicit `org.flywaydb:flyway-database-postgresql`: missing this dep causes "Unsupported Database: PostgreSQL 16.x" at startup
- jjwt **0.13.0** (was 0.12.x): latest stable, safe upgrade, decompression leak fix included
- Resilience4j 2.4.0, Testcontainers 1.21.x, WireMock Spring Boot 3.9.0: all stable, no breaking changes

**Frontend**
- React **18.3.x**: stay on 18; React 19 is available but incompatible with dnd-kit 6.x stable API and requires react-leaflet v5
- react-leaflet **4.2.x** (pin required): v5 requires React 19 as peer dep — do NOT upgrade
- Axios **1.16.0** (was 1.x): must be >= 1.15.0; CVE-2025-62718 and CVE-2026-40175 affect all earlier versions
- Zod **3.24.x** (stay on v3): v4 breaks `z.string().email()` -> `z.email()`, `z.record()` signature — plan v4 migration post-v1
- dnd-kit `@dnd-kit/core` **6.x** + `@dnd-kit/sortable`: do NOT use `@dnd-kit/react` (pre-1.0, unstable API)
- Tailwind CSS **3.4.x**: v4 (Jan 2025) has CSS-first config incompatible with v3; shadcn/ui CLI now defaults to v4 for new installs — use v3 flag explicitly
- Vite **6.x** (advisory for greenfield), Zustand **5.0.x**, React Router **7.x** (advisory): all are the current stable versions for new projects
- Vitest **3.x** (compatible with both Vite 5 and 6)

### Expected Features

Research confirms the 22 FRs map cleanly to the table-stakes layer of the competitive landscape. The v1 scope matches or exceeds Wanderlog's free-tier core and intentionally omits features requiring paid APIs (AI generation, routing) or separate product surfaces (booking import, collaboration, budget). The feature set is complete for portfolio purposes — no further reduction is recommended.

**Must have (table stakes):**
- Destination and attraction search — entry point to every competing product
- Attraction detail view with photos, hours, and address — absence makes the product feel like a stub
- Day-by-day itinerary structure — users think in "Day 1, Day 2"; this is the core mental model
- Add/remove destinations from days — basic CRUD; without this it is a viewer not a planner
- Map view of trip — spatial overview is a core mental model in Wanderlog and Sygic
- Email + password authentication with email verification — gates all write operations
- Persistent sessions and saved trips — users plan over multiple sessions

**Should have (differentiators in v1):**
- Drag-drop reorder within day and between days (FR-16, FR-17) — elevates above list tools; demonstrates dnd-kit + optimistic UI
- Optional time slot per item (FR-18) — differentiates from unordered lists
- Per-item notes (FR-20) — personal context layer present in Wanderlog
- Favorites page (FR-21) — cross-session wishlist workflow
- Login-deferred "add to trip" flow (FR-10) — explicit UX differentiator that reduces conversion friction
- Provider circuit breaker with degraded-mode UI indicator (NFR-3) — strong portfolio reviewer signal
- W3C trace context + structured JSON logs (NFR-9) — demonstrates microservices operational maturity

**Defer to v2+:**
- Real-time multi-user collaboration — requires CRDTs or pessimistic locking; out of portfolio scope
- AI itinerary generation — requires paid LLM API budget
- OAuth (Google/GitHub) login — Spring Authorization Server overhead; not worth it for email+password v1
- Weather forecast, travel time, PDF/ics export, PWA — all valid v2 additions with clear free-tier paths

### Architecture Approach

The 4-service decomposition (auth / trip / destination / gateway) is validated as correct by DDD subdomain decomposition principles and the OWASP Microservices Security Cheat Sheet. The trust model — gateway validates JWT, strips the incoming `X-User-Id` header, injects its own from JWT claims, and downstream services re-validate the JWT independently as defense in depth — is the reference pattern for this style of architecture. Five key algorithmic patterns are well-documented in ARCHITECTURE.md: provider gateway anti-corruption layer (Resilience4j per-provider circuit breakers), cache-aside with two levels (Redis L1 + PostgreSQL L2 with stale-while-revalidate), gap-based integer position algorithm with midpoint insertion, schema-per-service with shared Postgres cluster using per-service DB users, and day materialization with idempotent reconciliation and 409 confirmation gate.

**Major components:**
1. **api-gateway** — JWT validation, header injection (`X-User-Id`/`X-User-Email`/`X-Request-Id`), CORS, Redis-backed rate limiting; security perimeter for the entire system
2. **auth-service** — user accounts, JWT issuance (HS256, 15min access + 7-day refresh rotation), email verification via SMTP, bcrypt cost 12
3. **trip-service** — trip CRUD, day materialization, itinerary items with gap-100 position algorithm, favorites; reads `X-User-Id` from gateway + independent JWT re-validation
4. **destination-service** — city FTS (GeoNames seed + Redis cache), POI list + detail via OpenTripMap/Foursquare with Resilience4j circuit breakers, two-level cache (Redis 1h / PostgreSQL 24h)
5. **eureka-server** — service registry; gateway resolves all routes by service name via `lb://` URIs
6. **Frontend (React SPA)** — TanStack Query for server state, Zustand for auth/UI state, dnd-kit for drag-drop, Leaflet/OSM for map, Axios with 401 refresh interceptor

### Critical Pitfalls

1. **X-User-Id header spoofing via direct downstream port access (Pitfall 1)** — Wire `JwtCommonFilter` into trip-service and destination-service in Phase 1 (before real auth data exists); the filter must reject any request carrying `X-User-Id` without a matching valid `Authorization: Bearer` header; add `DirectServiceAccessWithoutGatewayReturns401` integration test as a mandatory gate
2. **Flyway shared history table collision at startup (Pitfall 3)** — Set `spring.flyway.table=<service>_flyway_schema_history` (e.g. `auth_flyway_schema_history`) in every service's `application.yml` in Phase 0; the default `flyway_schema_history` table causes checksum mismatch when multiple services share the same Postgres instance
3. **dnd-kit + TanStack Query optimistic update flicker (Pitfall 2)** — Use a local ephemeral order `useState` for dnd-kit rendering; on `onDragEnd`, call `queryClient.setQueryData` in `onMutate`, but do NOT call `invalidateQueries` on success — only on error for rollback; implement a `isRefreshing` flag + failed-queue pattern to prevent concurrent drag mutations racing
4. **Foursquare free-tier Premium fields silently absent (Pitfall 6)** — Photos and opening hours are Premium fields not returned on the free tier; annotate all Foursquare DTOs with `@JsonIgnoreProperties(ignoreUnknown = true)` and make `photos`/`hours` nullable; WireMock stubs must reflect the actual free-tier response (no `photos`/`hours` keys), not the full documented schema
5. **Axios 401 infinite refresh loop on expired refresh token (Pitfall 9)** — Implement `isRefreshing` flag + `failedQueue` pattern in the Axios interceptor (see PITFALLS.md Pitfall 9 for complete TypeScript snippet); never retry calls to `/auth/refresh` or `/auth/login`; disable TanStack Query's default `retry: 3` for auth-related queries

---

## Implications for Roadmap

The existing 11-phase roadmap in `docs/09-roadmap.md` is structurally correct. The dependency graph is validated and the phase ordering is optimal. No structural changes are recommended. The implications below are additive: acceptance criteria to add, phase-specific patterns to apply, and pitfalls to proactively address within each phase.

### Phase 0: Monorepo Scaffolding
**Rationale:** Infrastructure before features; pitfalls here cause cascade failures in every subsequent phase
**Delivers:** All services boot, register in Eureka, docker compose healthy
**Must address:**
- Use Spring Boot **3.5.x** and Spring Cloud **2024.0.x** in `libs.versions.toml` — not 3.3.x as in current `docs/02-architecture.md`
- Add `org.flywaydb:flyway-database-postgresql` explicitly to each service's `build.gradle.kts`
- Set per-service Flyway history table names (`auth_flyway_schema_history`, etc.) in `application.yml` for all three services
- Pin `micrometer-tracing-bom` once in the Gradle version catalog; do NOT let individual service build files override it
- Set `spring.application.name` in each service's `application.yml` — Zipkin derives service name from this
- Do NOT manually register `ServerHttpObservationFilter` (deprecated in Spring Boot 3.2; auto-configured via `WebHttpHandlerBuilder`)
- Add Eureka `registry-fetch-interval-seconds: 5` and `lease-renewal-interval-in-seconds: 5` in gateway config for dev (avoids 60s cold-start 503 window)
- Use `docker compose depends_on: condition: service_healthy` for all services depending on eureka-server

**Avoids:** Pitfall 3 (Flyway checksum mismatch), Pitfall 7 (trace context lost at gateway), Pitfall 10 (Eureka registration lag)

### Phase 1: API Gateway
**Rationale:** Security perimeter before any protected data exists; JWT trust model must be correct from day one
**Delivers:** JWT validation, header injection, rate limiting, CORS, routing
**Must address:**
- Wire `JwtCommonFilter` into downstream services (trip-service, destination-service) even though they have no real endpoints yet — this is the Phase 1 critical security gate
- The filter must derive `currentUserId` from the JWT `sub` claim, never from `X-User-Id` alone
- Expose downstream service ports bound to `127.0.0.1` only in docker-compose; add note that production must not publish these ports
- Add mandatory integration test: `DirectServiceAccessWithoutGatewayReturns401`
- Validate Zipkin trace continuity in Phase 1 (not deferred to Phase 10): one request through gateway to a downstream stub should show a single trace ID in both service logs and Zipkin UI

**Avoids:** Pitfall 1 (X-User-Id spoofing), Pitfall 7 (trace continuity)
**Research flag:** Standard patterns — no additional research phase needed

### Phase 2: Auth Service
**Rationale:** JWT issuance must exist before any protected endpoint can be tested end-to-end; auth is the highest-risk service
**Delivers:** Signup, email verify, login, refresh-token rotation, logout
**Must address:**
- Use jjwt **0.13.0** (not 0.12.x) — includes decompression leak fix
- Store raw JWT access token in memory (Zustand), never in `localStorage`; refresh token in `httpOnly` cookie with `SameSite=Strict`
- bcrypt cost factor 12 in all non-test profiles; override with cost 4 in `application-test.yml`
- All 8 mandatory security integration tests must pass before Phase 2 is marked complete

**Research flag:** Well-documented patterns — no additional research phase needed

### Phase 3: Destination Service (Search)
**Rationale:** City data is foundational; proven before adding provider complexity in Phase 4
**Delivers:** GeoNames seed, city FTS with Redis cache, `/api/search` endpoint at NFR-1 SLA
**Must address:**
- Population-weighted ORDER BY: `ts_rank(search_tsv, query) * LOG(population + 1) DESC` — plain `ts_rank` returns "London, Ontario" before "London, UK" (Pitfall 11)
- Store `search_tsv` as a generated/pre-computed column; computing `to_tsvector()` at query time disables the GIN index on 23k rows
- Add Phase 3 acceptance criterion explicitly: `GET /api/search?q=lon` returns London (GB) as first result
- Implement single-flight Redis lock (`SET lock:search:{key} 1 NX EX 5`) for cache population to prevent stampede on cold start

**Avoids:** Pitfall 8 (cache stampede), Pitfall 11 (wrong FTS ranking)
**Research flag:** Well-documented patterns — no additional research phase needed

### Phase 4: Destination Service (Providers + Cache)
**Rationale:** Builds on Phase 3 infrastructure; provider complexity isolated from city search
**Delivers:** OpenTripMap + Foursquare integration, Resilience4j circuit breakers, destinations_cache
**Must address:**
- Foursquare free-tier: photos and hours are Premium fields — annotate DTOs `@JsonIgnoreProperties(ignoreUnknown = true)`; make `photos`/`hours` nullable; WireMock stubs must NOT include these fields
- Use `ON CONFLICT (provider_ref) DO UPDATE` for destinations_cache upsert to prevent duplicate POI rows from overlapping radius searches (Pitfall 12)
- Add single-flight lock for provider calls to prevent cache stampede (Pitfall 8)
- Add WireMock scenario with 4000ms `fixedDelay` to confirm Resilience4j timeout fires (circuit breaker test discipline)
- Capture WireMock stubs from actual free-tier API response, not the full documented schema

**Avoids:** Pitfall 6 (Foursquare Premium fields), Pitfall 8 (provider stampede), Pitfall 12 (duplicate cache entries)
**Research flag:** Foursquare free-tier field availability is a known gap — validate against a real free-tier API call during Phase 4 development before writing WireMock stubs

### Phase 5: Trip Service (Trips + Days)
**Rationale:** Core planning data model; day materialization is the most complex business logic and should be isolated before adding items
**Delivers:** Trip CRUD, date range setting, idempotent day materialization, 409 shrink confirmation
**Must address:**
- Place ALL of materialization — confirmation count check, insert new days, delete removed days + cascade items — inside a single `@Transactional(isolation = REPEATABLE_READ)` service method
- Never call `@Transactional` methods from within the same bean; split to a separate Spring bean for proxy interception
- Cascade deletion via `DELETE FROM itinerary_items WHERE day_id IN (...)` in the same transaction, NOT via JPA `CascadeType.REMOVE`
- Add concurrent shrink integration test: two simultaneous PATCH requests on the same trip must not produce orphan `itinerary_items`

**Avoids:** Pitfall 4 (day materialization partial transaction)
**Research flag:** Standard patterns — no additional research phase needed

### Phase 6: Trip Service (Itinerary Items + Favorites)
**Rationale:** Depends on Phase 5 itinerary_days; position algorithm and cross-day move are isolated here
**Delivers:** Items CRUD, gap-100 position algorithm, drag-drop backend, notes, favorites
**Must address:**
- Store `position` as `BIGINT` (not `INT`) to push integer overflow further out
- Use `SELECT ... FOR UPDATE` on the parent `itinerary_days` row before reading/writing positions, to serialize concurrent reorder operations
- Implement reindex as a single SQL window function UPDATE (see PITFALLS.md Pitfall 5 for exact SQL)
- 50-random-reorder test is necessary but not sufficient — add a concurrent test: two threads each issue 25 reorders on the same day simultaneously

**Avoids:** Pitfall 5 (position collision under concurrent inserts)
**Research flag:** Well-documented patterns — no additional research phase needed

### Phase 7: Frontend (Auth + Discovery)
**Rationale:** First frontend phase; auth interceptor pattern is foundational for all subsequent frontend work
**Delivers:** SPA routing, auth pages, search/discovery UI, Axios JWT interceptor
**Must address:**
- Implement `isRefreshing` flag + `failedQueue` pattern in Axios interceptor — never retry `/auth/refresh` or `/auth/login` endpoints; complete TypeScript implementation is in PITFALLS.md Pitfall 9
- Disable TanStack Query's default `retry: 3` for auth-related queries (return `false` for 401 errors in the retry function)
- Use react-leaflet **4.2.x** (pin in package.json); do NOT upgrade to v5
- Use `@dnd-kit/core` **6.x** + `@dnd-kit/sortable`; do NOT use `@dnd-kit/react` (pre-1.0, unstable)
- Use Axios **>= 1.15.0** (CVE-2025-62718, CVE-2026-40175)
- Zod **3.x** (stay on v3; v4 migration is a post-v1 task)

**Avoids:** Pitfall 9 (Axios 401 infinite refresh loop)
**Research flag:** Well-documented patterns — no additional research phase needed

### Phase 8: Frontend (Trip Planner)
**Rationale:** Most complex frontend phase; depends on Phase 5+6 backend and Phase 7 auth/routing
**Delivers:** Itinerary editor with dnd-kit drag-drop, map view, favorites page
**Must address:**
- Maintain local ephemeral order state (`useState`) inside `ItineraryBoard` for the drag session; initialize from query cache, discard after drag resolves
- On `onDragEnd`: call `queryClient.setQueryData` in `onMutate`, do NOT call `invalidateQueries` on success — only on error for rollback (see PITFALLS.md Pitfall 2 for exact TypeScript shape)
- Debounce/queue concurrent drag mutations: second drag must `cancelQueries` before proceeding
- Use dnd-kit's `DragOverlay` component; set `opacity: 0` on the original item via CSS `data-dragging` attribute
- Register `KeyboardSensor` alongside `PointerSensor` (WCAG keyboard navigation — NFR-7)
- `fitBounds` on `TripMap`: call only on mount and when marker coordinates actually change; calling on every render causes jerk
- Deferred login flow: persist pending `{destinationRef, tripId}` intent to `sessionStorage` before redirecting to login; restore and execute in `useEffect` on redirect-back target
- Note edits: PATCH on blur, not on keystroke; show "Saved"/"Saving..." indicator from TanStack Query mutation state

**Avoids:** Pitfall 2 (dnd-kit optimistic update flicker)
**Research flag:** The exact dnd-kit + TanStack Query optimistic update pattern is non-obvious — review PITFALLS.md Pitfall 2 before writing any dnd-kit code

### Phase 9: Polish
**Rationale:** Correctness before quality; polish on a feature-complete base
**Delivers:** Loading skeletons, error boundaries, a11y pass, mobile responsive, toast system
**Research flag:** Standard UI patterns — no additional research phase needed

### Phase 10: Observability + Performance Hardening
**Rationale:** Final validation; traces the full call path under realistic load
**Delivers:** Zipkin tracing confirmed, Micrometer metrics, k6 load test, final security audit
**Note:** Zipkin trace continuity validation belongs in Phase 1 (first cross-service request), not discovered here. Phase 10 is for k6 load testing and coverage confirmation.
**Research flag:** Standard patterns — no additional research phase needed

### Phase Ordering Rationale

- The existing P0->P1->P2->P3->P4, P2->P5->P6, P2->P7->P8, P8->P9->P10 dependency graph is validated and should not change
- P3 and P5 can proceed in parallel after P2 (different services, different schemas, no dependency)
- P7 can start as soon as P2 backend is working; use WireMock/MSW stubs for destination endpoints until P4 is live
- Security work (Pitfalls 1, 4, 5, 9) is distributed across phases P0-P8 to avoid a large security retrofit sprint at the end
- Performance work (Pitfalls 8, 11) is addressed in P3/P4 during first implementation, not deferred to P10

### Research Flags

**Needs validation during implementation:**
- Phase 4: Foursquare free-tier field availability — validate actual free-tier response against DTO before writing WireMock stubs; do not trust the official API documentation for field presence on free tier
- Phase 4: OpenTripMap free-tier rate limit headroom — verify the cities15000 seed plus a 24h cache is sufficient for portfolio-scale traffic before deploying

**Standard patterns (skip research phase):**
- Phase 0: Gradle multi-module, Spring Boot auto-configuration, Docker Compose health checks — all extensively documented
- Phase 1: Spring Cloud Gateway JWT filter — reference implementations exist in Spring docs
- Phase 2: JWT issuance, bcrypt, refresh-token rotation — standard Spring Security patterns
- Phase 3: PostgreSQL FTS with GIN index, Redis cache-aside — well-documented; use population-weighted ORDER BY from day one
- Phase 5: `@Transactional` service pattern, JPA cascade — standard Spring Data patterns
- Phase 7: Axios interceptor, TanStack Query setup — community consensus patterns available

---

## Required Updates to docs/

These are concrete changes needed in the locked design package before Phase 0 scaffolding begins.

### docs/02-architecture.md — Tech Stack Table

| Location | Current Value | Correct Value | Source |
|----------|--------------|---------------|--------|
| Spring Boot version | `3.3.x` | `3.5.x` (latest: 3.5.14) | STACK.md — 3.3.x EOL Jun 2025 |
| Spring Cloud version | `2023.x` (implied) | `2024.0.x` | STACK.md — release train must match SB minor |
| Spring Cloud Gateway | not pinned | `4.2.x` | STACK.md — CVE-2025-41235, CVE-2025-41253 fixed in 4.2.x |
| Flyway | `10` | `10` + explicit `flyway-database-postgresql` dep | STACK.md — required for PG 16 support |
| jjwt | `0.12.x` | `0.13.0` | STACK.md — latest stable, decompression leak fix |
| react-leaflet | not pinned | `4.2.x` (pin explicitly) | STACK.md — v5 requires React 19; incompatible with React 18 |
| Axios | `1.x` | `>= 1.15.0` (recommended: `1.16.0`) | STACK.md — CVE-2025-62718, CVE-2026-40175 in < 1.15.0 |
| dnd-kit | `@dnd-kit/core 6.x` | `@dnd-kit/core 6.x` + note: do NOT use `@dnd-kit/react` (pre-1.0) | STACK.md |
| Vite | `5` | `6.x` advisory for greenfield projects | STACK.md |
| React Router | `6` | `7.x` advisory for greenfield projects | STACK.md |
| Zustand | `4.x` | `5.0.x` | STACK.md — v5 drops React < 18, uses native useSyncExternalStore |
| Zod | `3.x` | `3.x` (explicit pin; note v4 migration planned for v2) | STACK.md |

### gradle/libs.versions.toml — Version Catalog (create in Phase 0)

The version catalog does not yet exist. Phase 0 must create it with the following corrected pins:

```toml
[versions]
springBoot = "3.5.14"
springCloud = "2024.0.4"
jjwt = "0.13.0"
resilience4j = "2.4.0"
testcontainers = "1.21.x"
wiremock = "3.9.0"
```

Frontend pins in `package.json`:
```
"react": "^18.3.1"
"axios": "^1.16.0"
"react-leaflet": "^4.2.0"
"zod": "^3.24.0"
"@dnd-kit/core": "^6.0.0"
```

### services/*/src/main/resources/application.yml — Flyway Per-Service History Tables

Each service must add (Phase 0):
```yaml
# auth-service
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

### services/*/src/main/resources/application.yml — Spring Application Names

Each service must set `spring.application.name` to a distinct value. Zipkin derives service name from this; missing it groups all spans under a single default name, making traces unreadable.

### infra/docker-compose.yml — Eureka Health Check + Downstream Depends-On

Add Eureka `healthcheck` on `/actuator/health` and change all downstream service and gateway `depends_on` entries to use `condition: service_healthy`. Reduces cold-start 503 window from ~60s to ~15s.

### infra/docker-compose.yml — Downstream Service Port Binding

Bind destination-service and trip-service ports to `127.0.0.1` only (e.g. `127.0.0.1:8082:8082`) so they are not accessible outside the Docker network without going through the gateway.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All version pins verified against Maven Central, official release pages, and CVE databases. Spring Boot EOL dates from endoflife.date (authoritative). Axios CVEs from HeroDevs April 2026 report. react-leaflet peer dep verified from official GitHub releases. |
| Features | MEDIUM-HIGH | Competitive analysis from 7 reference products via third-party reviews and official product sites. Feature presence/absence cross-validated across Wanderlog, TripIt, Sygic, Google Maps. Some sources are vendor content with acknowledged bias. |
| Architecture | HIGH | Decomposition validated against DDD microservices patterns and OWASP Microservices Security Cheat Sheet. JWT trust model, cache-aside, gap-based positioning, and schema-per-service patterns are all well-documented with reference implementations. |
| Pitfalls | HIGH | 12 pitfalls with concrete prevention code. Foursquare free-tier field restrictions verified against official pricing and rate-limit docs. dnd-kit + TanStack Query pattern verified against community discussion threads and tkdodo.eu. All other pitfalls are Spring/Postgres/Redis well-known issues with referenced sources. |

**Overall confidence:** HIGH — the design package is sound, the architecture is validated, and the pitfalls are concrete and preventable. The primary uncertainty is Foursquare free-tier field availability, which must be validated with a real API call during Phase 4.

### Gaps to Address

- **Foursquare free-tier field reality**: Research confirms photos and hours are Premium fields, but the exact JSON shape of a real free-tier response must be captured during Phase 4 to build accurate WireMock stubs. Do not write stubs from the official API documentation.
- **OpenTripMap free-tier quota headroom**: Research assumes 24h caching is sufficient to stay under the free-tier daily quota for portfolio-scale traffic. Verify actual quota limits during Phase 4 setup.
- **jjwt long-term maintenance trajectory**: jjwt 0.13.0 is the latest release (Aug 2023) and library activity has slowed. The Spring Security Nimbus JOSE path eliminates the external dependency entirely. Flag as a v2 migration candidate when OAuth/OIDC login is considered. No action needed for v1.
- **Zod v4 migration**: Zod v4 has breaking API changes. A `zod-v3-to-v4` community codemod exists. Plan as a Phase 5+ v2 upgrade task, not a v1 concern.

---

## Sources

### Primary (HIGH confidence)
- [Spring Boot EOL dates — endoflife.date](https://endoflife.date/spring-boot) — 3.3.x EOL Jun 2025, 3.5.x current
- [Spring Boot 3.5 release — spring.io](https://spring.io/blog/2025/05/22/spring-boot-3-5-0-available-now/)
- [Spring Cloud Gateway CVE-2025-41235 / CVE-2025-41253 — GitHub releases](https://github.com/spring-cloud/spring-cloud-gateway/releases)
- [Flyway PostgreSQL modularization issue — GitHub #3969](https://github.com/flyway/flyway/issues/3969)
- [Axios CVE advisory — HeroDevs April 2026](https://www.herodevs.com/blog-posts/axios-versions-cves-and-safe-upgrade-path-updated-april-2026)
- [react-leaflet releases — GitHub](https://github.com/PaulLeCam/react-leaflet/releases) — v5 requires React 19 peer dep
- [Foursquare Rate Limits and Premium fields — official docs](https://docs.foursquare.com/developer/reference/personalization-apis-rate-limits)
- [OWASP Microservices Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Microservices_Security_Cheat_Sheet.html)
- [TanStack Query concurrent optimistic updates — tkdodo.eu](https://tkdodo.eu/blog/concurrent-optimistic-updates-in-react-query)
- [dnd-kit optimistic update discussion — GitHub #1522](https://github.com/clauderic/dnd-kit/discussions/1522)

### Secondary (MEDIUM confidence)
- [React 19 stable — react.dev](https://react.dev/blog/2024/12/05/react-19) — peer dep implications
- [Zod v4 release notes — zod.dev](https://zod.dev/v4) — breaking changes catalog
- [Vite 6.0 announcement — vite.dev](https://vite.dev/blog/announcing-vite6)
- [Zustand v5 — pmnd.rs](https://pmnd.rs/blog/announcing-zustand-v5/)
- [React Router 7 — GitHub releases](https://github.com/remix-run/react-router/releases)
- [Tailwind CSS v4.0 — tailwindcss.com](https://tailwindcss.com/blog/tailwindcss-v4)
- Wanderlog, TripIt, Sygic Travel, Google Maps Lists competitive feature analysis — multiple third-party review sources
- [Postgres FTS ranking with population weights — The Gnar Company](https://www.thegnar.com/blog/postgres-full-text-search)
- [Redis cache stampede protection — Medium](https://medium.com/@AlexanderObregon/cache-stampede-protection-in-spring-boot-applications-341f87b37649)

### Tertiary (LOW-MEDIUM confidence)
- [Stippl Best AI Travel Planner 2026](https://www.stippl.io/blog/best-ai-travel-planner-2026) — competitive landscape (vendor content)
- [Shivlab Top Features 2025](https://shivlab.com/blog/top-features-for-travel-planning-app/) — mobile usage statistics (vendor blog)

---
*Research completed: 2026-05-08*
*Ready for roadmap: yes*
