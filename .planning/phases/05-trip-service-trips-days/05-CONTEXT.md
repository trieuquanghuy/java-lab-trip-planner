# Phase 5: Trip Service — Trips + Days - Context

**Gathered:** 2026-05-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Trip CRUD (create, read, update, delete) and idempotent day materialization with a shrink-confirmation 409 guard. All endpoints require authentication. User A cannot access User B's trips (404 not 403). Setting date range materializes exactly N itinerary_days; shrinking range that orphans items returns 409 unless `confirmShorten=true`.

Phase 5 does NOT include: itinerary items CRUD (Phase 6), favorites (Phase 6), drag-drop reorder (Phase 8), frontend (Phase 7/8).

</domain>

<decisions>
## Implementation Decisions

### Day Materialization Transaction
- **D-01:** All day materialization logic — count check, insert new days, delete removed days + cascade items — runs inside a single `@Transactional(isolation = Isolation.REPEATABLE_READ)` method on a **separate Spring bean** (`DayMaterializationService`). This avoids the self-invocation proxy pitfall where `@Transactional` is silently ignored when called from within the same bean.
- **D-02:** Cascade deletion uses `DELETE FROM itinerary_items WHERE day_id IN (...)` in the same transaction, NOT JPA `CascadeType.REMOVE`. This is explicit, auditable, and avoids N+1 delete issues.
- **D-03:** When shrinking the date range would orphan items, the service checks `SELECT COUNT(*) FROM itinerary_items WHERE itinerary_day_id IN (days-to-remove)`. If count > 0 and `confirmShorten` is false → throw a custom `ShortenConflictException`. If `confirmShorten=true` → proceed with cascade delete.
- **D-04:** Concurrent PATCH requests shrinking the same trip are serialized by `REPEATABLE_READ` isolation — if two requests read the same snapshot, the second one's commit detects the conflict and retries or fails. No explicit `SELECT ... FOR UPDATE` needed at this phase (Phase 6 adds it for position reorder).

### Trip API Contract
- **D-05:** REST endpoints follow nested resource pattern:
  - `POST /api/trips` — create trip (name required, dates optional)
  - `GET /api/trips` — list my trips (paginated: `page`, `size` params, default size=20, sorted `created_at DESC`)
  - `GET /api/trips/{id}` — trip detail with embedded `days[]` array (each day includes `id`, `dayDate`, `dayIndex`; items are NOT included — Phase 6)
  - `PATCH /api/trips/{id}` — partial update (name, startDate, endDate, coverImageUrl). Date changes trigger day materialization.
  - `DELETE /api/trips/{id}` — soft check: owner only, then hard delete with cascade
- **D-06:** PATCH for date changes accepts `confirmShorten` boolean query param (default false). When shrink conflict detected and `confirmShorten=false`, returns 409 with body shape:
  ```json
  {
    "type": "https://tripplanner.example.com/errors/trip.shorten_conflict",
    "status": 409,
    "detail": "Shortening this trip would remove 3 planned items from 2 days",
    "code": "trip.shorten_conflict",
    "orphanedDays": [
      {"dayDate": "2026-09-13", "dayIndex": 4, "itemCount": 2},
      {"dayDate": "2026-09-14", "dayIndex": 5, "itemCount": 1}
    ]
  }
  ```
- **D-07:** Trip list response shape: `{"content": [...], "totalElements": N, "totalPages": N, "page": N, "size": N}` — follows Spring Data Page convention.
- **D-08:** Trip create response: 201 with Location header + full trip body (including empty `days[]` if no dates set).

### Ownership Enforcement
- **D-09:** Service-layer ownership check — every `TripService` method that takes a `tripId` also takes the `userId` from `@AuthenticationPrincipal UserContext`. Repository queries filter by both `id` and `userId`. If no match → service returns empty `Optional` → controller returns 404. No `@PreAuthorize` annotations — keep it simple and explicit.
- **D-10:** Pattern mirrors destination-service: controller extracts `userId` from `UserContext`, passes it to service. Service never trusts external `userId` claims.

### Empty State & Error Responses
- **D-11:** Empty trips list: returns standard paginated response with `content: []`, `totalElements: 0`. No special empty-state body — frontend handles presentation.
- **D-12:** Validation errors use existing `ProblemDetailFactory` from `libs/error-handling` with RFC 7807 format. New error codes to add to `ErrorCode` enum: `TRIP_NOT_FOUND`, `TRIP_SHORTEN_CONFLICT`, `TRIP_INVALID_DATES`.
- **D-13:** Trip name validation: length 1–120, trimmed, reject blank. Date validation: `endDate >= startDate` when both set, either can be null independently.

### Agent's Discretion
- Entity field naming and JPA mapping details
- Flyway migration file numbering (V2, V3, etc.)
- Test structure and naming conventions (follow existing patterns from auth-service and destination-service)
- Whether to use `@ControllerAdvice` for trip-specific exceptions or handle in controller (recommend `@ControllerAdvice`)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Data Model
- `docs/03-data-model.md` §3.3–3.5 — trips, itinerary_days, itinerary_items table definitions, indexes, constraints, position algorithm

### API Specification
- `docs/04-api-spec.md` — REST endpoint conventions, error response format (§6)

### Auth & Security
- `docs/05-auth-security.md` — JWT validation, ownership model
- `libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java` — `@AuthenticationPrincipal` target record
- `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` — existing error code enum (extend with trip.* codes)
- `libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java` — RFC 7807 factory

### Existing Patterns (follow these)
- `services/destination-service/src/main/java/com/tripplanner/destination/search/SearchController.java` — controller pattern
- `services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java` — security config (all `/api/trips/**` requires auth)
- `services/trip-service/src/main/resources/db/migration/V1__init.sql` — empty baseline, real schema starts at V2

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `libs/error-handling` — `ProblemDetailFactory.of(status, code, detail)` for RFC 7807 responses
- `libs/api-contracts` — `UserContext` record for `@AuthenticationPrincipal`
- `libs/jwt-common` — `ServletJwtCommonFilter` already wired in trip-service security config

### Established Patterns
- Controller: `@RestController` + `@RequestMapping("/api/...")` + constructor injection (destination-service pattern)
- Security: `anyRequest().authenticated()` — all trip endpoints need JWT
- Error handling: `@ControllerAdvice` with `@ExceptionHandler` returning `ProblemDetail` (auth-service pattern)
- Testing: `@ExtendWith(MockitoExtension.class)` for unit tests, `@WebMvcTest` for controller tests, `@MockitoBean` (Spring Boot 3.5+)

### Integration Points
- Flyway: V2+ migrations in `services/trip-service/src/main/resources/db/migration/`
- JPA: `spring-boot-starter-data-jpa` already in build.gradle.kts
- Redis: not needed for Phase 5 (no caching layer for trips in v1)

</code_context>

<specifics>
## Specific Ideas

- ROADMAP note: "Never call `@Transactional` methods from within the same bean" → solved by D-01 (separate `DayMaterializationService` bean)
- ROADMAP note: cascade via SQL DELETE not JPA CascadeType.REMOVE → solved by D-02
- Success criteria #5: "Two simultaneous PATCH requests produce no orphan rows" → solved by D-04 (REPEATABLE_READ isolation)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 05-trip-service-trips-days*
*Context gathered: 2026-05-15*
