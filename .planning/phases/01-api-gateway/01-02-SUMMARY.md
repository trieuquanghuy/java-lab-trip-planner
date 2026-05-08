---
phase: 01-api-gateway
plan: "02"
subsystem: jwt-common
tags: [jwt, spring-security, filters, autoconfig, observability-fix, test-fixtures]
dependency_graph:
  requires: ["01-01 (libs/jwt-common build infrastructure, testFixtures source set wired)"]
  provides:
    - "libs/jwt-common JwtVerifier (HS256 pure-Java verifier, jjwt 0.13.0)"
    - "libs/jwt-common JwtAutoConfiguration (registered via AutoConfiguration.imports)"
    - "libs/jwt-common ServletJwtCommonFilter (trip-service + destination-service)"
    - "libs/jwt-common ServerBearerTokenConverter + ReactiveJwtAuthenticationManager (api-gateway WebFlux)"
    - "libs/api-contracts UserContext (Principal-typed record)"
    - "libs/error-handling ErrorCode extended (AUTH_INVALID_TOKEN, AUTH_TOKEN_EXPIRED, BAD_GATEWAY)"
    - "libs/jwt-common JwtFixtures testFixtures JAR (mintValid/mintExpired/mintWrongSig/mintMalformed)"
  affects:
    - "services/api-gateway/... — ServerBearerTokenConverter + ReactiveJwtAuthenticationManager autowireable in Wave 2 WebFluxSecurityConfig"
    - "services/trip-service/... — FilterRegistrationBean<ServletJwtCommonFilter> auto-registered at Integer.MIN_VALUE + 200"
    - "services/destination-service/... — same as trip-service"
    - "libs/observability/ObservabilityAutoConfiguration — WR-02 fix applied"
tech_stack:
  added:
    - "jjwt 0.13.0 modern API: Jwts.parser().verifyWith(SecretKey).build().parseSignedClaims()"
    - "JwtAutoConfiguration @ConditionalOnWebApplication discriminator (C31-P1)"
    - "FilterRegistrationBean<ServletJwtCommonFilter> at Integer.MIN_VALUE + 200 (after MdcEnrichmentFilter at +100)"
    - "JwtFixtures testFixtures JAR (TEST_SECRET + 4 mint methods)"
    - "junit-platform-launcher testRuntimeOnly (JUnit Platform 1.12.x alignment fix)"
    - "jackson-databind + slf4j-api added to jwt-common implementation deps"
  patterns:
    - "Convention C27-P1: jjwt 0.13.0 modern API only — verifyWith() not parserBuilder()/setSigningKey()"
    - "Convention C28-P1: JwtVerifier constructor throws IllegalStateException on null/<32-byte secret"
    - "Convention C29-P1: ServletJwtCommonFilter writes userId to MDC; reactive components do NOT touch MDC"
    - "Convention C31-P1: @ConditionalOnWebApplication discriminates SERVLET vs REACTIVE in all autoconfigs"
    - "Convention C32-P1: UserContext is a record implementing java.security.Principal with getName()=userId"
    - "Convention C35-P1: // Source: header citation on every new file"
key_files:
  created:
    - libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java
    - libs/api-contracts/src/test/java/com/tripplanner/contracts/UserContextTest.java
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAuthenticationException.java
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtProperties.java
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ServerBearerTokenConverter.java
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ReactiveJwtAuthenticationManager.java
    - libs/jwt-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    - libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java
    - libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java
    - libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtFixturesSmokeMintTask.java
  modified:
    - libs/api-contracts/build.gradle.kts (added spring-boot-starter-test, junit-platform-launcher testRuntimeOnly, spring-dependency-management)
    - libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java (3 new constants)
    - libs/jwt-common/build.gradle.kts (added jackson-databind, slf4j-api, spring-security-web/config test, testFixtures dep, junit-platform-launcher)
    - libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java (WR-02 fix)
