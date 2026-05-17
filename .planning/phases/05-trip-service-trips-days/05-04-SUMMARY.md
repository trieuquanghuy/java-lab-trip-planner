---
phase: 5
plan: 4
title: "TripController + TripControllerAdvice + serialization failure handler"
subsystem: trip-service/api
tags: [controller, rest, error-handling, rfc7807]
dependency_graph:
  requires: [05-02-PLAN (DTOs + exceptions), 05-03-PLAN (TripService)]
  provides: [REST API layer for trip CRUD]
  affects: [services/trip-service]
tech_stack:
  added: []
  patterns: [ResponseEntityExceptionHandler, @ControllerAdvice, @AuthenticationPrincipal, PageableDefault]
key_files:
  created:
    - services/trip-service/src/main/java/com/tripplanner/trip/api/TripController.java
    - services/trip-service/src/main/java/com/tripplanner/trip/api/TripControllerAdvice.java
  modified: []
decisions:
  - "Used private body() helper for consistent ProblemDetail construction (matches AuthControllerAdvice pattern)"
  - "ShortenConflictException handler builds dynamic detail string with orphan counts"
metrics:
  duration: "1min"
  completed: "2026-05-17"
---

# Phase 5 Plan 4: TripController + TripControllerAdvice Summary

**One-liner:** REST controller with 5 trip CRUD endpoints + exception advice mapping all trip exceptions to RFC 7807 ProblemDetail responses including 409 for concurrent modifications.

## Tasks Completed

| # | Task | Commit | Key Files |
|---|------|--------|-----------|
| 1 | Create TripController | 6251d67 | TripController.java |
| 2 | Create TripControllerAdvice | b82225b | TripControllerAdvice.java |

## Implementation Details

### TripController (5 endpoints)
- **POST /api/trips** → 201 + Location header + body with empty days[]
- **GET /api/trips** → paginated list, default sort by createdAt DESC, page size 20
- **GET /api/trips/{id}** → trip with embedded days[] or 404 for wrong owner
- **PATCH /api/trips/{id}?confirmShorten=false** → partial update with shorten guard
- **DELETE /api/trips/{id}** → 204 for owner, 404 for non-owner

All endpoints extract userId from `@AuthenticationPrincipal UserContext ctx`.

### TripControllerAdvice (5 exception handlers)
- `MethodArgumentNotValidException` → 400 VALIDATION_FAILED
- `TripNotFoundException` → 404 TRIP_NOT_FOUND
- `ShortenConflictException` → 409 TRIP_SHORTEN_CONFLICT with orphanedDays property
- `InvalidDateRangeException` → 400 TRIP_INVALID_DATES
- `CannotAcquireLockException` → 409 TRIP_CONCURRENT_MODIFICATION

## Deviations from Plan

None - plan executed exactly as written.

## Verification

```
./gradlew :services:trip-service:compileJava → BUILD SUCCESSFUL
```

## Self-Check: PASSED
- [x] TripController.java exists at expected path
- [x] TripControllerAdvice.java exists at expected path
- [x] Commit 6251d67 verified in git log
- [x] Commit b82225b verified in git log
