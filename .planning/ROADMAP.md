# Roadmap: Trip Planner

**Milestone:** v1
**Granularity:** Fine (11 phases)
**Coverage:** 31 / 31 requirements mapped
**Numbering:** Phases 0–10 (mirrors docs/09-roadmap.md exactly)
**Last updated:** 2026-05-08

---

## Phases

- [x] **Phase 0: Monorepo Scaffolding** - All services boot, register in Eureka, and docker compose up is healthy
- [x] **Phase 1: API Gateway** - Gateway routes traffic, validates JWTs, strips and injects X-User-Id, and downstream services trust only gateway-injected headers (completed 2026-05-09)
- [ ] **Phase 2: Auth Service** - Full signup → verify email → login → refresh → logout works end-to-end with all 8 mandatory security tests passing
- [ ] **Phase 3: Destination Service — Search** - City/country search with seeded GeoNames data hits the 500 ms p95 SLA
- [ ] **Phase 4: Destination Service — Providers + Cache** - Nearby attractions and detail endpoints work with provider failure isolation via circuit breakers
- [ ] **Phase 5: Trip Service — Trips + Days** - Trip CRUD and idempotent day materialization work correctly under date range changes
- [ ] **Phase 6: Trip Service — Itinerary Items + Favorites** - Items can be added, removed, reordered (backend), and favorited; notes and cover images are sanitized
- [ ] **Phase 7: Frontend — Auth + Discovery** - A logged-out user can search destinations and view detail; auth pages work end-to-end
- [ ] **Phase 8: Frontend — Trip Planner** - Full itinerary editor with drag-drop reorder, cross-day moves, time slots, and map view
- [ ] **Phase 9: Polish** - Production-quality UX: loading states, error boundaries, a11y pass, and mobile-responsive layout
- [ ] **Phase 10: Observability + Performance Hardening** - Distributed traces confirmed end-to-end, k6 load test meets SLA, final security and coverage audit passes

---

## Phase Details

### Phase 0: Monorepo Scaffolding
**Goal**: All services boot, register in Eureka, and `docker compose up` reaches a fully healthy state.
**Depends on**: Nothing (first phase)
**Requirements**: NFR-04
**Success Criteria** (what must be TRUE):
  1. `docker compose up` brings every container to healthy status in under 60 seconds with no manual intervention
  2. All four services (api-gateway, auth-service, trip-service, destination-service) are visible in the Eureka dashboard at `localhost:8761`
  3. `curl localhost:8080/actuator/health` returns `{"status":"UP"}` through the gateway
  4. The frontend dev server at `localhost:5173` renders a React page without console errors
  5. All three Flyway migrations run without checksum mismatch errors; per-service history tables (`auth_flyway_schema_history`, `trip_flyway_schema_history`, `destination_flyway_schema_history`) are present in their respective schemas
**Plans**: 10 plans
Plans:
- [x] 00-01-PLAN.md — Gradle multi-module skeleton + version catalog (Spring Cloud 2025.0.x per D-30) + .env.example + root README
- [x] 00-02-PLAN.md — scripts/smoke.sh (D-33 Wave 1 phase-gate verifier) + scripts/README.md
- [x] 00-03-PLAN.md — libs/observability fully wired (D-04): @AutoConfiguration + servlet + reactive MDC filters + shared logback-spring-base.xml
- [x] 00-04-PLAN.md — libs/error-handling (D-05 stubs: ProblemDetailFactory + 2-baseline ErrorCode) + libs/api-contracts (D-06 empty module)
- [x] 00-05-PLAN.md — eureka-server skeleton (port 8761, no DB, register-with-eureka false)
- [x] 00-06-PLAN.md — api-gateway skeleton (port 8080, Spring Cloud Gateway 4.2.x, /__health/<svc> static-URI routing per D-02)
- [x] 00-07-PLAN.md — auth/trip/destination service skeletons (per-service Flyway history table per D-09 / Pitfall 3, V1 baseline migrations, /__health controllers)
- [x] 00-08-PLAN.md — infra/postgres/init.sql (D-08 schemas + per-service users) + infra/docker-compose.yml (D-18/D-20/D-22) + root alias (D-19) + per-service Dockerfiles
- [x] 00-09-PLAN.md — Frontend Vite 6 + React 18 + Tailwind v3.4 + provider stack + UI-SPEC landing page + shadcn@2.x init checkpoint (D-32)
- [x] 00-10-PLAN.md — .github/workflows/backend.yml + frontend.yml (D-15 skeleton CI) + final smoke validation checkpoint
**Notes**:
  - Use Spring Boot **3.5.x** (not 3.3.x from docs/02-architecture.md — 3.3.x is EOL June 2025); Spring Cloud **2024.0.x** release train to match
  - Add `org.flywaydb:flyway-database-postgresql` explicitly to each service's `build.gradle.kts` — missing it causes "Unsupported Database: PostgreSQL 16.x" at startup (Pitfall 3)
  - Set `spring.flyway.table=<service>_flyway_schema_history` in each service's `application.yml` to prevent Flyway checksum collision when multiple services share one Postgres instance (Pitfall 3)
  - Pin `micrometer-tracing-bom` once in the Gradle version catalog; do NOT let individual service build files override it (Pitfall 7)
  - Set `spring.application.name` in each service's `application.yml` — Zipkin derives service name from this field
  - Do NOT manually register `ServerHttpObservationFilter` (deprecated in Spring Boot 3.2; auto-configured via `WebHttpHandlerBuilder`)
  - Add `registry-fetch-interval-seconds: 5` and `lease-renewal-interval-in-seconds: 5` in gateway's Eureka config for dev to reduce cold-start 503 window (Pitfall 10)
  - Use `depends_on: condition: service_healthy` for all services that depend on eureka-server in docker-compose.yml

