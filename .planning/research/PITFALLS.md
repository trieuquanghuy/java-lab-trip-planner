# Pitfalls Research — v1.1 Trip Enhancement

## Common Mistakes When Adding These Features to Existing System

### Pitfall 1: OSRM Demo Server Reliability

**Risk:** OSRM demo server (`router.project-osrm.org`) has no SLA, may be slow or unavailable.

**Prevention:**
- Circuit breaker (Resilience4j) on OSRM calls — open after 3 consecutive failures
- Cache aggressively (24h TTL) — same route pair rarely changes
- Return "travel time unavailable" gracefully; never fail the whole trip view
- Frontend treats travel time as optional decoration, never blocking

**Phase to address:** Travel time implementation

### Pitfall 2: Weather API Overfetching

**Risk:** Fetching weather for every trip load, even for past dates or >16 days out.

**Prevention:**
- Only fetch for future dates within 16-day window
- Cache per (lat, lon, date) with 3h TTL
- Batch: one Open-Meteo call per trip (supports date ranges)
- Frontend checks date range before requesting

**Phase to address:** Weather implementation

### Pitfall 3: Public Share Link Security

**Risk:** Exposing user data through share endpoints, or token enumeration.

**Prevention:**
- Token = UUID v4 (128-bit, non-guessable, not sequential)
- Shared DTO NEVER includes: owner email, userId, edit endpoints
- Rate-limit public endpoint (prevent brute-force)
- Index `share_token` for fast lookup

**Phase to address:** Trip sharing implementation

### Pitfall 4: Gateway JWT Bypass Misconfiguration

**Risk:** Accidentally making ALL trip routes public, or breaking existing auth.

**Prevention:**
- Separate route definition (not an exception in existing filter)
- Place public route BEFORE catch-all authenticated route in priority
- Integration test: non-shared endpoints still require JWT
- Integration test: shared endpoint rejects write operations

**Phase to address:** Trip sharing implementation

### Pitfall 5: N+1 API Calls for Travel Time

**Risk:** N-1 individual OSRM calls for N items in a day.

**Prevention:**
- Use OSRM Table service — one call for all coordinates in a day
- Frontend requests travel times as batch per day
- Lazy-load (don't block initial trip render)

**Phase to address:** Travel time implementation

### Pitfall 6: Trip Duplication — Shared State Leakage

**Risk:** Duplicated trip inherits share_token or other owner-specific state.

**Prevention:**
- Explicitly null out: share_token, share_enabled, dates
- Generate new trip ID (obvious but worth asserting)
- Test: duplicated trip has no share link

**Phase to address:** Trip duplication implementation

### Pitfall 7: Open-Meteo Coordinate Grid Snapping

**Risk:** Different attractions in same city return identical weather (grid cell ~11km).

**Prevention:**
- Use city-level coordinates for weather, not per-attraction
- One weather fetch per trip (primary destination), not per item
- Document in UI: "Weather for the destination area"

**Phase to address:** Weather implementation

### Pitfall 8: Favorites Optimistic Update

**Risk:** TanStack Query cache shows stale data after unfavorite until refetch.

**Prevention:**
- Optimistic update: remove from list immediately on unfavorite
- Invalidate favorites query on mutation success
- Use `useMutation` with `onMutate` for instant feedback

**Phase to address:** Favorites implementation

### Pitfall 9: External API Timeout Cascade

**Risk:** Slow OSRM/Open-Meteo response → gateway timeout → 504.

**Prevention:**
- WebClient timeout: 5 seconds for both APIs
- Circuit breaker opens after 3 consecutive failures
- Return partial response (trip data without travel time/weather)
- Frontend renders trip first, loads enhancements async

**Phase to address:** Both external API phases

### Pitfall 10: Database Migration Conflicts

**Risk:** Multiple features adding columns to `trips` table — migration ordering issues.

**Prevention:**
- Number migrations sequentially (V3__, V4__, etc.)
- One migration per feature (sharing columns together)
- Sharing migration BEFORE duplication (duplication needs to know which columns to skip)

**Phase to address:** First backend phase (sharing or duplication)

## Summary: Top 5 Watch Items

1. **Circuit breaker + timeout on ALL external API calls** (OSRM + Open-Meteo)
2. **Public share endpoint security** (no PII leak, rate-limited, UUID v4 token)
3. **Batch API calls** (Table service for travel time, date-range for weather)
4. **Gateway route ordering** (public route before authenticated catch-all)
5. **Lazy-load external data** (trip renders first, enhancements load async)
