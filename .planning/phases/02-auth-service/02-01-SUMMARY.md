---
phase: 02-auth-service
plan: 01
subsystem: database
tags: [flyway, jpa, hibernate, postgres, jakarta-persistence, jpa-pessimistic-lock, gradle-version-catalog, error-handling]

# Dependency graph
requires:
  - phase: 00-monorepo-scaffolding
    provides: "auth schema + auth_svc PG user (infra/postgres/init.sql); per-service auth_flyway_schema_history config (application.yml); flyway-database-postgresql mandatory dep alias; Spring Boot 3.5.14 + Spring Cloud 2025.0.x BOM wiring"
  - phase: 01-api-gateway
    provides: "libs/jwt-common (JwtVerifier + JwtFixtures testFixtures); libs/error-handling baseline ErrorCode (5 entries — AUTH_UNAUTHORIZED, AUTH_RATE_LIMITED, AUTH_INVALID_TOKEN, AUTH_TOKEN_EXPIRED, BAD_GATEWAY); libs/observability MdcEnrichmentFilter pattern"
provides:
  - "auth.users / auth.email_verification_tokens / auth.refresh_tokens schema (V2/V3/V4 Flyway migrations)"
  - "JPA entities (User, EmailVerificationToken, RefreshToken) with Instant timestamps + Jakarta EE 9+ imports"
  - "RefreshTokenRepository.findByTokenHashForUpdate (@Lock PESSIMISTIC_WRITE) — D-13 concurrent-refresh row lock primitive"
  - "RefreshTokenRepository.findByRotatedTo — Pitfall 4 chain reverse-lookup primitive"
  - "EmailVerificationTokenRepository.markAllUnconsumedAsConsumedFor (@Modifying) — D-23 re-signup UPDATE primitive"
  - "13-entry ErrorCode enum (5 baseline + 8 Phase 2 additions: AUTH_EMAIL_ALREADY_REGISTERED / AUTH_INVALID_CREDENTIALS / AUTH_EMAIL_NOT_VERIFIED / AUTH_TOKEN_INVALID / AUTH_REFRESH_INVALID / AUTH_WEAK_PASSWORD / AUTH_INVALID_EMAIL / VALIDATION_FAILED)"
  - "5 new gradle/libs.versions.toml aliases + greenmail 2.1.4 version pin"
  - "Phase 2 dep wiring on auth-service runtime classpath (jwt-common + spring-security + mail + validation + data-redis servlet) and test classpath (spring-security-test + testFixtures(jwt-common) + testcontainers-postgresql + testcontainers-junit-jupiter + greenmail-junit5 + junit-platform-launcher)"
affects: [02-02, 02-03, 02-04, 02-05, 02-06, 02-07]

# Tech tracking
tech-stack:
  added:
    - "spring-boot-starter-security 3.5.14 (BOM-managed)"
    - "spring-boot-starter-mail 3.5.14 (BOM-managed)"
    - "spring-boot-starter-validation 3.5.14 (BOM-managed)"
    - "spring-boot-starter-data-redis 3.5.14 servlet variant (BOM-managed)"
    - "testcontainers-postgresql (managed by testcontainers-bom)"
    - "greenmail-junit5 2.1.4 (explicit pin — not Spring-managed)"
    - "junit-platform-launcher (testRuntimeOnly per Phase 1 01-02 SUMMARY convention)"
  patterns:
    - "Pattern: JPA entities under com.tripplanner.auth.domain — @Table(name=..., schema=\"auth\") + @Column(columnDefinition=\"char(64)\") for token PKs + java.time.Instant for ALL timestamps (Pitfall 9). protected no-arg constructor for JPA + parameterized constructor for service layer."
    - "Pattern: Repository interfaces under com.tripplanner.auth.repository — @Repository annotation + JpaRepository<Entity, IdType>; pessimistic-lock methods use @Lock(LockModeType.PESSIMISTIC_WRITE) + explicit @Query JPQL; @Modifying for UPDATE primitives."
    - "Pattern: Flyway DDL files under src/main/resources/db/migration — V{N}__create_{table_name}.sql; auth-schema-qualified table refs; partial WHERE-clause indexes for sparse columns (rt_rotated_to_idx, evt_unconsumed_idx); rt_expires_at_idx (full B-tree) for cleanup-job scans."

