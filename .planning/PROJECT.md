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
- ✓ All `/api/*` traffic routed through api-gateway with JWT validation — v1.0
- ✓ Downstream services reject any request that bypasses the gateway — v1.0
- ✓ RFC 7807 problem-detail JSON contract for 401/429 — v1.0
- ✓ IP-based login rate limiting at gateway — v1.0
- ✓ Distributed trace continuity across gateway → downstream — v1.0

**Authentication & Accounts**
- ✓ User can sign up with email + password (FR-12) — v1.0
- ✓ User receives a verification email; clicking the link activates their account (FR-13) — v1.0
- ✓ User can log in with email + password and stay signed in across page refreshes (FR-14) — v1.0
- ✓ User can log out, ending the session and blocking authenticated routes (FR-15) — v1.0

**Destination Discovery**
- ✓ User can search for a city or country by name (FR-1) — v1.0
- ✓ User sees up to 20 recommended attractions for a selected location (FR-2) — v1.0
- ✓ User can open a destination detail view (FR-3) — v1.0
- ✓ User can swipe through multiple photos in the destination detail view (FR-4) — v1.0
- ✓ User sees opening hours when present (FR-5) — v1.0

**Trip Planning (core)**
- ✓ User can create a trip with a required name (FR-6) — v1.0
- ✓ User can set a trip date range with day materialization (FR-7) — v1.0
- ✓ User can add a destination to a chosen day (FR-8) — v1.0
- ✓ User can remove a destination from a trip day (FR-9) — v1.0
- ✓ Logged-out users prompted to log in when creating a trip (FR-10) — v1.0
- ✓ Returning users see their previously created trips (FR-11) — v1.0

**In-Day Scheduling**
- ✓ User can drag-drop reorder items within a day (FR-16) — v1.0
- ✓ User can drag an item from one day to another (FR-17) — v1.0
- ✓ Each item supports an optional time slot (FR-18) — v1.0

**Trip Map View**
- ✓ User sees all destinations of a trip on an interactive map (FR-19) — v1.0

**Personalization**
- ✓ User can attach a free-text note to any itinerary item (FR-20) — v1.0
- ✓ User can set a cover image URL for a trip (FR-22) — v1.0

**Cross-cutting (NFRs)**
- ✓ Search returns within 500 ms p95 (NFR-1) — v1.0
- ✓ Per-user authorization enforced at service layer (NFR-2) — v1.0
- ✓ Provider failures fall back to cache (NFR-3) — v1.0
- ✓ All external services on free tier only (NFR-4) — v1.0
- ✓ Backend service-layer line coverage ≥70% (NFR-5) — v1.0
- ✓ OWASP Top 10 explicitly addressed (NFR-6) — v1.0
- ✓ Keyboard-navigable controls; WCAG AA color contrast (NFR-7) — v1.0
- ✓ Trip planner usable on screens ≥360 px (NFR-8) — v1.0
- ✓ W3C trace context propagated across services (NFR-9) — v1.0

### Active

<!-- Requirements for current milestone (v1.1). -->

- [ ] User can favorite/unfavorite destinations and view them on a Favorites page (FR-21 / PERS-02)
- [ ] User can see travel time/distance between consecutive itinerary items
- [ ] User can view weather forecast for trip dates at the destination
- [ ] User can share a trip via a public link (read-only)
- [ ] User can duplicate an existing trip as a starting template

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- **Real-time multi-user collaboration** — requires CRDTs or pessimistic locking; out of portfolio scope
- **Native mobile apps** — web responsive (NFR-8) covers basic mobile use; native is a separate project
- **Push / email notifications** — requires VAPID keys, background jobs, deliverability ops; deferred past v1.1
- **Budget tracking** — adds complexity for low portfolio value; deferred past v1.1
- **Export to PDF/.ics** — out-of-band concern; deferred past v1.1
- **Internationalization, multi-currency** — English-only
- **OAuth (Google, GitHub) login** — adds Spring Authorization Server overhead; deferred past v1.1
- **File-uploaded cover image** — deferred past v1.1 to avoid S3/upload pipeline complexity
- **Admin tooling** — DB access via psql/pgAdmin sufficient for portfolio scope; deferred past v1.1
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

## Current Milestone: v1.1 Trip Enhancement

**Goal:** Enhance trips with routing/weather context, social sharing, and duplication — making itineraries smarter and more shareable.

**Target features:**
- Favorites page (FR-21 carry-over from v1.0)
- Travel time/distance between consecutive itinerary items
- Weather forecast integration for trip dates
- Trip sharing via public link (read-only)
- Trip duplication / templates

## Current State (post v1.0)

- **Shipped:** v1.0 MVP on 2026-05-20
- **Timeline:** 12 days (2026-05-08 → 2026-05-19)
- **Codebase:** ~10,700 LOC Java + ~5,400 LOC TypeScript/TSX
- **Files:** 539 tracked
- **Commits:** 255
- **Architecture:** 5 Spring Boot services + React SPA, fully dockerized
- **Observability:** Zipkin tracing, Prometheus metrics, Grafana dashboards, structured JSON logs
- **Testing:** JaCoCo coverage + OWASP dependency-check + CI pipeline

## Key Decisions

<!-- ADR-1 through ADR-8 from docs/02-architecture.md. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| ADR-1: Monorepo + microservices over modular monolith | User wants portfolio signal of distributed-system skill; clear domain seams (auth/planning/discovery) make microservices a defensible split | ✅ Shipped |
| ADR-2: Gradle Kotlin DSL over Maven | Better multi-module ergonomics, version catalog, parallel build | ✅ Shipped |
| ADR-3: Single Postgres with per-service schemas vs separate DB instances | Memory budget; ownership preserved by schema; per-service DB users prevent cross-schema joins | ✅ Shipped |
| ADR-4: Eureka over Consul or static URLs | Spring-native, minimal config, common in Spring shops | ✅ Shipped |
| ADR-5: Custom JWT (jjwt) over Spring Authorization Server / Keycloak | Email+password auth doesn't justify OAuth2 server overhead at v1 | ✅ Shipped |
| ADR-6: Sync HTTP only in v1, no message broker | YAGNI; no async use case yet justifies Kafka/RabbitMQ | ✅ Shipped |
| ADR-7: TanStack Query + Zustand over Redux Toolkit | Better DX for server-cached data; Zustand handles small UI state slice without boilerplate | ✅ Shipped |
| ADR-8: Leaflet + OpenStreetMap over Google Maps | No API key, no billing, sufficient quality for v1 | ✅ Shipped |
| ADR-9: Ship 22 FRs as v1; defer 15 ideas to v2 backlog | Scope discipline; design package explicitly bounds v1 around the core planning loop | ✅ Shipped (31/32 reqs delivered; FR-21 frontend deferred) |

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
*Last updated: 2026-05-21 — Milestone v1.1 started*
