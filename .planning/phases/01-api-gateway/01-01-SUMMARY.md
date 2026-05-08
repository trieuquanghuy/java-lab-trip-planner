---
phase: 01-api-gateway
plan: "01"
subsystem: build-infrastructure
tags: [gradle, version-catalog, jwt-common, spring-security, redis, testfixtures]
dependency_graph:
  requires: []
  provides: [":libs:jwt-common", "spring-boot-starter-security accessor", "spring-boot-starter-data-redis-reactive accessor", "spring-security-test accessor", "testcontainers-junit-jupiter accessor"]
  affects: ["services/api-gateway/build.gradle.kts", "services/trip-service/build.gradle.kts", "services/destination-service/build.gradle.kts"]
tech_stack:
  added:
    - "libs/jwt-common: java-library + java-test-fixtures, jjwt 0.13.0, Spring Security (compileOnly)"
    - "spring-boot-starter-security (WebFlux on gateway, Servlet on trip/destination)"
    - "spring-boot-starter-data-redis-reactive (RedisRateLimiter backing store on gateway)"
    - "spring-security-test (all three services)"
    - "testcontainers-junit-jupiter (gateway)"
  patterns:
    - "Convention C16-P1: all new deps via catalog accessors, no version literals in service build files"
    - "java-test-fixtures plugin: testFixtures JAR consumable via testImplementation(testFixtures(project(\":libs:jwt-common\")))"
    - "Phase 0 D-08 invariant: trip-service and destination-service build files remain byte-identical modulo comments"
key_files:
  created:
    - libs/jwt-common/build.gradle.kts
    - libs/jwt-common/src/main/java/com/tripplanner/jwt/.gitkeep
    - libs/jwt-common/src/main/resources/META-INF/spring/.gitkeep
  modified:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - services/api-gateway/build.gradle.kts
    - services/trip-service/build.gradle.kts
    - services/destination-service/build.gradle.kts
decisions:
  - "Explicit LIFT of Phase 0 D-07 per 01-CONTEXT.md Open Question 1: libs/jwt-common is now a registered Gradle subproject"
  - "spring-security types declared as compileOnly in jwt-common so reactive-only (gateway) and servlet-only (trip/destination) consumers can independently choose their Security stack at runtime"
  - "micrometer-tracing-bom NOT imported in jwt-common (Convention C6-P1: pinned once in libs/observability)"
  - "testFixtures plugin enables JwtFixtures test helper in Plan 01-02 without coupling test utilities into main compile classpath"
metrics:
  duration_minutes: 2
  completed_date: "2026-05-08"
  tasks_completed: 3
  files_created: 3
  files_modified: 5
---

# Phase 1 Plan 1: Wave 0a Build Infrastructure Summary

**One-liner:** Added 4 Spring Security catalog accessors + registered :libs:jwt-common with java-test-fixtures plugin, wiring jwt-common and Security deps into api-gateway/trip-service/destination-service via catalog accessors only.

---

## What Was Built

Wave 0a lands the build infrastructure required before any Phase 1 Java code can compile. Three tasks executed:

### Task 1.1: Version Catalog + settings.gradle.kts
- Added 4 new library entries to `gradle/libs.versions.toml` under a clearly labeled `# --- Spring Security (Phase 1 ...)` section:
  - `spring-boot-starter-security` (BOM-managed, no version literal)
  - `spring-boot-starter-data-redis-reactive` (BOM-managed)
  - `spring-security-test` (BOM-managed)
  - `testcontainers-junit-jupiter` (BOM-managed via testcontainers BOM)
- Registered `:libs:jwt-common` in `settings.gradle.kts` `include()` block between `:libs:api-contracts` and `:services:eureka-server`

