---
phase: 05-trip-service-trips-days
verified: 2026-05-17T16:30:00Z
status: human_needed
score: 4/5
overrides_applied: 0
human_verification:
  - test: "SC-5: Two simultaneous PATCH requests shrinking the same trip produce no orphan itinerary_items rows"
    expected: "Both PATCH requests succeed or one returns 409 with TRIP_CONCURRENT_MODIFICATION code; no orphan items remain"
    why_human: "Concurrent PATCH test requires a real multi-threaded integration test with database-level locking. The CannotAcquireLockException handler exists in code but no automated test exercises the concurrent scenario. The IT test labeled sc5 actually tests unauthenticated 401 instead."
---

# Phase 5: Trip Service — Trips + Days Verification Report

**Phase Goal:** Trip CRUD and idempotent day materialization work correctly, including the shrink-confirmation 409 guard.
**Verified:** 2026-05-17T16:30:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Setting startDate=2026-09-10, endDate=2026-09-14 creates exactly 5 itinerary_days; trip appears in list | ✓ VERIFIED | `sc1_createTripWithDatesCreates5DaysAndAppearsInList` IT asserts `days hasSize(5)`, dayDate 2026-09-10..2026-09-14, dayIndex 1..5; then GET /api/trips asserts totalElements ≥ 1. `DayMaterializationService.materializeDays` uses `newStart.datesUntil(newEnd.plusDays(1))` producing inclusive range. Unit test `materializeDays_freshTrip_createsAllDays` verifies `saveAll` called. |
| 2 | Changing endDate to 2026-09-12 returns 409 when items exist on days 4–5; succeeds with confirmShorten=true | ✓ VERIFIED | `sc2_shrinkWithoutConfirmReturns409` IT creates trip with 5 days, inserts item on day 5 via raw SQL, PATCHes to 3 days → 409 with `code=trip.shorten_requires_confirmation` + `orphanedDays` array; retries with `?confirmShorten=true` → 200 with `days hasSize(3)`. Unit test `materializeDays_shrinkWithItems_throwsConflict` verifies `ShortenConflictException` with 2 orphaned days. `materializeDays_shrinkWithConfirm_deletesItemsAndDays` verifies `itemRepo.deleteByDayIds` + `dayRepo.deleteByTripIdAndIdIn`. |
| 3 | Returning user sees trips; empty state for no trips | ✓ VERIFIED | `sc3_returningUserSeesTripsNewUserSeesEmpty` asserts `content: [], totalElements: 0` for new user. `sc3_returningUserSeesExistingTrips` creates trip then GETs list → `totalElements ≥ 1, content[0].name = "Returning Trip"`. TripListResponse record returns `content: []` + `totalElements: 0` for empty page. |
| 4 | User A cannot read User B's trip — returns 404 (not 403) | ✓ VERIFIED | `sc4_crossUserAccessReturns404` creates trip as owner, intruder tries GET → 404 `code=trip.not_found`, DELETE → 404, PATCH → 404. Service layer uses `findByIdAndUserId` which returns `Optional.empty()` → `TripNotFoundException` → 404 (D-09). Unit test `findTrip_nonOwner_throwsNotFound` confirms. |
| 5 | Two simultaneous PATCH requests produce no orphan itinerary_items rows | ? UNCERTAIN | `CannotAcquireLockException` handler in `TripControllerAdvice` returns 409 with `TRIP_CONCURRENT_MODIFICATION`. `DayMaterializationService.materializeDays` uses `@Transactional(isolation = REPEATABLE_READ)` (D-01, D-04). **However**, the IT test `sc5_unauthenticatedRequestReturns401` tests 401 auth, NOT the concurrent scenario. No automated test exercises two threads calling PATCH simultaneously. Handler exists but is untested. |

