---
phase: 02-auth-service
plan: 03
subsystem: auth-services-config
tags: [spring-security, bcrypt, refresh-token-rotation, redis-rate-limit, async-mdc-propagation, problem-detail, configuration-properties, lua-script, pessimistic-lock]

# Dependency graph
requires:
  - phase: 02-auth-service
    provides: "Plan 01 — JPA entities (User/EmailVerificationToken/RefreshToken), 3 repositories including RefreshTokenRepository.findByTokenHashForUpdate (@Lock PESSIMISTIC_WRITE) + findByRotatedTo, EmailVerificationTokenRepository.markAllUnconsumedAsConsumedFor, 13-entry ErrorCode catalog (5 baseline + 8 Phase 2 additions); Plan 02 — JwtIssuer in libs/jwt-common with @Bean auto-configure"
  - phase: 01-api-gateway
    provides: "libs/jwt-common (JwtVerifier + JwtAutoConfiguration + ServletJwtCommonFilter); libs/error-handling (ProblemDetailFactory + ProblemDetailJacksonMixin auto-configured ObjectMapper); libs/observability (MdcEnrichmentFilter pattern); ServletSecurityConfig + RestAuthenticationEntryPoint analogs in trip-service / destination-service"
  - phase: 00-monorepo-scaffolding
    provides: "spring-boot 3.5.x BOM + Spring Cloud 2025.0.x train; per-service application.yml + application-docker.yml scaffolds; infra/docker-compose.yml mailhog (:1025) + redis (healthchecked) services; .env.example baseline"
provides:
  - "SecurityConfig (third instance of trip-service ServletSecurityConfig) — permitAll on /api/auth/{signup,verify,login,refresh}; everything else authenticated; BCryptPasswordEncoder(12) bean (D-19)"
  - "RestAuthenticationEntryPoint — 401 RFC 7807 emitter constructor-injecting auto-configured ObjectMapper (BL-01 / Pitfall 7)"
  - "AuthProperties — @ConfigurationProperties('app') binding 4 nested groups (Auth.Cookie, Frontend, Mail, Verification) — corrected prefix per plan revision iteration 1"
  - "AsyncConfig — @EnableAsync + named authAsyncExecutor (core 2 / max 4 / queue 50) + inline MdcCopyingTaskDecorator (D-01/D-22)"
  - "AuthServiceApplication wired with @EnableAsync + @EnableScheduling + @EnableConfigurationProperties(AuthProperties.class)"
  - "7 skeletal exception classes (RuntimeException no-arg) for Plan 04 advice handler: EmailAlreadyRegistered / InvalidCredentials / EmailNotVerified / TokenInvalid / TokenExpired / RefreshInvalid / LoginRateLimited"
  - "HashUtil.sha256Hex helper — single call site; eliminates 4-line try/catch boilerplate at every refresh-token hash callsite"
  - "LoginRateLimiter — Redis Lua INCR+EXPIRE atomic primitive; rl:login:fail:{ip}:{email_lower} keyspace; >= 5 threshold per D-06 (1-5 ok, 6th trips); clear() on successful login (D-07)"
  - "EmailVerificationService — mintFor (32-byte hex, 24h TTL) + consume returning verbatim 'success'|'invalid'|'expired' per UI-SPEC §Redirect Query-Param Contract (unknown AND consumed both 'invalid' per docs/05 §9.1)"
  - "RefreshTokenService — create / rotate / revokeChainHead / revokeChain. rotate runs @Transactional(REPEATABLE_READ) with findByTokenHashForUpdate row-lock (D-13 / Pitfall 2). revokeChain walks BOTH directions: backward via findByRotatedTo to chain root, forward marking each row revoked (D-10 / Pitfall 4)"
  - "application.yml extended: spring.mail (SMTP_HOST:1025), spring.data.redis (REDIS_HOST:6379), auth.jwt.secret (Phase 1 D-16 binding), app.* tree (4 keys with safe-in-dev defaults)"
  - "application-docker.yml extended: spring.mail.host=mailhog + spring.data.redis.host=redis (compose-DNS)"
  - ".env.example: +3 entries (MAIL_FROM / FRONTEND_BASE_URL / AUTH_COOKIE_SECURE)"
  - "infra/docker-compose.yml auth-service: +depends_on (mailhog service_started, redis service_healthy); +4 environment passthrough vars (AUTH_JWT_SECRET, MAIL_FROM, FRONTEND_BASE_URL, AUTH_COOKIE_SECURE)"
