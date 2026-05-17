---
phase: 5
plan: 2
subsystem: trip-service
tags: [jpa, entities, repositories, dto, exceptions]
dependency_graph:
  requires: [05-01]
  provides: [trip-domain-model, trip-repositories, trip-dtos, trip-exceptions]
  affects: [05-03, 05-04, 05-05]
tech_stack:
  added: []
  patterns: [jpa-entity-no-cascade, ownership-filtered-repository, record-dto, deviation-rule-skinny-exception]
key_files:
  created:
    - services/trip-service/src/main/java/com/tripplanner/trip/domain/Trip.java
    - services/trip-service/src/main/java/com/tripplanner/trip/domain/ItineraryDay.java
    - services/trip-service/src/main/java/com/tripplanner/trip/domain/ItineraryItem.java
    - services/trip-service/src/main/java/com/tripplanner/trip/repository/TripRepository.java
    - services/trip-service/src/main/java/com/tripplanner/trip/repository/ItineraryDayRepository.java
    - services/trip-service/src/main/java/com/tripplanner/trip/repository/ItineraryItemRepository.java
    - services/trip-service/src/main/java/com/tripplanner/trip/service/exception/TripNotFoundException.java
    - services/trip-service/src/main/java/com/tripplanner/trip/service/exception/InvalidDateRangeException.java
    - services/trip-service/src/main/java/com/tripplanner/trip/service/exception/ShortenConflictException.java
    - services/trip-service/src/main/java/com/tripplanner/trip/api/dto/CreateTripRequest.java
    - services/trip-service/src/main/java/com/tripplanner/trip/api/dto/UpdateTripRequest.java
    - services/trip-service/src/main/java/com/tripplanner/trip/api/dto/TripResponse.java
    - services/trip-service/src/main/java/com/tripplanner/trip/api/dto/TripListResponse.java
    - services/trip-service/src/main/java/com/tripplanner/trip/api/dto/DayResponse.java
  modified: []
decisions:
  - "No @ManyToOne relationships — FK stored as UUID columns (flat entities, no cascade)"
  - "ItineraryItem entity is minimal — only @Entity + @Id for JPQL COUNT compile; full CRUD is Phase 6"
  - "ItineraryDay is immutable (no setters) — days are deleted+recreated on materialization"
  - "ShortenConflictException carries OrphanedDayInfo record for 409 response body"
metrics:
  duration: 2min
  completed: "2026-05-17T09:07:38Z"
---

# Phase 5 Plan 2: JPA Entities + Repositories + Exceptions + DTOs Summary

**One-liner:** Trip domain type system — 3 JPA entities, 3 ownership-filtered repositories, 3 domain exceptions, 5 record DTOs all compiling against V2/V3/V4 DDL

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Create JPA entities (Trip, ItineraryDay, ItineraryItem) | a55d789 | 3 |
| 2 | Create repositories + exception classes + DTOs | f9d85ac | 11 |

## Deviations from Plan

None — plan executed exactly as written.

## Key Implementation Details

- **Trip entity** follows same pattern as auth-service's User.java: `protected Trip(){}` for JPA, setters update `updatedAt`, no `@GeneratedValue` (app-generated UUIDs)
- **Ownership queries**: `TripRepository.findByIdAndUserId` returns `Optional<Trip>` — service treats empty as 404 (not 403) per D-09
- **JPQL parameterized queries**: `countByDayIds` and `deleteByDayIds` use `@Param` bindings — no SQL injection surface
- **DTO validation**: `@NotBlank @Size(max=120)` on CreateTripRequest.name; `@Size(min=1, max=120)` on UpdateTripRequest.name (null=no-change for PATCH)
- **ShortenConflictException**: nested `OrphanedDayInfo` record carries day metadata for 409 body

## Verification

```
./gradlew :services:trip-service:compileJava → BUILD SUCCESSFUL
```

All 14 files compile cleanly. Entity annotations match V2/V3/V4 DDL column types.

## Self-Check: PASSED