decisions:
  - "jjwt 0.13.0 modern API only (Jwts.parser().verifyWith().build().parseSignedClaims()) — C27-P1. parserBuilder()/setSigningKey() absent in all new code."
  - "JwtVerifier constructor validates secret at construction time (null or < 32 bytes → IllegalStateException) — C28-P1. Fail-fast on misconfiguration."
  - "UserContext is a record (not a class) implementing java.security.Principal — getName() = userId for audit logging. C32-P1."
  - "WR-02 closed: @ConditionalOnWebApplication replaces @ConditionalOnClass in both ObservabilityAutoConfiguration and JwtAutoConfiguration — C31-P1. Convention now uniform across all autoconfigs."
  - "jackson-databind and slf4j-api added as implementation deps to jwt-common (Rule 2 — ServletJwtCommonFilter requires both for writing ProblemDetail JSON and MDC userId writes)."
  - "junit-platform-launcher testRuntimeOnly added to api-contracts and jwt-common (Rule 2 — JUnit Platform 1.12.x engine/launcher version alignment; without it Gradle's useJUnitPlatform() fails to discover tests)."
  - "JwtFixtures TEST_SECRET = 36-byte string 'phase-1-jwt-fixture-secret-32bytes!!' — satisfies C28-P1 minimum 32 bytes."
  - "Filter ordering chain locked: MdcEnrichmentFilter at Integer.MIN_VALUE + 100 → ServletJwtCommonFilter at Integer.MIN_VALUE + 200. traceId/spanId in MDC before userId is written."
metrics:
  duration_minutes: 8
  completed_date: "2026-05-08"
  tasks_completed: 5
  files_created: 13
  files_modified: 4
---

# Phase 1 Plan 2: Wave 1 JWT Primitives Summary

**One-liner:** HS256 JwtVerifier (jjwt 0.13.0 modern API) + UserContext Principal record + ServletJwtCommonFilter (defense-in-depth) + ReactiveJwtAuthenticationManager + JwtAutoConfiguration with @ConditionalOnWebApplication + JwtFixtures testFixtures JAR + WR-02 observability fix.

---

## What Was Built

Wave 1 lands all shared JWT primitives in `libs/jwt-common`, extends `libs/api-contracts` and `libs/error-handling`, and closes the Phase 0 WR-02 carryover in `libs/observability`.

### Task 2.1: Core types — UserContext, ErrorCode extension, JwtVerifier, JwtAuthenticationException, JwtProperties

- **UserContext** (`libs/api-contracts`): `record UserContext(String userId, String email, boolean verified) implements Principal` — `getName()` returns `userId` per C32-P1. Controllers consume via `@AuthenticationPrincipal UserContext`.
- **ErrorCode** (`libs/error-handling`): Extended with 3 new constants (Phase 0 ordinals preserved): `AUTH_INVALID_TOKEN("auth.invalid_token")`, `AUTH_TOKEN_EXPIRED("auth.token_expired")`, `BAD_GATEWAY("gateway.bad_gateway")`.
- **JwtVerifier** (`libs/jwt-common`): Pure-Java HS256 verifier using jjwt 0.13.0 modern API exclusively (`Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(jws).getPayload()`). Constructor throws `IllegalStateException` on null or <32-byte secret (C28-P1 fail-fast). Returns `UserContext(sub, email, ver)` on valid token.
- **JwtAuthenticationException** (`libs/jwt-common`): Extends `org.springframework.security.core.AuthenticationException` so Spring Security's filter chain treats JWT failures as auth failures (not 500s).
- **JwtProperties** (`libs/jwt-common`): `@ConfigurationProperties("auth.jwt")` — binds `AUTH_JWT_SECRET` env var to `secret` field (D-16).
- **UserContextTest** passes: all 5 assertions (accessors, getName(), Principal assignability, equals/hashCode, inequality).
- **api-contracts build.gradle.kts**: Updated to add `spring-boot-starter-test` + `junit-platform-launcher testRuntimeOnly` + `spring-dependency-management` plugin (Rule 2 — needed for UserContextTest).

