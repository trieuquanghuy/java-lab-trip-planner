# Phase 5: Trip Service — Trips + Days - Research

**Researched:** 2026-05-16
**Domain:** Spring Boot JPA CRUD + Transactional Day Materialization
**Confidence:** HIGH
**Status:** Complete

## Summary

Phase 5 adds trip CRUD and idempotent day materialization to the existing trip-service skeleton. The codebase already has a fully wired security config (`ServletSecurityConfig` + `RestAuthenticationEntryPoint`), Flyway with an empty V1 baseline, and build.gradle.kts with all necessary dependencies (JPA, Flyway, security, jwt-common, error-handling, api-contracts). The primary technical challenge is the `@Transactional(isolation = REPEATABLE_READ)` day materialization in a separate bean (`DayMaterializationService`) to avoid the self-invocation proxy pitfall, and the 409 shrink-confirmation guard with explicit SQL DELETE for cascading items.

All patterns needed for this phase have direct precedents in auth-service (entities, repositories, services, controllers, controller advice, exception classes, integration tests with Testcontainers).

**Primary recommendation:** Follow auth-service patterns verbatim — entity/repository/service/controller layers with constructor injection, skinny exception classes, `@ControllerAdvice` for trip-specific error mapping, and Testcontainers + `@ServiceConnection` integration tests. The `DayMaterializationService` is the only novel component requiring careful transactional design.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Day materialization in separate `DayMaterializationService` bean with `@Transactional(isolation = Isolation.REPEATABLE_READ)`
- **D-02:** Cascade deletion via `DELETE FROM itinerary_items WHERE day_id IN (...)`, NOT JPA `CascadeType.REMOVE`
- **D-03:** Shrink conflict check: `SELECT COUNT(*) FROM itinerary_items WHERE itinerary_day_id IN (days-to-remove)`, if >0 and `confirmShorten=false` → throw `ShortenConflictException`
- **D-04:** REPEATABLE_READ isolation serializes concurrent PATCHes; no explicit `SELECT ... FOR UPDATE` in Phase 5
- **D-05:** REST endpoints: POST/GET/GET/{id}/PATCH/{id}/DELETE/{id} on `/api/trips`
- **D-06:** PATCH accepts `confirmShorten` query param; 409 response shape with `orphanedDays` array
- **D-07:** Spring Data Page convention for list response
- **D-08:** Create returns 201 + Location header + full body with empty `days[]`
- **D-09:** Service-layer ownership check — repository queries filter by `id` AND `userId`; no match → 404
- **D-10:** Pattern mirrors destination-service: controller extracts userId from UserContext
- **D-11:** Empty list returns standard paginated response with `content: []`
- **D-12:** Validation errors via `ProblemDetailFactory` with new error codes: `TRIP_NOT_FOUND`, `TRIP_SHORTEN_CONFLICT`, `TRIP_INVALID_DATES`
- **D-13:** Name validation 1–120, trimmed, reject blank. Date: `endDate >= startDate` when both set, either can be null

### Agent's Discretion
- Entity field naming and JPA mapping details
- Flyway migration file numbering (V2, V3, etc.)
- Test structure and naming conventions (follow existing patterns from auth-service and destination-service)
- Whether to use `@ControllerAdvice` for trip-specific exceptions or handle in controller (recommend `@ControllerAdvice`)

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TRIP-01 | User can create a trip with a required name (1–120). New trip appears in "My Trips" list and planner opens immediately. | Trip entity + POST /api/trips + name validation + 201 + TripController + TripService.create |
| TRIP-02 | User can set start/end dates. One itinerary day materialized per date. Shrinking range with orphaned items requires 409 confirmation. | DayMaterializationService + REPEATABLE_READ + ShortenConflictException + 409 response shape |
| TRIP-06 | Returning user sees previously created trips, days, items; empty state when no trips. | GET /api/trips (paginated, userId-filtered) + GET /api/trips/{id} with embedded days[] |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Trip CRUD | API / Backend (trip-service) | Database (PostgreSQL trip schema) | Business logic + persistence; no frontend in Phase 5 |
| Day materialization | API / Backend (DayMaterializationService) | Database (single transaction) | Must be atomic within REPEATABLE_READ transaction |
| Ownership enforcement | API / Backend (service layer) | — | Repository queries filter by userId; controller extracts from JWT |
| Input validation | API / Backend (Bean Validation + service) | — | Jakarta Bean Validation on DTOs + custom date logic in service |
| Error responses | API / Backend (@ControllerAdvice) | — | RFC 7807 via ProblemDetailFactory |

