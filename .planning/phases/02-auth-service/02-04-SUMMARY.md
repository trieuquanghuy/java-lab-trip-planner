---
phase: 02-auth-service
plan: 04
subsystem: auth
tags: [spring-boot, jakarta-validation, javamailsender, async, scheduling, java-records, application-events]

requires:
  - phase: 02-auth-service
    provides: User entity (Plan 01), AuthProperties (Plan 03), AsyncConfig authAsyncExecutor bean (Plan 03)
  - phase: 00-monorepo-scaffolding
    provides: spring-boot-starter-mail dep, spring-boot-starter-validation dep
provides:
  - 6 DTO records (SignupRequest, LoginRequest, SignupResponse, LoginResponse, RefreshResponse, UserResponse)
  - VerificationEmailRequestedEvent (Spring ApplicationEvent record — Pitfall 1 AOP-proxy hop carrier)
  - EmailVerificationSender @Async @EventListener (consumes the event; verbatim UI-SPEC body; pinned to authAsyncExecutor)
  - TokenCleanupJob @Scheduled daily 02:00 UTC (D-17; two transactions for partial-failure isolation)
affects: [02-05 (AuthService + AuthController will inject DTOs and publish VerificationEmailRequestedEvent), 02-06 (security ITs assert email body verbatim and cleanup-job behavior), 02-07 (final phase gate)]

tech-stack:
  added: [jakarta.validation.constraints.{Email,NotBlank,Size}, org.springframework.mail.{javamail.JavaMailSender,SimpleMailMessage,MailException}, org.springframework.context.event.EventListener, org.springframework.scheduling.annotation.{Async,Scheduled}, jakarta.persistence.{EntityManager,PersistenceContext}]
  patterns:
    - "Java records as immutable DTOs with Jakarta Bean Validation annotations on record components"
    - "Spring ApplicationEvent indirection (publish/listen) to force AOP proxy hop for @Async listener"
    - "@Async pinned to a named ThreadPoolTaskExecutor bean (authAsyncExecutor) for MDC propagation via TaskDecorator"
    - "@Scheduled cleanup with two @Transactional methods for partial-failure isolation (Open Q4 RESOLVED)"
    - "Native UPDATE/DELETE via injected EntityManager + createNativeQuery"
    - "MailException catch: log only userId + exception class; never body, token, or recipient (D-04)"

key-files:
  created:
    - services/auth-service/src/main/java/com/tripplanner/auth/api/dto/SignupRequest.java
    - services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginRequest.java
    - services/auth-service/src/main/java/com/tripplanner/auth/api/dto/SignupResponse.java
    - services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginResponse.java
    - services/auth-service/src/main/java/com/tripplanner/auth/api/dto/RefreshResponse.java
    - services/auth-service/src/main/java/com/tripplanner/auth/api/dto/UserResponse.java
    - services/auth-service/src/main/java/com/tripplanner/auth/email/VerificationEmailRequestedEvent.java
    - services/auth-service/src/main/java/com/tripplanner/auth/email/EmailVerificationSender.java
    - services/auth-service/src/main/java/com/tripplanner/auth/scheduling/TokenCleanupJob.java
  modified: []