### Task 2.2: Filters — ServletJwtCommonFilter + ServerBearerTokenConverter + ReactiveJwtAuthenticationManager + JwtFixtures

- **ServletJwtCommonFilter** (`libs/jwt-common/servlet`): `extends OncePerRequestFilter`. Whitelist: `/__health`, `/actuator/health`, `/actuator/info`. On valid Bearer: sets `SecurityContextHolder` + `MDC.put("userId", ...)`. In `finally`: clears `SecurityContextHolder` + `MDC.remove("userId")`. On expired token: 401 with `AUTH_TOKEN_EXPIRED`. On invalid/wrong-sig: 401 with `AUTH_INVALID_TOKEN`. On missing Bearer: 401 with `AUTH_UNAUTHORIZED`.
- **ServerBearerTokenConverter** (`libs/jwt-common/reactive`): `implements ServerAuthenticationConverter`. Returns `Mono.empty()` on missing/wrong-scheme headers; emits `BearerTokenAuthentication(token, isAuthenticated=false)` on valid Bearer.
- **ReactiveJwtAuthenticationManager** (`libs/jwt-common/reactive`): `implements ReactiveAuthenticationManager`. Wraps `JwtVerifier`; on `JwtAuthenticationException` → `Mono.error(BadCredentialsException)`.
- **JwtFixtures** (testFixtures source set): `TEST_SECRET = "phase-1-jwt-fixture-secret-32bytes!!"` (36 bytes). Exposes `mintValid`, `mintExpired`, `mintWrongSig`, `mintMalformed`.
- **JwtVerifierTest**: 7 test cases all pass (valid, expired, wrongSig, malformed, missingSub, null secret, short secret).
- **JwtFixturesSmokeMintTask**: Prints a valid JWT to stdout; Plan 01-06's `scripts/mint-test-token.sh` captures it.
- **jwt-common build.gradle.kts**: Added `jackson-databind`, `slf4j-api` (implementation), `spring-security-web/config` + `testFixtures(project(":libs:jwt-common"))` (testImplementation), `junit-platform-launcher` (testRuntimeOnly).

### Task 2.3a: JwtAutoConfiguration + AutoConfiguration.imports

- **JwtAutoConfiguration** (`libs/jwt-common`): `@AutoConfiguration @EnableConfigurationProperties(JwtProperties.class)`. Provides `@Bean JwtVerifier`. Inner `@Configuration @ConditionalOnWebApplication(SERVLET)` registers `FilterRegistrationBean<ServletJwtCommonFilter>` at order `Integer.MIN_VALUE + 200`. Inner `@Configuration @ConditionalOnWebApplication(REACTIVE)` exposes `ServerBearerTokenConverter` + `ReactiveJwtAuthenticationManager` beans.
- **AutoConfiguration.imports**: Single line `com.tripplanner.jwt.JwtAutoConfiguration` — Spring Boot SB 3.x auto-configuration registration.

### Task 2.3b: WR-02 fix — ObservabilityAutoConfiguration

- Replaced `@ConditionalOnClass(name = "jakarta.servlet.Filter")` with `@ConditionalOnWebApplication(Type.SERVLET)`.
- Replaced `@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")` with `@ConditionalOnWebApplication(Type.REACTIVE)`.
- Removed `@ConditionalOnClass` import (no longer used).
- Added WR-02 header note.
- `./gradlew :libs:observability:check` exits 0.
- **Phase 0 carryover WR-02 is closed.**

### Task 2.4: JwtFixtures (testFixtures source set entry point)

Completed as part of Task 2.2 above. `testFixturesJar` produced at `libs/jwt-common/build/libs/jwt-common-0.1.0-SNAPSHOT-test-fixtures.jar`. Plans 01-04, 01-05, 01-06 consume via `testImplementation(testFixtures(project(":libs:jwt-common")))`.

---

## Filter Ordering Chain