## 1. Existing Codebase Patterns

### Package Structure Convention
Auth-service uses this flat layout under `com.tripplanner.auth`:
```
api/                  # Controllers + ControllerAdvice + DTOs
  dto/                # Request/Response records
config/               # @ConfigurationProperties, @Configuration
domain/               # JPA entities
email/                # Event listener for async email
health/               # HealthPlaceholderController
repository/           # Spring Data JPA repositories
scheduling/           # @Scheduled jobs
security/             # SecurityConfig + EntryPoint
service/              # Business logic
  exception/          # Skinny RuntimeException subclasses
```

**Trip-service should follow:**
```
com.tripplanner.trip/
├── api/                  # TripController + TripControllerAdvice
│   └── dto/              # CreateTripRequest, UpdateTripRequest, TripResponse, etc.
├── domain/               # Trip, ItineraryDay entities
├── repository/           # TripRepository, ItineraryDayRepository, ItineraryItemRepository
├── service/              # TripService, DayMaterializationService
│   └── exception/        # TripNotFoundException, ShortenConflictException, InvalidDateRangeException
├── health/               # (existing) HealthPlaceholderController
└── security/             # (existing) ServletSecurityConfig, RestAuthenticationEntryPoint
```

### Controller Pattern (from auth-service AuthController)
- `@RestController` + `@RequestMapping("/api/...")` [VERIFIED: codebase]
- Constructor injection (no `@Autowired` on fields) [VERIFIED: codebase]
- `@Valid @RequestBody` for request DTOs [VERIFIED: codebase]
- Returns `ResponseEntity<T>` with explicit status codes [VERIFIED: codebase]
- No `@AuthenticationPrincipal` in auth-service (auth endpoints are public), but destination-service's internal patterns and the CONTEXT.md D-09/D-10 specify: controller extracts `userId` via `@AuthenticationPrincipal UserContext ctx` → `ctx.userId()`

### Service Pattern (from auth-service AuthService)
- `@Service` with constructor injection [VERIFIED: codebase]
- `@Transactional` on mutating methods, `@Transactional(readOnly = true)` on reads [VERIFIED: codebase]
- Returns domain objects or inner `record` result types (e.g., `SignupResult`, `LoginResult`) [VERIFIED: codebase]
- Throws skinny exception classes (no-arg constructors, no message) [VERIFIED: codebase]

### Entity Pattern (from auth-service User)
- `@Entity` + `@Table(name = "...", schema = "...")` [VERIFIED: codebase]
- `@Id` + `@Column(name = "...", nullable = false, updatable = false)` for PKs [VERIFIED: codebase]
- Protected no-arg constructor for JPA + public constructor for application code [VERIFIED: codebase]
- Getters + targeted setters (only mutable fields) [VERIFIED: codebase]
- `Instant` for timestamps (`created_at`, `updated_at`) [VERIFIED: codebase]
- Manual `updatedAt = Instant.now()` in setters (not `@PreUpdate`) [VERIFIED: codebase]

### Repository Pattern (from auth-service UserRepository)
- `@Repository` annotation + `JpaRepository<Entity, IdType>` [VERIFIED: codebase]
- Custom query methods via method-name derivation or `@Query` JPQL [VERIFIED: codebase]
- `@Lock(LockModeType.PESSIMISTIC_WRITE)` with explicit `@Query` for row-lock primitives [VERIFIED: RefreshTokenRepository]

