# 03 — Data Model

**Status**: Draft for review
**Last updated**: 2026-05-08

All tables live in PostgreSQL 16. Per-service schemas. Each service owns its
schema and ships its own Flyway migrations. No cross-schema foreign keys.

## 1. Schema map

| Schema | Owner service | Tables |
|--------|---------------|--------|
| `auth` | auth-service | `users`, `email_verification_tokens` |
| `trip` | trip-service | `trips`, `itinerary_days`, `itinerary_items`, `favorites` |
| `destination` | destination-service | `cities`, `destinations_cache` |

Schemas are created by `infra/postgres/init.sql` at compose startup. Flyway
within each service uses `schemas: <name>` so it is sandboxed.

## 2. ERD (per service)

### 2.1 auth-service

```
┌─────────────────────────────┐         ┌────────────────────────────────────┐
│ users                       │         │ email_verification_tokens          │
│─────────────────────────────│         │────────────────────────────────────│
│ id              UUID PK     │  1───◇  │ token            CHAR(64) PK       │
│ email           VARCHAR UK  │   *     │ user_id          UUID FK → users   │
│ password_hash   VARCHAR     │         │ expires_at       TIMESTAMPTZ       │
│ email_verified  BOOLEAN     │         │ consumed_at      TIMESTAMPTZ NULL  │
│ created_at      TIMESTAMPTZ │         │ created_at       TIMESTAMPTZ       │
│ updated_at      TIMESTAMPTZ │         └────────────────────────────────────┘
└─────────────────────────────┘
```

### 2.2 trip-service

```
┌──────────────────────────────────┐
│ trips                            │
│──────────────────────────────────│       ┌────────────────────────────────────┐
│ id                UUID PK        │       │ favorites                           │
│ user_id           UUID (no FK)   │ ┐     │────────────────────────────────────│
│ name              VARCHAR(120)   │ │     │ user_id          UUID  PK pt 1     │
│ start_date        DATE NULL      │ │     │ destination_ref  VARCHAR PK pt 2   │
│ end_date          DATE NULL      │ │     │ created_at       TIMESTAMPTZ        │
│ cover_image_url   VARCHAR NULL   │ │     └────────────────────────────────────┘
│ created_at        TIMESTAMPTZ    │ │
│ updated_at        TIMESTAMPTZ    │ │
└─────────────┬────────────────────┘ │
              │ 1                    │
              │                      │
              │ *                    │
   ┌──────────▼─────────────────┐    │
   │ itinerary_days             │    │
   │────────────────────────────│    │
   │ id           UUID PK       │    │
   │ trip_id      UUID FK→trips │    │
   │ day_date     DATE          │    │
   │ day_index    INT           │    │  (1-based; redundant w/ day_date for ergonomic ordering)
   └────────┬───────────────────┘    │
            │ 1                       │
            │                         │
            │ *                       │
  ┌─────────▼─────────────────────────┴────────┐
  │ itinerary_items                              │
  │──────────────────────────────────────────────│
  │ id                UUID  PK                   │
  │ itinerary_day_id  UUID  FK → itinerary_days  │
  │ destination_ref   VARCHAR (opaque)           │
  │ position          INT      (gap-spaced)      │
  │ time_slot         TIME NULL                  │
  │ note              VARCHAR(500) NULL          │
  │ created_at        TIMESTAMPTZ                │
  │ updated_at        TIMESTAMPTZ                │
  └──────────────────────────────────────────────┘
```

### 2.3 destination-service

```
┌──────────────────────────────────┐    ┌──────────────────────────────────────┐
│ cities                           │    │ destinations_cache                    │
│──────────────────────────────────│    │──────────────────────────────────────│
│ id           BIGINT PK           │    │ provider_ref     VARCHAR(80)  PK     │
│ geoname_id   BIGINT UK           │    │ name             VARCHAR(200)        │
│ name         VARCHAR(200)        │    │ category         VARCHAR(80) NULL    │
│ country      VARCHAR(120)        │    │ rating           NUMERIC(3,1) NULL   │
│ country_code CHAR(2)             │    │ lat              NUMERIC(9,6)        │
│ lat          NUMERIC(9,6)        │    │ lng              NUMERIC(9,6)        │
│ lng          NUMERIC(9,6)        │    │ address          VARCHAR(400) NULL   │
│ population   BIGINT              │    │ photos           JSONB    (array<url>)│
│ search_tsv   TSVECTOR (gen col)  │    │ opening_hours    JSONB NULL          │
└──────────────────────────────────┘    │ raw              JSONB    (provider) │
                                        │ fetched_at       TIMESTAMPTZ          │
                                        └──────────────────────────────────────┘
```

## 3. Table specifications