```
MDC filter (MdcEnrichmentFilter)        @ Integer.MIN_VALUE + 100  → writes traceId/spanId/requestId
JWT filter (ServletJwtCommonFilter)     @ Integer.MIN_VALUE + 200  → writes userId to MDC + SecurityContextHolder
Spring Security FilterChain             @ Spring default order     → authenticated() / permitAll()
Controller filter(s)                    @ application order        → real endpoint logic
```

The reactive gateway does NOT have the MDC → JWT ordering (Pitfall F sidestep per C29-P1); it uses `SecurityWebFilterChain` with `AuthenticationWebFilter` wired by Wave 2's `WebFluxSecurityConfig`.

---

## Verification Results

```
./gradlew :libs:jwt-common:check :libs:api-contracts:check :libs:error-handling:check :libs:observability:check
→ EXIT 0

./gradlew :libs:jwt-common:jar :libs:jwt-common:testFixturesJar
→ EXIT 0; produces jwt-common-0.1.0-SNAPSHOT.jar + jwt-common-0.1.0-SNAPSHOT-test-fixtures.jar

./gradlew :libs:jwt-common:test --tests "com.tripplanner.jwt.JwtVerifierTest"
→ EXIT 0 (7/7 test cases pass)

./gradlew :libs:api-contracts:test --tests "com.tripplanner.contracts.UserContextTest"
→ EXIT 0 (5/5 test cases pass)

./gradlew :libs:jwt-common:test --tests "com.tripplanner.jwt.JwtFixturesSmokeMintTask" --console=plain
→ EXIT 0; prints eyJhbGciOiJIUzI1NiJ9... (valid compact JWT)

grep -F 'verifyWith' libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java
→ 1 match (C27-P1 confirmed)

grep -rF 'parserBuilder' libs/jwt-common/src/main/java/ (excluding comments)
→ 0 matches (deprecated API absent — C27-P1)

grep -F 'implements Principal' libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java
→ 1 match (C32-P1)

grep -rF 'MDC.put' libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/
→ 0 matches (C29-P1 — reactive does not touch MDC)

WR-02 check: grep ConditionalOnClass(name = "org.springframework.web.server.WebFilter") libs/observability/ObservabilityAutoConfiguration.java
→ 0 matches (WR-02 closed)
```

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing] Added junit-platform-launcher testRuntimeOnly to api-contracts and jwt-common**
- **Found during:** Task 2.1 — `./gradlew :libs:api-contracts:test` failed with "OutputDirectoryProvider not available; probably due to unaligned versions of the junit-platform-engine and junit-platform-launcher jars"
- **Issue:** JUnit Platform 1.12.x requires `junit-platform-launcher` to be explicitly on the test runtime classpath when using Gradle's `useJUnitPlatform()` (especially with newer JUnit Platform versions). The Spring BOM managed the engine but not the launcher.
- **Fix:** Added `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` to both `libs/api-contracts/build.gradle.kts` and `libs/jwt-common/build.gradle.kts`.
- **Files modified:** `libs/api-contracts/build.gradle.kts`, `libs/jwt-common/build.gradle.kts`
- **Commit:** c0aaa64

**2. [Rule 2 - Missing] Added jackson-databind and slf4j-api to jwt-common implementation deps**
- **Found during:** Task 2.2 — `compileJava` for jwt-common failed with "package com.fasterxml.jackson.databind does not exist" and "symbol: variable MDC"
- **Issue:** `ServletJwtCommonFilter` uses `ObjectMapper` (Jackson) for writing ProblemDetail JSON to the response, and `MDC` (SLF4J) for userId MDC population. Both are managed by Spring BOM but were not explicitly listed as `implementation` deps in `libs/jwt-common/build.gradle.kts`.
- **Fix:** Added `implementation("com.fasterxml.jackson.core:jackson-databind")` and `implementation("org.slf4j:slf4j-api")`.
- **Files modified:** `libs/jwt-common/build.gradle.kts`
- **Commit:** 2d100ac