### Task 1.2: libs/jwt-common Module
- Created `libs/jwt-common/build.gradle.kts` with:
  - `java-library` + `java-test-fixtures` + `spring-dependency-management` plugins
  - `api` deps: `:libs:api-contracts` (UserContext), `:libs:error-handling` (ErrorCode), `jjwt-api` (0.13.0)
  - `runtimeOnly` deps: `jjwt-impl`, `jjwt-jackson`
  - `compileOnly` deps: `spring-security-web`, `spring-security-config`, `jakarta.servlet-api`, `spring-webflux`
  - `testFixtures` classpath: `jjwt-api/impl/jackson` for JWT token minting in test helpers
  - BOM import: `spring-boot-dependencies` (no micrometer-tracing-bom per Convention C6-P1)
- Created source directory placeholders (`src/main/java/.gitkeep`, `src/main/resources/META-INF/spring/.gitkeep`)
- `./gradlew :libs:jwt-common:tasks` exits 0 — `testFixturesJar` task visible

### Task 1.3: Service build.gradle.kts Extensions
- **api-gateway**: Added `jwt-common` (implementation), `spring-security` WebFlux, `redis-reactive`, `spring-security-test`, `testcontainers-junit-jupiter`, `testFixtures(jwt-common)`, `wiremock-spring-boot`; updated header comment
- **trip-service**: Added `jwt-common` (implementation), `spring-security` Servlet, `spring-security-test`, `testFixtures(jwt-common)`
- **destination-service**: Identical additions to trip-service (Phase 0 D-08 byte-identical invariant preserved)

---

## Verification Results

```
./gradlew :libs:jwt-common:tasks --quiet
→ EXIT 0
→ Tasks listed include: testFixturesJar, testFixturesClasses, jar, classes

./gradlew :services:api-gateway:tasks :services:trip-service:tasks :services:destination-service:tasks --quiet
→ EXIT 0

diff <(grep -v '^//' services/trip-service/build.gradle.kts) \
     <(grep -v '^//' services/destination-service/build.gradle.kts)
→ IDENTICAL (Phase 0 D-08 invariant preserved)
```

### Catalog additions audit:
- `grep -c 'spring-boot-starter-security' gradle/libs.versions.toml` → 1
- `grep -c 'spring-boot-starter-data-redis-reactive' gradle/libs.versions.toml` → 1
- `grep -c 'spring-security-test' gradle/libs.versions.toml` → 1
- `grep -c 'testcontainers-junit-jupiter' gradle/libs.versions.toml` → 1
- `grep -F '":libs:jwt-common"' settings.gradle.kts` → 1 match in include() block

---

## Deviations from Plan

None — plan executed exactly as written. All 3 task actions followed the PLAN.md specifications verbatim. All acceptance criteria passed on first attempt.

---

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. This plan is build infrastructure only (Gradle/catalog changes). No new threat surface beyond what was already documented in the plan's threat model.

---

## Hand-off Note for Plan 01-02

The `libs/jwt-common` module is registered and resolves on the Gradle build graph. The `java-test-fixtures` plugin is configured and the `testFixturesJar` task is available.

Plan 01-02 (Wave 0b: JwtFixtures + JwtVerifier) can immediately:
1. Create `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java` — the testFixtures source set is wired
2. Create `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java`
3. All three consuming services can use `testImplementation(testFixtures(project(":libs:jwt-common")))` to pull signed-JWT test helpers

No `build.gradle.kts` or `settings.gradle.kts` changes needed in Plan 01-02 — the dependency graph is fully declared.

---

## Self-Check: PASSED

Files verified:
- `libs/jwt-common/build.gradle.kts` — FOUND
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/.gitkeep` — FOUND
- `libs/jwt-common/src/main/resources/META-INF/spring/.gitkeep` — FOUND
- Catalog entries in `gradle/libs.versions.toml` — FOUND (all 4)
- `:libs:jwt-common` in `settings.gradle.kts` — FOUND

Commits verified:
- `2bd3258` — chore(01-01): extend version catalog + register :libs:jwt-common
- `9a0ea92` — feat(01-01): create libs/jwt-common module with java-test-fixtures
- `a41fe4b` — feat(01-01): wire jwt-common + security deps into api-gateway/trip/destination