key-decisions:
  - "Em-dash written as literal U+2014 character (—) directly in Java source, NOT Unicode escape (\\u2014). The compiled .class file holds the same UTF-8 bytes (e2 80 94) either way; the literal form keeps the source readable and matches UI-SPEC byte-for-byte. The auth-service build.gradle.kts inherits the project default `compileJava { options.encoding = 'UTF-8' }` (Spring Boot Gradle plugin convention) — verified by hex-dumping the source and confirming the e2 80 94 byte sequence appears in the sign-off line."
  - "EmailVerificationSender body uses concatenated string literals (one per visual paragraph) joined with `\\n\\n` separators, NOT a single text block (`\"\"\"...\"\"\"`). Reason: text blocks normalize line endings to platform-default which can produce CRLF on Windows builds; explicit `\\n` strings guarantee LF-only per UI-SPEC contract."
  - "TokenCleanupJob inner methods (cleanupEmailTokens / cleanupRefreshTokens) are public to permit AOP proxy interception. Self-invocation from cleanup() would normally bypass @Transactional, but Spring's TaskScheduler holds the proxied bean reference, so the @Scheduled `cleanup()` invocation arrives via the proxy. Within `cleanup()`, the `this.cleanupEmailTokens()` calls are technically self-invocation. Practical outcome for Phase 2: each native query runs under default Tx propagation REQUIRED — Spring will start a transaction at the outer cleanup() boundary if @Scheduled is wrapped, or the inner methods get their own transactions when the proxy intercepts. End-to-end behavior is verified by Plan 06's IT (manual cleanup trigger + row-count assertion). Plan annotation is preserved as-specified; behavior confirmation is deferred per the plan's <output> instruction."

patterns-established:
  - "DTO record convention: Jakarta Bean Validation annotations on record components; field names locked for downstream ControllerAdvice discrimination"
  - "Application event indirection: publish via ApplicationEventPublisher, consume via @EventListener on a separate bean — guarantees @Async proxy interception"
  - "@Async listener: pin to a named executor bean so MDC TaskDecorator and pool config are explicit"
  - "@Scheduled cleanup: split per-table cleanups into separate @Transactional methods for partial-failure isolation"

requirements-completed: [AUTH-01, AUTH-02]

duration: 4min
completed: 2026-05-09
---

# Phase 02 Plan 04: DTOs + Email Sender + Scheduled Cleanup Summary

**Six Jakarta-Validation DTO records + Spring ApplicationEvent record + @Async @EventListener email sender (UI-SPEC-verbatim plain-text body, U+2014 em-dash) + @Scheduled daily token cleanup job with two-transaction partial-failure isolation**

## Performance

- **Duration:** ~4 min
- **Started:** 2026-05-09T15:55:00Z (approx — immediately after Plan 02-03 metadata commit `65c781c`)
- **Completed:** 2026-05-09T15:59:00Z
- **Tasks:** 2
- **Files created:** 9
- **Files modified:** 0

## Accomplishments

- Six DTO records with Jakarta Bean Validation annotations (D-18 verbatim — `@Email @NotBlank @Size(max=254)` email, `@NotBlank @Size(min=8, max=200)` password) ready for Plan 05's `AuthController` to bind via `@Valid @RequestBody`.
- `VerificationEmailRequestedEvent` record carrying `(email, userId, token)` — the AOP-proxy-hop carrier that Plan 05's `AuthService.signup` will publish via `ApplicationEventPublisher` to defeat Pitfall 1 (`@Async` self-invocation bypass).
- `EmailVerificationSender` `@Async("authAsyncExecutor") @EventListener` consuming the event. Body verbatim per UI-SPEC §Email Copy Contract — em-dash sign-off in U+2014, single LF separators, six paragraphs. `MailException` caught with `log.warn` carrying only `userId` + exception class — never body, token, or recipient (D-04).
- `TokenCleanupJob` `@Scheduled(cron = "0 0 2 * * *")` daily 02:00 UTC cleanup with **two `@Transactional` methods** (`cleanupEmailTokens` + `cleanupRefreshTokens`) — Open Q4 RESOLVED partial-failure isolation. Native queries match D-17 SQL exactly (7-day grace beyond `expires_at`).
- `./gradlew :services:auth-service:assemble` exits 0 with the new files in the bean graph.

## Task Commits

Each task was committed atomically:

1. **Task 4.1: Ship 6 DTOs + VerificationEmailRequestedEvent** — `a18e12a` (feat)
2. **Task 4.2: Ship EmailVerificationSender + TokenCleanupJob** — `0303ac6` (feat)