**3. [Rule 2 - Missing] Added spring-security-web/config + testFixtures dep to jwt-common testImplementation**
- **Found during:** Task 2.2 — `compileTestJava` failed with "class file for org.springframework.security.core.AuthenticationException not found"
- **Issue:** Tests reference `JwtAuthenticationException` which extends `AuthenticationException`. Spring Security was `compileOnly` on the main classpath but not on the test classpath.
- **Fix:** Added `testImplementation("org.springframework.security:spring-security-web")`, `testImplementation("org.springframework.security:spring-security-config")`, and `testImplementation(testFixtures(project(":libs:jwt-common")))`.
- **Files modified:** `libs/jwt-common/build.gradle.kts`
- **Commit:** 2d100ac

---

## WR-02 Closure

Phase 0 carryover WR-02 is confirmed closed:
- `libs/observability/ObservabilityAutoConfiguration.java`: Both `ServletConfig` and `ReactiveConfig` inner `@Configuration` classes now use `@ConditionalOnWebApplication` (not `@ConditionalOnClass`).
- Convention C31-P1 is now uniformly applied across `libs/observability` and `libs/jwt-common`.
- Reference: `.planning/phases/00-monorepo-scaffolding/00-REVIEW.md` WR-02.

---

## Hand-off Notes

### To Plan 01-04 (gateway WebFluxSecurityConfig)

`ServerBearerTokenConverter` and `ReactiveJwtAuthenticationManager` are auto-configured `@Bean`s on REACTIVE web applications via `JwtAutoConfiguration.ReactiveConfig`. Wire them into `AuthenticationWebFilter` in your `SecurityWebFilterChain`:

```java
AuthenticationWebFilter jwtFilter = new AuthenticationWebFilter(reactiveJwtAuthenticationManager);
jwtFilter.setServerAuthenticationConverter(serverBearerTokenConverter);
http.addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION);
```

### To Plan 01-06 (trip-service + destination-service ServletSecurityConfig)

`FilterRegistrationBean<ServletJwtCommonFilter>` is auto-registered at order `Integer.MIN_VALUE + 200` on SERVLET web applications via `JwtAutoConfiguration.ServletConfig`. The `SecurityFilterChain` only needs to recognize the pre-authenticated token that the filter places in `SecurityContextHolder`:

```java
http.addFilterBefore(servletJwtFilter, UsernamePasswordAuthenticationFilter.class)
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
```

The `FilterRegistrationBean` bean already exists in the context via auto-configuration — no manual `new ServletJwtCommonFilter(...)` call needed.

---

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes introduced. All threat model items from the plan's `<threat_model>` are mitigated:

| Threat ID | Mitigation Status |
|-----------|------------------|
| T-01-01 | Mitigated — JwtVerifier shared across gateway + downstream services; same secret enforced |
| T-01-02 | Mitigated — jjwt 0.13.0 `verifyWith(SecretKey)` rejects `none` algorithm path |
| T-01-03 | Mitigated — constructor throws IllegalStateException on null/<32-byte secret (C28-P1) |
| T-01-09 | Accepted — UserContext not serialized in API responses; only userId in `_ping` debug payloads |

No new threat surface beyond what was already documented in the plan's threat model.

---

## Self-Check: PASSED

Files verified:
- `libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java` — FOUND
- `libs/api-contracts/src/test/java/com/tripplanner/contracts/UserContextTest.java` — FOUND
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java` — FOUND
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAuthenticationException.java` — FOUND
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtProperties.java` — FOUND
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java` — FOUND
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java` — FOUND
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ServerBearerTokenConverter.java` — FOUND
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ReactiveJwtAuthenticationManager.java` — FOUND
- `libs/jwt-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — FOUND
- `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java` — FOUND
- `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java` — FOUND
- `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtFixturesSmokeMintTask.java` — FOUND
- `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java` — FOUND (WR-02 fix applied)

Commits verified:
- `c0aaa64` — feat(01-02): core types
- `2d100ac` — feat(01-02): filters
- `1869400` — feat(01-02): JwtAutoConfiguration + imports
- `5ceb2a2` — fix(01-02): WR-02 fix