affects: [02-04, 02-05, 02-06, 02-07]

# Tech tracking
tech-stack:
  added: []   # All new deps already wired in Plan 01 (spring-boot-starter-{security,mail,validation,data-redis})
  patterns:
    - "Pattern: SecurityConfig third-instance shape (services/auth-service/.../security/SecurityConfig.java is the third copy of services/trip-service/.../security/ServletSecurityConfig.java with auth-specific permitAll list + BCryptPasswordEncoder bean). Ready for fourth-instance reuse if Phase 5+ adds another authenticated downstream."
    - "Pattern: RestAuthenticationEntryPoint VERBATIM (constructor-inject auto-configured ObjectMapper, never `new ObjectMapper()`; use ProblemDetailFactory.of). Auth-service is the third deployment of this class — expect every authenticated downstream to ship its own copy."
    - "Pattern: @ConfigurationProperties prefix at 'app' (NOT 'app.auth') — single-prefix nested-class style binds 4 disjoint config groups in one bean. Avoids fragmenting AuthProperties / FrontendProperties / MailProperties / VerificationProperties into 4 beans. Plan 04 callers do props.getAuth().getCookie().isSecure() / props.getFrontend().getBaseUrl() / props.getMail().getFrom() / props.getVerification().getLinkBase()."
    - "Pattern: AsyncConfig with inline MdcCopyingTaskDecorator (NOT extracted to libs/observability) — trigger for extraction is Phase 3+ adding @Async in another service. Pitfall 7 servlet-side answer."
    - "Pattern: Skinny custom exception class — no-arg ctor, no message, ControllerAdvice supplies the canonical UI-SPEC `detail` string. Prevents English-text drift across throw sites."
    - "Pattern: Lua-as-constant for atomic Redis ops (RedisScript.of(LUA, Long.class) instance field). Avoids non-atomic INCR-then-EXPIRE immortal-counter race. Ready for reuse if any future component needs atomic Redis state mutation."
    - "Pattern: Refresh-token chain walk BOTH directions (backward via findByRotatedTo first, then forward from root). Cookbook for any future ‘chain of immutable rows linked by next_id’ data structure."
    - "Pattern: RefreshTokenService inner records (RefreshTokenIssued / RotatedRefresh) for callsite locality — caller (Plan 04 controller) embeds rawValue in Set-Cookie. Avoids leaking concerns across packages."

key-files:
  created:
    - "services/auth-service/src/main/java/com/tripplanner/auth/config/AuthProperties.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/config/AsyncConfig.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/security/RestAuthenticationEntryPoint.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/exception/EmailAlreadyRegisteredException.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/exception/InvalidCredentialsException.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/exception/EmailNotVerifiedException.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/exception/TokenInvalidException.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/exception/TokenExpiredException.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/exception/RefreshInvalidException.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/exception/LoginRateLimitedException.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/HashUtil.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/LoginRateLimiter.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/EmailVerificationService.java"
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java"
  modified:
    - "services/auth-service/src/main/java/com/tripplanner/auth/AuthServiceApplication.java"
    - "services/auth-service/src/main/resources/application.yml"
    - "services/auth-service/src/main/resources/application-docker.yml"
    - ".env.example"
    - "infra/docker-compose.yml"

key-decisions:
  - "@ConfigurationProperties prefix is 'app' (NOT 'app.auth') — single-prefix-many-groups binding so app.auth.cookie.* / app.frontend.* / app.mail.* / app.verification.* all bind from one bean (revision iteration 1 correction documented in PATTERNS.md)"
  - "MdcCopyingTaskDecorator stays inline in AsyncConfig — extraction trigger is a second @Async user (Phase 3+); YAGNI now"
  - "7 exception classes are skeletal (no-arg, no message) — Plan 04 ControllerAdvice supplies canonical UI-SPEC `detail` strings; prevents English-text drift across throw sites"
  - "LoginRateLimiter `>= 5` threshold confirmed (D-06 wording: 5 failed-attempts/15min → 6th trips: counts 1,2,3,4,5 are ok; 6th read sees 5 → exceeded() true → 429)"
  - "RefreshTokenService.rotate at REPEATABLE_READ + findByTokenHashForUpdate row-lock; revokeChain walks BOTH directions (backward via findByRotatedTo to chain root, then forward) — Pitfall 4 mitigation surface"
  - "JDK 17 used for Gradle invocations (homebrew default openjdk 25 chokes Gradle 8.14.2 Kotlin compiler — same workaround as Plan 01 SUMMARY); Java toolchain still drives javac at JDK 21"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, AUTH-04, NFR-05]