### Phase 1: API Gateway
**Goal**: The gateway routes traffic, validates JWTs, injects trusted headers, and downstream services reject any request that bypasses the gateway.
**Depends on**: Phase 0
**Requirements**: NFR-02, NFR-06
**Success Criteria** (what must be TRUE):
  1. Calling `/api/auth/anything` reaches auth-service; calling `/api/trips/anything` reaches trip-service; calling `/api/search/anything` reaches destination-service
  2. A request to `/api/trips/*` without an `Authorization` header returns 401 from the gateway and never reaches trip-service
  3. A request with a forged or expired JWT returns 401 from the gateway
  4. A direct request to `localhost:8082/api/trips` carrying a crafted `X-User-Id` header but no valid JWT returns 401 from trip-service (`DirectServiceAccessWithoutGatewayReturns401` test passes)
  5. The login route `/api/auth/login` is rate-limited at 5 requests per 15 minutes per IP+email; the 6th attempt returns 429
  6. A single request through gateway to a downstream stub shows one trace ID in both service logs and Zipkin UI
**Plans**: TBD
**Notes**:
  - Wire `JwtCommonFilter` into trip-service and destination-service in THIS phase even though they have no real endpoints — this is the critical security gate; derives `currentUserId` from JWT `sub` claim, never from `X-User-Id` alone (Pitfall 1)
  - Gateway must strip any incoming `X-User-Id` header from the client before injecting its own from JWT claims
  - Expose downstream service ports bound to `127.0.0.1` only in docker-compose.yml
  - Validate Zipkin trace continuity here, not in Phase 10 — one request through gateway should produce a single trace ID spanning both service logs

### Phase 2: Auth Service
**Goal**: Full signup → email verification → login → refresh → logout works end-to-end with all 8 mandatory security scenarios passing.
**Depends on**: Phase 1
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, NFR-05
**Success Criteria** (what must be TRUE):
  1. A user can sign up with email + password, receive a verification link in Mailhog UI, click it to activate the account, and then log in
  2. An unverified account cannot log in; the error message does not reveal whether the account exists
  3. A logged-in user can call `/api/auth/me`, receive their profile, then log out; subsequent authenticated requests return 401
  4. A user can refresh their session using the httpOnly cookie and receive a new access token; after logout the refresh token is invalidated
  5. All 8 mandatory security integration tests pass (including cross-user JWT forgery test, login rate limit at attempt 6, and refresh-token rotation validation)
