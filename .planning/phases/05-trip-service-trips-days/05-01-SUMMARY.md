---
phase: 5
plan: 1
subsystem: trip-service
tags: [flyway, schema, error-codes, dependencies]
dependency_graph:
  requires: [libs/error-handling, gradle/libs.versions.toml]
  provides: [trip.trips, trip.itinerary_days, trip.itinerary_items, TRIP_NOT_FOUND, TRIP_SHORTEN_CONFLICT, TRIP_INVALID_DATES, TRIP_CONCURRENT_MODIFICATION]
  affects: [services/trip-service, libs/error-handling]
tech_stack:
  added: []
  patterns: [flyway-versioned-migrations, db-level-check-constraints]
key_files:
  created:
    - services/trip-service/src/main/resources/db/migration/V2__create_trips.sql
    - services/trip-service/src/main/resources/db/migration/V3__create_itinerary_days.sql
    - services/trip-service/src/main/resources/db/migration/V4__create_itinerary_items.sql
  modified:
    - services/trip-service/build.gradle.kts
    - libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java
decisions:
  - "user_id has no FK constraint (cross-schema boundary — auth.users in different service)"
  - "itinerary_items table created now (empty for shrink-conflict COUNT query in D-03)"
metrics:
  duration: "4min"
  completed: "2026-05-17T09:04:18Z"
  tasks: 3
  files: 5
---

# Phase 5 Plan 1: Flyway Migrations + build.gradle.kts deps + ErrorCode Extension Summary

**One-liner:** Flyway V2-V4 migrations for trips/itinerary_days/itinerary_items schema with DB-level CHECK constraints, plus validation dependency and 4 trip error codes.

## Tasks Completed

| # | Task | Commit | Key Changes |
|---|------|--------|-------------|
| 1 | Add missing dependencies to build.gradle.kts | `dd75dc1` | +spring-boot-starter-validation, +testcontainers-postgresql |
| 2 | Create V2__create_trips.sql migration | `849448b` | trips table with dates CHECK, user_id index |
| 3 | Create V3/V4 migrations + extend ErrorCode | `f900ef2` | itinerary_days, itinerary_items tables + 4 error codes |

## Deviations from Plan

None — plan executed exactly as written.

## Verification Results

```
./gradlew compileJava → BUILD SUCCESSFUL (all 9 modules)
./gradlew :services:trip-service:dependencies | grep validation → resolved 3.5.14
```

## Self-Check: PASSED

- [x] V2__create_trips.sql exists
- [x] V3__create_itinerary_days.sql exists
- [x] V4__create_itinerary_items.sql exists
- [x] build.gradle.kts contains validation and testcontainers-postgresql
- [x] ErrorCode.java contains TRIP_NOT_FOUND, TRIP_SHORTEN_CONFLICT, TRIP_INVALID_DATES, TRIP_CONCURRENT_MODIFICATION
- [x] Full project compiles successfully
