# Phase 5: Trip Service — Trips + Days - Pattern Map

**Mapped:** 2026-05-17
**Files analyzed:** 22 (new/modified files)
**Analogs found:** 20 / 22

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `services/trip-service/src/main/resources/db/migration/V2__create_trips.sql` | migration | DDL | `services/auth-service/src/main/resources/db/migration/V2__create_users.sql` | exact |
| `services/trip-service/src/main/resources/db/migration/V3__create_itinerary_days.sql` | migration | DDL | `services/auth-service/src/main/resources/db/migration/V3__create_email_verification_tokens.sql` | exact |
| `services/trip-service/src/main/resources/db/migration/V4__create_itinerary_items.sql` | migration | DDL | `services/auth-service/src/main/resources/db/migration/V4__create_refresh_tokens.sql` | exact |
| `.../trip/domain/Trip.java` | entity | CRUD | `services/auth-service/src/main/java/com/tripplanner/auth/domain/User.java` | exact |
| `.../trip/domain/ItineraryDay.java` | entity | CRUD | `services/auth-service/src/main/java/com/tripplanner/auth/domain/User.java` | role-match |
| `.../trip/repository/TripRepository.java` | repository | CRUD | `services/auth-service/src/main/java/com/tripplanner/auth/repository/UserRepository.java` | exact |
| `.../trip/repository/ItineraryDayRepository.java` | repository | CRUD | `services/auth-service/src/main/java/com/tripplanner/auth/repository/UserRepository.java` | role-match |
| `.../trip/repository/ItineraryItemRepository.java` | repository | CRUD | `services/auth-service/src/main/java/com/tripplanner/auth/repository/RefreshTokenRepository.java` | role-match |
| `.../trip/service/TripService.java` | service | CRUD | `services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java` | exact |
| `.../trip/service/DayMaterializationService.java` | service | transform | `services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java` | role-match |
| `.../trip/service/exception/TripNotFoundException.java` | exception | — | `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/InvalidCredentialsException.java` | exact |
| `.../trip/service/exception/ShortenConflictException.java` | exception | — | `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/InvalidCredentialsException.java` | role-match |
| `.../trip/service/exception/InvalidDateRangeException.java` | exception | — | `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/InvalidCredentialsException.java` | exact |
| `.../trip/api/TripController.java` | controller | request-response | `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java` | role-match |
| `.../trip/api/TripControllerAdvice.java` | controller-advice | request-response | `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java` | exact |
| `.../trip/api/dto/CreateTripRequest.java` | dto | request-response | `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/SignupRequest.java` | exact |
| `.../trip/api/dto/UpdateTripRequest.java` | dto | request-response | `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/SignupRequest.java` | role-match |
| `.../trip/api/dto/TripResponse.java` | dto | request-response | `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginResponse.java` | exact |
| `.../trip/api/dto/TripListResponse.java` | dto | request-response | `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginResponse.java` | role-match |
| `.../trip/api/dto/DayResponse.java` | dto | request-response | `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginResponse.java` | exact |
| `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` | config | — | (self — append new entries) | exact |
| `services/trip-service/build.gradle.kts` | config | — | (self — add 2 dependencies) | exact |

## Pattern Assignments

### Flyway Migrations (`V2__create_trips.sql`, `V3__create_itinerary_days.sql`, `V4__create_itinerary_items.sql`)

**Analog:** `services/auth-service/src/main/resources/db/migration/V2__create_users.sql` (lines 1-10)

**Structure pattern:**
```sql
-- Source comment referencing docs/03-data-model.md section.
-- Phase/context decision references.
CREATE TABLE trip.<table_name> (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    -- columns ...
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX <index_name> ON trip.<table_name> (<columns>);
```

**Key conventions:**
- Schema-qualified table names: `trip.trips`, `trip.itinerary_days`, `trip.itinerary_items`
- UUID PKs with `DEFAULT gen_random_uuid()`
- `TIMESTAMPTZ` for timestamps (not `TIMESTAMP`)
- `NOT NULL DEFAULT NOW()` on created_at/updated_at
- `REFERENCES` with `ON DELETE CASCADE` for parent FK
- Source comment at top linking to data model doc

