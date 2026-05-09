# Trip Planner

## What This Is

A web app for travelers who want to go from "I want to visit somewhere" to a structured day-by-day itinerary in minutes. Users search a city or country, browse a curated list of attractions, open detail views with photos and opening hours, and assemble the ones they want into trips with drag-drop day-by-day scheduling. Trips and favorited destinations persist across sessions behind email-verified accounts.

This is a practice/portfolio project demonstrating Spring Boot microservices + React, not a product launch.

## Core Value

A signed-in user can search a city, discover attractions, and assemble them into a multi-day itinerary that persists across sessions — with drag-drop reorder, optional time slots, and a map view. Anything else can fail; this primary flow cannot.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

**Gateway & Cross-Cutting Security**
- [x] All `/api/*` traffic routed through api-gateway with JWT validation; gateway strips inbound X-User-Id and injects from JWT claims (Validated in Phase 1: api-gateway)
- [x] Downstream services (trip, destination) reject any request that bypasses the gateway (Validated in Phase 1 via `DirectServiceAccessWithoutGatewayReturns401IT`)
- [x] RFC 7807 problem-detail JSON contract for 401/429 (Validated in Phase 1; gateway↔downstream JSON-shape divergence flagged in 01-REVIEW.md BL-01 for Phase 2 follow-up)
- [x] IP-based login rate limiting (30 req / 15 min per IP) at gateway (Validated in Phase 1 via `LoginRateLimiterIT`; strict 5/15min IP+email leg deferred to Phase 2 per D-05)
- [x] Distributed trace continuity across gateway → downstream (Validated in Phase 1; user-attested Zipkin UI 2026-05-09)

### Active

<!-- Current scope. Building toward these. Maps to 22 numbered FRs in docs/01-prd.md. -->

**Authentication & Accounts**
- [ ] User can sign up with email + password (FR-12)
- [ ] User receives a verification email; clicking the link activates their account (FR-13)
- [ ] User can log in with email + password and stay signed in across page refreshes (FR-14)
- [ ] User can log out, ending the session and blocking authenticated routes (FR-15)

**Destination Discovery**
- [ ] User can search for a city or country by name; up to 5 ranked, deduplicated results returned (FR-1)
- [ ] User sees up to 20 recommended attractions for a selected location, with name, category, rating, and thumbnail when available (FR-2)
- [ ] User can open a destination detail view showing description, photos, address, opening hours, website (FR-3)
- [ ] User can swipe through multiple photos in the destination detail view (FR-4)
- [ ] User sees opening hours when present, or an explicit "not available" state when missing (FR-5)

**Trip Planning (core)**
- [ ] User can create a trip with a required name (FR-6)
- [ ] User can set a trip date range; one itinerary day is materialized per date, with confirmation when shrinking would orphan items (FR-7)
- [ ] User can add a destination to a chosen day of a chosen trip from list or detail view (FR-8)
- [ ] User can remove a destination from a trip day (FR-9)
- [ ] Logged-out users prompted to log in when creating a trip or adding a destination; original action completes after login (FR-10)
- [ ] Returning users see their previously created trips, items, and days; empty state when none (FR-11)

**In-Day Scheduling**
- [ ] User can drag-drop reorder items within a day with optimistic update (FR-16)
- [ ] User can drag an item from one day to another within the same trip (FR-17)
- [ ] Each item supports an optional time slot (HH:mm), used for sort order when set (FR-18)

**Trip Map View**
- [ ] User sees all destinations of a trip on an interactive map; markers auto-fit to bounds (FR-19)

**Personalization**
- [ ] User can attach a free-text note (≤500 chars) to any itinerary item; sanitized server-side (FR-20)
- [ ] User can favorite/unfavorite destinations and view them on a Favorites page (FR-21)
- [ ] User can set a cover image URL for a trip (FR-22)

**Cross-cutting (NFRs)**
- [ ] Search returns within 500 ms p95 for cached/seeded results (NFR-1)
- [ ] Per-user authorization enforced at service layer; cross-user reads return 404 (NFR-2)
- [ ] Provider failures fall back to cache; UI surfaces degraded mode (NFR-3)
- [ ] All external services on free tier only (NFR-4)
- [ ] Backend service-layer line coverage ≥70%; auth + ownership paths 100% (NFR-5)
- [ ] OWASP Top 10 explicitly addressed (NFR-6)
- [ ] Keyboard-navigable controls; WCAG AA color contrast (NFR-7)
- [ ] Trip planner usable on screens ≥360 px (NFR-8)
- [ ] W3C trace context propagated across services; structured JSON logs (NFR-9)

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. See docs/09-roadmap.md v2 backlog for the full list. -->

