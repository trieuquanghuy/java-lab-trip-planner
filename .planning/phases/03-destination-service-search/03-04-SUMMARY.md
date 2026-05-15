# Plan 03-04 Summary: Integration + Unit tests

**Status:** Complete
**Committed:** test(destination): add search integration + unit tests

## What was built

1. **SearchControllerIT.java** — 7 integration tests using Testcontainers (Postgres 16 + Redis 7):
   - `searchLonReturnsLondonGBFirst` — SC #1 (population-weighted ranking)
   - `repeatedSearchReturnsFasterFromCache` — SC #2 (cache hit verification)
   - `coldSearchReturnsWithinSLA` — SC #3 (< 500ms generous, target 250ms)
   - `emptyQueryReturnsEmptyArray` — SC #4 (empty/blank/no-match → 200 + [])
   - `accentedSearchReturnsCorrectCity` — SC #5 (Münch → Munich)
   - `searchLimitCappedAtFive` — D-14 verification
   - `searchEndpointIsPublicNoAuthRequired` — security config verification

2. **SearchServiceTest.java** — 7 unit tests with Mockito:
   - blank/null/whitespace returns empty, no DB call
   - limit capped at 5
   - cache hit skips database
   - cache miss queries DB and writes cache
   - cache key normalized to lowercase

3. **Test infrastructure:**
   - `application-test.yml` — Flyway enabled with test-migration location
   - `V0__create_destination_schema.sql` — Creates destination schema + extensions in Testcontainers Postgres
   - Added `testcontainers-postgresql` + `testcontainers-junit-jupiter` to build.gradle.kts

## Key decisions

- Used `@BeforeEach` to flush Redis between tests (test isolation)
- Used `ReflectionTestUtils` for creating City instances in unit tests (protected constructor)
- Used `@MockitoSettings(strictness = Strictness.LENIENT)` to handle early-return paths

## Deviations

- Integration tests require Docker (Testcontainers) — could not run in this session (Docker daemon not running)
- Unit tests all pass (7/7 green)

## Verification status

- Unit tests: 7/7 PASS
- Integration tests: Structurally complete, compilation verified, requires Docker to run
