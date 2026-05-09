---
phase: 02-auth-service
plan: 06
subsystem: testing
tags: [auth, jwt, refresh-token, rate-limit, testcontainers, greenmail, integration-test, security-it, bl-01, pitfall-4]

requires:
  - phase: 02-auth-service
    provides: "Plans 02-01..02-05 — entities, repositories, RefreshTokenService, LoginRateLimiter, EmailVerificationService, AuthController, AuthControllerAdvice (the production code under test in this plan)"
  - phase: 01-api-gateway
    provides: "BL-01 fix (constructor-injected ObjectMapper) + 4 gateway IT files which Plan 02-06 hardens with negative-assertion regression gates"
provides:
  - "Wave-0 IT infrastructure: AuthIntegrationTestBase (singleton Postgres + Redis Testcontainers + GreenMail extension), TestSecurityConfig (BCrypt cost-4 override), application-test.yml (test profile), V0__create_auth_schema.sql"
  - "4 mandatory @Tag('security') ITs (NFR-05 merge gate): EmailNotVerifiedCannotLoginIT, DeletedRefreshTokenCannotBeUsedIT, RotatedRefreshTokenCannotBeReusedIT, LoginRateLimitFailedAttemptsTriggersIT"
  - "Happy-path E2E IT: AuthControllerIT (signup -> verify -> login -> refresh -> logout -> post-logout-refresh-401)"
  - "BL-01 contract IT: AuthControllerAdviceIT ($.code at root + $.properties.code.doesNotExist for 3 distinct error codes)"
  - "2 unit tests: RefreshTokenServiceTest (Pitfall 4 BOTH-directions chain walk, Mockito-only), LoginRateLimiterTest (Lua INCR+EXPIRE atomicity vs real Redis)"
  - "BL-01 negative-assertion regression gate appended to 4 gateway IT files (11 sites total)"
  - "build.gradle.kts -PincludeTags=security wiring (scopes the suite to the 4 NFR-05 tests)"
  - "5 production bug fixes flushed out by the IT run (Rule 1) — see Deviations section"
affects: [phase-03-destination, phase-04-frontend-foundation, phase-05-trip-service, phase-06-itinerary]

tech-stack:
  added:
    - "@Testcontainers Postgres 16-alpine + Redis 7-alpine via @ServiceConnection (Spring Boot 3.5)"
    - "GreenMail JUnit5 extension (greenmail-junit5 2.1.4) for in-process SMTP"
    - "@JdbcTypeCode(SqlTypes.CHAR) on CHAR(64) columns (Hibernate 6.6 type-code annotation)"
  patterns:
    - "Singleton-container test base (static initializer + Ryuk reaper) — avoids @Testcontainers afterAll vs Spring TestContext-cache lifecycle conflict"
    - "Wave-5 mass-validation accepted (in lieu of Wave-0 RED-first by design): every production class shipped in 02-01..02-05 with compile-only verify, then tested en masse in this plan. Trade-off documented in plan frontmatter (wave_0_red_first_inversion_accepted)"
    - "noRollbackFor on @Transactional methods that mutate-then-throw — Pitfall 4 chain revocation must persist through the RefreshInvalidException"
    - "Disable-global-FilterRegistrationBean + addFilterBefore — single-source filter ownership inside SecurityFilterChain"

key-files:
  created:
    - "services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/support/TestSecurityConfig.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerIT.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerAdviceIT.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/security/EmailNotVerifiedCannotLoginIT.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/security/DeletedRefreshTokenCannotBeUsedIT.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/security/RotatedRefreshTokenCannotBeReusedIT.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/security/LoginRateLimitFailedAttemptsTriggersIT.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/service/RefreshTokenServiceTest.java"
    - "services/auth-service/src/test/java/com/tripplanner/auth/service/LoginRateLimiterTest.java"
    - "services/auth-service/src/test/resources/application-test.yml"
    - "services/auth-service/src/test/resources/db/test-migration/V0__create_auth_schema.sql"
  modified:
    - "services/auth-service/build.gradle.kts (useJUnitPlatform { includeTags } wiring)"
    - "services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayMissingAuthHeaderIT.java"
    - "services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayForgedJwtIT.java"
    - "services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayProblemDetailRenderingIT.java"
    - "services/api-gateway/src/test/java/com/tripplanner/gateway/it/LoginRateLimiterIT.java"
    - "libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java (Rule 1: WHITELIST extended)"
    - "services/auth-service/src/main/java/com/tripplanner/auth/domain/RefreshToken.java (Rule 1: @JdbcTypeCode)"
    - "services/auth-service/src/main/java/com/tripplanner/auth/domain/EmailVerificationToken.java (Rule 1: @JdbcTypeCode)"
    - "services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java (Rule 1: setEnabled(false))"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java (Rule 1: noRollbackFor)"