### Exception Pattern (from auth-service)
- Skinny `RuntimeException` subclasses with no-arg constructors [VERIFIED: codebase]
- Example: `public class InvalidCredentialsException extends RuntimeException { public InvalidCredentialsException() { super(); } }`
- `@ControllerAdvice` maps exceptions → `ProblemDetail` via `ProblemDetailFactory.of(status, code, detail)` [VERIFIED: AuthControllerAdvice]

### Error Code Extension Pattern
- `ErrorCode` enum in `libs/error-handling` — append-only, semicolon on last entry [VERIFIED: codebase]
- New trip codes to add: `TRIP_NOT_FOUND("trip.not_found")`, `TRIP_SHORTEN_CONFLICT("trip.shorten_requires_confirmation")`, `TRIP_INVALID_DATES("trip.invalid_date_range")`
- Pattern: last entry carries `;`, new entries appended before it (or migrate last entry comma → new entry semicolon)

### ControllerAdvice Pattern (from AuthControllerAdvice)
- Extends `ResponseEntityExceptionHandler` [VERIFIED: codebase]
- Overrides `handleMethodArgumentNotValid` for Bean Validation errors [VERIFIED: codebase]
- Individual `@ExceptionHandler` methods per custom exception [VERIFIED: codebase]
- Returns `ResponseEntity<ProblemDetail>` with explicit `application/problem+json` content type [VERIFIED: codebase]
- Private helper: `body(HttpStatus, ErrorCode, String detail)` [VERIFIED: codebase]

## 2. Database Schema & Migrations

### Current State
- `V1__init.sql`: Empty baseline (`SELECT 1;`) — comment says "Real schema lands in Phase 5 (V2__trips.sql, V3__itinerary_days.sql, ...)" [VERIFIED: codebase]
- Schema name: `trip` (configured in `application.yml` via `spring.flyway.schemas: trip` and `currentSchema=trip` JDBC param)
- Flyway history table: `trip_flyway_schema_history`

### Required Migrations (from docs/03-data-model.md §3.3–3.4)

**V2__create_trips.sql:**
```sql
CREATE TABLE trip.trips (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL,
    name            VARCHAR(120) NOT NULL,
    start_date      DATE         NULL,
    end_date        DATE         NULL,
    cover_image_url VARCHAR(2048) NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT trips_dates_check CHECK (end_date >= start_date)
);
CREATE INDEX trips_user_id_idx ON trip.trips (user_id, created_at DESC);
```

**V3__create_itinerary_days.sql:**
```sql
CREATE TABLE trip.itinerary_days (
    id        UUID NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id   UUID NOT NULL REFERENCES trip.trips(id) ON DELETE CASCADE,
    day_date  DATE NOT NULL,
    day_index INT  NOT NULL,
    CONSTRAINT itinerary_days_trip_date_uq UNIQUE (trip_id, day_date),
    CONSTRAINT itinerary_days_trip_index_uq UNIQUE (trip_id, day_index),
    CONSTRAINT itinerary_days_index_positive CHECK (day_index >= 1)
);
CREATE INDEX itinerary_days_trip_idx ON trip.itinerary_days (trip_id, day_index);
```

**V4__create_itinerary_items.sql** (empty table for now — needed so `DayMaterializationService` can query item counts):
```sql
CREATE TABLE trip.itinerary_items (
    id                UUID         NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    itinerary_day_id  UUID         NOT NULL REFERENCES trip.itinerary_days(id) ON DELETE CASCADE,
    destination_ref   VARCHAR(80)  NOT NULL,
    position          INT          NOT NULL,
    time_slot         TIME         NULL,
    note              VARCHAR(500) NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX itinerary_items_day_pos_idx ON trip.itinerary_items (itinerary_day_id, position);
```

### Important: `ON DELETE CASCADE` on itinerary_days
- `itinerary_days.trip_id` → `trips(id)` ON DELETE CASCADE: deleting a trip cascades to its days at the DB level
- `itinerary_items.itinerary_day_id` → `itinerary_days(id)` ON DELETE CASCADE: deleting a day cascades to its items at the DB level
- However, D-02 mandates that the **application** uses explicit SQL `DELETE FROM itinerary_items WHERE day_id IN (...)` in the materialization transaction — the DB CASCADE is defense-in-depth for the trip DELETE path only