key-files:
  created:
    - "services/auth-service/src/main/resources/db/migration/V2__create_users.sql"
    - "services/auth-service/src/main/resources/db/migration/V3__create_email_verification_tokens.sql"
    - "services/auth-service/src/main/resources/db/migration/V4__create_refresh_tokens.sql"
    - "services/auth-service/src/main/java/com/tripplanner/auth/domain/User.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/domain/EmailVerificationToken.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/domain/RefreshToken.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/repository/UserRepository.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/repository/EmailVerificationTokenRepository.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/repository/RefreshTokenRepository.java"
  modified:
    - "gradle/libs.versions.toml"
    - "services/auth-service/build.gradle.kts"
    - "libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java"

key-decisions:
  - "Pinned greenmail 2.1.4 explicitly (not Spring-managed) — latest stable 2.x as of 2026-05; test-only artifact"
  - "@Column(columnDefinition=\"char(64)\") on token PKs forces Hibernate to round-trip to CHAR(64) (not VARCHAR(64)) so ddl-auto: validate matches the Flyway DDL byte-for-byte"
  - "boolean (primitive, NOT Boolean) for User.emailVerified — column is BOOLEAN NOT NULL"
  - "Instant for every timestamp column (Pitfall 9 enforcement) — never LocalDateTime/LocalDate"
  - "Pre-set createdAt = updatedAt = Instant.now() in entity constructors — DB DEFAULT NOW() acts as a fallback only; service layer + Hibernate set the value explicitly"

patterns-established:
  - "Flyway migration naming: V{N}__create_{table}.sql (snake_case table) — extending Phase 0's V1 baseline"
  - "Per-table schema qualification (auth.users, auth.email_verification_tokens, auth.refresh_tokens) in BOTH DDL and @Table.schema — defense-in-depth against Pitfall 3"
  - "Partial-index pattern for sparse columns: WHERE consumed_at IS NULL on email_verification_tokens; WHERE rotated_to IS NOT NULL on refresh_tokens — keeps indexes tiny and matches D-23/Pitfall-4 query shapes"
  - "Repository @Repository + @Lock + @Query + @Modifying + @Param idiom — established here for downstream services (trip-service / destination-service in Phase 5+) to copy"
  - "ErrorCode enum extension idiom — append-only; trailing semicolon migrates to penultimate-comma when entries are added (no constructor change)"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, AUTH-04]

# Metrics
duration: 13min
completed: 2026-05-09
---

# Phase 02 Plan 01: Auth Persistence Foundation Summary

**Phase 2 persistence foundation: 3 Flyway migrations (auth.users / email_verification_tokens / refresh_tokens), 3 JPA entities with Instant timestamps, 3 repositories including @Lock(PESSIMISTIC_WRITE) findByTokenHashForUpdate + findByRotatedTo reverse-lookup; 8 new ErrorCode entries + 5 new catalog aliases unblock Plans 02-02 through 02-07.**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-05-09T15:30:00Z (approximate)
- **Completed:** 2026-05-09T15:43:00Z (approximate)
- **Tasks:** 3 (all `type=auto`)
- **Files created:** 9
- **Files modified:** 3

## Accomplishments