---

### `Trip.java` (entity, CRUD)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/domain/User.java` (lines 1-62)

**Imports pattern:**
```java
package com.tripplanner.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
```

**Entity annotation pattern:**
```java
@Entity
@Table(name = "trips", schema = "trip")
public class Trip {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
```

**Constructor pattern:**
```java
    protected Trip() {}   // JPA

    public Trip(UUID id, UUID userId, String name, LocalDate startDate, LocalDate endDate) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
```

**Setter pattern** (mutable fields update `updatedAt`):
```java
    public void setName(String name) { this.name = name; this.updatedAt = Instant.now(); }
    public void setStartDate(LocalDate d) { this.startDate = d; this.updatedAt = Instant.now(); }
    public void setEndDate(LocalDate d) { this.endDate = d; this.updatedAt = Instant.now(); }
    public void setCoverImageUrl(String u) { this.coverImageUrl = u; this.updatedAt = Instant.now(); }
```

---

### `ItineraryDay.java` (entity, CRUD)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/domain/User.java` (structure)

**Key differences from Trip:** No `updatedAt`, has FK to Trip, uses `@ManyToOne` or just stores `tripId` as UUID column.

**Recommended pattern (FK as UUID column, not @ManyToOne — avoids lazy-loading complexity):**
```java
@Entity
@Table(name = "itinerary_days", schema = "trip")
public class ItineraryDay {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "trip_id", nullable = false, updatable = false)
    private UUID tripId;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Column(name = "day_index", nullable = false)
    private int dayIndex;

    protected ItineraryDay() {}

    public ItineraryDay(UUID id, UUID tripId, LocalDate dayDate, int dayIndex) {
        this.id = id;
        this.tripId = tripId;
        this.dayDate = dayDate;
        this.dayIndex = dayIndex;
    }
```

---

### `TripRepository.java` (repository, CRUD)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/repository/UserRepository.java` (lines 1-13)

**Pattern:**
```java
package com.tripplanner.trip.repository;

import com.tripplanner.trip.domain.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    Optional<Trip> findByIdAndUserId(UUID id, UUID userId);
    Page<Trip> findByUserId(UUID userId, Pageable pageable);
}
```

---

### `ItineraryDayRepository.java` (repository, CRUD)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/repository/UserRepository.java`

**Pattern:**
```java
@Repository
public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {
    List<ItineraryDay> findByTripIdOrderByDayIndex(UUID tripId);
    void deleteByTripIdAndDayDateIn(UUID tripId, List<LocalDate> dates);
}
```

---

### `ItineraryItemRepository.java` (repository, CRUD — native query for bulk delete)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/repository/RefreshTokenRepository.java` (lines 1-28)

**Pattern** (D-02: explicit SQL DELETE, `@Modifying` + `@Query`):
```java
package com.tripplanner.trip.repository;

import com.tripplanner.trip.domain.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    @Query("SELECT COUNT(i) FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds")
    long countByDayIds(@Param("dayIds") List<UUID> dayIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ItineraryItem i WHERE i.itineraryDayId IN :dayIds")
    void deleteByDayIds(@Param("dayIds") List<UUID> dayIds);
}
```

---

### `TripService.java` (service, CRUD)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java` (lines 1-100)

**Imports pattern:**
```java
package com.tripplanner.trip.service;

import com.tripplanner.trip.domain.Trip;
import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import com.tripplanner.trip.service.exception.InvalidDateRangeException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
```

**Class structure pattern:**
```java
@Service
public class TripService {

    private final TripRepository tripRepo;
    private final ItineraryDayRepository dayRepo;
    private final DayMaterializationService dayMaterializationService;

    public TripService(TripRepository tripRepo,
                       ItineraryDayRepository dayRepo,
                       DayMaterializationService dayMaterializationService) {
        this.tripRepo = tripRepo;
        this.dayRepo = dayRepo;
        this.dayMaterializationService = dayMaterializationService;
    }

    @Transactional
    public Trip create(String userId, ...) { ... }

    @Transactional(readOnly = true)
    public Trip findTrip(UUID tripId, String userId) {
        return tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId))
                .orElseThrow(TripNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<Trip> listTrips(String userId, Pageable pageable) { ... }
```