### Test Schema Bootstrap
Following auth-service pattern, trip-service needs:
- `src/test/resources/db/test-migration/V0__create_trip_schema.sql`: `CREATE SCHEMA IF NOT EXISTS trip;`
- `src/test/resources/application-test.yml` with `spring.flyway.locations: classpath:db/migration,classpath:db/test-migration`

## 3. Transactional Isolation Strategy

### REPEATABLE_READ in PostgreSQL with JPA/Hibernate
[CITED: PostgreSQL docs https://www.postgresql.org/docs/16/transaction-iso.html]

PostgreSQL's REPEATABLE_READ uses **Serializable Snapshot Isolation (SSI) lite**:
- Each transaction sees a snapshot of data as of its start
- Write-write conflicts: if two concurrent transactions try to update/delete the same row, the second one gets a serialization failure (`org.postgresql.util.PSQLException` with SQL state `40001`)
- This is NOT the same as MySQL's REPEATABLE_READ (which uses gap locks)

**Key behavior for Phase 5:**
- Two concurrent PATCH requests shrinking the same trip: transaction A reads day count, deletes days. Transaction B reads the same snapshot, tries to delete — PostgreSQL detects the conflict and throws serialization failure on B.
- Spring's `@Transactional` translates this to `CannotAcquireLockException` or `PessimisticLockingFailureException`

### Retry Strategy
D-04 says "retries or fails" — for Phase 5 simplicity, **let it fail** with a 409/500. Reasons:
1. The scenario (two users editing the same trip simultaneously) doesn't apply — trips are single-owner
2. The scenario (same user sends two PATCHes simultaneously) is a client bug — the 409/500 correctly rejects the race
3. Adding retry logic (`@Retryable` + Resilience4j) adds complexity disproportionate to the risk

**If serialization failure occurs:** Spring wraps it in `CannotAcquireLockException`. The `@ControllerAdvice` can catch `DataAccessException` subtypes and return 409.

### DayMaterializationService Transaction Design
```java
@Service
public class DayMaterializationService {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<ItineraryDay> materializeDays(Trip trip, LocalDate newStart, LocalDate newEnd,
                                              boolean confirmShorten) {
        // 1. Compute desired day set from newStart..newEnd
        // 2. Query existing days for this trip
        // 3. Determine days to add and days to remove
        // 4. If removing days: check item count on those days
        //    - If count > 0 && !confirmShorten → throw ShortenConflictException (with orphan info)
        //    - If count > 0 && confirmShorten → DELETE items, then DELETE days
        //    - If count == 0 → DELETE days
        // 5. INSERT new days
        // 6. Return final day list
    }
}
```

**Critical:** This method is on a **separate bean** from `TripService` (D-01). When `TripService.updateTrip()` calls `dayMaterializationService.materializeDays()`, the call goes through Spring's proxy, ensuring the `@Transactional` annotation takes effect. If it were a private method on `TripService`, the proxy would be bypassed and the isolation level would be silently ignored.

### Native Query for Item Deletion
D-02 mandates SQL DELETE, not JPA cascade:
```java
@Modifying
@Query("DELETE FROM ItineraryItem i WHERE i.itineraryDay.id IN :dayIds")
void deleteByDayIds(@Param("dayIds") List<UUID> dayIds);
```
Or use a native query:
```java
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(value = "DELETE FROM trip.itinerary_items WHERE itinerary_day_id IN (:dayIds)", nativeQuery = true)
void deleteItemsByDayIds(@Param("dayIds") List<UUID> dayIds);
```

## 4. API Design Patterns

### Endpoint Contract (from docs/04-api-spec.md §5 + CONTEXT.md D-05)

| Method | Path | Auth | Response |
|--------|------|------|----------|
| POST | /api/trips | Yes | 201 + Location + body |
| GET | /api/trips | Yes | 200 + paginated list |
| GET | /api/trips/{id} | Yes | 200 + trip with days[] |
| PATCH | /api/trips/{id}?confirmShorten=false | Yes | 200 + updated trip |
| DELETE | /api/trips/{id} | Yes | 204 |

### Request/Response DTOs

**CreateTripRequest:**
```java
public record CreateTripRequest(
    @NotBlank @Size(max = 120) String name,
    LocalDate startDate,   // nullable
    LocalDate endDate      // nullable
) {}
```

**UpdateTripRequest:**
```java
public record UpdateTripRequest(
    @Size(max = 120) String name,          // nullable = no change
    LocalDate startDate,                    // nullable = no change (use explicit flag or Optional)
    LocalDate endDate,
    @Size(max = 2048) String coverImageUrl
) {}
```

**TripResponse:**
```java
public record TripResponse(
    UUID id, String name, LocalDate startDate, LocalDate endDate,
    String coverImageUrl, Instant createdAt, Instant updatedAt,
    List<DayResponse> days    // null/absent on list endpoint, populated on detail
) {}
```

**TripListResponse** (for GET /api/trips — Spring Data Page shape per D-07):
```java
public record TripListResponse(
    List<TripSummaryResponse> content,
    long totalElements, int totalPages, int page, int size
) {}
```

**DayResponse:**
```java
public record DayResponse(UUID id, LocalDate dayDate, int dayIndex) {}
```

**ShortenConflictResponse** (D-06 — 409 body):
```java
// Returned as ProblemDetail extension property
public record OrphanedDayInfo(LocalDate dayDate, int dayIndex, long itemCount) {}
```

### PATCH Semantics
PATCH is partial update — only provided fields are changed. Two approaches:
1. **Explicit null handling:** If a field is absent from JSON, Jackson deserializes it as `null`. Service checks for `null` to decide "no change" vs "set to null".
2. **JsonNullable (from OpenAPI):** Distinguishes "absent" from "explicitly null". Overkill for this project.

**Recommendation:** Use approach 1 (simple null check) since `name`, `startDate`, `endDate`, `coverImageUrl` are all independently nullable in the data model. For `name`, validation ensures it's not blank if provided. The DTO can use `@Size` without `@NotBlank` (so null = no change, blank = validation error).

### Pagination
docs/04-api-spec.md §1 says `?page=0&size=20` with response `{ items, page, size, total }`. But CONTEXT.md D-07 says Spring Data Page convention: `{ content, totalElements, totalPages, page, size }`. **D-07 wins** (CONTEXT.md overrides docs when evolved).

Use `Pageable` parameter in controller with `@PageableDefault(size = 20, sort = "createdAt", direction = DESC)`.

## 5. Security & Ownership Patterns

### How Authentication Works in trip-service
1. Gateway validates JWT, injects `X-User-Id` and `X-User-Email` headers
2. `ServletJwtCommonFilter` (from `libs/jwt-common`) re-validates the JWT from the `Authorization: Bearer` header (defense-in-depth)
3. Filter populates `SecurityContextHolder` with a `UserContext` principal
4. Controller accesses via `@AuthenticationPrincipal UserContext ctx`

**File:** `services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java`
- All paths except `/__health` and actuator require authentication [VERIFIED: codebase]
- `csrf` disabled (stateless JSON API) [VERIFIED: codebase]
- `SessionCreationPolicy.STATELESS` [VERIFIED: codebase]

### Ownership Enforcement Pattern (D-09)
```java
// In TripRepository:
Optional<Trip> findByIdAndUserId(UUID id, UUID userId);

// In TripService:
public Trip findTrip(UUID tripId, String userId) {
    return tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
            .orElseThrow(TripNotFoundException::new);
}

// In TripController:
@GetMapping("/{id}")
public ResponseEntity<TripResponse> getTrip(@PathVariable UUID id,
                                             @AuthenticationPrincipal UserContext ctx) {
    Trip trip = tripService.findTrip(id, ctx.userId());
    // ... map to response
}
```

**Key:** No 403 — always 404 for cross-user access. The repository query filters by both `id` AND `userId`, so a missing match is indistinguishable from "trip doesn't exist".

### UserContext Record
Located at: `libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java`
```java
public record UserContext(String userId, String email, boolean verified) implements Principal {
    @Override public String getName() { return userId; }
}
```
Note: `userId` is a `String` (not `UUID`). Service layer must parse to `UUID` when querying repositories.

## 6. Testing Infrastructure

### Two Test Tiers in the Codebase

**Tier 1: Lightweight security ITs (H2 in-memory, no Docker)**
- Used by: `DirectServiceAccessWithoutGatewayReturns401IT` in trip-service [VERIFIED: codebase]
- Pattern: `@SpringBootTest` + `@TestPropertySource` disabling Flyway + H2 datasource
- Purpose: Security filter chain tests only — no persistence layer needed
- Already exists in trip-service

**Tier 2: Full integration tests (Testcontainers PostgreSQL)**
- Used by: auth-service `AuthControllerIT`, `AuthControllerAdviceIT`, security ITs [VERIFIED: codebase]
- Pattern: abstract `AuthIntegrationTestBase` with `@SpringBootTest` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- Singleton container pattern (started ONCE per JVM, not per test class)
- `@ServiceConnection` for Postgres auto-wiring (no `@DynamicPropertySource`)
- Schema bootstrap: `V0__create_<schema>_schema.sql` in `src/test/resources/db/test-migration/`

### What trip-service Needs for Phase 5 Testing

**Test base class** (`TripIntegrationTestBase`):
```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class TripIntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("tripplanner");

    static { PG.start(); }
}
```
- No Redis needed for Phase 5 (no caching)
- No GreenMail needed (no email)

**Test resources:**
- `src/test/resources/application-test.yml` — Flyway locations including test-migration
- `src/test/resources/db/test-migration/V0__create_trip_schema.sql` — `CREATE SCHEMA IF NOT EXISTS trip;`

**JWT Authentication in Tests:**
Using `JwtFixtures.mintValid(userId, email)` from `testFixtures(project(":libs:jwt-common"))`:
```java
mvc.perform(post("/api/trips")
    .header("Authorization", "Bearer " + JwtFixtures.mintValid(userId, email))
    .contentType(MediaType.APPLICATION_JSON)
    .content(json))
    .andExpect(status().isCreated());
```

**Test property for JWT secret:**
```yaml
auth:
  jwt:
    secret: phase-1-jwt-fixture-secret-32bytes!!
```

### Test Categories for Phase 5

1. **Unit tests** (`@ExtendWith(MockitoExtension.class)`):
   - `TripServiceTest` — CRUD logic, ownership checks, validation
   - `DayMaterializationServiceTest` — day computation, shrink conflict detection

2. **Controller tests** (`@WebMvcTest(TripController.class)`) — optional, lighter than full IT:
   - Request/response shape assertions
   - Validation error assertions

3. **Integration tests** (`TripIntegrationTestBase`):
   - Full CRUD flow
   - Day materialization: set dates → verify days created
   - Shrink confirmation: 409 with orphan info → retry with confirmShorten=true
   - Ownership: user A can't read user B's trip → 404
   - Empty state: new user → empty list

4. **Security ITs** (already exists):
   - `DirectServiceAccessWithoutGatewayReturns401IT` — already passing

## 7. Dependencies & Build Configuration

### Current build.gradle.kts (trip-service)
Already has all needed dependencies [VERIFIED: codebase]:
- `implementation(project(":libs:observability"))` ✓
- `implementation(project(":libs:error-handling"))` ✓
- `implementation(project(":libs:api-contracts"))` ✓ (provides `UserContext`)
- `implementation(project(":libs:jwt-common"))` ✓
- `implementation(libs.spring.boot.starter.web)` ✓
- `implementation(libs.spring.boot.starter.security)` ✓
- `implementation(libs.spring.boot.starter.data.jpa)` ✓
- `implementation(libs.flyway.core)` ✓
- `runtimeOnly(libs.flyway.database.postgresql)` ✓ (Pitfall A)
- `runtimeOnly(libs.postgresql.jdbc)` ✓
- `testImplementation(libs.spring.boot.starter.test)` ✓
- `testImplementation(libs.spring.security.test)` ✓
- `testImplementation(testFixtures(project(":libs:jwt-common")))` ✓
- `testImplementation(libs.spring.boot.testcontainers)` ✓
- `testRuntimeOnly(libs.h2)` ✓

### What Needs Adding to build.gradle.kts
- `implementation(libs.spring.boot.starter.validation)` — for `@Valid`, `@NotBlank`, `@Size` on DTOs
- `testImplementation(libs.testcontainers.postgresql)` — for `PostgreSQLContainer` in integration tests

### What's Already in libs.versions.toml
Both libraries are already catalogued [VERIFIED: codebase]:
- `spring-boot-starter-validation` → `org.springframework.boot:spring-boot-starter-validation`
- `testcontainers-postgresql` → `org.testcontainers:postgresql`

### libs/error-handling Changes
Add 3 new error codes to `ErrorCode` enum:
```java
TRIP_NOT_FOUND("trip.not_found"),
TRIP_SHORTEN_CONFLICT("trip.shorten_requires_confirmation"),
TRIP_INVALID_DATES("trip.invalid_date_range"),
```

## 8. Key Risks & Pitfalls

### Pitfall 1: Self-Invocation Proxy Bypass
**What goes wrong:** Calling a `@Transactional` method from within the same Spring bean bypasses the AOP proxy, so the transaction annotation is silently ignored.
**Why it happens:** Spring's `@Transactional` works via CGLIB proxies. Internal method calls don't go through the proxy.
**How to avoid:** D-01 mandates `DayMaterializationService` as a separate bean. `TripService` injects it and calls through the proxy.
**Warning signs:** Day materialization works in happy path but concurrent PATCH test shows orphaned rows (transaction wasn't actually serialized).

### Pitfall 2: Hibernate ddl-auto: validate vs Flyway Schema
**What goes wrong:** Entity field types don't match Flyway DDL byte-for-byte → application crashes on boot.
**Why it happens:** `spring.jpa.hibernate.ddl-auto=validate` is set (correctly) and rejects any drift.
**How to avoid:**
- `UUID` fields: use `@Column(columnDefinition = "uuid")` if needed, though Hibernate 6 handles UUID→UUID natively
- `LocalDate` maps to DATE — fine
- `Instant` maps to TIMESTAMPTZ — fine
- No CHAR columns in trip-service (unlike auth-service's refresh_tokens), so no `@JdbcTypeCode` needed
**Warning signs:** `SchemaManagementException` on boot.

### Pitfall 3: REPEATABLE_READ Serialization Failure Not Handled
**What goes wrong:** Two concurrent PATCHes get `PSQLException` with state 40001, which Spring wraps as `CannotAcquireLockException`.
**How to avoid:** Add a catch for `CannotAcquireLockException` (or its parent `PessimisticLockingFailureException`) in the `@ControllerAdvice` → return 409 Conflict.
**Warning signs:** Unhandled 500 errors in concurrent tests.

### Pitfall 4: Forgetting test-migration V0 Schema Bootstrap
**What goes wrong:** Testcontainers Postgres doesn't have the `trip` schema → Flyway V2 migration fails.
**Why it happens:** `infra/postgres/init.sql` creates schemas in Docker Compose but not in Testcontainers.
**How to avoid:** Ship `V0__create_trip_schema.sql` under `src/test/resources/db/test-migration/`.

### Pitfall 5: confirmShorten as Query Param vs Body Field
**What goes wrong:** docs/04-api-spec.md shows `confirmShorten` in the PATCH body. CONTEXT.md D-06 says query param.
**Resolution:** D-06 wins (CONTEXT.md is more recent). Use `@RequestParam(defaultValue = "false") boolean confirmShorten`.

### Pitfall 6: Pageable Default Sort
**What goes wrong:** Using `Pageable` without specifying default sort → unpredictable order.
**How to avoid:** Use `@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)`. D-05 says sorted `created_at DESC`.

### Pitfall 7: ItineraryItem Table Must Exist for COUNT Query
**What goes wrong:** The `DayMaterializationService` queries `SELECT COUNT(*) FROM itinerary_items WHERE itinerary_day_id IN (...)`. If the `itinerary_items` table doesn't exist (deferred to Phase 6), this query fails.
**Resolution:** Create the `itinerary_items` table in Phase 5's V4 migration (empty, no CRUD endpoints yet). The table needs to exist for the shrink-conflict COUNT query.

### Pitfall 8: UserContext.userId() is String, Not UUID
**What goes wrong:** Passing `ctx.userId()` directly to a repository method expecting `UUID` → type mismatch.
**How to avoid:** Parse in the service layer: `UUID.fromString(userId)`.

## 9. Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Mockito |
| Config file | `src/test/resources/application-test.yml` (to be created) |
| Quick run command | `./gradlew :services:trip-service:test` |
| Full suite command | `./gradlew :services:trip-service:check` |

### Success Criteria → Test Map

| SC# | Behavior | Test Type | Automated | Notes |
|-----|----------|-----------|-----------|-------|
| SC-1 | Setting dates creates exactly 5 days; trip in "My Trips" | Integration | `POST /api/trips` with dates → assert 5 days in `GET /api/trips/{id}` response | TRIP-01 + TRIP-02 |
| SC-2 | Shrinking with items → 409; confirmShorten=true → success | Integration | `PATCH /api/trips/{id}?confirmShorten=false` → 409 with orphanedDays; retry with `true` → 200 | TRIP-02 |
| SC-3 | Returning user sees trips/days; no-trip user sees empty | Integration | Create trip → re-GET → verify; new user → GET → empty content | TRIP-06 |
| SC-4 | User A cannot read User B's trip → 404 | Integration | Create with user A JWT → GET with user B JWT → 404 | NFR-02 |
| SC-5 | Concurrent PATCHes produce no orphan items | Integration | Requires either concurrent thread test or verify via serialization failure response | TRIP-02 |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | File |
|--------|----------|-----------|------|
| TRIP-01 | Create trip with name, appears in list | Integration IT + Unit | `TripControllerIT` + `TripServiceTest` |
| TRIP-02 | Date materialization + 409 shrink guard | Integration IT + Unit | `DayMaterializationIT` + `DayMaterializationServiceTest` |
| TRIP-06 | Returning user sees trips, empty state | Integration IT | `TripControllerIT` |

---

## RESEARCH COMPLETE

**Phase:** 5 - Trip Service — Trips + Days
**Confidence:** HIGH

### Key Findings
1. All dependencies already exist in `build.gradle.kts` except `spring-boot-starter-validation` and `testcontainers-postgresql` — both already catalogued in `libs.versions.toml`
2. Auth-service provides complete, battle-tested patterns for every layer (entity → repository → service → controller → advice → IT)
3. `DayMaterializationService` as a separate bean is the critical architectural decision — prevents self-invocation proxy bypass
4. The `itinerary_items` table must be created in Phase 5 (V4 migration) even though item CRUD is Phase 6 — needed for the `COUNT(*)` shrink-conflict query
5. REPEATABLE_READ in PostgreSQL handles concurrent PATCHes via write-write conflict detection; no explicit `SELECT FOR UPDATE` needed

### Confidence Assessment
| Area | Level | Reason |
|------|-------|--------|
| Standard Stack | HIGH | All libraries already in project; version catalog verified |
| Architecture | HIGH | Direct precedent in auth-service; patterns verified in codebase |
| Transactional Isolation | HIGH | PostgreSQL REPEATABLE_READ well-documented; D-04 decision clear |
| Testing | HIGH | Auth-service IT patterns verified; Testcontainers singleton pattern established |
| Pitfalls | HIGH | Every pitfall has a verified mitigation path |

### Open Questions
None — all patterns are established, all decisions are locked in CONTEXT.md.

### Ready for Planning
Research complete. Planner can now create PLAN.md files.
