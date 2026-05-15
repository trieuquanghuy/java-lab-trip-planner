# Plan 03-01 Summary: Flyway migrations + City entity + CityRepository

**Status:** Complete
**Committed:** feat(destination): add cities table, GeoNames seed data, City entity + FTS repo

## What was built

1. **V2__enable_extensions.sql** — Enables `unaccent` and `pg_trgm` extensions (defense-in-depth for standalone DB provisioning)
2. **V3__create_cities.sql** — Creates `cities` table with:
   - TSVECTOR generated column using `to_tsvector('simple', unaccent(name) || ' ' || unaccent(country))`
   - GIN index on `search_tsv` for FTS queries
   - GIN trigram index on `name` for fuzzy matching
   - B-tree index on `country`
3. **V4__seed_cities.sql** — ~33,657 cities from GeoNames cities15000 dataset (population >= 15000)
4. **City.java** — JPA entity in `com.tripplanner.destination.city` with all 8 columns mapped
5. **CityRepository.java** — Native FTS query with `ts_rank * LOG(population + 1) DESC` ranking, prefix matching via `:*`, and `unaccent()` on query input

## Key decisions

- Used `scripts/generate-seed-sql.py` for reproducible seed generation from GeoNames
- Population-weighted ranking ensures London GB ranks above London Ontario for "lon"
- `'simple'` text search config (no stemming) for proper nouns
- Schema-qualified `destination.cities` in native query to avoid search_path ambiguity

## Deviations

None.