### 3.1 `auth.users`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PRIMARY KEY, default `gen_random_uuid()` | |
| `email` | VARCHAR(254) | NOT NULL, UNIQUE (lower-case stored) | RFC 5321 max length |
| `password_hash` | VARCHAR(72) | NOT NULL | bcrypt result |
| `email_verified` | BOOLEAN | NOT NULL, DEFAULT FALSE | |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

Indexes: `users_pkey (id)`, `users_email_key (email)`.

### 3.2 `auth.email_verification_tokens`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `token` | CHAR(64) | PRIMARY KEY | hex of 32 random bytes |
| `user_id` | UUID | NOT NULL, FK → `users(id)` ON DELETE CASCADE | |
| `expires_at` | TIMESTAMPTZ | NOT NULL | now + 24h on insert |
| `consumed_at` | TIMESTAMPTZ | NULL | set when used; prevents reuse |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

Indexes: PK; partial index `WHERE consumed_at IS NULL` for fast "unconsumed only" lookups.

Cleanup: a daily scheduled job in auth-service deletes rows where
`expires_at < NOW() - 7 days`.

### 3.3 `trip.trips`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK, default `gen_random_uuid()` | |
| `user_id` | UUID | NOT NULL | No FK — owner is `auth.users` in another service |
| `name` | VARCHAR(120) | NOT NULL, length ≥ 1 | |
| `start_date` | DATE | NULL until set | |
| `end_date` | DATE | NULL; CHECK `end_date >= start_date` when both set | |
| `cover_image_url` | VARCHAR(2048) | NULL | URL only in v1 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

Indexes: `trips_user_id_idx (user_id, created_at DESC)` — primary access pattern is "list my trips, newest first".

### 3.4 `trip.itinerary_days`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `trip_id` | UUID | NOT NULL, FK → `trips(id)` ON DELETE CASCADE | |
| `day_date` | DATE | NOT NULL | |
| `day_index` | INT | NOT NULL, ≥ 1 | 1-based; equals `day_date - trip.start_date + 1` |

Constraints: `UNIQUE(trip_id, day_date)` and `UNIQUE(trip_id, day_index)`.
Indexes: `(trip_id, day_index)`.

Materialization: when `trips.start_date`/`end_date` are set or changed, the
service deletes/upserts rows so exactly one row exists per date in the range.
If the range shrinks and would orphan items, the API requires the client to
have confirmed (returns `409 Conflict` with item list otherwise).

### 3.5 `trip.itinerary_items`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `id` | UUID | PK | |
| `itinerary_day_id` | UUID | NOT NULL, FK → `itinerary_days(id)` ON DELETE CASCADE | |
| `destination_ref` | VARCHAR(80) | NOT NULL | Format: `<provider>:<id>` |
| `position` | INT | NOT NULL | gap-spaced (100, 200, 300…) |
| `time_slot` | TIME | NULL | local time, no TZ |
| `note` | VARCHAR(500) | NULL | sanitized server-side |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |
| `updated_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

Indexes: `(itinerary_day_id, position)` — primary access pattern is rendering a day in order.

#### Position algorithm
- Append: take `MAX(position) + 100`. First item: 100.
- Insert between existing items: take midpoint. e.g. between 200 and 300 → 250.
- Move between days: same logic, with the new day's positions.
- Reindex: when gap < 2, run a single `UPDATE` to renumber the day's items by 100s. Triggered lazily.

### 3.6 `trip.favorites`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `user_id` | UUID | NOT NULL | composite PK part 1 |
| `destination_ref` | VARCHAR(80) | NOT NULL | composite PK part 2 |
| `created_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

PK: `(user_id, destination_ref)`. Index `(user_id, created_at DESC)` for "my favorites, newest first" listing.

### 3.7 `destination.cities`

Seeded from GeoNames `cities15000.txt` (cities of population ≥ 15,000, ~25k rows).

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK |
| `geoname_id` | BIGINT | UNIQUE |
| `name` | VARCHAR(200) | NOT NULL |
| `country` | VARCHAR(120) | NOT NULL |
| `country_code` | CHAR(2) | NOT NULL |
| `lat` | NUMERIC(9,6) | NOT NULL |
| `lng` | NUMERIC(9,6) | NOT NULL |
| `population` | BIGINT | NOT NULL |
| `search_tsv` | TSVECTOR | GENERATED ALWAYS AS `to_tsvector('simple', unaccent(name) \|\| ' ' \|\| unaccent(country))` STORED |

Indexes:
- `cities_search_tsv_idx` GIN(`search_tsv`) — for FTS
- `cities_name_trgm_idx` GIN(`name gin_trgm_ops`) — for prefix/partial match (`pg_trgm` extension)
- `cities_country_idx` (`country`)

The `unaccent` and `pg_trgm` extensions are enabled by `init.sql`.