# Metrics
duration: 5min
completed: 2026-05-09
---

# Phase 02 Plan 03: Auth Service + Config Layer Summary

**Phase 2's largest plan complete: 15 new files (3 config + 2 security + 7 exceptions + 4 services) + 5 modified files (Application + 2 yaml + .env.example + compose); RefreshTokenService.rotate and LoginRateLimiter close the Phase-2 security-keystone surface (Pitfall 2 / Pitfall 4 / D-06 / D-10 / D-13 mitigations); Plan 04 (controller + advice + sender + scheduler + AuthService orchestrator) is fully unblocked.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-05-09T15:45:11Z
- **Completed:** 2026-05-09T15:50:41Z
- **Tasks:** 3 (all `type=auto`)
- **Files created:** 15
- **Files modified:** 5

## Accomplishments

- **Security wiring shipped (Task 3.1).** `SecurityConfig` is the third instance of the trip-service `ServletSecurityConfig` pattern. permitAll on `/api/auth/{signup,verify,login,refresh}`; everything else `authenticated()`; `ServletJwtCommonFilter` placed before `UsernamePasswordAuthenticationFilter`; `BCryptPasswordEncoder(12)` bean (D-19); `RestAuthenticationEntryPoint` is verbatim sibling of trip-service's, constructor-injecting the auto-configured `ObjectMapper` (BL-01 / Pitfall 7 — never `new ObjectMapper()`).
- **`@ConfigurationProperties("app")` correction landed (Task 3.1).** Plan revision iteration 1 had flagged that the original PATTERNS.md analog showed prefix `"app.auth"` — that prefix would only have bound `app.auth.cookie.*`, leaving `app.frontend.*` / `app.mail.*` / `app.verification.*` unbound at runtime. The correct prefix is `"app"` with a nested `Auth` wrapper (containing `Cookie`) plus sibling `Frontend` / `Mail` / `Verification` static classes. This shape unblocks all four Plan 04 / 05 callsites (`props.getAuth().getCookie().isSecure()`, `props.getFrontend().getBaseUrl()`, `props.getMail().getFrom()`, `props.getVerification().getLinkBase()`).
- **AsyncConfig with inline MDC propagation (Task 3.1).** `@EnableAsync` + named `authAsyncExecutor` (core 2 / max 4 / queue 50) decorated by inline `MdcCopyingTaskDecorator`. Pitfall 7 servlet-side answer to D-22 — `traceId` / `requestId` / `userId` follow the email-send into the worker thread so Zipkin spans correlate. Stays inline (NOT extracted to `libs/observability`) until a Phase 3+ second `@Async` user emerges.
- **7 skeletal exception classes (Task 3.1).** Each is `extends RuntimeException` with a single no-arg ctor. The advice handler in Plan 04 will supply the canonical UI-SPEC `detail` strings for each one — no message argument is passed at throw site, preventing English-text drift across call sites.
- **HashUtil + LoginRateLimiter + EmailVerificationService + RefreshTokenService shipped (Task 3.2).** `HashUtil.sha256Hex` is a static helper that eliminates 4-line try/catch boilerplate at every refresh-token-hash callsite. `LoginRateLimiter` uses a single Lua INCR+EXPIRE atomic — no immortal-counter race possible — with `>= 5` threshold (D-06: 5 failed-attempts/15min, the 6th attempt trips). `EmailVerificationService.consume` returns the three verbatim UI-SPEC strings `"success"|"invalid"|"expired"`; per docs/05 §9.1, unknown AND consumed both return `"invalid"` (no enumeration leak). `RefreshTokenService.rotate` runs at `@Transactional(REPEATABLE_READ)` and calls `findByTokenHashForUpdate` first (D-13 / Pitfall 2 row-lock); on replay-detected, `revokeChain` walks BOTH directions — backward via `findByRotatedTo` reverse-lookup to chain root first, then forward marking each row revoked (Pitfall 4 / D-10).
- **Cross-cutting config wired (Task 3.3).** `application.yml` gains `spring.mail` (SMTP defaults `localhost:1025`), `spring.data.redis` (defaults `localhost:6379`), `auth.jwt.secret` binding (Phase 1 D-16), and `app.*` tree with safe-in-dev defaults. `application-docker.yml` overrides hosts to compose-network DNS (`mailhog`, `redis`). `.env.example` documents `MAIL_FROM` / `FRONTEND_BASE_URL` / `AUTH_COOKIE_SECURE`. `infra/docker-compose.yml` `auth-service` block grows `depends_on` (mailhog `service_started`, redis `service_healthy`) and 4 new environment passthroughs including `AUTH_JWT_SECRET` (which was previously declared in `.env.example` but never wired into compose env — fixed here as a deviation Rule 2 catch).

