---
phase: 04-destination-service-providers-cache
plan: 01
subsystem: destination-service
tags: [database, flyway, jpa, earth-distance, geo-query]

requires: []
provides: [destinations_cache_table, DestinationsCacheEntity, DestinationsCacheRepository]
affects: [destination-service]

tech-stack:
  added: [cube-extension, earthdistance-extension]
  patterns: [earth_distance-radial-query, upsert-newer-wins, GIST-index]

key-files:
  created:
    - services/destination-service/src/main/resources/db/migration/V5__create_destinations_cache.sql
    - services/destination-service/src/main/java/com/tripplanner/destination/destination/DestinationsCacheEntity.java
    - services/destination-service/src/main/java/com/tripplanner/destination/destination/DestinationsCacheRepository.java
  modified: []

key-decisions:
  - Used individual @Param values for upsert instead of SpEL entity references — more reliable with native queries

requirements-completed: [SRCH-02, DEST-01]

duration: 3 min
completed: 2026-05-15
---

# Phase 04 Plan 01: Migration + Entity + Repository Summary

Flyway V5 migration creates destinations_cache table with cube/earthdistance extensions and GIST index for radial geo-queries; JPA entity and repository provide findNearby, findNearbyFresh, and upsert with newer-wins dedup.

## Tasks Completed

| # | Task | Files | Commit |
|---|------|-------|--------|
| 1 | Flyway migration V5 | V5__create_destinations_cache.sql | 1262db1 |
| 2 | JPA Entity + Repository | DestinationsCacheEntity.java, DestinationsCacheRepository.java | 12fa1d7 |

## Deviations from Plan

- **[Rule 1 - Bug Prevention] Used individual @Param for upsert** — SpEL `#{}` with native queries is unreliable across Spring Data JPA versions. Used explicit `@Param` values for all upsert fields instead of entity-level SpEL binding. More verbose but guaranteed to work.

**Total deviations:** 1 auto-fixed. **Impact:** None — same behavior, more explicit.

## Issues Encountered

None.

## Next Phase Readiness

Ready for Plan 04-02 (Provider Clients).
