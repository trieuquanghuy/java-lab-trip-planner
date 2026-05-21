# Architecture Research — v1.1 Trip Enhancement

## Existing Architecture

```
[Frontend SPA] → [API Gateway :8080] → [Eureka :8761]
                      ↓
         ┌───────────┼───────────┐
         ↓           ↓           ↓
   [auth :8081] [trip :8082] [dest :8083]
         ↓           ↓           ↓
      [PostgreSQL — auth/trip/destination schemas]
                      ↓
                  [Redis :6379]
```

## Integration Architecture for New Features

### Travel Time/Distance

**Service ownership:** `trip-service` — already knows item order and destination coordinates. Avoids inter-service call.

**Flow:**
```
Frontend → Gateway → trip-service → OSRM (external)
                                         ↓
                                     Redis cache
```

**New components:**
- `TravelTimeService` in trip-service — calls OSRM Route/Table API
- `TravelTimeResponse` DTO — duration (seconds), distance (meters), transport mode
- Redis cache key: `travel:{originLat},{originLon}:{destLat},{destLon}:{mode}` (TTL: 24h)
- New endpoint: `GET /api/trips/{tripId}/days/{dayId}/travel-times`
- Circuit breaker via Resilience4j on OSRM calls

### Weather Forecast

**Service ownership:** `trip-service` — owns trip dates and can resolve destination coordinates.

**Flow:**
```
Frontend → Gateway → trip-service → Open-Meteo (external)
                                         ↓
                                     Redis cache
```

**New components:**
- `WeatherService` in trip-service — calls Open-Meteo Forecast API
- `DailyWeather` DTO — date, weatherCode, tempMin, tempMax, precipitation, windSpeed
- Redis cache key: `weather:{lat},{lon}:{date}` (TTL: 3h)
- New endpoint: `GET /api/trips/{tripId}/weather`
- Only fetches for future dates within 16-day window

### Trip Sharing (Public Link)

**Service ownership:** `trip-service`

**Key changes:**
- **Gateway:** Add route for `/api/trips/shared/**` that SKIPS JWT filter
- **trip-service:** New `SharedTripController` with public endpoint
- **Database:** Add `share_token UUID` + `share_enabled BOOLEAN` to trips table

**New components:**
- `share_token` column (nullable UUID, unique index) on `trips` table
- `PUT /api/trips/{tripId}/share` — generate/regenerate token
- `DELETE /api/trips/{tripId}/share` — revoke sharing
- `GET /api/trips/shared/{token}` — PUBLIC, no auth, returns read-only trip

**Security:**
- Share token = UUID v4 (128-bit, non-guessable)
- Shared DTO strips: owner email, userId, edit endpoints
- Rate-limit public endpoint at gateway

### Trip Duplication

**Service ownership:** `trip-service`

**New components:**
- `POST /api/trips/{tripId}/duplicate` — deep copies trip + days + items
- Copies: name ("Copy of X"), days (relative order), items (all fields)
- Does NOT copy: share_token, original dates (reset to null)
- Owner verification: only trip owner can duplicate

### Favorites Page (Frontend only)

**No architecture changes.** Backend API exists:
- `POST /api/destinations/{id}/favorite`
- `DELETE /api/destinations/{id}/favorite`
- `GET /api/destinations/favorites`

## New API Endpoints Summary

| Method | Path | Auth | Service |
|--------|------|------|---------|
| GET | `/api/trips/{id}/days/{dayId}/travel-times` | JWT | trip-service |
| GET | `/api/trips/{id}/weather` | JWT | trip-service |
| PUT | `/api/trips/{id}/share` | JWT (owner) | trip-service |
| DELETE | `/api/trips/{id}/share` | JWT (owner) | trip-service |
| GET | `/api/trips/shared/{token}` | **NONE** | trip-service |
| POST | `/api/trips/{id}/duplicate` | JWT (owner) | trip-service |

## Gateway Route Update

Add public route BEFORE authenticated catch-all:
```yaml
- id: trip-shared-public
  uri: lb://trip-service
  predicates:
    - Path=/api/trips/shared/**
  # No JWT filter
```

## Database Migrations

**trip-service (new migration):**
```sql
ALTER TABLE trip.trips ADD COLUMN share_token UUID UNIQUE;
ALTER TABLE trip.trips ADD COLUMN share_enabled BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_trips_share_token ON trip.trips(share_token) WHERE share_token IS NOT NULL;
```

## Suggested Build Order

1. **Favorites page** — Frontend only, zero backend changes
2. **Trip duplication** — One migration (optional), one endpoint, simple CRUD
3. **Trip sharing** — Migration + gateway route + public controller
4. **Weather integration** — External API + cache + new service class
5. **Travel time** — External API + cache + UI integration in itinerary view