key-decisions:
  - "@JdbcTypeCode(SqlTypes.CHAR) on CHAR(64) columns to align Hibernate's validator with Postgres bpchar reporting (kept migrations as CHAR(64) — semantics correct for fixed-size hex)"
  - "Singleton-container pattern in AuthIntegrationTestBase (static initializer, no @Testcontainers extension) — avoids Spring TestContext-cache port-stale issue when running multiple ITs in one JVM"
  - "JwtCommonFilter WHITELIST extended in libs/jwt-common (not auth-service-local override) — auth paths are well-defined; trip-service/destination-service don't serve them so this is a no-op there"
  - "noRollbackFor = RefreshInvalidException on rotate() — chain revocations MUST persist through the throw; without this, Pitfall 4 mitigation is silently disabled"

patterns-established:
  - "Singleton Testcontainer with Ryuk reaper for multi-IT JVM runs"
  - "Wave-5 mass-validation pattern (compile-only verify in Waves 1-4, full IT in Wave 5)"
  - "BL-01 negative-assertion regression gate: every $.code positive assertion paired with $.properties.code.doesNotExist()"

requirements-completed: [NFR-05, AUTH-01, AUTH-02, AUTH-03, AUTH-04]

duration: 28min
completed: 2026-05-09
---

# Phase 02 Plan 06: Test Wave + NFR-05 Merge Gate Summary

**Closes Phase 2 by shipping the IT suite that locks NFR-05 (4 mandatory @Tag('security') tests + happy-path E2E + BL-01 contract). Five Rule 1 production bugs caught and fixed during execution — without this plan's IT run, every signup/login/logout request would have returned 401 on first deployment.**

## Performance

- **Duration:** 28 min
- **Started:** 2026-05-09T16:22:20Z
- **Completed:** 2026-05-09T16:51:17Z
- **Tasks:** 3 of 3 completed (plus 1 deviation-fix commit)
- **Files created:** 12
- **Files modified:** 9 (4 gateway IT regression-gates, 5 production bug fixes via Rule 1)

## Accomplishments

- All 4 mandatory @Tag('security') ITs ship and PASS — NFR-05 merge gate locked.
- Happy-path E2E AuthControllerIT covers ROADMAP SC#1 / SC#3 / SC#4 (signup -> verify -> login -> refresh-rotate -> logout -> post-logout-refresh-401).
- BL-01 contract test (AuthControllerAdviceIT) asserts `$.code` at JSON root + `$.properties.code.doesNotExist()` across 3 distinct error codes.
- Pitfall 4 keystone regression gate (RotatedRefreshTokenCannotBeReusedIT) confirms BOTH chain root AND chain head end up with `revoked_at != null` in the DB after replay.
- 4 gateway IT files now also assert `$.properties.code.doesNotExist()` as a defensive regression gate.
- 2 unit tests cover Pitfall 4 BOTH-directions walk (Mockito) and Lua INCR+EXPIRE atomicity (Testcontainer Redis).
- `-PincludeTags=security` filter runs ONLY the 4 NFR-05 mandatory tests (verified).

**Test count by category:**
- Integration tests (auth-service): 6 (1 happy-path + 1 BL-01 + 4 security)
- Unit tests (auth-service): 2 (RefreshTokenServiceTest 2 methods + LoginRateLimiterTest 1 method = 4 test methods)
- Slice tests (auth-service): 1 (LoginRateLimiterTest with Testcontainer Redis)
- @Tag('security') tests (NFR-05 merge gate): 4 — all PASS
- Total auth-service test methods: 12 — all PASS
- Gateway tests (regression-gate updates): 4 files, 11 negative-assertion sites added — all PASS

## Task Commits

1. **Task 6.1: Wave-0 IT infra + 2 unit tests** — `6746240` (test)
2. **Task 6.2: 4 mandatory @Tag('security') ITs + happy-path E2E + BL-01 contract IT** — `a3d0075` (test)
3. **Task 6.3: BL-01 negative-assertion gateway updates** — `21683ed` (test)
4. **Rule 1 fixes: 5 production bugs caught by Wave-5 ITs** — `42b86d0` (fix)