- **Trip sharing via public link** — adds auth-mode complexity (public-vs-authenticated routes); v2 feature
- **Trip duplication / templates** — small CRUD addition; deferred to v2 to keep v1 focused
- **Real-time multi-user collaboration** — requires CRDTs or pessimistic locking; out of portfolio scope
- **Native mobile apps** — web responsive (NFR-8) covers basic mobile use; native is a separate project
- **Push / email notifications** — requires VAPID keys, background jobs, deliverability ops; v2
- **Budget tracking** — adds complexity for low portfolio value
- **Weather forecast integration** — nice-to-have but not core to the planning loop; v2
- **Travel time/distance between items** — depends on routing API; v2
- **Export to PDF/.ics** — out-of-band concern; v2
- **Internationalization, multi-currency** — English-only v1
- **OAuth (Google, GitHub) login** — adds Spring Authorization Server overhead; v2
- **File-uploaded cover image** — URL-only in v1 to avoid S3/upload pipeline; v2
- **Admin tooling** — DB access via psql/pgAdmin sufficient for portfolio scope
- **GDPR-grade data export** — out-of-band per request only

## Context

- **Project type:** Practice/portfolio. Single developer (huyqtrieu@kms-technology.com). Goal is to demonstrate end-to-end Spring Boot microservices + React skill that holds up to senior reviewer scrutiny.
- **Source documents:** Original feature list in `Trip Planner Feature List.pdf` (4 features, NFR-1, NFR-2). 11 SDLC artifacts already produced in `docs/` covering PRD, architecture, data model, API spec, auth/security, frontend design, test strategy, deployment, roadmap, and risk register.
- **Architectural style chosen:** Monorepo + microservices over modular monolith — the user explicitly chose this for portfolio signal value, accepting the build-time tradeoff (~2x vs monolith) in exchange for demonstrating distributed-system skills (gateway, JWT propagation, distributed tracing, per-service migrations).
- **External providers:** OpenTripMap (POI source) and Foursquare (enrichment) — both free tier. GeoNames cities-15000 dataset (public domain) seeded into local DB for fast city/country search.
- **Design package:** Already approved by user. `.planning/` artifacts must stay consistent with `docs/`; if conflict arises, `docs/` is canonical until explicitly evolved.

## Constraints

- **Tech stack — locked:** Java 21 + Spring Boot 3.3.x + Gradle Kotlin DSL multi-module + PostgreSQL 16 + Redis 7 + React 18 + Vite + TypeScript + Tailwind + shadcn/ui — User picked this stack; no negotiation on language/framework. (See `docs/02-architecture.md` for full table with rationale.)
- **Architecture — locked:** Monorepo with 5 services (api-gateway, auth-service, trip-service, destination-service, eureka-server) + 4 shared libs + frontend — User explicitly chose this over modular monolith.
- **Single Postgres, schema-per-service:** `auth`, `trip`, `destination` schemas with per-service DB users — Memory budget for laptop dev; ownership preserved without spinning up multiple DB instances.
- **Cost — free tier only:** No paid external APIs in v1. No credit-card-required signups — Portfolio scope; can't justify recurring cost.
- **Auth — JWT with email verification:** HS256 access (15 min) + refresh-token rotation (7 days httpOnly cookie); bcrypt cost 12; email verify required before login — Hand-rolled, not Spring Authorization Server (overkill for v1).
- **Local-only deployment in v1:** `docker compose up` is the ship target. Cloud (Fly.io/Neon/Upstash) is documented but not built — Portfolio doesn't require live cloud demo; local recording suffices.
- **Test discipline:** ≥70% backend service-layer line coverage; 100% on auth + ownership-check paths; 8 mandatory security integration tests gate every PR — Portfolio reviewers care about test discipline as much as feature breadth.

## Key Decisions

<!-- ADR-1 through ADR-8 from docs/02-architecture.md. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| ADR-1: Monorepo + microservices over modular monolith | User wants portfolio signal of distributed-system skill; clear domain seams (auth/planning/discovery) make microservices a defensible split | — Pending |
| ADR-2: Gradle Kotlin DSL over Maven | Better multi-module ergonomics, version catalog, parallel build | — Pending |
| ADR-3: Single Postgres with per-service schemas vs separate DB instances | Memory budget; ownership preserved by schema; per-service DB users prevent cross-schema joins | — Pending |
| ADR-4: Eureka over Consul or static URLs | Spring-native, minimal config, common in Spring shops | — Pending |
| ADR-5: Custom JWT (jjwt) over Spring Authorization Server / Keycloak | Email+password auth doesn't justify OAuth2 server overhead at v1 | — Pending |
| ADR-6: Sync HTTP only in v1, no message broker | YAGNI; no async use case yet justifies Kafka/RabbitMQ | — Pending |
| ADR-7: TanStack Query + Zustand over Redux Toolkit | Better DX for server-cached data; Zustand handles small UI state slice without boilerplate | — Pending |
| ADR-8: Leaflet + OpenStreetMap over Google Maps | No API key, no billing, sufficient quality for v1 | — Pending |
| ADR-9: Ship 22 FRs as v1; defer 15 ideas to v2 backlog | Scope discipline; design package explicitly bounds v1 around the core planning loop | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-09 after Phase 1 (api-gateway) completion*
