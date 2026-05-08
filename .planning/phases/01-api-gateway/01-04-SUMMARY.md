---
phase: 01-api-gateway
plan: "04"
subsystem: downstream-servlet-security
tags: [spring-security, servlet, jwt, defense-in-depth, pitfall-1-keystone, sc4]
dependency_graph:
  requires:
    - "01-01 (build infrastructure — spring-boot-starter-security, jwt-common deps in service build files)"
    - "01-02 (jwt-common — ServletJwtCommonFilter auto-registered via JwtAutoConfiguration.ServletConfig)"
    - "01-03 (gateway — XUserIdInjectionGlobalFilter strips X-User-Id at trust boundary)"
  provides:
    - "services/trip-service SecurityFilterChain (ServletSecurityConfig) — permitAll health, anyRequest authenticated"
    - "services/destination-service SecurityFilterChain (ServletSecurityConfig) — symmetric"
    - "services/trip-service RestAuthenticationEntryPoint — RFC 7807 ProblemDetail 401"
    - "services/destination-service RestAuthenticationEntryPoint — symmetric"
    - "SC#4 closure: DirectServiceAccessWithoutGatewayReturns401IT (both services) — Pitfall 1 keystone"
  affects:
    - "services/trip-service — @AuthenticationPrincipal UserContext now resolvable in any controller (NFR-02 machinery)"
    - "services/destination-service — symmetric"
    - "libs/jwt-common — ServletJwtCommonFilter now accepts injected ObjectMapper (Rule 1 fix)"
tech_stack:
  added:
    - "@EnableWebSecurity SecurityFilterChain with addFilterBefore(UsernamePasswordAuthenticationFilter) (T-01-11)"
    - "RestAuthenticationEntryPoint: RFC 7807 ProblemDetail 401 via Spring auto-configured ObjectMapper"
    - "DirectServiceAccessWithoutGatewayReturns401IT: @SpringBootTest(RANDOM_PORT) + H2 in-memory + Flyway disabled"
    - "H2 database: testRuntimeOnly for security ITs (no live PostgreSQL needed)"
  patterns:
    - "Convention C20-P0: trip-service and destination-service SecurityConfig bodies byte-identical (post-package)"
    - "Convention C26-P1: anyRequest().authenticated() default-deny; /__health permitAll preserved"
    - "Convention C35-P1: // Source: citation on every new file"
    - "T-01-11 mitigation: addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)"
    - "T-01-13 mitigation: SessionCreationPolicy.STATELESS"
key_files:
  created:
    - services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java
    - services/trip-service/src/main/java/com/tripplanner/trip/security/RestAuthenticationEntryPoint.java
    - services/destination-service/src/main/java/com/tripplanner/destination/security/ServletSecurityConfig.java
    - services/destination-service/src/main/java/com/tripplanner/destination/security/RestAuthenticationEntryPoint.java
    - services/trip-service/src/test/java/com/tripplanner/trip/security/DirectServiceAccessWithoutGatewayReturns401IT.java
    - services/destination-service/src/test/java/com/tripplanner/destination/security/DirectServiceAccessWithoutGatewayReturns401IT.java
  modified:
    - services/trip-service/build.gradle.kts (stale comment removal + testRuntimeOnly(libs.h2))
    - services/destination-service/build.gradle.kts (symmetric)
    - gradle/libs.versions.toml (added h2 catalog entry)
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java (Rule 1: inject ObjectMapper)
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java (Rule 1: pass ObjectMapper to filter)
    - services/trip-service/src/main/java/com/tripplanner/trip/security/RestAuthenticationEntryPoint.java (Rule 1: inject ObjectMapper)
    - services/destination-service/src/main/java/com/tripplanner/destination/security/RestAuthenticationEntryPoint.java (Rule 1: symmetric)
decisions:
  - "FilterRegistrationBean<ServletJwtCommonFilter> parameter form used (not bare ServletJwtCommonFilter) — JwtAutoConfiguration.ServletConfig only exposes FilterRegistrationBean, confirmed by reading JwtAutoConfiguration.java after 01-02 shipped"
  - "Spring Boot auto-configured ObjectMapper injected into ServletJwtCommonFilter + RestAuthenticationEntryPoint — the ProblemDetailJacksonMixin registered on the auto-configured ObjectMapper flattens extension properties (code, etc.) to JSON root; new ObjectMapper() nests them under 'properties', breaking $.code jsonPath assertions"
  - "H2 in-memory DB added for security ITs — @SpringBootTest(RANDOM_PORT) loads the full Spring context including JPA; Flyway disabled via spring.flyway.enabled=false but DataSource still needed; H2 satisfies this without docker-compose dependency"
metrics:
  duration_minutes: 15
  completed_date: "2026-05-09"
  tasks_completed: 3
  files_created: 6
  files_modified: 7