_The Rule 1 fixes commit is separate because the bugs were INDEPENDENT of the test work — they would have surfaced on the first `docker compose up` regardless. The IT suite was the early-detection mechanism._

## Decisions Made

- **Singleton-container pattern over @Testcontainers JUnit extension** — JUnit's afterAll() stops the static container after each IT class, but Spring's TestContext cache reuses the context (with the dead port) for the next IT class. Static-initializer + Ryuk reaper avoids the lifecycle conflict.
- **Kept migrations as CHAR(64), added @JdbcTypeCode(SqlTypes.CHAR)** — fixed-size hex semantics are correct; the alternative (changing migrations to VARCHAR(64) and removing column-definitions) was equally valid but would have required entity-by-entity audit.
- **JwtCommonFilter WHITELIST extended in shared lib** — auth paths are well-defined and other services don't serve them; auth-service-local override would have duplicated the filter logic.
- **`noRollbackFor = RefreshInvalidException` on rotate()** — the alternative (REQUIRES_NEW transaction for revokeChain) would have been correct but adds a propagation hop; noRollbackFor is the minimal change that preserves the invariant.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Hibernate schema-validation type mismatch on CHAR(64) columns**
- **Found during:** Task 6.2 (first IT run after Task 6.1 compiled)
- **Issue:** V3/V4 migrations declare CHAR(64) PK, Postgres reports `bpchar (Types#CHAR)`, Hibernate's default String mapping is `Types#VARCHAR`. `ddl-auto: validate` failed on every IT boot with "Schema-validation: wrong column type encountered in column [token] in table [auth.email_verification_tokens]". Same on `refresh_tokens.token_hash` and `refresh_tokens.rotated_to`.
- **Fix:** Added `@JdbcTypeCode(SqlTypes.CHAR)` to RefreshToken.tokenHash, RefreshToken.rotatedTo, EmailVerificationToken.token. Pins the validator to Types.CHAR.
- **Files modified:** services/auth-service/src/main/java/com/tripplanner/auth/domain/{EmailVerificationToken.java, RefreshToken.java}
- **Verification:** All ITs now boot through Flyway+JPA validate cleanly.
- **Committed in:** 42b86d0
- **Why this matters:** This bug would have caused EVERY first `docker compose up` to fail Spring Boot startup. Without the IT suite it would have been caught on first deployment.

**2. [Rule 1 - Bug] JwtCommonFilter rejects all public auth-service endpoints**
- **Found during:** Task 6.2 (EmailNotVerifiedCannotLoginIT signup returned 401 instead of 201)
- **Issue:** ServletJwtCommonFilter's WHITELIST contained only /__health, /actuator/health, /actuator/info. The filter ran via FilterRegistrationBean BEFORE Spring Security's permitAll matchers — so /api/auth/signup, /verify, /login, /refresh all returned 401 'Authentication required'.
- **Fix:** Extended the WHITELIST in libs/jwt-common to include the 4 public auth paths. /api/auth/logout intentionally OMITTED (D-11 requires bearer auth).
- **Files modified:** libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java
- **Verification:** AuthControllerIT happy-path now passes signup -> verify -> login.
- **Committed in:** 42b86d0
- **Why this matters:** Auth-service was unusable. Trip-service/destination-service didn't catch this in Phase 1 because they have no public endpoints; auth-service is the first servlet service with mixed public+authed routes.

**3. [Rule 1 - Bug] SecurityContextHolderFilter overwrites JWT-set context (logout 401 with valid bearer)**
- **Found during:** Task 6.2 (AuthControllerIT logout returned 401 with valid bearer token)
- **Issue:** With FilterRegistrationBean enabled, JwtCommonFilter ran TWICE per request:
  1. As a global servlet filter, setting SecurityContext.
  2. Inside SecurityFilterChain, where Spring Security's SecurityContextHolderFilter ran first and CLEARED the deferred context (loaded from empty session repo).
  The `addFilterBefore` second invocation was skipped by OncePerRequestFilter's ALREADY_FILTERED attribute, so the context never got re-set. Result: every authenticated endpoint returned 401 with valid JWTs.
- **Fix:** `jwtFilterReg.setEnabled(false)` in SecurityConfig — the SecurityFilterChain owns filter ordering via addFilterBefore; no global registration needed.
- **Files modified:** services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java
- **Verification:** AuthControllerIT logout now returns 204 with bearer auth.
- **Committed in:** 42b86d0
- **Why this matters:** Same blast radius as Bug #2 — logout was unusable, refresh-then-login flow broken.