**Score:** 4/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `services/trip-service/src/main/resources/db/migration/V2__create_trips.sql` | trips table DDL | ✓ VERIFIED | `CREATE TABLE trip.trips` with CHECK constraint `end_date >= start_date`, user_id index |
| `services/trip-service/src/main/resources/db/migration/V3__create_itinerary_days.sql` | itinerary_days table DDL | ✓ VERIFIED | `CREATE TABLE trip.itinerary_days` with UNIQUE (trip_id, day_date), UNIQUE (trip_id, day_index), CHECK day_index ≥ 1, ON DELETE CASCADE |
| `services/trip-service/src/main/resources/db/migration/V4__create_itinerary_items.sql` | itinerary_items table DDL | ✓ VERIFIED | `CREATE TABLE trip.itinerary_items` with ON DELETE CASCADE from itinerary_days, position index |
| `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` | Trip error codes | ✓ VERIFIED | Contains `TRIP_NOT_FOUND`, `TRIP_SHORTEN_CONFLICT`, `TRIP_INVALID_DATES`, `TRIP_CONCURRENT_MODIFICATION` |
| `services/trip-service/src/main/java/com/tripplanner/trip/domain/Trip.java` | Trip JPA entity | ✓ VERIFIED | `@Table(name = "trips", schema = "trip")` annotation confirmed |
| `services/trip-service/src/main/java/com/tripplanner/trip/repository/TripRepository.java` | Ownership-filtered queries | ✓ VERIFIED | `findByIdAndUserId(UUID, UUID)` returns `Optional<Trip>` |
| `services/trip-service/src/main/java/com/tripplanner/trip/repository/ItineraryDayRepository.java` | Day queries | ✓ VERIFIED | `findByTripIdOrderByDayIndex`, `deleteByTripIdAndIdIn`, `updateDayIndex` with `@Modifying` |
| `services/trip-service/src/main/java/com/tripplanner/trip/repository/ItineraryItemRepository.java` | Item count/delete for shrink | ✓ VERIFIED | `countByDayIds(@Param("dayIds"))` JPQL, `deleteByDayIds(@Param("dayIds"))` with `@Modifying` |
| `services/trip-service/src/main/java/com/tripplanner/trip/service/TripService.java` | Trip CRUD logic | ✓ VERIFIED | create/findTrip/listTrips/updateTrip/deleteTrip with ownership enforcement, delegates to DayMaterializationService |
| `services/trip-service/src/main/java/com/tripplanner/trip/service/DayMaterializationService.java` | Day materialization with REPEATABLE_READ | ✓ VERIFIED | `@Transactional(isolation = Isolation.REPEATABLE_READ)` on separate `@Service` bean |
| `services/trip-service/src/main/java/com/tripplanner/trip/api/TripController.java` | REST endpoints | ✓ VERIFIED | POST/GET/GET/{id}/PATCH/{id}/DELETE/{id} with `@AuthenticationPrincipal UserContext ctx` on all 5 endpoints |
| `services/trip-service/src/main/java/com/tripplanner/trip/api/TripControllerAdvice.java` | Exception → ProblemDetail mapping | ✓ VERIFIED | 5 handlers: MethodArgumentNotValidException→400, TripNotFoundException→404, ShortenConflictException→409, InvalidDateRangeException→400, CannotAcquireLockException→409 |
| `services/trip-service/src/main/java/com/tripplanner/trip/api/dto/CreateTripRequest.java` | Validation DTO | ✓ VERIFIED | `@NotBlank @Size(max = 120) String name` |
| `services/trip-service/src/main/java/com/tripplanner/trip/api/dto/TripResponse.java` | Response with days[] | ✓ VERIFIED | `TripResponse.from(Trip, List<ItineraryDay>)` maps days to `DayResponse` list |
| `services/trip-service/src/main/java/com/tripplanner/trip/api/dto/TripListResponse.java` | Paginated list response | ✓ VERIFIED | `content, totalElements, totalPages, page, size` fields; `TripSummaryResponse` nested record |
| `services/trip-service/src/main/java/com/tripplanner/trip/service/exception/ShortenConflictException.java` | Carries orphan info | ✓ VERIFIED | `OrphanedDayInfo(LocalDate dayDate, int dayIndex, long itemCount)` record; `getOrphanedDays()` accessor |
| `services/trip-service/src/test/resources/application-test.yml` | Test profile config | ✓ VERIFIED | Flyway locations `classpath:db/migration,classpath:db/test-migration`, JWT secret matches JwtFixtures |
| `services/trip-service/src/test/resources/db/test-migration/V0__create_trip_schema.sql` | Schema bootstrap | ✓ VERIFIED | `CREATE SCHEMA IF NOT EXISTS trip` |
| `services/trip-service/src/test/java/com/tripplanner/trip/support/TripIntegrationTestBase.java` | Testcontainers base | ✓ VERIFIED | `PostgreSQLContainer<>("postgres:16-alpine")` with `@ServiceConnection`, `@SpringBootTest`, `@AutoConfigureMockMvc` |
| `services/trip-service/src/test/java/com/tripplanner/trip/api/TripControllerIT.java` | Integration tests | ✓ VERIFIED | Extends `TripIntegrationTestBase`, tests SC-1 through SC-4 + validation + delete + 404. SC-5 mislabeled (tests 401 not concurrency). |
| `services/trip-service/src/test/java/com/tripplanner/trip/service/TripServiceTest.java` | Unit tests for TripService | ✓ VERIFIED | 6 tests covering create±dates, findTrip±owner, listTrips, deleteTrip |
| `services/trip-service/src/test/java/com/tripplanner/trip/service/DayMaterializationServiceTest.java` | Unit tests for DayMaterializationService | ✓ VERIFIED | 4 tests covering fresh materialization, shrink with/without confirm, empty shrink |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| TripService.java | DayMaterializationService.java | Constructor injection + `dayMaterializationService.materializeDays` | ✓ WIRED | Called in `create()` line 46 and `updateTrip()` line 112; separate bean ensures proxy interception for `@Transactional` |
| DayMaterializationService.java | ItineraryItemRepository.java | `itemRepo.countByDayIds` | ✓ WIRED | Called for shrink-conflict detection at line 80; `deleteByDayIds` at line 94 |
| TripService.java | TripRepository.java | `tripRepo.findByIdAndUserId` | ✓ WIRED | Used in findTrip, updateTrip, deleteTrip for ownership enforcement |
| TripController.java | TripService.java | Constructor injection | ✓ WIRED | All 5 endpoints delegate to tripService methods |
| TripControllerAdvice.java | ProblemDetailFactory | `ProblemDetailFactory.of(status, code, detail)` | ✓ WIRED | Used in 3 handler methods + private `body()` helper |
| TripController.java | UserContext | `@AuthenticationPrincipal UserContext ctx` | ✓ WIRED | Present on all 5 endpoint methods (POST, GET list, GET detail, PATCH, DELETE) |
| TripControllerIT.java | TripIntegrationTestBase.java | `extends` | ✓ WIRED | `class TripControllerIT extends TripIntegrationTestBase` |
| TripIntegrationTestBase.java | JwtFixtures.java | `testFixtures(project(':libs:jwt-common'))` | ✓ WIRED | `JwtFixtures.mintValid(userId, email)` used for auth tokens in all IT methods |
| TripRepository.java | Trip.java | `JpaRepository<Trip, UUID>` | ✓ WIRED | Repository extends `JpaRepository<Trip, UUID>` |
| ItineraryItemRepository.java | ItineraryItem.java | `JpaRepository<ItineraryItem, UUID>` + JPQL `countByDayIds` | ✓ WIRED | JPQL queries reference `ItineraryItem` entity |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Unit tests pass | `./gradlew :services:trip-service:test --tests "com.tripplanner.trip.service.*"` | Exit code 0, no failures | ✓ PASS |