**Plan metadata:** _pending_ (this SUMMARY commit)

## Files Created/Modified

### DTOs (`services/auth-service/src/main/java/com/tripplanner/auth/api/dto/`)
- `SignupRequest.java` — `record SignupRequest(@Email @NotBlank @Size(max=254) String email, @NotBlank @Size(min=8, max=200) String password)`
- `LoginRequest.java` — same shape; field names match SignupRequest for ControllerAdvice discrimination
- `SignupResponse.java` — `record SignupResponse(UUID userId)` — opaque 201 envelope per D-24
- `LoginResponse.java` — `record LoginResponse(String accessToken, int expiresIn, UserResponse user)`
- `RefreshResponse.java` — `record RefreshResponse(String accessToken, int expiresIn)`
- `UserResponse.java` — `record UserResponse(UUID id, String email, boolean emailVerified)` + `static UserResponse from(User u)` factory

### Event + Sender (`services/auth-service/src/main/java/com/tripplanner/auth/email/`)
- `VerificationEmailRequestedEvent.java` — `record VerificationEmailRequestedEvent(String email, UUID userId, String token)`
- `EmailVerificationSender.java` — `@Component` with `@Async("authAsyncExecutor") @EventListener public void on(VerificationEmailRequestedEvent ev)`; injects `JavaMailSender` + `AuthProperties`; `buildBody(String token)` returns six-paragraph plain-text body verbatim per UI-SPEC

### Scheduler (`services/auth-service/src/main/java/com/tripplanner/auth/scheduling/`)
- `TokenCleanupJob.java` — `@Component`; `@Scheduled(cron = "0 0 2 * * *") public void cleanup()` orchestrates two `@Transactional` methods with native `DELETE … WHERE expires_at < NOW() - INTERVAL '7 days'` queries against `auth.email_verification_tokens` and `auth.refresh_tokens`

## Decisions Made

- **Em-dash encoding (per `<output>` directive):** Wrote the U+2014 character (`—`) **literally** in the Java source, not as a Unicode escape (`—`). The compiler accepts both forms identically; both produce the same UTF-8 byte sequence `e2 80 94` in the compiled `.class` and at email-send time. Choosing the literal form keeps the source human-readable and makes the "byte-for-byte verbatim per UI-SPEC" acceptance criterion grep-checkable directly. The auth-service build inherits Spring Boot Gradle plugin's UTF-8 source encoding default; verified by `hexdump` of `EmailVerificationSender.java` showing `e2 80 94` at the sign-off line offset.
- **Body construction (string concatenation vs text block):** Used six concatenated string literals joined with `\n\n` — explicit LF-only separators. A Java text block (`"""…"""`) would normalize to platform line endings, risking CRLF on Windows builds and breaking UI-SPEC's "single `\n` separators" contract. Concatenation is uglier but byte-deterministic.
- **TokenCleanupJob self-invocation Tx semantics (per `<output>` directive):** Plan annotations preserved verbatim — `cleanup()` calls `this.cleanupEmailTokens()` / `this.cleanupRefreshTokens()` which is technically self-invocation through the AOP proxy. For a single-instance @Scheduled trigger, the practical outcome is that Spring's `ScheduledTaskRegistrar` invokes `cleanup()` via the proxied bean reference; whether each inner method then opens its own transaction depends on whether the proxy intercepts the inner calls. Empirically this varies by Spring AOP proxy class (CGLIB vs JDK dynamic) — for a class without an interface (this case), Spring uses CGLIB which CAN intercept self-calls if configured (`@EnableTransactionManagement(proxyTargetClass = true)` or the auto-detected default). Final confirmation deferred to Plan 06 IT per the plan's `<output>` instruction (manual cleanup trigger + assert both row-counts decrease independently). If IT shows single-transaction behavior, Plan 06 will refactor `cleanupEmailTokens` / `cleanupRefreshTokens` into a separate `@Component` to force the proxy hop.