**Key conventions from AuthService:**
- `@Transactional` on mutating methods, `@Transactional(readOnly = true)` on reads
- Constructor injection (no `@Autowired`)
- Throws skinny exception classes (method reference: `TripNotFoundException::new`)
- `userId` arrives as `String` from UserContext, parsed to UUID in service

---

### `DayMaterializationService.java` (service, transform)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java` (transactional isolation pattern)

**Key pattern** (separate bean with explicit isolation — mirrors RefreshTokenService's REPEATABLE_READ for rotation):
```java
package com.tripplanner.trip.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DayMaterializationService {

    private final ItineraryDayRepository dayRepo;
    private final ItineraryItemRepository itemRepo;

    public DayMaterializationService(ItineraryDayRepository dayRepo,
                                     ItineraryItemRepository itemRepo) {
        this.dayRepo = dayRepo;
        this.itemRepo = itemRepo;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<ItineraryDay> materializeDays(Trip trip, LocalDate newStart, LocalDate newEnd,
                                              boolean confirmShorten) {
        // D-01: This is a SEPARATE BEAN — the proxy intercepts this call from TripService
        // ...
    }
}
```

---

### Exception Classes (`TripNotFoundException.java`, `ShortenConflictException.java`, `InvalidDateRangeException.java`)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/InvalidCredentialsException.java` (lines 1-6)

**Pattern (skinny, no-arg):**
```java
package com.tripplanner.trip.service.exception;

public class TripNotFoundException extends RuntimeException {
    public TripNotFoundException() { super(); }
}
```

**ShortenConflictException** — needs state (orphaned days info for 409 body):
```java
package com.tripplanner.trip.service.exception;

import java.time.LocalDate;
import java.util.List;

public class ShortenConflictException extends RuntimeException {

    public record OrphanedDayInfo(LocalDate dayDate, int dayIndex, long itemCount) {}

    private final List<OrphanedDayInfo> orphanedDays;

    public ShortenConflictException(List<OrphanedDayInfo> orphanedDays) {
        super();
        this.orphanedDays = orphanedDays;
    }

    public List<OrphanedDayInfo> getOrphanedDays() { return orphanedDays; }
}
```

---

### `TripController.java` (controller, request-response)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java` (lines 40-70) + `services/destination-service/src/main/java/com/tripplanner/destination/search/SearchController.java` (lines 1-27)

**Imports pattern:**
```java
package com.tripplanner.trip.api;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.trip.api.dto.CreateTripRequest;
import com.tripplanner.trip.api.dto.TripResponse;
import com.tripplanner.trip.api.dto.TripListResponse;
import com.tripplanner.trip.api.dto.UpdateTripRequest;
import com.tripplanner.trip.service.TripService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;
```

**Class structure pattern:**
```java
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;

    public TripController(TripService tripService) {
        this.tripService = tripService;
    }

    @PostMapping
    public ResponseEntity<TripResponse> create(@Valid @RequestBody CreateTripRequest req,
                                               @AuthenticationPrincipal UserContext ctx) {
        // ... create trip, return 201 + Location header
        return ResponseEntity.created(URI.create("/api/trips/" + trip.getId()))
                .body(TripResponse.from(trip, days));
    }

    @GetMapping
    public ResponseEntity<TripListResponse> list(
            @AuthenticationPrincipal UserContext ctx,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        // ...
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> get(@PathVariable UUID id,
                                            @AuthenticationPrincipal UserContext ctx) {
        // ...
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TripResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdateTripRequest req,
                                               @RequestParam(defaultValue = "false") boolean confirmShorten,
                                               @AuthenticationPrincipal UserContext ctx) {
        // ...
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @AuthenticationPrincipal UserContext ctx) {
        // ... return 204
        return ResponseEntity.noContent().build();
    }
}
```

**Key differences from AuthController:**
- Uses `@AuthenticationPrincipal UserContext ctx` (auth endpoints are public, trip endpoints are authenticated)
- Uses `@PageableDefault` for paginated list
- Returns `ResponseEntity.created(URI)` for POST (201 + Location header per D-08)
- `@PatchMapping` with `@RequestParam` for `confirmShorten`

---

### `TripControllerAdvice.java` (controller-advice, request-response)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java` (lines 1-140)

**Imports pattern:**
```java
package com.tripplanner.trip.api;

import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import com.tripplanner.trip.service.exception.InvalidDateRangeException;
import com.tripplanner.trip.service.exception.ShortenConflictException;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
```

**Class structure pattern:**
```java
@ControllerAdvice
public class TripControllerAdvice extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest req) {
        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED, "Request validation failed.");
        HttpHeaders out = new HttpHeaders();
        out.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, out, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TripNotFoundException.class)
    public ResponseEntity<ProblemDetail> onNotFound(TripNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ErrorCode.TRIP_NOT_FOUND,
                "Trip not found.");
    }

    @ExceptionHandler(ShortenConflictException.class)
    public ResponseEntity<ProblemDetail> onShortenConflict(ShortenConflictException ex) {
        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.CONFLICT,
                ErrorCode.TRIP_SHORTEN_CONFLICT,
                "Shortening this trip would remove planned items.");
        pd.setProperty("orphanedDays", ex.getOrphanedDays());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, headers, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ResponseEntity<ProblemDetail> onInvalidDates(InvalidDateRangeException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.TRIP_INVALID_DATES,
                "End date must be on or after start date.");
    }

    private ResponseEntity<ProblemDetail> body(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetailFactory.of(status, code, detail);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, headers, status);
    }
}
```

---

### DTO Records (`CreateTripRequest.java`, `UpdateTripRequest.java`, `TripResponse.java`, `TripListResponse.java`, `DayResponse.java`)

**Analog:** `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/SignupRequest.java` (lines 1-13) + `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginResponse.java` (lines 1-8)

**Request DTO pattern:**
```java
package com.tripplanner.trip.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTripRequest(
        @NotBlank @Size(max = 120) String name,
        LocalDate startDate,
        LocalDate endDate
) {}
```

**Response DTO pattern:**
```java
package com.tripplanner.trip.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripResponse(
        UUID id, String name, LocalDate startDate, LocalDate endDate,
        String coverImageUrl, Instant createdAt, Instant updatedAt,
        List<DayResponse> days
) {}
```

---

### `ErrorCode.java` additions

**Analog:** `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` (lines 1-30)

**Current last entry** (line 24):
```java
    VALIDATION_FAILED("validation.failed");
```

**Pattern:** Change semicolon to comma, append new entries:
```java
    VALIDATION_FAILED("validation.failed"),
    TRIP_NOT_FOUND("trip.not_found"),
    TRIP_SHORTEN_CONFLICT("trip.shorten_requires_confirmation"),
    TRIP_INVALID_DATES("trip.invalid_date_range");
```

---

### Test Infrastructure

#### `TripIntegrationTestBase.java`

**Analog:** `services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java` (lines 1-56)

**Pattern:**
```java
package com.tripplanner.trip.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

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

**Key differences from AuthIntegrationTestBase:**
- No Redis container (no caching in Phase 5)
- No GreenMail extension (no email)
- No `@Import(TestSecurityConfig.class)` needed (unless overriding security for specific tests)

#### `application-test.yml`

**Analog:** `services/auth-service/src/test/resources/application-test.yml` (lines 1-50)

**Pattern:**
```yaml
spring:
  flyway:
    enabled: true
    schemas: trip
    default-schema: trip
    table: trip_flyway_schema_history
    locations: classpath:db/migration,classpath:db/test-migration
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.default_schema: trip

auth:
  jwt:
    secret: phase-1-jwt-fixture-secret-32bytes!!

eureka:
  client:
    enabled: false
```

#### `V0__create_trip_schema.sql`

**Analog:** `services/auth-service/src/test/resources/db/test-migration/V0__create_auth_schema.sql` (lines 1-5)

**Pattern:**
```sql
-- Test-only schema bootstrap (Testcontainer Postgres lacks infra/postgres/init.sql).
CREATE SCHEMA IF NOT EXISTS trip;
```

#### Integration Test Class

**Analog:** `services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerIT.java` (lines 1-60)

**Pattern:**
```java
package com.tripplanner.trip.api;

import com.tripplanner.jwt.JwtFixtures;
import com.tripplanner.trip.support.TripIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TripControllerIT extends TripIntegrationTestBase {

    @Autowired MockMvc mvc;

    @Test
    void create_trip_returns_201() throws Exception {
        String userId = UUID.randomUUID().toString();
        mvc.perform(post("/api/trips")
                .header("Authorization", "Bearer " + JwtFixtures.mintValid(userId, "u@test.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Rome 2026\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Rome 2026"));
    }
}
```

#### Unit Test

**Analog:** `services/auth-service/src/test/java/com/tripplanner/auth/service/RefreshTokenServiceTest.java` (lines 1-40)

**Pattern:**
```java
package com.tripplanner.trip.service;

import com.tripplanner.trip.repository.TripRepository;
import com.tripplanner.trip.repository.ItineraryDayRepository;
import com.tripplanner.trip.service.exception.TripNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock TripRepository tripRepo;
    @Mock ItineraryDayRepository dayRepo;
    @Mock DayMaterializationService dayMaterializationService;
    @InjectMocks TripService tripService;

    @Test
    void findTrip_not_owned_throws_TripNotFoundException() {
        UUID tripId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();
        when(tripRepo.findByIdAndUserId(tripId, UUID.fromString(userId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.findTrip(tripId, userId))
                .isInstanceOf(TripNotFoundException.class);
    }
}
```

---

### `build.gradle.kts` additions

**Analog:** `services/trip-service/build.gradle.kts` (self — lines 1-40)

**Pattern** — add these two lines to existing `dependencies` block:
```kotlin
    implementation(libs.spring.boot.starter.validation)      // @Valid, @NotBlank, @Size on DTOs
    testImplementation(libs.testcontainers.postgresql)        // PostgreSQLContainer for ITs
```

---

## Shared Patterns

### Authentication (applies to: TripController)
**Source:** `libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java`
**Apply to:** All controller methods
```java
@AuthenticationPrincipal UserContext ctx
// then: ctx.userId() returns String — parse to UUID in service layer
```

### Error Handling (applies to: TripControllerAdvice, all services)
**Source:** `libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java`
**Apply to:** All exception handlers in TripControllerAdvice
```java
ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.NOT_FOUND, ErrorCode.TRIP_NOT_FOUND, "Trip not found.");
```

### Security Config (already exists — no changes needed)
**Source:** `services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java`
**Apply to:** All `/api/trips/**` endpoints automatically protected by `anyRequest().authenticated()`

### JWT Test Fixtures (applies to: all integration tests)
**Source:** `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java`
**Apply to:** All controller integration tests
```java
.header("Authorization", "Bearer " + JwtFixtures.mintValid(userId, email))
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `ShortenConflictException.java` (stateful variant) | exception | — | No existing exception carries structured data; all auth-service exceptions are no-arg. Pattern must be invented for the `orphanedDays` list payload (shown above in Pattern Assignments). |
| `TripListResponse.java` (paginated wrapper) | dto | request-response | No existing paginated response DTO exists in the codebase; auth-service doesn't paginate. Spring Data `Page` shape must be manually mapped to match D-07. |

---

## Metadata

**Analog search scope:** `services/auth-service/src/`, `services/destination-service/src/`, `libs/error-handling/src/`, `libs/api-contracts/src/`, `libs/jwt-common/src/`
**Files scanned:** 67
**Pattern extraction date:** 2026-05-17