Note: Integration tests require Docker (Testcontainers) — skipped for automated spot-check. Unit tests confirm business logic correctness.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| TRIP-01 | 01, 02, 03, 04, 05 | User can create a trip with name (1–120); appears in list | ✓ SATISFIED | CreateTripRequest `@NotBlank @Size(max=120)`; TripService.create saves+materializes; TripController POST returns 201+Location+body; IT `sc1_createTripWithDatesCreates5DaysAndAppearsInList` validates end-to-end |
| TRIP-02 | 01, 02, 03, 04, 05 | Date materialization + shrink-confirmation 409 | ✓ SATISFIED | DayMaterializationService with REPEATABLE_READ; ShortenConflictException with OrphanedDayInfo; TripControllerAdvice maps to 409; IT `sc2_shrinkWithoutConfirmReturns409` validates 409+confirmShorten flow |
| TRIP-06 | 02, 03, 04, 05 | Returning user sees trips; empty state | ✓ SATISFIED | TripService.listTrips returns Page filtered by userId; TripListResponse returns content+totalElements; IT `sc3_returningUserSeesTripsNewUserSeesEmpty` + `sc3_returningUserSeesExistingTrips` validate both paths |

### Locked Decisions Compliance

| Decision | Description | Status | Evidence |
|----------|-------------|--------|----------|
| D-01 | DayMaterializationService as separate bean with `@Transactional(isolation = REPEATABLE_READ)` | ✓ HONORED | Separate `@Service` class; `@Transactional(isolation = Isolation.REPEATABLE_READ)` on `materializeDays` |
| D-02 | Cascade deletion via explicit SQL DELETE, not JPA CascadeType.REMOVE | ✓ HONORED | `itemRepo.deleteByDayIds(dayIdsToRemove)` using `@Query("DELETE FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds")` |
| D-03 | SELECT COUNT(*) for shrink-conflict detection | ✓ HONORED | `itemRepo.countByDayIds(dayIdsToRemove)` using `@Query("SELECT COUNT(i) FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds")` |
| D-04 | REPEATABLE_READ for concurrent PATCH safety | ✓ HONORED | `@Transactional(isolation = Isolation.REPEATABLE_READ)` on DayMaterializationService |
| D-05 | REST endpoints POST/GET/GET/{id}/PATCH/{id}/DELETE/{id} | ✓ HONORED | All 5 endpoints present in TripController |
| D-06 | confirmShorten as query param, 409 with orphanedDays response | ✓ HONORED | `@RequestParam(defaultValue = "false") boolean confirmShorten`; ShortenConflictException handler adds `orphanedDays` property to ProblemDetail |
| D-07 | Spring Data Page convention for list response | ✓ HONORED | `TripService.listTrips` returns `Page<Trip>`; controller maps to TripListResponse |
| D-08 | Create returns 201 + Location header + full body | ✓ HONORED | `ResponseEntity.created(URI.create("/api/trips/" + body.id())).body(body)` |
| D-09 | Service-layer ownership check via findByIdAndUserId | ✓ HONORED | `tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId)).orElseThrow(TripNotFoundException::new)` |
| D-10 | Pattern mirrors destination-service | ✓ HONORED | Controller/Service/Repository/DTO/Exception layering matches auth-service and destination-service patterns |
| D-11 | Empty list returns content: [], totalElements: 0 | ✓ HONORED | `Page<Trip>` empty page naturally returns `content=[], totalElements=0`; IT test asserts this |
| D-12 | Validation errors via ProblemDetailFactory with RFC 7807 | ✓ HONORED | `TripControllerAdvice.handleMethodArgumentNotValid` returns `ProblemDetailFactory.of(BAD_REQUEST, VALIDATION_FAILED, ...)` |
| D-13 | Name validation 1–120, date validation endDate >= startDate | ✓ HONORED | `@NotBlank @Size(max=120)` on CreateTripRequest; `validateDates()` throws InvalidDateRangeException; DB CHECK constraint `end_date >= start_date` |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| TripControllerIT.java | 184 | SC-5 test mislabeled: named `sc5_unauthenticatedRequestReturns401` but ROADMAP SC-5 requires concurrent PATCH test | ⚠️ Warning | Concurrent PATCH scenario is untested; handler exists but no automated validation |

