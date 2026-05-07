# 04 — API Specification

**Status**: Draft for review
**Last updated**: 2026-05-08

All endpoints are reachable through `api-gateway` at `http://localhost:8080`.
The SPA never calls a backend service directly. Direct service ports
(8081–8083) are for debugging only.

## 1. Conventions

- Content type: `application/json; charset=utf-8` for requests and responses.
- IDs: UUID v4 strings, lowercase canonical form.
- Timestamps: ISO 8601 in UTC with `Z` suffix (`2026-05-08T14:23:00Z`).
- Dates: ISO 8601 calendar date (`2026-05-08`).
- Times: `HH:mm` 24-hour, no timezone.
- Pagination: when applicable, query params `?page=0&size=20`. Response wraps in `{ items, page, size, total }`.
- Authentication: `Authorization: Bearer <accessToken>` header. Some endpoints accept a refresh-token cookie additionally.
- All mutating endpoints require auth unless explicitly noted as public.

## 2. Error contract (RFC 7807)

```json
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "https://tripplanner.example/errors/auth.invalid_credentials",
  "title": "Invalid credentials",
  "status": 400,
  "code": "auth.invalid_credentials",
  "detail": "Email or password is incorrect",
  "instance": "/api/auth/login",
  "traceId": "00-2f5e3a..."
}
```

`code` is the stable identifier the UI keys off. `title`/`detail` are human-readable. See §6 for the catalog.

## 3. Auth API (auth-service)

All routes under `/api/auth/**`. **Public** unless noted.

### POST /api/auth/signup — public
```http
POST /api/auth/signup
Content-Type: application/json

{ "email": "mai@example.com", "password": "correcthorsebattery" }

→ 201 Created
{ "userId": "5f3c…" }
```
Errors: `auth.email_already_registered` (409), `auth.weak_password` (400, generic), `auth.invalid_email` (400).

### GET /api/auth/verify?token=… — public
```http
GET /api/auth/verify?token=abc...64hex

→ 200 OK
{ "verified": true }
```
Errors: `auth.token_invalid` (400), `auth.token_expired` (400). Idempotent — reusing a consumed token returns `auth.token_invalid`.

### POST /api/auth/login — public
```http
POST /api/auth/login
Content-Type: application/json

{ "email": "mai@example.com", "password": "correcthorsebattery" }

→ 200 OK
Set-Cookie: refresh_token=…; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800

{
  "accessToken": "eyJhbGciOi…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": "5f3c…", "email": "mai@example.com" }
}
```
Errors: `auth.invalid_credentials` (400, generic), `auth.email_not_verified` (403).

### POST /api/auth/refresh — uses refresh-token cookie
```http
POST /api/auth/refresh
Cookie: refresh_token=…

→ 200 OK
Set-Cookie: refresh_token=NEW; HttpOnly; …   (rotation)

{ "accessToken": "…", "expiresIn": 900 }
```
Errors: `auth.refresh_invalid` (401).

### POST /api/auth/logout — authenticated
```http
POST /api/auth/logout
Authorization: Bearer …

→ 204 No Content
Set-Cookie: refresh_token=; Max-Age=0
```

## 4. Destination & Search API (destination-service)