**Plans**: 7 plans
Plans:
- [x] 02-01-PLAN.md — Flyway V2/V3/V4 + JPA entities + repos + ErrorCode expansion + catalog deps (Wave 1)
- [x] 02-02-PLAN.md — JwtIssuer in libs/jwt-common + auto-config + round-trip unit test (Wave 1, parallel with 01)
- [x] 02-03-PLAN.md — Service layer (LoginRateLimiter Lua, EmailVerificationService, RefreshTokenService rotate+revokeChain) + SecurityConfig + AsyncConfig + AuthProperties + 7 exceptions + yaml/.env wiring (Wave 2)
- [x] 02-04-PLAN.md — 6 DTOs + VerificationEmailRequestedEvent + EmailVerificationSender (UI-SPEC body verbatim) + TokenCleanupJob @Scheduled (Wave 3)
- [ ] 02-05-PLAN.md — AuthService (D-23 re-signup + D-05 timing defense) + AuthController (5 endpoints, 302 verify, ResponseCookie) + AuthControllerAdvice (9 verbatim UI-SPEC detail strings) (Wave 4)
- [ ] 02-06-PLAN.md — Wave-0 test infra + AuthControllerIT happy path + AuthControllerAdviceIT BL-01 + 4 @Tag(security) ITs + 2 unit tests + BL-01 negative-assertion gateway updates (Wave 5)
- [ ] 02-07-PLAN.md — scripts/smoke.sh extension + fresh-stack smoke + MailHog visual checkpoint (Wave 6)
**Notes**:
  - Use jjwt **0.13.0** (not 0.12.x) — includes decompression leak fix
  - Store raw JWT access token in Zustand memory only, never in `localStorage`; refresh token in `httpOnly` cookie with `SameSite=Strict`
  - bcrypt cost factor 12 in all non-test profiles; override with cost 4 in `application-test.yml`
  - `@Scheduled` daily cleanup of expired verification tokens and refresh tokens
  - NFR-05 is mapped here: the 8 mandatory security integration tests are the explicit gate for Phase 2 completion; final coverage numbers confirmed in Phase 10

### Phase 3: Destination Service — Search
**Goal**: City/country search over the GeoNames seed returns the correct top-ranked result within 500 ms p95.
**Depends on**: Phase 2
**Requirements**: SRCH-01, NFR-01
**Success Criteria** (what must be TRUE):
  1. `GET /api/search?q=lon` returns London (GB) as the first result, not London Ontario or another smaller city
  2. A repeated search call returns in under 50 ms (Redis cache hit)
  3. A cold search call (cache miss) returns in under 250 ms (Postgres FTS with GIN index)
  4. A search for an empty string or a query with no matches returns an empty array with HTTP 200, not a 404
  5. Searching with accented characters (e.g. "Münich") returns the correct city
**Plans**: TBD
**Notes**:
  - Use population-weighted ORDER BY: `ts_rank(search_tsv, query) * LOG(population + 1) DESC` — plain ts_rank returns London Ontario before London UK for "lon" prefix (Pitfall 11)
  - Store `search_tsv` as a pre-computed generated column indexed by GIN — computing `to_tsvector()` at query time disables the GIN index on 23k rows
  - Implement single-flight Redis lock (`SET lock:search:{key} 1 NX EX 5`) to prevent cache stampede on cold start (Pitfall 8)

### Phase 4: Destination Service — Providers + Cache
**Goal**: Nearby attractions and destination detail endpoints work, with automatic fallback to cache when providers are unavailable.
**Depends on**: Phase 3
**Requirements**: SRCH-02, DEST-01, DEST-02, DEST-03, NFR-03
**Success Criteria** (what must be TRUE):
  1. `GET /api/destinations?lat=51.5074&lng=-0.1278&radius=20000` returns up to 20 attractions with name, category, rating, and thumbnail
  2. A destination detail view shows photos (or explicit "no photos" state), address, opening hours (or "Opening hours not available"), and website URL
  3. When OpenTripMap's stub is configured to return 500, responses still come from cache after at least one prior successful call, and the response includes `providerStatus.openTripMap === "circuit_open"`
  4. Provider failures do not cascade — when one provider circuit is open, the other provider still returns partial data
  5. Two overlapping radius searches produce zero duplicate `provider_ref` values in `destinations_cache`
**Plans**: TBD
**Notes**:
  - Foursquare free tier: photos and hours are Premium fields silently absent — annotate DTOs `@JsonIgnoreProperties(ignoreUnknown = true)`, make `photos`/`hours` nullable; WireMock stubs must NOT include these fields (Pitfall 6)
  - Use `ON CONFLICT (provider_ref) DO UPDATE` for destinations_cache upsert to prevent duplicate rows from overlapping radius searches (Pitfall 12)
  - Add single-flight lock for provider calls to prevent cache stampede (Pitfall 8)
  - Capture WireMock stubs from actual free-tier API responses, not from official full-schema documentation
  - Validate actual Foursquare free-tier response shape with a real API call before writing WireMock stubs (research flag)
