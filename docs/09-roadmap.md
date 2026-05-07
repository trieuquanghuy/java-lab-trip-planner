# 09 — Roadmap

**Status**: Draft for review
**Last updated**: 2026-05-08

11 sequenced phases. Each phase ends in a demoable state and is small enough
to be one or two focused work sessions.

## Phase 0 — Monorepo scaffolding

**Goal**: an empty repo runs `docker compose up` and all services report healthy on Eureka.

Deliverables:
- Gradle multi-module setup (`settings.gradle.kts`, version catalog).
- 4 service modules with empty Spring Boot apps + Actuator + Eureka client.
- 1 eureka-server module.
- 4 shared libs scaffolded (`jwt-common`, `api-contracts`, `error-handling`, `observability`).
- `infra/docker-compose.yml` with Postgres, Redis, Mailhog, Zipkin, Eureka, all 4 services.
- `infra/postgres/init.sql` creating 3 schemas.
- GitHub Actions skeleton (`backend.yml`, `frontend.yml`) — runs lint + empty test suites.
- `frontend/` Vite + React + TS app with a "Hello world" route.
- README with setup instructions and architecture diagram.
- `.env.example` complete.

Acceptance:
- `docker compose up` brings everything healthy in < 60 seconds.
- All 4 services visible in Eureka dashboard at `localhost:8761`.
- `curl localhost:8080/actuator/health` returns OK.

## Phase 1 — API gateway

**Goal**: gateway routes traffic and validates JWTs; downstream services trust gateway-injected headers.

Deliverables:
- Spring Cloud Gateway configured with route predicates for the 4 path prefixes.
- JWT signature validation filter (rejects invalid/expired tokens).
- Header injection: `X-User-Id`, `X-User-Email`, `X-Request-Id`.
- CORS configuration.
- Rate limit filter (Redis-backed) on `/api/auth/login`.
- `jwt-common` lib with re-validation filter installed in 3 downstream services.
- Integration tests: route forwarding, JWT rejection cases, CORS preflight.

Acceptance:
- Calling `/api/auth/anything` reaches auth-service; calling `/api/trips/anything` reaches trip-service.
- Without an `Authorization` header, `/api/trips/*` returns 401 from the gateway (never reaches trip-service).
- With a forged JWT, gateway returns 401.
- Login route is rate-limited at 5/15min/IP+email.

## Phase 2 — Auth service

**Goal**: full signup → verify → login → refresh → logout works end-to-end.

Deliverables:
- `auth.users`, `auth.email_verification_tokens`, `auth.refresh_tokens` migrations.
- Endpoints: signup, verify, login, refresh, logout.
- bcrypt password hashing.
- JWT issuance + refresh-token rotation logic.
- SMTP verification email (Mailhog).
- Bean Validation on inputs.
- Generic-error responses for enumeration resistance.
- Daily cleanup of expired tokens (Spring `@Scheduled`).
- Unit + integration + security tests covering all 8 mandatory security scenarios.

Acceptance:
- A user can sign up, click the email link in Mailhog UI, log in, hit `/api/auth/me`, refresh, log out.
- Cross-user JWT can't read other users' data (verified through manually crafted JWT in test).
- Login rate limit triggers at attempt 6.

## Phase 3 — Destination service: search

**Goal**: city/country search with seeded data hits the 500ms p95 target.

Deliverables:
- `destination.cities` migration with `pg_trgm`/`unaccent` extensions.
- Flyway migration `V3__seed_cities.sql` populates from `infra/seeds/cities-15000.tsv` (GeoNames).
- `GET /api/search` endpoint.
- Redis caching (1h TTL) for `(q, type)` keys.
- Postgres FTS query as DB fallback.
- Integration tests: search with seeded cities, cache hit on second call, edge cases (empty q, no results, accented chars).
- p95 latency assertion in test (synthetic 100-call run < 500 ms).

Acceptance:
- `GET /api/search?q=lon` returns London first.
- Repeated call < 50 ms (cache).
- Cold call < 250 ms (FTS).
- "No results" returns empty array, 200, not 404.

## Phase 4 — Destination service: providers + cache

