# Phase 3: Destination Service — Search - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-10
**Phase:** 03-destination-service-search
**Areas discussed:** GeoNames seed ingestion, Search ranking + query strategy, Redis caching layer, Response shape + edge cases

---

## GeoNames seed ingestion

| Option | Description | Selected |
|--------|-------------|----------|
| Flyway SQL migration | V1__seed_cities.sql with INSERT/COPY. Runs once on first boot, versioned in git. Keeps DB state reproducible. | ✓ |
| Spring Boot CommandLineRunner | Java code downloads/parses TSV at app startup. More flexible but slower startup. | |
| External shell script | Separate script in infra/seeds/ that downloads and loads via psql COPY. Manual run required. | |

**User's choice:** Flyway SQL migration
**Notes:** Aligns with existing Flyway setup from prior phases.

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-process + commit SQL file | Download cities15000.txt, transform to SQL, commit resulting .sql migration. ~2.5MB. Fully reproducible. | ✓ |
| Commit raw TSV + COPY FROM | Commit raw TSV in infra/seeds/, use COPY FROM in migration. Requires volume mount. | |
| Download at migration time | Flyway migration downloads via pl/pgsql + pg_http. Network dependency at boot. | |

**User's choice:** Pre-process + commit SQL file
**Notes:** No network needed at runtime.

| Option | Description | Selected |
|--------|-------------|----------|
| Enable in migration (recommended) | CREATE EXTENSION IF NOT EXISTS unaccent, pg_trgm. Required for TSVECTOR + trigram index. | ✓ |
| Skip for now | Skip accent-folding. Simpler but 'München' won't match 'Munich'. | |

**User's choice:** Enable in migration
**Notes:** Required by success criterion #5.

---

## Search ranking + query strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Population-weighted FTS | ts_rank * LOG(population + 1) DESC. London UK ranks above London Ontario. | ✓ |
| Trigram similarity only | Pure pg_trgm score. Good for typos but doesn't leverage TSVECTOR. | |
| Hybrid FTS + trigram | Combined score. Most robust but more complex. | |

**User's choice:** Population-weighted FTS
**Notes:** Matches ROADMAP Pitfall 11 note.

| Option | Description | Selected |
|--------|-------------|----------|
| FTS prefix with :* operator | Convert 'lon' to 'lon:*' for to_tsquery. Leverages GIN index. | ✓ |
| ILIKE prefix match | WHERE name ILIKE 'lon%'. Bypasses GIN index on search_tsv. | |
| FTS primary + ILIKE fallback | Two queries if FTS returns <5. Most comprehensive. | |

**User's choice:** FTS prefix with :* operator
**Notes:** Works well with pre-computed TSVECTOR approach.

| Option | Description | Selected |
|--------|-------------|----------|
| simple | No stemming, no stop words. Predictable for proper nouns. | ✓ |
| english | Applies stemming, removes stop words. Overkill for city names. | |

**User's choice:** simple
**Notes:** Matches data model's generated column definition.

---

## Redis caching layer

| Option | Description | Selected |
|--------|-------------|----------|
| SEARCH:{q}:{type}:{limit} | Normalized query as key. Simple, deterministic, debuggable. | ✓ |
| Hash-based key | SHA256 of params. Shorter but opaque. | |

**User's choice:** SEARCH:{q}:{type}:{limit}
**Notes:** Easy to inspect in Redis CLI during debugging.

| Option | Description | Selected |
|--------|-------------|----------|
| 1h TTL, no manual invalidation | API spec says 1h. Data is static. Simplest approach. | ✓ |
| 24h TTL | Longer cache, data doesn't change. | |
| 1h TTL + manual flush | Admin flush endpoint added. | |

**User's choice:** 1h TTL, no manual invalidation
**Notes:** GeoNames data doesn't change after seed.

| Option | Description | Selected |
|--------|-------------|----------|
| Redis SETNX single-flight lock | SET lock:search:{key} 1 NX EX 5. First thread queries, others wait. | ✓ |
| No protection | Accept thundering herd. Simpler but risky. | |
| Pre-warm at startup | Run all common queries at boot. Slow startup. | |

**User's choice:** Redis SETNX single-flight lock
**Notes:** Matches ROADMAP Pitfall 8 note.

---

## Response shape + edge cases

| Option | Description | Selected |
|--------|-------------|----------|
| Accent-fold at both query and index | unaccent() on both sides. 'München' matches 'Munich'. | ✓ |
| Fold stored data only | Only stored data is folded. User must type exact. | |

**User's choice:** Accent-fold at both query and index
**Notes:** Required by success criterion #5.

| Option | Description | Selected |
|--------|-------------|----------|
| Empty array 200 for all short/empty | No 400 error. Matches API spec and SC#4. | ✓ |
| 200 for empty, 400 for too-short | Stricter validation with error code. | |

**User's choice:** Empty array 200 for all short/empty
**Notes:** API spec says returns empty array, not 404.

| Option | Description | Selected |
|--------|-------------|----------|
| Filter on city/country from cities table | type=city returns cities, type=country searches country field. Default both. | ✓ |
| Ignore type, return all | Skip type param in Phase 3. | |

**User's choice:** Filter on city/country from cities table
**Notes:** Matches API spec's type parameter contract.

---

## Agent's Discretion

- Repository layer implementation (native query vs @Query)
- Service layer organization
- RedisTemplate configuration approach
- Test approach for single-flight lock

## Deferred Ideas

None — discussion stayed within phase scope.