All routes are **public-readable** (search and discovery don't require login).
Rate-limited by IP at the gateway.

### GET /api/search?q=…&type=…
```http
GET /api/search?q=lon&type=city,country&limit=5

→ 200 OK
{
  "items": [
    { "type": "city",    "name": "London",       "country": "United Kingdom", "lat": 51.5074, "lng": -0.1278 },
    { "type": "city",    "name": "Long Beach",   "country": "United States",  "lat": 33.7701, "lng": -118.1937 },
    { "type": "country", "name": "Lebanon",      "country": "Lebanon",        "lat": 33.8547, "lng": 35.8623 }
  ]
}
```
- `q` required, ≥1 char.
- `type` optional CSV; default `city,country`.
- `limit` optional, max 5 (per FR-1).
- Backed by Postgres FTS on `cities` table (city + country) plus Redis cache (`SEARCH:{q}:{type}` TTL 1h).
- Returns empty array (not 404) when no matches.

### GET /api/destinations?lat=…&lng=…&radius=…&limit=…
```http
GET /api/destinations?lat=51.5074&lng=-0.1278&radius=20000&limit=20

→ 200 OK
{
  "items": [
    {
      "providerRef": "otm:N12345",
      "name": "British Museum",
      "category": "museum",
      "rating": 4.7,
      "photoUrl": "https://...",
      "lat": 51.5194,
      "lng": -0.1270
    }, …
  ],
  "fromCache": true,
  "providerStatus": { "openTripMap": "ok", "foursquare": "circuit_open" }
}
```
- `radius` in meters; default 20,000 for cities.
- Pipeline: cache (`POI:{lat}:{lng}:{radius}` 24h TTL) → OpenTripMap → Foursquare enrichment → cache write-through.
- `providerStatus` makes degraded-mode visible to the UI (per NFR-3).

### GET /api/destinations/{providerRef}
```http
GET /api/destinations/otm:N12345

→ 200 OK
{
  "providerRef": "otm:N12345",
  "name": "British Museum",
  "category": "museum",
  "shortDescription": "…",
  "rating": 4.7,
  "lat": 51.5194,
  "lng": -0.1270,
  "address": "Great Russell St, London WC1B 3DG",
  "website": "https://britishmuseum.org",
  "photos": [ "https://...", "https://..." ],
  "openingHours": {
    "mon": [{ "open": "10:00", "close": "17:00" }],
    "tue": null, …
  },
  "fromCache": true,
  "fetchedAt": "2026-05-08T10:30:00Z"
}
```
Errors: `destination.not_found` (404).

## 5. Trip API (trip-service)

All routes under `/api/trips/**` and `/api/favorites/**`. **Authenticated.**
Ownership enforced at service layer (`trip.user_id == jwt.userId`). Cross-user
access returns `404` not `403` (does not leak existence).

### POST /api/trips
```http
POST /api/trips
Authorization: Bearer …
Content-Type: application/json

{ "name": "Tokyo 2026", "startDate": "2026-09-10", "endDate": "2026-09-15" }

→ 201 Created
{ "id": "7a1d…", "name": "Tokyo 2026", "startDate": "2026-09-10", "endDate": "2026-09-15",
  "coverImageUrl": null, "createdAt": "…", "updatedAt": "…" }
```
- `name` required, length 1–120.
- `startDate`, `endDate` optional at creation; if both, `start ≤ end`.
- Days are materialized synchronously when both dates set.

Errors: `trip.invalid_date_range` (400), `validation.failed` (400 with field details).

### GET /api/trips
```http
GET /api/trips
Authorization: Bearer …

→ 200 OK
{
  "items": [
    { "id": "7a1d…", "name": "Tokyo 2026", "startDate": "2026-09-10", "endDate": "2026-09-15",
      "coverImageUrl": "https://…", "itemCount": 12, "updatedAt": "…" }, …
  ]
}
```
Sorted by `updated_at DESC`.

### GET /api/trips/{id}
```http
GET /api/trips/7a1d…
Authorization: Bearer …

→ 200 OK
{
  "id": "7a1d…",
  "name": "Tokyo 2026",
  "startDate": "2026-09-10",
  "endDate": "2026-09-15",
  "coverImageUrl": null,
  "days": [
    {
      "id": "d1…",
      "dayDate": "2026-09-10",
      "dayIndex": 1,
      "items": [
        { "id": "i1…", "destinationRef": "otm:N123", "position": 100, "timeSlot": "10:00", "note": null },
        { "id": "i2…", "destinationRef": "otm:N456", "position": 200, "timeSlot": null, "note": "book ahead" }
      ]
    }, …
  ]
}
```
Returns `404` if not owned by current user.

### PATCH /api/trips/{id}
```http
PATCH /api/trips/7a1d…
Authorization: Bearer …

{ "name": "Tokyo Sept", "endDate": "2026-09-13", "confirmShorten": true }
```
- Any subset of `name`, `startDate`, `endDate`, `coverImageUrl`.
- If shrinking date range orphans existing items, response is `409 Conflict` with body listing affected items unless `confirmShorten: true`.

Errors: `trip.shorten_requires_confirmation` (409), `trip.invalid_date_range` (400).

### DELETE /api/trips/{id}
```http
DELETE /api/trips/7a1d…
Authorization: Bearer …

→ 204 No Content
```
Cascades to days, items.

### POST /api/trips/{tripId}/days/{dayId}/items
```http
POST /api/trips/7a1d…/days/d1…/items
Authorization: Bearer …

{ "destinationRef": "otm:N123", "note": null, "timeSlot": null }

→ 201 Created
{ "id": "i1…", "itineraryDayId": "d1…", "destinationRef": "otm:N123",
  "position": 300, "timeSlot": null, "note": null }
```
Position auto-assigned to `MAX(position) + 100` for the day.

Errors: `trip.day_not_in_trip` (400), `validation.failed` (400).

### PATCH /api/trips/{tripId}/items/{itemId}
```http
PATCH /api/trips/7a1d…/items/i1…
Authorization: Bearer …

{ "position": 250, "itineraryDayId": "d2…", "timeSlot": "11:30", "note": "remember camera" }
```
- Any subset of `position`, `itineraryDayId`, `timeSlot`, `note`.
- Moving to a different day requires `itineraryDayId` belonging to the same trip.

Errors: `trip.day_not_in_trip` (400).

### DELETE /api/trips/{tripId}/items/{itemId}
```http
DELETE /api/trips/7a1d…/items/i1…
Authorization: Bearer …

→ 204 No Content
```

### POST /api/favorites
```http
POST /api/favorites
Authorization: Bearer …

{ "destinationRef": "otm:N123" }

→ 201 Created
{ "destinationRef": "otm:N123", "createdAt": "…" }
```
Idempotent: re-favoriting returns the existing row with `200`.

### DELETE /api/favorites/{ref}
```http
DELETE /api/favorites/otm:N123
Authorization: Bearer …

→ 204 No Content
```

### GET /api/favorites
```http
GET /api/favorites
Authorization: Bearer …

→ 200 OK
{ "items": [ { "destinationRef": "otm:N123", "createdAt": "…" }, … ] }
```
For UI render, the SPA hydrates each `destinationRef` via the destination-service in parallel.

## 6. Error code catalog

| Code | HTTP | Service | When |
|------|------|---------|------|
| `validation.failed` | 400 | all | Bean Validation rejected request body or query params |
| `auth.email_already_registered` | 409 | auth | signup with existing email |
| `auth.weak_password` | 400 | auth | password < 8 chars |
| `auth.invalid_email` | 400 | auth | malformed email |
| `auth.invalid_credentials` | 400 | auth | login failure (generic) |
| `auth.email_not_verified` | 403 | auth | login before verify |
| `auth.token_invalid` | 400 | auth | verify token unknown/consumed |
| `auth.token_expired` | 400 | auth | verify token > 24h |
| `auth.refresh_invalid` | 401 | auth | refresh token bad/missing |
| `auth.unauthorized` | 401 | gateway | missing/invalid JWT on protected route |
| `auth.rate_limited` | 429 | gateway | login throttle |
| `trip.not_found` | 404 | trip | trip not owned by user (or actually missing) |
| `trip.invalid_date_range` | 400 | trip | start > end |
| `trip.shorten_requires_confirmation` | 409 | trip | shrinking range would orphan items |
| `trip.day_not_in_trip` | 400 | trip | targeted day belongs to a different trip |
| `destination.not_found` | 404 | destination | provider lookup failed and not in cache |
| `destination.provider_unavailable` | 503 | destination | provider down AND no cache |

## 7. Rate limits (gateway)

| Route pattern | Window | Limit | Key |
|---------------|--------|-------|-----|
| `POST /api/auth/login` | 15 min | 5 | IP + email |
| `POST /api/auth/signup` | 1 h | 3 | IP |
| `GET /api/search` | 1 min | 60 | IP |
| `GET /api/destinations*` | 1 min | 60 | IP |
| `* /api/trips/*` | 1 min | 120 | userId |

Implementation: Spring Cloud Gateway `RequestRateLimiter` filter backed by Redis.

## 8. CORS

Allowed origins:
- Dev: `http://localhost:5173`
- Prod (when deployed): the SPA's deployed origin only.

Allowed methods: GET, POST, PATCH, DELETE, OPTIONS.
Allowed headers: Authorization, Content-Type, X-Request-Id.
Exposed headers: X-Request-Id.
Allow credentials: true (for refresh-token cookie).

## 9. Versioning

v1 lives at `/api/**` without an explicit version segment. If a breaking
change is needed pre-v2, introduce `/api/v2/**` and keep `/api/**` aliased for
a deprecation window. v1 versioning is mentioned only for future-proofing,
not introduced now.