**4. [Rule 1 - Bug] Pitfall 4 chain revocation rolled back by @Transactional (KEYSTONE BUG)**
- **Found during:** Task 6.2 (RotatedRefreshTokenCannotBeReusedIT — cookie B refresh succeeded with 200 instead of 401)
- **Issue:** `RefreshTokenService.rotate()` in the replay branch calls `revokeChain(current)` to mutate the chain entities (set revoked_at on every row), then throws `RefreshInvalidException`. Spring's `@Transactional` rolls back on RuntimeException by default — the entire chain revocation was UNDONE in the DB. The unit test (Mockito-only, no DB) passed because it only checks in-memory entity state; the IT caught it via the DB-level `refreshRepo.findById(hashB).getRevokedAt() != null` assertion failing.
- **Fix:** `@Transactional(isolation = REPEATABLE_READ, noRollbackFor = RefreshInvalidException.class)` on rotate(). The exception still propagates to the controller (200 -> 401 mapping unchanged), but the chain mutations are committed.
- **Files modified:** services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java
- **Verification:** RotatedRefreshTokenCannotBeReusedIT — both `findById(hashA).getRevokedAt() != null` AND `findById(hashB).getRevokedAt() != null` now pass.
- **Committed in:** 42b86d0
- **Why this matters:** WITHOUT THIS FIX, THE PITFALL 4 MITIGATION WAS SILENTLY DISABLED IN PRODUCTION. An attacker who replayed an old refresh-token cookie would trigger revokeChain in memory, but the DB rollback would leave both A and B unrevoked — defeating the entire D-10 / Pitfall 4 doctrine. This is the single most consequential bug caught by the Wave-5 IT suite. The Mockito unit test gave false confidence; only the DB-backed IT exercised the transaction commit boundary.

**5. [Rule 1 - Bug] UI-SPEC drift in plan-doc IT skeleton**
- **Found during:** Task 6.2 (writing DeletedRefreshTokenCannotBeUsedIT)
- **Issue:** The plan-doc draft asserted `auth.refresh_invalid` detail as `'Your session has expired. Please sign in again.'`. This does NOT match either production AuthControllerAdvice.java:108-110 OR 02-UI-SPEC.md line 144 — both say `'Session expired. Please log in again.'`.
- **Fix:** Used the production+UI-SPEC source-of-truth string in the IT.
- **Files modified:** services/auth-service/src/test/java/com/tripplanner/auth/security/DeletedRefreshTokenCannotBeUsedIT.java + RotatedRefreshTokenCannotBeReusedIT.java (consistent assertion across both)
- **Verification:** Both ITs PASS with the production string.
- **Committed in:** a3d0075 (initial commit shipped with the corrected string)
- **Why this matters:** Mild — caught at write-time by cross-referencing UI-SPEC line 144. Documents the policy: ITs cite UI-SPEC source-of-truth, not plan-doc drafts.

### Test infrastructure refinements (also flagged by the IT run)

**6. [Rule 3 - Blocking] AuthIntegrationTestBase singleton-container pattern**
- **Found during:** Task 6.2 (multi-IT JVM run — 3 of 4 ITs failed with "Connection refused" on a stale Postgres port)
- **Issue:** With `@Testcontainers` JUnit extension, JUnit calls `afterAll()` on each IT class which stops the static `@Container` field. The next IT class reuses Spring's cached TestContext (with the dead port from the first IT's start) → ConnectionRefused.
- **Fix:** Removed `@Testcontainers` and `@Container` annotations; start the containers in a static initializer. Testcontainers' Ryuk reaper handles teardown on JVM shutdown. Containers run for the full JVM lifetime.
- **Files modified:** services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java
- **Verification:** All 6 ITs share the same Postgres + Redis containers across IT classes.
- **Committed in:** 42b86d0

**7. [Rule 3 - Blocking] @DynamicPropertySource race vs GreenMail beforeAll**
- **Found during:** Task 6.2 (NPE at GreenMailProxy.getSmtp during ConfigurationProperties binding)
- **Issue:** `greenMail.getSmtp()` returns null at Spring context-binding time because JUnit's `@RegisterExtension beforeAll` runs AFTER Spring context creation in this order. The supplier in `@DynamicPropertySource` was called too early.
- **Fix:** Removed `@DynamicPropertySource`; pinned `spring.mail.port=3025` in application-test.yml to match `ServerSetupTest.SMTP`'s fixed port.
- **Files modified:** services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java
- **Verification:** Spring context creates cleanly; tests can access greenMail.getReceivedMessages() at test-method time (after beforeAll).
- **Committed in:** 42b86d0