**UI hint**: yes

### Phase 5: Trip Service — Trips + Days
**Goal**: Trip CRUD and idempotent day materialization work correctly, including the shrink-confirmation 409 guard.
**Depends on**: Phase 2
**Requirements**: TRIP-01, TRIP-02, TRIP-06
**Success Criteria** (what must be TRUE):
  1. Setting `startDate=2026-09-10, endDate=2026-09-14` on a trip creates exactly 5 `itinerary_days`; the new trip appears immediately in "My Trips"
  2. Changing `endDate` to `2026-09-12` returns 409 Conflict when items exist on days 4–5, and succeeds when `confirmShorten=true` is passed
  3. A returning logged-in user sees all previously created trips, days, and items; a user with no trips sees an empty state
  4. User A cannot read User B's trip — requests return 404 (not 403)
  5. Two simultaneous PATCH requests shrinking the same trip produce no orphan `itinerary_items` rows
**Plans**: TBD
**Notes**:
  - All of day materialization — confirmation count check, insert new days, delete removed days + cascade items — must be inside a single `@Transactional(isolation = REPEATABLE_READ)` service method (Pitfall 4)
  - Never call `@Transactional` methods from within the same bean; use a separate Spring bean for proxy interception
  - Cascade deletion via `DELETE FROM itinerary_items WHERE day_id IN (...)` in the same transaction, NOT via JPA `CascadeType.REMOVE`

### Phase 6: Trip Service — Itinerary Items + Favorites
**Goal**: Items can be added to, removed from, and reordered across days; favorites and cover images work; notes are sanitized against XSS.
**Depends on**: Phase 5
**Requirements**: TRIP-03, TRIP-04, PERS-01, PERS-02, PERS-03, NFR-06
**Success Criteria** (what must be TRUE):
  1. Adding an item to a day appends it with `position = MAX + 100`; the item appears immediately under the chosen day
  2. PATCHing an item to a position between two existing items takes the integer midpoint; after 50 random reorders no two items in a day share a position
  3. A note containing `<script>alert(1)</script>` is stored and returned as `alert(1)` (HTML stripped server-side)
  4. A user can favorite and unfavorite a destination; the "My Favorites" page lists all favorites and supports add-to-trip from there
  5. A trip cover image URL is stored and shown on the trip card and trip header; if unset, the first item's photo is used
**Plans**: TBD
**Notes**:
  - Store `position` as `BIGINT` (not `INT`) to push integer overflow further out (Pitfall 5)
  - Use `SELECT ... FOR UPDATE` on the parent `itinerary_days` row before reading/writing positions, to serialize concurrent reorder operations (Pitfall 5)
  - Implement reindex as a single SQL window function UPDATE rather than N individual updates
  - NFR-06 (OWASP Top 10) is mapped here: note sanitization completes the stored-XSS item (A03); combined with bcrypt+JWT rotation from Phase 2, gateway header injection from Phase 1, and parameterized queries throughout, OWASP A01–A07 are fully addressed by end of Phase 6

### Phase 7: Frontend — Auth + Discovery
**Goal**: A user can search for destinations and view their details in the browser; auth pages work end-to-end including the signup verification flow.
**Depends on**: Phase 2, Phase 3 (Phase 4 stubs acceptable for provider data)
**Requirements**: TRIP-05
**Success Criteria** (what must be TRUE):
  1. Opening `localhost:5173`, searching "Tokyo", and selecting the result shows a list of attractions with name, category, rating, and thumbnail
  2. Clicking into a destination shows a detail view with photos (or placeholder), address, opening hours (or "not available"), and website
  3. Signing up, clicking the Mailhog email link, and logging in completes the full auth flow; the user lands on a success page
  4. The "Add to Trip" button on a destination detail is disabled (with tooltip) when the user is not logged in; clicking it when logged out prompts login and the original intent is preserved
  5. After a session expires, the browser shows exactly one failed `/api/auth/refresh` request in the network tab — no infinite retry loop