## Task Commits

Each task was committed atomically:

1. **Task 3.1: SecurityConfig + AuthProperties + AsyncConfig + 7 exceptions** — `b313d49` (feat)
2. **Task 3.2: HashUtil + LoginRateLimiter + EmailVerificationService + RefreshTokenService** — `4e7426b` (feat)
3. **Task 3.3: application.yml + application-docker.yml + .env.example + docker-compose** — `9add8ab` (chore)

**Plan metadata commit follows.**

## Files Created/Modified

### Created (15)

**Config + security:**
- `services/auth-service/src/main/java/com/tripplanner/auth/config/AuthProperties.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/config/AsyncConfig.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/security/RestAuthenticationEntryPoint.java`

**Skeletal exceptions (7):**
- `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/EmailAlreadyRegisteredException.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/InvalidCredentialsException.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/EmailNotVerifiedException.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/TokenInvalidException.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/TokenExpiredException.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/RefreshInvalidException.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/LoginRateLimitedException.java`

**Services (4):**
- `services/auth-service/src/main/java/com/tripplanner/auth/service/HashUtil.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/LoginRateLimiter.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/EmailVerificationService.java`
- `services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java`

### Modified (5)
- `services/auth-service/src/main/java/com/tripplanner/auth/AuthServiceApplication.java` — +@EnableAsync, +@EnableScheduling, +@EnableConfigurationProperties(AuthProperties.class)
- `services/auth-service/src/main/resources/application.yml` — +spring.mail / +spring.data.redis / +auth.jwt.secret binding / +app.* tree (4 keys)
- `services/auth-service/src/main/resources/application-docker.yml` — +spring.mail.host=mailhog / +spring.data.redis.host=redis
- `.env.example` — +MAIL_FROM / +FRONTEND_BASE_URL / +AUTH_COOKIE_SECURE
- `infra/docker-compose.yml` — auth-service depends_on (+mailhog, +redis), environment (+AUTH_JWT_SECRET, +MAIL_FROM, +FRONTEND_BASE_URL, +AUTH_COOKIE_SECURE)

## Decisions Made