**Goal**: nearby attractions and detail endpoints work, with provider failure isolation.

Deliverables:
- `destination.destinations_cache` migration.
- `DestinationGateway` interface + `OpenTripMapClient` + `FoursquareClient` implementations.
- `EnrichingDestinationService` composing both.
- Resilience4j circuit breaker per provider.
- `GET /api/destinations` (lat/lng/radius) and `GET /api/destinations/{ref}` endpoints.
- Cache write-through on every fetch; 24h staleness rule.
- WireMock-backed integration tests including circuit-open scenarios.

Acceptance:
- `GET /api/destinations?lat=51.5074&lng=-0.1278&radius=20000` returns 20 attractions including British Museum.
- Force OpenTripMap stub to 500: response still returns from cache (after one prior success), `providerStatus.openTripMap === "circuit_open"`.
- All photos and opening_hours appear when available.

## Phase 5 — Trip service: trips + days

**Goal**: trip CRUD and day materialization works correctly under date changes.

Deliverables:
- `trip.trips`, `trip.itinerary_days` migrations.
- Endpoints: `POST /api/trips`, `GET /api/trips`, `GET /api/trips/{id}`, `PATCH /api/trips/{id}`, `DELETE /api/trips/{id}`.
- Day materialization service: idempotent reconciliation of days against trip dates.
- Shorten-confirmation 409 logic.
- Integration tests covering: create with dates, create then set dates, extend range, shrink range with confirmation, shrink without confirmation (409), cross-user 404.

Acceptance:
- Setting `startDate=2026-09-10, endDate=2026-09-14` creates 5 itinerary_days.
- Changing `endDate` to `2026-09-12` requires `confirmShorten=true` if items exist on day 4–5.
- User A cannot read User B's trip (returns 404).

## Phase 6 — Trip service: itinerary items + favorites

**Goal**: drag-drop reorder and favorites work.

Deliverables:
- `trip.itinerary_items`, `trip.favorites` migrations.
- Endpoints: `POST /api/trips/{tripId}/days/{dayId}/items`, `PATCH /api/trips/{tripId}/items/{itemId}`, `DELETE /api/trips/{tripId}/items/{itemId}`, favorites CRUD.
- Position assignment algorithm (gap-100, midpoint insert, lazy reindex).
- Note + cover-image-URL sanitization (OWASP HTML Sanitizer).
- Integration tests: append, insert, move-to-day, reindex on collision, note XSS attempt sanitized.

Acceptance:
- Adding an item appends with `position = MAX + 100`.
- PATCHing to a position between two existing items takes midpoint.
- After 50 random reorders, no two items in a day share a position.
- A note with `<script>alert(1)</script>` is stored as `alert(1)`.

## Phase 7 — Frontend: auth + discovery

**Goal**: a logged-out user can search and view destinations; auth pages work.

Deliverables:
- React Router setup, providers (TanStack Query, AuthProvider).
- Pages: HomePage (search + recommendations), DestinationDetailPage, LoginPage, SignupPage, VerifyEmailPage, NotFoundPage.
- Axios client with JWT + refresh interceptors.
- `useSearch`, `useDestinations`, `useDestinationDetail` hooks.
- Component tests for each page.
- Playwright E2E: signup → verify → login → search.

Acceptance:
- Manual click-through: open `localhost:5173`, search "Tokyo", see attractions, click into one, see photos.
- Logged-out "Add to trip" is disabled with a tooltip explaining login is required.
- Signup → click Mailhog link → land on success page.

## Phase 8 — Frontend: trip planner

**Goal**: full itinerary editor with drag-drop and map view.

Deliverables:
- TripListPage with empty state and create-trip dialog.
- TripDetailPage with header (dates, cover), List/Map toggle.
- ItineraryBoard using dnd-kit: drag within day, drag between days, optimistic updates.
- AddToTripDialog supporting deferred login flow (per FR-10 + UI flow §6.3).
- TripMap with Leaflet, day-index color coding, fitBounds.
- FavoritesPage with hydration of destination_refs.
- Component tests for each, Playwright for full reorder flow + add-to-trip-from-logged-out flow.

