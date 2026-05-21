# Research Summary — v1.1 Trip Enhancement

## Stack Additions

| Feature | External API | Cost | API Key | Integration |
|---------|-------------|------|---------|-------------|
| Travel time/distance | OSRM Demo (`router.project-osrm.org`) | Free (no SLA) | None | Spring WebClient + Redis cache |
| Weather forecast | Open-Meteo (`api.open-meteo.com`) | Free (<10K/day) | None | Spring WebClient + Redis cache |
| Trip sharing | None | — | — | UUID token + gateway public route |
| Trip duplication | None | — | — | Deep-copy CRUD |
| Favorites page | None | — | — | Frontend only (backend exists) |

**Zero new paid dependencies. Zero new API keys. Zero new infrastructure.**

## Feature Table Stakes

| Feature | Must-Have Behaviors |
|---------|-------------------|
| Travel time | Duration + distance between consecutive day items; auto-recalculate on reorder |
| Weather | Daily temp high/low + weather icon + precipitation for trip dates (≤16 days future) |
| Sharing | Generate link → read-only view without auth → owner can revoke |
| Duplication | One-click copy → new trip with all items → dates reset |
| Favorites | Page listing favorites → unfavorite → navigate to detail |

## Architecture Impact

- **New endpoints:** 6 (4 authenticated, 1 public, 1 frontend-only)
- **Services modified:** trip-service (primary), api-gateway (one route)
- **Database changes:** 2 new columns + 1 index on `trip.trips`
- **External calls:** 2 APIs (OSRM, Open-Meteo) — both wrapped in circuit breaker + cache
- **Frontend pages:** 2 new (favorites, shared trip view), 1 modified (trip detail with weather/travel)

## Watch Out For

1. **Circuit breaker on external APIs** — OSRM has no SLA, Open-Meteo rate-limits at 10K/day
2. **Share link security** — UUID v4 token, stripped PII, rate-limited public endpoint
3. **Gateway route ordering** — public `/api/trips/shared/**` route must precede authenticated catch-all
4. **Batch API calls** — Use OSRM Table (one call per day), Open-Meteo date ranges (one call per trip)
5. **Async UI loading** — Trip renders immediately; travel time + weather load as enhancements

## Recommended Build Order

1. **Favorites page** — Frontend only, closes v1.0 carry-over, zero risk
2. **Trip duplication** — Simple CRUD, no external deps, builds confidence
3. **Trip sharing** — Moderate: gateway change + public endpoint + security
4. **Weather forecast** — External API but simple (one endpoint, good caching)
5. **Travel time** — Most complex (external API + modifies existing itinerary UI)

## Constraints Confirmed

- ✅ Free-tier only: Both OSRM and Open-Meteo require zero payment
- ✅ No new infrastructure: Uses existing Redis for caching, existing PostgreSQL for storage
- ✅ Local-only deployment: No cloud services required
- ✅ Existing architecture preserved: No new services, no broker, no new database