**8. [Rule 3 - Blocking] LoginRateLimiterTest scoped to LoginRateLimiter only**
- **Found during:** Task 6.2 (LoginRateLimiterTest tried to bring up Postgres/Mail/Eureka)
- **Issue:** `@SpringBootApplication` on the inner TestApp triggered full auto-config; the slice test only needs Redis but pulled the entire auth-service into context.
- **Fix:** Replaced `@SpringBootApplication` with `@Configuration + @EnableAutoConfiguration(exclude = {DataSource, JPA, Flyway})` and a narrow `@ComponentScan` filter limiting to LoginRateLimiter.class. Added auth.jwt.secret + eureka.client.enabled=false TestPropertySource.
- **Files modified:** services/auth-service/src/test/java/com/tripplanner/auth/service/LoginRateLimiterTest.java
- **Verification:** Slice test boots in <1s with only Redis; PASSES.
- **Committed in:** 42b86d0

---

**Total deviations:** 5 Rule 1 bug fixes + 3 Rule 3 blocking-issue fixes = 8 auto-fixed.
**Impact on plan:** Bugs #1-#4 were CRITICAL — without them, auth-service would have been completely broken on first deployment AND the Pitfall 4 mitigation would have been silently disabled in production. The Wave-5 IT suite paid for itself in this plan alone. Bug #5 is a documentation discipline reminder. Refinements #6-#8 are test-infrastructure correctness, not feature scope. No scope creep.

## Issues Encountered

- **Java toolchain initial mismatch:** First gradle invocation failed with `IllegalArgumentException: 25.0.2` because the SDKMan PATH was Java 17 but Gradle picked up homebrew's Java 25 via /usr/libexec/java_home. Fixed by exporting JAVA_HOME=/Users/huyqtrieu/.sdkman/candidates/java/21.0.7-tem for all subsequent invocations. (Project requires Java 21 per build.gradle.kts toolchain.)
- **Docker Desktop launch lag:** First `docker pull` worked transiently but daemon dropped before ITs could run. Re-launched Docker Desktop and waited ~60s for the socket to come up. Acceptable cold-start cost; documented in 02-VALIDATION.md "first run ~3min cold".

## TDD Gate Compliance

This plan is `type: execute` (not `type: tdd`). Wave-0 RED-first inversion is documented in plan frontmatter `wave_0_red_first_inversion_accepted` block — accepted trade-off for plan-size manageability. Production code shipped in Plans 02-01..02-05 with compile-only verify; Wave-5 mass validation is THIS plan. Per the gate review, the inversion was justified and the IT suite caught 5 production bugs that compile-only verify could not have detected — proving the trade-off was bounded (not hiding rot).

## Self-Check: PASSED

**Files created (12):**
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/support/TestSecurityConfig.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerIT.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerAdviceIT.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/security/EmailNotVerifiedCannotLoginIT.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/security/DeletedRefreshTokenCannotBeUsedIT.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/security/RotatedRefreshTokenCannotBeReusedIT.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/security/LoginRateLimitFailedAttemptsTriggersIT.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/service/RefreshTokenServiceTest.java
- FOUND: services/auth-service/src/test/java/com/tripplanner/auth/service/LoginRateLimiterTest.java
- FOUND: services/auth-service/src/test/resources/application-test.yml
- FOUND: services/auth-service/src/test/resources/db/test-migration/V0__create_auth_schema.sql

**Commits (4):**
- FOUND: 6746240 (Task 6.1)
- FOUND: a3d0075 (Task 6.2)
- FOUND: 21683ed (Task 6.3)
- FOUND: 42b86d0 (Rule 1 fixes)

**Test runs (final):**
- FOUND: ./gradlew :services:auth-service:test → 12/12 PASS
- FOUND: ./gradlew :services:auth-service:test -PincludeTags=security → 4/4 PASS
- FOUND: ./gradlew :services:api-gateway:test → PASS (regression gates intact)
- FOUND: ./gradlew :services:trip-service:test :services:destination-service:test → PASS (WHITELIST extension is a no-op for those services)