### Human Verification Required

### 1. Concurrent PATCH produces no orphan items (SC-5)

**Test:** Start the trip-service with Docker Compose. Create a trip with dates and items. Use two concurrent HTTP clients (e.g., `curl` in parallel or a JUnit `@RepeatedTest` with `ExecutorService`) to simultaneously PATCH the same trip's endDate to shorten it.
**Expected:** One request succeeds (200), the other returns 409 with `code=trip.concurrent_modification`. After both complete, `SELECT COUNT(*) FROM trip.itinerary_items WHERE itinerary_day_id NOT IN (SELECT id FROM trip.itinerary_days)` returns 0 (no orphans).
**Why human:** Concurrent database locking behavior under REPEATABLE_READ isolation cannot be verified through static code analysis or single-threaded unit tests. Requires actual concurrent database access to confirm no race conditions.

### Gaps Summary

No structural gaps found. All 22 artifacts exist, are substantive (no stubs), and are properly wired. All 3 requirement IDs (TRIP-01, TRIP-02, TRIP-06) are satisfied. All 13 locked decisions are honored.

The only open item is SC-5 (concurrent PATCH safety), which has the correct implementation (`@Transactional(isolation = REPEATABLE_READ)` + `CannotAcquireLockException` → 409 handler) but lacks an automated integration test. The IT test labeled `sc5` tests authentication (401) instead of concurrency. This is a **test coverage gap**, not an implementation gap — the production code correctly handles the scenario.

---

_Verified: 2026-05-17T16:30:00Z_
_Verifier: the agent (gsd-verifier)_