Acceptance:
- Manual: create trip "Tokyo 2026", set dates, add 4 destinations, drag to spread across 3 days, see them on map.
- Reorder persists across page reload.
- Logout-add-flow: log out, click "Add to trip" on a destination, get bounced to login, log back in, item appears in the chosen trip.

## Phase 9 — Polish

**Goal**: production-quality UX hardening.

Deliverables:
- Loading skeletons on every list/detail page.
- Empty states with CTAs.
- Error boundaries per route.
- Toast system for transient feedback.
- Mobile responsive (tested at 360 px width on Chrome DevTools).
- Keyboard navigation across drag-drop board (dnd-kit's keyboard sensor).
- Accessibility pass: aria-labels, focus rings, color contrast (axe DevTools clean).
- 404, 500 friendly pages.
- Lighthouse pass: ≥ 90 perf, ≥ 95 a11y on Home and TripDetail.

Acceptance:
- No console errors during a 5-minute click-through.
- Every interactive control reachable by Tab.
- axe DevTools reports 0 violations on Home and TripDetail.

## Phase 10 — Observability + perf hardening

**Goal**: production-debuggable system, perf budget met.

Deliverables:
- Zipkin tracing wired across all services (a single trace from gateway through 1–2 hops visible in Zipkin UI).
- Custom Micrometer metrics (provider calls, cache, reindexes).
- Logback JSON encoder + redaction.
- Final security audit: re-run all 8 mandatory tests; OWASP Dependency-Check clean.
- k6 baseline: 100 RPS on `/api/search` under 500 ms p95.
- Final coverage check: ≥ 70% backend lines, 100% on auth + ownership.
- README updated with architecture diagram, demo GIFs.

Acceptance:
- A single end-to-end "create trip → add item → reorder" generates a single Zipkin trace spanning gateway + trip-service + DB.
- k6 report committed under `docs/perf/`.
- CI green for 7 consecutive days on `main`.

---

## v2 Backlog (post-v1)

These were considered during design but explicitly cut from v1. Tracked here so they aren't lost.

| Item | Sketch |
|------|--------|
| **Trip sharing** | Read-only public link `/share/<slug>`; new table `trip_shares (slug, trip_id, expires_at)`. Public route in gateway with no auth. |
| **Trip duplication / templates** | `POST /api/trips/{id}/duplicate` deep-copies trip + days + items into a new trip owned by the same user. |
| **Weather forecast** | Open-Meteo (free) integration for trip date range; new `weather` panel on TripDetailPage. |
| **Travel time between items** | OSRM public API for distance/duration; cached per `(fromRef, toRef, mode)`. |
| **Export to PDF / .ics** | Server-side rendering via OpenHTMLtoPDF; .ics generated by ical4j. New endpoint `GET /api/trips/{id}/export?format=pdf|ics`. |
| **Budget tracking** | Optional `cost_amount`/`cost_currency` per item; trip totals; multi-currency via free FX feed. |
| **Trip categories/tags** | New `trip.tags` table; filter trips by tag. |
| **PWA** | Service worker, offline-cache trips, install banner. |
| **Push notifications** | Web Push for "trip starts in 7 days". Requires VAPID keys + backend cron. |
| **OAuth login** | Add Google + GitHub via Spring Authorization Server (or migrate to Keycloak). |
| **Multi-traveler collaboration** | Trip co-owners; permission model; CRDT-based item ordering for real-time edits. |
| **File-uploaded cover image** | S3 + pre-signed URL upload; image processing pipeline. |
| **Internationalization** | i18next; backend message externalization. |
| **Admin tools** | Read-only ops dashboard for system health; user delete with cascade. |
| **Mobile native** | React Native or Flutter sharing the API. |

## Phase dependency graph

```
P0 ─→ P1 ─→ P2 ─→ P3 ─→ P4
                 │
                 ├─→ P5 ─→ P6
                 │
                 └─→ P7 (after P2 + some of P3)
                          │
                          └─→ P8 (needs P5, P6 backend)
                                  │
                                  └─→ P9 ─→ P10
```

P3 and P5 can proceed in parallel after P2. P7 can start once P2 backend is up (search uses stubs until P3 is ready).