---

# Phase 1 Plan 4: Wave 3 Downstream Servlet Security Wiring Summary

**One-liner:** @EnableWebSecurity SecurityFilterChain + RestAuthenticationEntryPoint (RFC 7807 ProblemDetail 401) wired into trip-service + destination-service via FilterRegistrationBean injection; Pitfall 1 SC#4 keystone DirectServiceAccessWithoutGatewayReturns401IT green in both services.

---

## What Was Built

Wave 3 closes the defense-in-depth gap: trip-service and destination-service now enforce JWT validation independently of the gateway. The Pitfall 1 keystone (SC#4) is proven by a running integration test in both services.

### Task 4.1: build.gradle.kts wiring

- Removed stale `// catalogued; first integration test arrives in Phase 1+` trailing comment from `testImplementation(libs.spring.boot.testcontainers)` in both service build files. All 4 Phase 1 catalog accessors (jwt-common, spring.boot.starter.security, spring.security.test, testFixtures(jwt-common)) were already present from Plan 01-01.
- Convention C20-P0 preserved: dependency blocks byte-identical between trip-service and destination-service.
- Commit: f940561

### Task 4.2: ServletSecurityConfig + RestAuthenticationEntryPoint (both services)

**ServletSecurityConfig** (`security/`): `@Configuration @EnableWebSecurity`. `SecurityFilterChain` composition:
- `permitAll()`: `/__health`, `/__health/**`, `/actuator/health`, `/actuator/health/**`, `/actuator/info` (Convention C10 / Phase 0 D-01 preservation)
- `anyRequest().authenticated()` (Convention C26-P1 default-deny)
- CSRF disabled via `AbstractHttpConfigurer::disable` (stateless JSON API)
- `SessionCreationPolicy.STATELESS` — no JSESSIONID cookie (T-01-13)
- `.addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)` — T-01-11 mitigation (explicit ordering, not relying on FilterRegistrationBean order alone)
- `.exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))` — pins RestAuthenticationEntryPoint for Spring Security internal 401s

**RestAuthenticationEntryPoint** (`security/`): `@Component implements AuthenticationEntryPoint`. Uses Spring Boot's auto-configured `ObjectMapper` (constructor-injected) so `ProblemDetailJacksonMixin` is registered — writes `ProblemDetail` with extension properties at JSON root (not under `"properties"`). Emits status 401 + `application/problem+json` + `ProblemDetailFactory.of(UNAUTHORIZED, AUTH_UNAUTHORIZED, "Authentication required")`.

**Key note — FilterRegistrationBean form:** `JwtAutoConfiguration.ServletConfig` (Plan 01-02) exposes only `FilterRegistrationBean<ServletJwtCommonFilter>`, not a bare `ServletJwtCommonFilter` bean. `servletJwtFilter.getFilter()` is the correct injection pattern. Plan 01-04's plan action had a fallback note for exactly this case — confirmed necessary after reading `JwtAutoConfiguration.java`.

- trip-service and destination-service `@Configuration`-body and `@Component`-body are byte-identical (Convention C20-P0 / Bucket D 'identical config text').
- Commit: 6f0d05a

### Task 4.3: DirectServiceAccessWithoutGatewayReturns401IT (both services)

**SC#4 / Pitfall 1 keystone integration test.** Three test methods:

1. `direct_call_with_xUserId_and_no_jwt_returns_401`: GET /api/trips/_ping with `X-User-Id: spoofed-uuid` and no Authorization → 401 `application/problem+json` `$.code == "auth.unauthorized"`
2. `direct_call_with_no_headers_at_all_returns_401`: GET /api/trips/_ping with no headers → same 401
3. `health_endpoint_remains_anonymous_accessible`: GET /__health → 200 with `$.service == "trip-service"` and `$.phase == 0` (Phase 0 D-01 preservation guard)

`@SpringBootTest(webEnvironment = RANDOM_PORT) @AutoConfigureMockMvc` with:
- `auth.jwt.secret=phase-1-jwt-fixture-secret-32bytes!!` (JwtFixtures.TEST_SECRET literal)
- `spring.cloud.discovery.enabled=false` + `eureka.client.enabled=false`
- `spring.flyway.enabled=false` + H2 in-memory DataSource (Rule 3 auto-fix)

destination-service mirror: identical test structure against `/api/destinations/_ping`; health asserts `"service":"destination-service"`.

**Verification:** `./gradlew :services:trip-service:test --tests com.tripplanner.trip.security.DirectServiceAccessWithoutGatewayReturns401IT` → BUILD SUCCESSFUL (3/3 tests pass). Same for destination-service.

- Commit: 6d466d7

---

## Filter Chain Ordering (Both Downstream Services After This Plan)

```
HTTP request → Tomcat Servlet Container
  → (libs/observability) MdcEnrichmentFilter      @ Integer.MIN_VALUE + 100
       writes traceId/spanId/requestId to MDC
  → (libs/jwt-common) ServletJwtCommonFilter       @ Integer.MIN_VALUE + 200 (FilterRegistrationBean)
       ALSO wired into Spring Security's chain via:
         addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)
       On missing/invalid Bearer: writes 401 application/problem+json directly (filter-level 401)
       On valid Bearer: populates SecurityContextHolder + MDC.put("userId", ...)
  → Spring Security FilterChain (default order)
       authorize step: /__health, /actuator/health, /actuator/info → permitAll
                       anyRequest() → authenticated()
       On unauthenticated: RestAuthenticationEntryPoint → 401 application/problem+json (safety net)
  → Spring MVC DispatcherServlet → Controller (e.g. PingController in Plan 01-05)
```

The dual wiring (FilterRegistrationBean + addFilterBefore) ensures the filter runs at filter-chain level regardless of Spring Security's internal ordering decisions (T-01-11 mitigation).

---

## SC#4 / Pitfall 1 Keystone Status

**CLOSED.** `DirectServiceAccessWithoutGatewayReturns401IT` is green in BOTH trip-service and destination-service.

Gate commands:
```
./gradlew :services:trip-service:test --tests com.tripplanner.trip.security.DirectServiceAccessWithoutGatewayReturns401IT
./gradlew :services:destination-service:test --tests com.tripplanner.destination.security.DirectServiceAccessWithoutGatewayReturns401IT
```

---

## Auth-Service Scope Confirmation

**auth-service is untouched.** Confirmed:
- `git status --porcelain services/auth-service/` → empty
- `find services/auth-service/src/test/java -name 'DirectServiceAccess*' 2>/dev/null | wc -l` → 0
- `files_modified` contains zero entries under `services/auth-service/`

auth-service stays open in Phase 1 per plan scope. Phase 2 owns its own auth surface.

---

## Convention Compliance

| Convention | Status | Verification |
|------------|--------|-------------|
| C20-P0 trip↔destination byte-identical | PASSED | `diff` of dep blocks, `@Configuration`-body, `@Component`-body all return 0 |
| C26-P1 every authenticated path requires JWT | PASSED | `anyRequest().authenticated()` in SecurityFilterChain; verified by `direct_call_with_no_headers_at_all_returns_401` |
| C35-P1 header citation on every new file | PASSED | `find ... -exec grep -L '^// Source:' {} \;` returns nothing |
| T-01-11 explicit addFilterBefore | PASSED | `grep -F 'addFilterBefore' ServletSecurityConfig.java` → 1 match; `grep -F 'UsernamePasswordAuthenticationFilter.class'` → 1 match |
| T-01-13 STATELESS session | PASSED | `grep -F 'SessionCreationPolicy.STATELESS' ServletSecurityConfig.java` → 1 match |

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added H2 in-memory DB for security integration tests**
- **Found during:** Task 4.3 — `./gradlew :services:trip-service:test` failed with `FlywaySqlException: PSQLException: Connection refused` (PostgreSQL not running in CI/local test environment)
- **Issue:** `@SpringBootTest(webEnvironment=RANDOM_PORT)` starts the full Spring application context including JPA/Flyway which requires a live PostgreSQL connection. The security IT does not need the persistence layer.
- **Fix:** Added `h2 = { module = "com.h2database:h2" }` to `gradle/libs.versions.toml` + `testRuntimeOnly(libs.h2)` to both service build files + `spring.flyway.enabled=false`, `spring.jpa.hibernate.ddl-auto=none`, H2 DataSource properties to `@TestPropertySource`.
- **Files modified:** `gradle/libs.versions.toml`, `services/trip-service/build.gradle.kts`, `services/destination-service/build.gradle.kts`, both IT test files
- **Commit:** 6d466d7

**2. [Rule 1 - Bug] Injected Spring Boot's auto-configured ObjectMapper into filter and entry point**
- **Found during:** Task 4.3 — tests failed with `PathNotFoundException: No results for path: $['code']` (response body had `{"properties":{"code":"auth.unauthorized"}}` not `{"code":"auth.unauthorized"}`)
- **Issue:** Both `ServletJwtCommonFilter` (Plan 01-02) and `RestAuthenticationEntryPoint` used `new ObjectMapper()` instead of Spring Boot's auto-configured `ObjectMapper`. Spring Boot auto-registers `ProblemDetailJacksonMixin` which flattens `ProblemDetail` extension properties (set via `setProperty(key, value)`) to the JSON root. A plain `ObjectMapper` serializes them nested under `"properties"`, breaking the `$.code` jsonPath assertions required by the plan.
- **Fix:** Updated `ServletJwtCommonFilter` constructor to accept `ObjectMapper`; updated `JwtAutoConfiguration.ServletConfig.servletJwtFilter()` to inject the Spring-managed `ObjectMapper`; updated `RestAuthenticationEntryPoint` to use constructor injection for `ObjectMapper`.
- **Files modified:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java`, `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java`, both `RestAuthenticationEntryPoint.java` files
- **Commit:** 6d466d7

**3. [Rule 3 - Blocking] Used FilterRegistrationBean<ServletJwtCommonFilter>.getFilter() form**
- **Found during:** Task 4.2 design phase — reading `JwtAutoConfiguration.java` confirmed that `ServletConfig` exposes only `FilterRegistrationBean<ServletJwtCommonFilter>`, not a bare `ServletJwtCommonFilter` bean. Plan 01-04's action block had a "Bean-availability fallback note" anticipating exactly this case.
- **Fix:** `securityFilterChain(HttpSecurity, FilterRegistrationBean<ServletJwtCommonFilter> jwtFilterReg, RestAuthenticationEntryPoint)` with `.addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)`.
- **Files modified:** Both `ServletSecurityConfig.java` files
- **Commit:** 6f0d05a

---

## Hand-off Notes

### To Plan 01-05 (PingController in trip-service + destination-service)

`ServletSecurityConfig` is in place. `PingController` at `/api/trips/_ping` (resp. `/api/destinations/_ping`) MUST consume `@AuthenticationPrincipal com.tripplanner.contracts.UserContext` — the principal is auto-populated by `ServletJwtCommonFilter` on valid Bearer token. `/__health` remains in the `permitAll` allowlist; do NOT add it again.

The `DirectServiceAccessWithoutGatewayReturns401IT` keystone test currently passes against the bare `SecurityFilterChain` (filter-level 401 from `ServletJwtCommonFilter` before Spring MVC routing). It will still pass after 01-05 ships `PingController` because the anonymous-and-malformed branches are filter-level rejections that pre-empt MVC routing.

### To Plan 01-06 (Phase 1 verification harness / smoke.sh extension)

The keystone test is in-place. The smoke script's anonymous `/__health` probe still works (preserved by `health_endpoint_remains_anonymous_accessible`). The harness can rely on:
```
./gradlew :services:trip-service:test --tests *DirectServiceAccessWithoutGateway*
./gradlew :services:destination-service:test --tests *DirectServiceAccessWithoutGateway*
```
as the standalone gate commands.

---

## Threat Surface Scan

No new network endpoints introduced. All plan threat model items mitigated:

| Threat ID | Mitigation |
|-----------|-----------|
| T-01-04 | ServletJwtCommonFilter wired into SecurityFilterChain in BOTH trip-service and destination-service; regression gate: DirectServiceAccessWithoutGatewayReturns401IT |
| T-01-11 | explicit `addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)` in both ServletSecurityConfig files |
| T-01-12 | RestAuthenticationEntryPoint emits fixed `ProblemDetailFactory.of(UNAUTHORIZED, AUTH_UNAUTHORIZED, "Authentication required")` — no exception message echoed |
| T-01-13 | `SessionCreationPolicy.STATELESS` in both ServletSecurityConfig files |

---

## Self-Check: PASSED

Files verified:
- `services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java` — FOUND
- `services/trip-service/src/main/java/com/tripplanner/trip/security/RestAuthenticationEntryPoint.java` — FOUND
- `services/destination-service/src/main/java/com/tripplanner/destination/security/ServletSecurityConfig.java` — FOUND
- `services/destination-service/src/main/java/com/tripplanner/destination/security/RestAuthenticationEntryPoint.java` — FOUND
- `services/trip-service/src/test/java/com/tripplanner/trip/security/DirectServiceAccessWithoutGatewayReturns401IT.java` — FOUND
- `services/destination-service/src/test/java/com/tripplanner/destination/security/DirectServiceAccessWithoutGatewayReturns401IT.java` — FOUND

Commits verified:
- `f940561` — chore(01-04): trip/destination-service build.gradle.kts cleanup
- `6f0d05a` — feat(01-04): ServletSecurityConfig + RestAuthenticationEntryPoint
- `6d466d7` — feat(01-04): DirectServiceAccessWithoutGatewayReturns401IT keystone IT

Tests verified:
- `./gradlew :services:trip-service:test --tests com.tripplanner.trip.security.DirectServiceAccessWithoutGatewayReturns401IT` → BUILD SUCCESSFUL (3/3 pass)
- `./gradlew :services:destination-service:test --tests com.tripplanner.destination.security.DirectServiceAccessWithoutGatewayReturns401IT` → BUILD SUCCESSFUL (3/3 pass)
- `./gradlew :services:trip-service:test :services:destination-service:test` → BUILD SUCCESSFUL

auth-service: `git status --porcelain services/auth-service/` → empty (untouched)