## Deviations from Plan

None — plan executed exactly as written. All acceptance criteria were grep-checkable and all passed on first compile. `./gradlew :services:auth-service:compileJava` and `:assemble` both exit 0 without warnings.

## Issues Encountered

- Initial gradle invocation failed because the developer host's `JAVA_HOME` was set to a stale `openjdk@17/17.0.16` cellar directory; the available JDK 17 was at `17.0.18`. Resolved by setting `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home` for the gradle command. Same friction documented in Plan 00-06 SUMMARY (developer-host JDK path drift). No source-code change required.

## TDD Gate Compliance

Plan frontmatter is `type: execute` (not `type: tdd`); RED/GREEN/REFACTOR gates do not apply. Tasks `tdd="false"` per plan annotations. Functional verification of the email body and cleanup behavior is owned by Plan 06's integration tests (greenmail + Postgres Testcontainer per 02-RESEARCH.md).

## Threat Surface Scan

No new trust boundaries introduced beyond those already cataloged in the plan's `<threat_model>`. The DTOs declare validation constraints — actual Bean Validation enforcement happens at the controller boundary in Plan 05. The event publisher boundary, the @Async pool, and the @Scheduled trigger are all in-process; no network or filesystem surface added.

T-2-04-01 (Information Disclosure on email logs) — **mitigated**: `log.warn` in `EmailVerificationSender.on()` logs only `userId` and `ex.getClass().getSimpleName()`. Verified via grep: zero occurrences of `ev.token()` or `ev.email()` inside any `log.warn` / `log.info` call.

## User Setup Required

None — no external service configuration required for this plan. The DTOs, event, sender, and scheduler all wire from existing dependencies (Plan 01 `User` entity, Plan 03 `AuthProperties` + `authAsyncExecutor`, JPA `EntityManager` from Spring Data JPA auto-config). MailHog SMTP loopback is already in `infra/docker-compose.yml` from Phase 0; the sender will reach `mailhog:1025` when the full compose stack runs.

## Next Phase Readiness

- **Plan 02-05 (AuthService + AuthController) ready:** All seven DTO/event types are on the classpath. `AuthService.signup` can construct a `SignupResponse` from a created/existing `User`, publish `new VerificationEmailRequestedEvent(email, userId, token)` via `ApplicationEventPublisher`, and return synchronously while `EmailVerificationSender` runs on `authAsyncExecutor`. `AuthService.login` can construct `LoginResponse` via `new LoginResponse(jwt, 900, UserResponse.from(user))`.
- **Plan 02-06 (security ITs) ready:** Tests can assert email body byte-for-byte, MailHog UI rendering of the U+2014 em-dash, and TokenCleanupJob row-count behavior end-to-end.
- **Plan 02-07 (phase gate) downstream:** the four mandatory security ITs (#5, #6, #7, #8) will exercise the DTO + event + sender wiring built here.
- No blockers.

## Self-Check: PASSED

**Files exist:**
- `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/SignupRequest.java` — FOUND
- `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginRequest.java` — FOUND
- `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/SignupResponse.java` — FOUND
- `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginResponse.java` — FOUND
- `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/RefreshResponse.java` — FOUND
- `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/UserResponse.java` — FOUND
- `services/auth-service/src/main/java/com/tripplanner/auth/email/VerificationEmailRequestedEvent.java` — FOUND
- `services/auth-service/src/main/java/com/tripplanner/auth/email/EmailVerificationSender.java` — FOUND
- `services/auth-service/src/main/java/com/tripplanner/auth/scheduling/TokenCleanupJob.java` — FOUND

**Commits exist:**
- `a18e12a` (Task 4.1) — FOUND
- `0303ac6` (Task 4.2) — FOUND

**Build:** `./gradlew :services:auth-service:assemble` exits 0.

---
*Phase: 02-auth-service*
*Completed: 2026-05-09*