**Plans**: TBD
**Notes**:
  - Implement `isRefreshing` flag + `failedQueue` pattern in Axios interceptor — never retry `/auth/refresh` or `/auth/login` endpoints; full TypeScript implementation in PITFALLS.md Pitfall 9 (Pitfall 9)
  - Disable TanStack Query's default `retry: 3` for auth-related queries (return `false` for 401 errors)
  - Pin react-leaflet **4.2.x** in package.json — v5 requires React 19, incompatible with the locked React 18 stack
  - Use `@dnd-kit/core` **6.x** + `@dnd-kit/sortable`; do NOT use `@dnd-kit/react` (pre-1.0, unstable API)
  - Use Axios **>= 1.15.0** (specifically 1.16.0) to address CVE-2025-62718 and CVE-2026-40175
  - TRIP-05 (deferred login flow) is mapped here: the frontend "add to trip while logged out → login → complete action" flow is implemented in Phase 7 and used through Phase 8
**UI hint**: yes

### Phase 8: Frontend — Trip Planner
**Goal**: The full itinerary editor works with drag-drop reorder, cross-day item moves, optional time slots, and an interactive map view.
**Depends on**: Phase 5, Phase 6, Phase 7
**Requirements**: SCHD-01, SCHD-02, SCHD-03, TMAP-01, NFR-07, NFR-08
**Success Criteria** (what must be TRUE):
  1. A user can create a trip "Tokyo 2026", set dates, add 4 destinations, drag them to spread across 3 days, and see all items persist across a page reload
  2. Dragging an item from Day 1 to Day 2 moves it correctly; the new day order persists after reload
  3. Setting a time slot (HH:mm) on an item causes items in that day to sort by time; items without a time slot appear after those with one
  4. All destinations of a trip appear as markers on a Leaflet map; clicking a marker shows the item name; the map auto-fits bounds to all markers
  5. All interactive controls (drag-drop board, date pickers, buttons) are reachable and usable by keyboard alone (Tab to focus, Space/Enter to activate, arrow keys to reorder)
  6. The trip planner layout is usable on a 360 px wide viewport without horizontal scroll or clipped content
**Plans**: TBD
**Notes**:
  - Maintain local ephemeral order state (`useState`) inside `ItineraryBoard` for the drag session; initialize from query cache; do NOT call `invalidateQueries` on success — only on error for rollback (Pitfall 2)
  - Use dnd-kit's `DragOverlay` component; set `opacity: 0` on original item via CSS `data-dragging` attribute
  - Register `KeyboardSensor` alongside `PointerSensor` — required for WCAG keyboard navigation (NFR-07)
  - `fitBounds` on `TripMap`: call only on mount and when marker coordinates actually change to avoid map jerk
  - Deferred login flow: persist pending `{destinationRef, tripId}` intent to `sessionStorage` before redirecting to login; restore and execute in `useEffect` on redirect-back target
  - Note edits: PATCH on blur, not on keystroke; show "Saved"/"Saving..." indicator from TanStack Query mutation state
  - NFR-07 and NFR-08 are mapped here: keyboard navigation and mobile-responsive requirements are implemented as part of the trip planner feature set and finalized in Phase 9 polish
**UI hint**: yes

### Phase 9: Polish
**Goal**: The full application is production-quality: loading states, error boundaries, a11y compliance, and mobile-responsive layout are complete.
**Depends on**: Phase 8
**Requirements**: (cross-cutting UX delivery — no additional functional REQ-IDs; NFR-07 and NFR-08 are first implemented in Phase 8 and confirmed complete here)
**Success Criteria** (what must be TRUE):
  1. A 5-minute click-through of the full user flow produces zero console errors
  2. Every interactive control is reachable by Tab and activatable by keyboard; axe DevTools reports 0 violations on the Home page and TripDetail page
  3. The app is fully usable on a 360 px wide Chrome DevTools viewport with no clipped content or horizontal scroll
  4. Loading skeletons appear during every list and detail page load; empty states with CTAs appear when data is absent; friendly 404 and 500 error pages are shown for routing and server errors
  5. Lighthouse scores ≥ 90 performance and ≥ 95 accessibility on Home and TripDetail pages
**Plans**: TBD
**Notes**:
  - No new functional requirements are introduced in this phase; all REQ-IDs were mapped to Phases 0–8
  - The a11y and mobile-responsive confirmation (axe DevTools, Lighthouse) is the completion gate for NFR-07 and NFR-08
**UI hint**: yes

