# Roadmap: Trip Planner

**Last updated:** 2026-05-21

---

## Milestones

- ✅ **v1.0 MVP** — Phases 0–10 (shipped 2026-05-20) → [archived](milestones/v1.0-ROADMAP.md)
- 🚧 **v1.1 Trip Enhancement** — Phases 11–15 (in progress)

---

## Active: v1.1 Trip Enhancement

**Goal:** Enhance trips with routing/weather context, social sharing, and duplication.
**Requirements:** 18 across 5 categories ([REQUIREMENTS.md](REQUIREMENTS.md))

### Phase 11: Favorites Page

**Goal:** Ship the frontend Favorites page so users can view, manage, and navigate favorited destinations.
**Requirements:** [PERS-01, PERS-02, PERS-03, PERS-04]
**Plans:** 2/2 plans complete
**Depends on:** None (frontend only, backend exists)

Plans:
- [x] 11-01-PLAN.md — Backend batch destinations endpoint (POST /api/destinations/batch)
- [x] 11-02-PLAN.md — Frontend FavoritesPage enhancement with DestinationCard grid and optimistic removal

**Success Criteria:**
1. `/favorites` route renders a grid/list of user's favorited destinations
2. Unfavorite button removes item with optimistic UI update
3. Clicking a destination navigates to its detail page
4. Empty state shown when no favorites exist
5. Page is accessible and responsive (≥360px)

---

### Phase 12: Trip Duplication

**Goal:** Allow users to duplicate an existing trip (with all days and items) as a starting point for a new trip.
**Requirements:** [DUP-01, DUP-02, DUP-03]
**Plans:** 2 plans
**Depends on:** None

Plans:
- [ ] 12-01-PLAN.md — Backend trip duplication endpoint + tests
- [ ] 12-02-PLAN.md — Frontend duplicate button on detail page and TripCard

**Success Criteria:**
1. "Duplicate" button visible on trip detail/list for owned trips
2. Clicking duplicate creates a new trip named "Copy of {original}"
3. All days and itinerary items are deep-copied to the new trip
4. Duplicated trip has null dates (user sets new dates)
5. User is navigated to the new duplicated trip after creation

---

### Phase 13: Trip Sharing

**Goal:** Enable trip owners to generate a public read-only link, allowing anyone to view a trip without authentication.
**Requirements:** [SHARE-01, SHARE-02, SHARE-03, SHARE-04]
**Plans:** To be planned
**Depends on:** None

**Success Criteria:**
1. "Share" button on trip detail generates a UUID-based public link
2. Public link loads trip in read-only mode without requiring login
3. Shared view shows days, itinerary items, and map (no edit controls)
4. Owner can revoke share link (link returns 404 after revocation)
5. Owner can regenerate a new share link (old one stops working)
6. Gateway routes public share endpoint without JWT validation

---

### Phase 14: Weather Forecast Integration

**Goal:** Display daily weather forecast (temperature, conditions, precipitation) for trip dates, helping users plan what to pack and expect.
**Requirements:** [WEATHER-01, WEATHER-02, WEATHER-03]
**Plans:** To be planned
**Depends on:** None

**Success Criteria:**
1. Trip detail shows weather card per day with temp high/low, icon, and precipitation
2. Weather only appears for future dates within 16-day forecast window
3. Past dates and dates >16 days out show "no forecast available"
4. Weather loads asynchronously (trip renders first, weather appears after)
5. Weather data is cached in Redis (3h TTL) to avoid excess API calls
6. Circuit breaker protects against Open-Meteo outages

---

### Phase 15: Travel Time & Distance

**Goal:** Show travel duration and distance between consecutive itinerary items in each day, giving users a realistic sense of logistics.
**Requirements:** [TRAVEL-01, TRAVEL-02, TRAVEL-03, TRAVEL-04]
**Plans:** To be planned
**Depends on:** None

**Success Criteria:**
1. Between each pair of consecutive items in a day, duration (min) and distance (km) displayed
2. Travel times recalculate automatically when items are reordered or added/removed
3. Travel info loads asynchronously (itinerary renders first)
4. If OSRM is unavailable, "travel time unavailable" shown gracefully (no error state)
5. Travel data is cached in Redis (24h TTL)
6. Circuit breaker protects against OSRM outages

---

## Completed: v1.0 MVP

<details>
<summary>✅ v1.0 Trip Planner MVP (12 phases, 64 plans) — SHIPPED 2026-05-20</summary>

- [x] Phase 0: Monorepo Scaffolding (10/10 plans) — completed 2026-05-08
- [x] Phase 1: API Gateway (6/6 plans) — completed 2026-05-09
- [x] Phase 2: Auth Service (7/7 plans) — completed 2026-05-10
- [x] Phase 3: Destination Service — Search (4/4 plans) — completed 2026-05-15
- [x] Phase 4: Destination Service — Providers + Cache (4/4 plans) — completed 2026-05-16
- [x] Phase 5: Trip Service — Trips + Days (5/5 plans) — completed 2026-05-17
- [x] Phase 6: Trip Service — Itinerary Items + Favorites (6/6 plans) — completed 2026-05-17
- [x] Phase 7: Frontend — Auth + Discovery (6/6 plans) — completed 2026-05-18
- [x] Phase 8: Frontend — Trip Planner (5/5 plans) — completed 2026-05-18
- [x] Phase 9: Polish (4/4 plans) — completed 2026-05-19
- [x] Phase 9.1: M3 Design System Refactor (3/3 plans) — completed 2026-05-19
- [x] Phase 10: Observability + Performance Hardening (4/4 plans) — completed 2026-05-19

**Known gap:** ~~PERS-02 (frontend /favorites page) — backend complete, frontend deferred to v1.1.~~ **Resolved:** FavoritesPage built and functional (confirmed in v1.0 audit 2026-05-21).

</details>

---

## Progress

| Milestone | Phases | Plans | Status | Shipped |
|-----------|--------|-------|--------|---------|
| v1.0 MVP | 12 | 64 | ✅ Complete | 2026-05-20 |
| v1.1 Trip Enhancement | 5 | TBD | 🚧 In Progress | — |

---

## Coverage Map

**v1.0:** All 31 requirements delivered. Full traceability in [v1.0-REQUIREMENTS.md](milestones/v1.0-REQUIREMENTS.md).

**v1.1:** 18 requirements across 5 phases. Traceability in [REQUIREMENTS.md](REQUIREMENTS.md).

| Phase | Requirements |
|-------|-------------|
| 11 — Favorites | PERS-01, PERS-02, PERS-03, PERS-04 |
| 12 — Duplication | DUP-01, DUP-02, DUP-03 |
| 13 — Sharing | SHARE-01, SHARE-02, SHARE-03, SHARE-04 |
| 14 — Weather | WEATHER-01, WEATHER-02, WEATHER-03 |
| 15 — Travel Time | TRAVEL-01, TRAVEL-02, TRAVEL-03, TRAVEL-04 |

---