### 3.8 `destination.destinations_cache`

| Column | Type | Constraints | Notes |
|--------|------|-------------|-------|
| `provider_ref` | VARCHAR(80) | PK | `<provider>:<id>` |
| `name` | VARCHAR(200) | NOT NULL | |
| `category` | VARCHAR(80) | NULL | normalized to top-level Foursquare category |
| `rating` | NUMERIC(3,1) | NULL | 0.0 — 10.0 |
| `lat` | NUMERIC(9,6) | NOT NULL | |
| `lng` | NUMERIC(9,6) | NOT NULL | |
| `address` | VARCHAR(400) | NULL | |
| `photos` | JSONB | NOT NULL DEFAULT `'[]'::jsonb` | array of URL strings |
| `opening_hours` | JSONB | NULL | structure: `{ "mon":[{"open":"09:00","close":"17:00"}], … }` |
| `raw` | JSONB | NOT NULL | unmodified provider response for debugging |
| `fetched_at` | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() | |

Indexes:
- PK
- `destinations_cache_geo_idx` `gist (point(lng, lat))` — for radial nearby queries (alternative: PostGIS, deferred)
- `destinations_cache_fetched_at_idx (fetched_at)` — for staleness sweeps

Stale rows (older than 24h) are eligible for refetch on next request that
needs them; not pre-emptively refreshed.

## 4. Migrations strategy

Each service has its own `db/migration/` folder with versioned scripts:

```
services/auth-service/src/main/resources/db/migration/
  V1__create_users.sql
  V2__create_email_verification_tokens.sql

services/trip-service/src/main/resources/db/migration/
  V1__create_trips.sql
  V2__create_itinerary_days.sql
  V3__create_itinerary_items.sql
  V4__create_favorites.sql

services/destination-service/src/main/resources/db/migration/
  V1__enable_extensions.sql      -- pg_trgm, unaccent, btree_gist
  V2__create_cities.sql
  V3__seed_cities.sql            -- COPY from /seeds/cities-15000.tsv
  V4__create_destinations_cache.sql
```

Each Flyway instance is configured with `schemas: <its-schema>` and
`createSchemas: false` (init.sql owns schema creation). This way each
service's Flyway only manipulates its own tables.

### `infra/postgres/init.sql`
```sql
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS trip;
CREATE SCHEMA IF NOT EXISTS destination;

CREATE EXTENSION IF NOT EXISTS pgcrypto;     -- gen_random_uuid
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- one DB user per service, with USAGE only on its own schema
CREATE USER auth_svc        WITH PASSWORD 'auth_svc';
CREATE USER trip_svc        WITH PASSWORD 'trip_svc';
CREATE USER destination_svc WITH PASSWORD 'destination_svc';

GRANT ALL ON SCHEMA auth        TO auth_svc;
GRANT ALL ON SCHEMA trip        TO trip_svc;
GRANT ALL ON SCHEMA destination TO destination_svc;
```

In production, passwords come from secrets manager; here they are local-only.

## 5. Data lifecycle

| Entity | Created when | Deleted when |
|--------|--------------|--------------|
| `users` | signup | manual (admin) — no UI delete in v1 |
| `email_verification_tokens` | signup | TTL sweep daily; or on `consumed_at` set |
| `trips` | user creates | user deletes |
| `itinerary_days` | trip dates set/changed | cascade with trip; recreated on date change |
| `itinerary_items` | user adds destination | user removes; cascade with day |
| `favorites` | user favorites | user unfavorites |
| `cities` | seed migration | never (re-seeded only by re-running migration) |
| `destinations_cache` | first lookup | never deleted; refreshed on staleness |

Privacy: when a user is deleted (manual op in v1), trip-service rows for that
`user_id` should also be deleted. Since there is no FK across services, this
is documented as a manual two-step operation in v1; a `DELETE` event over a
message broker is on the v2 backlog.

## 6. Sample data shapes

### `destinations_cache.photos` (JSONB)
```json
["https://opentripmap.com/img/abc.jpg", "https://opentripmap.com/img/def.jpg"]
```

### `destinations_cache.opening_hours` (JSONB)
```json
{
  "mon": [{"open": "09:00", "close": "17:00"}],
  "tue": [{"open": "09:00", "close": "17:00"}],
  "wed": null,
  "thu": [{"open": "09:00", "close": "17:00"}],
  "fri": [{"open": "09:00", "close": "20:00"}],
  "sat": [{"open": "10:00", "close": "20:00"}],
  "sun": null
}
```

### `destinations_cache.raw` (JSONB)
```json
{
  "source": "opentripmap",
  "fetched_at": "2026-05-08T10:30:00Z",
  "payload": { "...": "verbatim provider response" }
}
```