### Phase 10: Observability + Performance Hardening
**Goal**: The system is production-debuggable, the search SLA is verified under load, and the final security and coverage audit is clean.
**Depends on**: Phase 9
**Requirements**: NFR-09
**Success Criteria** (what must be TRUE):
  1. A single "create trip → add item → reorder" action generates one Zipkin trace spanning api-gateway, trip-service, and the DB call; traceId is identical in all service JSON logs for that request
  2. k6 report at 100 RPS on `/api/search` shows p95 latency under 500 ms; report committed under `docs/perf/`
  3. Final coverage check passes: ≥ 70% backend service-layer line coverage; 100% branch coverage on auth + ownership-check paths
  4. OWASP Dependency-Check reports zero critical or high CVEs on the production classpath
  5. CI is green for 7 consecutive days on `main`; README is updated with architecture diagram and demo GIFs
**Plans**: TBD
**Notes**:
  - Zipkin trace continuity was first validated in Phase 1; this phase confirms it at full load with realistic traffic patterns
  - NFR-09 (W3C trace context propagated across services; structured JSON logs include traceId, spanId, userId, requestId) is confirmed complete here with the k6-load-tested trace
  - Virtual threads: enable with `spring.threads.virtual.enabled=true` in destination-service for I/O-heavy provider calls

---

## Progress Table

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 0. Monorepo Scaffolding | 10/10 | Complete | 2026-05-08 |
| 1. API Gateway | 6/6 | Complete   | 2026-05-09 |
| 2. Auth Service | 0/? | Not started | - |
| 3. Destination Service — Search | 0/? | Not started | - |
| 4. Destination Service — Providers + Cache | 0/? | Not started | - |
| 5. Trip Service — Trips + Days | 0/? | Not started | - |
| 6. Trip Service — Itinerary Items + Favorites | 0/? | Not started | - |
| 7. Frontend — Auth + Discovery | 0/? | Not started | - |
| 8. Frontend — Trip Planner | 0/? | Not started | - |
| 9. Polish | 0/? | Not started | - |
| 10. Observability + Performance Hardening | 0/? | Not started | - |

---

## Phase Dependency Graph

```
P0 -> P1 -> P2 -> P3 -> P4
                |
                +-> P5 -> P6
                |
                +-> P7 (after P2 + P3 search stubs acceptable)
                         |
                         +-> P8 (needs P5, P6 backend complete)
                                 |
                                 +-> P9 -> P10
```

P3 and P5 can proceed in parallel after P2 (different services, different schemas).
P7 can start as soon as P2 backend is working; use WireMock/MSW stubs for destination endpoints until P4 is live.

---

## Coverage Map

All 31 v1 requirements mapped to exactly one phase:

| Requirement | Phase |
|-------------|-------|
| AUTH-01 | Phase 2 |
| AUTH-02 | Phase 2 |
| AUTH-03 | Phase 2 |
| AUTH-04 | Phase 2 |
| SRCH-01 | Phase 3 |
| SRCH-02 | Phase 4 |
| DEST-01 | Phase 4 |
| DEST-02 | Phase 4 |
| DEST-03 | Phase 4 |
| TRIP-01 | Phase 5 |
| TRIP-02 | Phase 5 |
| TRIP-03 | Phase 6 |
| TRIP-04 | Phase 6 |
| TRIP-05 | Phase 7 |
| TRIP-06 | Phase 5 |
| SCHD-01 | Phase 8 |
| SCHD-02 | Phase 8 |
| SCHD-03 | Phase 8 |
| TMAP-01 | Phase 8 |
| PERS-01 | Phase 6 |
| PERS-02 | Phase 6 |
| PERS-03 | Phase 6 |
| NFR-01 | Phase 3 |
| NFR-02 | Phase 1 |
| NFR-03 | Phase 4 |
| NFR-04 | Phase 0 |
| NFR-05 | Phase 2 |
| NFR-06 | Phase 6 |
| NFR-07 | Phase 8 |
| NFR-08 | Phase 8 |
| NFR-09 | Phase 10 |

Mapped: 31 / 31 ✓
Unmapped: 0 ✓

---

*Roadmap created: 2026-05-08*
*Derives from: docs/09-roadmap.md (11-phase hand-authored plan, approved by user)*
*Research applied from: .planning/research/SUMMARY.md (version corrections, pitfall mitigations)*