- **Schema foundation shipped.** V2/V3/V4 Flyway migrations produce auth.users + auth.email_verification_tokens + auth.refresh_tokens with all FK + unique + partial-index constraints called out in docs/03 §3.1, §3.2 and docs/05 §2. Token PKs are CHAR(64); user PK is UUID with `gen_random_uuid()` default; all 8 timestamp columns are TIMESTAMPTZ.
- **JPA entity + repository layer compiles cleanly.** `User`, `EmailVerificationToken`, `RefreshToken` use Jakarta EE 9+ persistence imports (zero `javax.persistence` references) and `java.time.Instant` exclusively (zero `LocalDateTime`/`LocalDate` references — Pitfall 9 hard rule held). Repositories expose the keystone primitives that Plans 02-03/02-05 will compose: `findByTokenHashForUpdate` (D-13 row lock), `findByRotatedTo` (Pitfall 4 reverse-lookup), `markAllUnconsumedAsConsumedFor` (D-23 re-signup UPDATE).
- **ErrorCode catalog expanded 5 → 13 entries.** Phase 2 controller advice (Plan 02-05) and the Phase-1 BL-01 fix (Plan 02-06) both consume these codes; the enum extension is the necessary precursor.
- **Gradle catalog + auth-service deps wired.** 5 new `libs.versions.toml` aliases + greenmail 2.1.4 version pin; auth-service runtime classpath now resolves `jwt-common` + `starter-security` + `starter-mail` + `starter-validation` + `starter-data-redis` (servlet variant — distinct from gateway's reactive variant). Test classpath gets `spring-security-test` + `testFixtures(jwt-common)` + `testcontainers-postgresql` + `testcontainers-junit-jupiter` + `greenmail-junit5` + `junit-platform-launcher`.

## Task Commits

Each task was committed atomically:

1. **Task 1.1: Catalog aliases + auth-service deps + extend ErrorCode** — `918b31d` (chore)
2. **Task 1.2: V2/V3/V4 Flyway migrations** — `7e66ca3` (feat)
3. **Task 1.3: JPA entities + repositories** — `91b0abd` (feat)

**Plan metadata commit follows.**

## Files Created/Modified

### Created
- `services/auth-service/src/main/resources/db/migration/V2__create_users.sql` — auth.users (UUID PK + UNIQUE email + bcrypt-sized password_hash)
- `services/auth-service/src/main/resources/db/migration/V3__create_email_verification_tokens.sql` — auth.email_verification_tokens + partial evt_unconsumed_idx
- `services/auth-service/src/main/resources/db/migration/V4__create_refresh_tokens.sql` — auth.refresh_tokens + partial rt_rotated_to_idx + rt_expires_at_idx
- `services/auth-service/src/main/java/com/tripplanner/auth/domain/User.java` — JPA @Entity for auth.users
- `services/auth-service/src/main/java/com/tripplanner/auth/domain/EmailVerificationToken.java` — JPA @Entity for auth.email_verification_tokens
- `services/auth-service/src/main/java/com/tripplanner/auth/domain/RefreshToken.java` — JPA @Entity for auth.refresh_tokens
- `services/auth-service/src/main/java/com/tripplanner/auth/repository/UserRepository.java` — JpaRepository<User, UUID> + findByEmail
- `services/auth-service/src/main/java/com/tripplanner/auth/repository/EmailVerificationTokenRepository.java` — JpaRepository + @Modifying markAllUnconsumedAsConsumedFor (D-23)
- `services/auth-service/src/main/java/com/tripplanner/auth/repository/RefreshTokenRepository.java` — JpaRepository + @Lock(PESSIMISTIC_WRITE) findByTokenHashForUpdate + findByRotatedTo (Pitfall 4)

### Modified
- `gradle/libs.versions.toml` — +1 version (greenmail), +5 library aliases
- `services/auth-service/build.gradle.kts` — +5 implementation deps, +6 test deps
- `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` — +8 enum entries (5 → 13 total)

## Decisions Made

- **Used JDK 17 for local gradle invocations** — same workaround documented in 00-06 SUMMARY (homebrew default openjdk 25 chokes Gradle 8.14.2's bundled Kotlin compiler with `JavaVersion.parse('25.0.2')`). `JavaLanguageVersion.of(21)` toolchain still drives the actual `javac`. CI is unaffected (`actions/setup-java@v4 java-version: 21`).
- **Plan-level boot test (Hibernate `ddl-auto: validate` against the live PostgreSQL container) deferred to Plan 02-04 / 02-05** — application.yml's `spring.flyway` and `spring.jpa` config is already correct from Phase 0, but no runtime smoke can run before service-layer beans (`AuthService`, `EmailVerificationService`, `RefreshTokenService`) wire up successfully. The plan-level `assemble` (clean) is the strongest static guarantee Phase 2 Plan 01 can give; the runtime gate happens when there's a service to start.
- **Verbatim DDL from RESEARCH §V2/V3/V4** — every column type, FK, index, and partial-WHERE clause is byte-for-byte from `02-RESEARCH.md` lines 765-819. No deviations.

## Deviations from Plan

None — plan executed exactly as written. All 3 tasks ran in `type=auto` mode without hitting auth gates, blocking issues, or architectural decisions. Acceptance criteria + plan-level verification all pass:

- `./gradlew :services:auth-service:assemble` — exit 0 (clean)
- `./gradlew :libs:error-handling:check` — exit 0 (clean)
- ErrorCode enum count = 13 (5 baseline + 8 Phase 2)
- Zero `LocalDateTime`/`LocalDate` imports under `services/auth-service/src/main/java/com/tripplanner/auth/domain/` (Pitfall 9 held)
- Zero `javax.persistence` imports (Jakarta EE 9+ held)
- All required substrings + grep patterns from `<acceptance_criteria>` blocks present in target files

## Issues Encountered

None. All static-analysis verifications passed on the first compile attempt for each task. Local-JDK workaround was a known precondition (documented in 00-06 SUMMARY); not a new issue.

## User Setup Required

None — no external service configuration required for this plan. Runtime-DB integration arrives in Plan 02-04/02-05; until then the artifacts in this plan are static (compile-only).

## Next Phase Readiness

**Plan 02-02 (JwtIssuer in libs/jwt-common)** is unblocked and runs in Wave 1 in parallel with this plan (zero file overlap by design — see plan frontmatter `depends_on: []` + plan objective).

**Plan 02-03 (Service layer)** is unblocked: `RefreshTokenService.rotate` will consume `findByTokenHashForUpdate`; `RefreshTokenService.revokeChain` will consume `findByRotatedTo` for Pitfall 4 reverse-lookup; `AuthService.signup` D-23 branch will consume `markAllUnconsumedAsConsumedFor`; AuthControllerAdvice (Plan 02-05) will consume the 8 new ErrorCode entries.

**Hibernate `ddl-auto: validate` boot test** explicitly deferred per plan `<output>` line ("Whether Hibernate `ddl-auto: validate` boot test was deferred to Plan 04 (likely yes — no runtime DB until then)") — confirmed: yes, deferred. Plan 02-04 / 02-05 will exercise the validate-against-live-Postgres path when `AuthServiceApplication` first boots with the full bean graph against the docker-compose Postgres container.

**No blockers, no concerns.** Wave-1 parallel-readiness is preserved (zero `libs/jwt-common` files touched here, so Plan 02-02 has no merge conflict surface).

## Self-Check: PASSED

Verifying claimed artifacts exist on disk:

- ✓ `services/auth-service/src/main/resources/db/migration/V2__create_users.sql` — present
- ✓ `services/auth-service/src/main/resources/db/migration/V3__create_email_verification_tokens.sql` — present
- ✓ `services/auth-service/src/main/resources/db/migration/V4__create_refresh_tokens.sql` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/domain/User.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/domain/EmailVerificationToken.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/domain/RefreshToken.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/repository/UserRepository.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/repository/EmailVerificationTokenRepository.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/repository/RefreshTokenRepository.java` — present
- ✓ `gradle/libs.versions.toml` — modified (5 aliases + 1 version added)
- ✓ `services/auth-service/build.gradle.kts` — modified (11 deps added)
- ✓ `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` — modified (8 entries added)

Verifying claimed commits exist:
- ✓ `918b31d` (Task 1.1) — present in `git log`
- ✓ `7e66ca3` (Task 1.2) — present in `git log`
- ✓ `91b0abd` (Task 1.3) — present in `git log`

---
*Phase: 02-auth-service*
*Completed: 2026-05-09*