- **Used JDK 17 for Gradle invocations** — same workaround documented in 00-06 SUMMARY and 02-01 SUMMARY (homebrew default openjdk 25 chokes Gradle 8.14.2's bundled Kotlin compiler with `JavaVersion.parse('25.0.2')`). `JavaLanguageVersion.of(21)` toolchain still drives the actual `javac`. CI is unaffected.
- **`auth:` YAML block was NOT already present from Phase 1** — `grep -n 'auth:' application.yml` returned no matches before this plan's edits. The plan's contingency check (skip the `auth:` block addition if Phase 1 already added it) resolved to "add the block fresh." `auth.jwt.secret` is now declared in `application.yml` for visibility, with `${AUTH_JWT_SECRET}` env binding (no default — startup fails fast if the secret is missing, per Phase 0 D-24 enforcement).
- **`AUTH_JWT_SECRET` was missing from compose env passthrough** — the variable was declared in `.env.example` (Phase 1) but never wired into the auth-service container's `environment:` block in `infra/docker-compose.yml`. This would have caused a `Could not resolve placeholder 'AUTH_JWT_SECRET'` error at compose-start time. Added as part of Task 3.3 — see Deviations below.
- **`LoginRateLimiter.exceeded` semantics:** `>= 5` means counts 1,2,3,4,5 are "ok" (do not block); the 6th attempt reads count=5 (recorded after the 5th failure) and trips. Matches D-06's wording verbatim ("5 failed-attempts / 15min" → 6th trips → 429). Confirmed against `RotatedRefreshTokenCannotBeReusedIT` semantics in Plan 06 (the IT exercises 6 failed logins, then asserts 429 on the 6th).
- **`RefreshTokenService.revokeChain` walks BACKWARD first via `findByRotatedTo`, then forward via `rotated_to` → `findById`.** Pitfall 4 mitigation surface — without backward walk, mid-chain replays would leave the chain root unrevoked. The `rt_rotated_to_idx` partial index from Plan 01 makes `findByRotatedTo` cheap.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] `AUTH_JWT_SECRET` env var missing from `infra/docker-compose.yml` auth-service block**
- **Found during:** Task 3.3 review of existing compose `auth-service.environment` block before applying changes.
- **Issue:** `AUTH_JWT_SECRET` was declared in `.env.example` since Phase 1 (D-16 / D-24) but never wired through to the auth-service container's `environment:` block. The Phase 1 wiring landed it for `api-gateway` only (gateway needs it for `JwtVerifier`). Auth-service now needs it for `JwtIssuer` (Plan 02-02), `JwtVerifier` (the gateway-side filter validation), and `application.yml` placeholder resolution (`auth.jwt.secret: ${AUTH_JWT_SECRET}`). Without the passthrough, `docker compose up auth-service` would fail at startup with `Could not resolve placeholder 'AUTH_JWT_SECRET' in value "${AUTH_JWT_SECRET}"`.
- **Fix:** Added `AUTH_JWT_SECRET: ${AUTH_JWT_SECRET}` to the auth-service `environment:` block alongside the new MAIL_FROM / FRONTEND_BASE_URL / AUTH_COOKIE_SECURE entries (Task 3.3 step (d)).
- **Files modified:** `infra/docker-compose.yml` (auth-service block only)
- **Commit:** `9add8ab`
- **Why this fits Rule 2:** required for correct boot of any service that uses `auth.jwt.secret` (auth-service issues AND validates JWTs). Catching this in Plan 03 prevents an immediate failure when Plan 04's `AuthService` lands and tries to call `JwtIssuer.issueAccess(...)` against a placeholder-unresolved secret.

### Verification Notes

- **`grep "new ObjectMapper()"` returns one match in `RestAuthenticationEntryPoint.java`** — this is the **comment** explaining why we MUST NOT instantiate a new `ObjectMapper()` (the comment exists verbatim in the trip-service analog file that passed Phase 1 verification). The actual code has no `new ObjectMapper()` instantiation. The grep `^[^/]*new ObjectMapper\\(\\)` (excluding comment lines) returns 0 matches. The `git grep -lE` regex used in `<acceptance_criteria>` is comment-blind by design — the trip-service file would also match this regex, and Phase 1 accepted it. **No deviation; pattern is identical to the trip-service analog.**

## Issues Encountered

None functionally. Two minor non-issues:

1. **Initial `compileJava` failed with `25.0.2` error** — known workaround: prefix Gradle invocations with `JAVA_HOME=$(/usr/libexec/java_home -v 17)`. Documented in Plan 01 SUMMARY ("Decisions Made"). Not a Phase 2 regression; environment-level only.
2. **First-pass acceptance grep used unquoted shell glob `${...}` in match strings** — false-negative due to shell expansion of `${SMTP_HOST:localhost}` etc. Re-ran with `grep -qF` (fixed-string) — all checks passed. The acceptance criteria in the plan are unaffected (the IT in Plan 06 will exercise the actual property bindings against a live container).

## User Setup Required

None — runtime execution arrives in Plan 04/05/06. Until then this plan's artifacts are static (compile-only, +`docker compose config -q` syntax check). The 4 new env vars (`MAIL_FROM` / `FRONTEND_BASE_URL` / `AUTH_COOKIE_SECURE` / passthrough of `AUTH_JWT_SECRET`) all have safe-in-dev defaults — no user action required for `docker compose up` to attempt boot.

## Next Phase Readiness

