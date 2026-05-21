# Stack Research — v1.1 Trip Enhancement

## Existing Stack (DO NOT re-research)

- Java 21 + Spring Boot 3.5.x + Gradle Kotlin DSL
- PostgreSQL 16 (schema-per-service)
- Redis 7 (caching)
- React 18 + Vite 6 + TypeScript 5.8 + Tailwind v3.4 + shadcn/ui
- Leaflet + react-leaflet v4 (maps)
- TanStack Query v5 + Zustand v5 (state)
- Spring Cloud Gateway + Eureka

## New Stack Additions for v1.1

### Routing / Travel Time

| Option | Type | Free Tier | API Key | Why |
|--------|------|-----------|---------|-----|
| **OSRM Demo Server** | Public API | Unlimited (demo, no SLA) | None | `router.project-osrm.org` — zero config, returns duration + distance via Table/Route service. Good for portfolio. |
| OpenRouteService | Hosted API | 2,000 req/day free | Required (free signup) | Matrix endpoint, more transport profiles (foot, bike, car). Rate limited. |
| OSRM self-hosted | Docker | Unlimited | None | Requires downloading OSM data (~1.5 GB for planet). Overkill for portfolio. |

**Recommendation: OSRM Demo Server** — No signup, no API key, no credit card. Table service returns NxN duration/distance matrix in one call. Sufficient for calculating travel time between consecutive itinerary items. Fallback: OpenRouteService if OSRM demo is down.

**Key endpoint — Route (pair):**
```
GET http://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=false
```
Returns `routes[0].duration` (seconds) and `routes[0].distance` (meters).

**Key endpoint — Table (batch):**
```
GET http://router.project-osrm.org/table/v1/driving/{coords}?annotations=duration,distance
```
Returns `durations[][]` (seconds) and `distances[][]` (meters) for all pairs.

### Weather Forecast

| Option | Type | Free Tier | API Key | Why |
|--------|------|-----------|---------|-----|
| **Open-Meteo** | Public API | 10,000 req/day (non-commercial) | None | Daily forecasts up to 16 days, no key needed, JSON response, WMO weather codes. |
| OpenWeatherMap | Hosted API | 1,000 req/day | Required | Well-known but requires signup + key. |
| WeatherAPI | Hosted API | 1M req/month (14 days) | Required | Good but requires signup. |

**Recommendation: Open-Meteo** — No API key, no signup, generous free tier, up to 16-day forecast with daily aggregates (temp min/max, weather code, precipitation, wind). Perfect for trip dates.

**Key endpoint:**
```
GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,wind_speed_10m_max&timezone=auto&start_date={yyyy-mm-dd}&end_date={yyyy-mm-dd}
```

Returns daily arrays: `weather_code` (WMO int), `temperature_2m_max/min` (°C), `precipitation_sum` (mm), `wind_speed_10m_max` (km/h).

### Trip Sharing (Public Links)

No new external dependencies. Requires:
- UUID-based share token generation (Java `UUID.randomUUID()`)
- New `share_token` column on trip table
- Gateway route that bypasses JWT validation for `/api/trips/shared/{token}`
- Read-only trip DTO (no edit actions exposed)

### Trip Duplication

No new external dependencies. Pure CRUD:
- Deep-copy trip + days + itinerary items
- New name: "Copy of {original}"
- Reset dates to null (user sets new dates)

### Favorites (FR-21)

Backend already built in v1.0. Frontend page missing. No new dependencies needed.

## Integration Points

| Feature | Service | New dependency |
|---------|---------|---------------|
| Travel time | trip-service → OSRM | Spring WebClient (already available) |
| Weather | trip-service → Open-Meteo | Spring WebClient (already available) |
| Sharing | trip-service | None (UUID built-in) |
| Duplication | trip-service | None |
| Favorites | frontend only | None |

## What NOT to Add

- **No message broker** — All features are request/response; no async needed
- **No file storage** — File upload was descoped from v1.1
- **No new database** — All data fits in existing PostgreSQL schemas
- **No Redis changes** — Existing cache patterns sufficient; weather can use same TTL cache