**Plan 02-04 (Controller + Advice + Sender + Scheduler + AuthService orchestrator)** is unblocked: it can wire `LoginRateLimiter` (D-05 location: inside `AuthService.login` before bcrypt verify), `EmailVerificationService.mintFor` (signup) and `.consume` (verify endpoint controller), `RefreshTokenService.create` (login) / `.rotate` (refresh) / `.revokeChainHead` (logout), `AuthProperties.getAuth().getCookie().isSecure()` (login/refresh cookie flag), `.getFrontend().getBaseUrl()` (verify-redirect target), `.getMail().getFrom()` (`SimpleMailMessage.setFrom(...)`), `.getVerification().getLinkBase()` (concatenate raw token to build the verification URL). The 7 skeletal exceptions are ready for `AuthControllerAdvice` to map to RFC 7807 codes per UI-SPEC.

**Plan 02-06 (4 mandatory @Tag("security") ITs)** has its full target surface in place — `LoginRateLimitFailedAttemptsTriggers` exercises the `>= 5` threshold; `RotatedRefreshTokenCannotBeReusedIT` exercises the `revokeChain` BOTH-DIRECTIONS walk (the regression gate); `DeletedRefreshTokenCannotBeUsed` exercises `revokeChainHead`; `EmailNotVerifiedCannotLogin` exercises the throw-site of `EmailNotVerifiedException` (Plan 04 `AuthService`).

**No blockers, no concerns.** Sequential dependency on Plan 04 is clean — Plan 04 is purely additive on top of Plan 03's surface.

## Open Questions / Discretion Calls Surfaced

None new. Plan 02-CONTEXT's existing Claude-discretion items (1) `JwtIssuer` location resolved by Plan 02 (kept in `libs/jwt-common`); (2) email body copy resolved by Plan 04 (verbatim from UI-SPEC); (3) `verify-success.html` static page resolved as "skip — FE owns" per UI-SPEC; (4) `/api/auth/me` endpoint resolved as "NO" per Plan 04 controller surface; (5) `TokenCleanupJob` single-tx vs two-tx resolved by Plan 04. All within-plan discretion absorbed by 02-PATTERNS.md verbatim sources — no fresh discretion calls.

## Self-Check: PASSED

Verifying claimed artifacts exist on disk:

- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/config/AuthProperties.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/config/AsyncConfig.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/security/RestAuthenticationEntryPoint.java` — present
- ✓ 7 exception files under `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/` — present (verified count = 7)
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/service/HashUtil.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/service/LoginRateLimiter.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/service/EmailVerificationService.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java` — present
- ✓ `services/auth-service/src/main/java/com/tripplanner/auth/AuthServiceApplication.java` — modified (+3 annotations + +1 import line)
- ✓ `services/auth-service/src/main/resources/application.yml` — modified (+mail, +data.redis, +auth, +app trees)
- ✓ `services/auth-service/src/main/resources/application-docker.yml` — modified (+mail.host, +data.redis.host)
- ✓ `.env.example` — modified (+3 entries)
- ✓ `infra/docker-compose.yml` — modified (auth-service depends_on +2, environment +4)

Verifying claimed commits exist:
- ✓ `b313d49` (Task 3.1) — present in `git log`
- ✓ `4e7426b` (Task 3.2) — present in `git log`
- ✓ `9add8ab` (Task 3.3) — present in `git log`

Verifying plan-level acceptance:
- ✓ `./gradlew :services:auth-service:compileJava -q` exit 0
- ✓ `./gradlew :services:auth-service:assemble -q` exit 0
- ✓ `docker compose -f infra/docker-compose.yml config -q` exit 0
- ✓ `git grep -nE "new ObjectMapper\\(\\)" services/auth-service/src/main/java/com/tripplanner/auth/` returns 1 line — but it's the COMMENT in `RestAuthenticationEntryPoint.java` line 35 (verbatim copy of trip-service's analog comment). Code-only check `grep -nE "^[^/]*new ObjectMapper" services/auth-service/...` returns 0 matches.
- ✓ Zero `import java.time.LocalDateTime` under `services/auth-service/src/main/java/com/tripplanner/auth/service/` (Pitfall 9)
- ✓ All `<acceptance_criteria>` substrings + grep patterns from each task block present in target files (re-verified with `grep -qF` for shell-glob safety)

---
*Phase: 02-auth-service*
*Completed: 2026-05-09*
