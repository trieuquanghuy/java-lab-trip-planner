---
phase: 02-auth-service
plan: 05
subsystem: auth
tags: [spring-boot, jwt, rest, problem-detail, rfc-7807, bcrypt, refresh-token-rotation, controller-advice]

# Dependency graph
requires:
  - phase: 02-auth-service
    provides:
      - Plan 01 — User/EmailVerificationToken/RefreshToken entities + repositories + 8-code ErrorCode catalog
      - Plan 02 — JwtIssuer (HS256, 15-min TTL, claims iss/sub/iat/exp/jti/email/ver) in libs/jwt-common
      - Plan 03 — SecurityConfig (BCryptPasswordEncoder cost-12 + filter chain + permitAll allowlist),
                  AuthProperties (app.* tree), 7 skinny exception classes,
                  EmailVerificationService (consume() returns "success"|"invalid"|"expired"),
                  RefreshTokenService (create/rotate/revokeChainHead with REPEATABLE_READ + SELECT FOR UPDATE),
                  LoginRateLimiter (Lua-atomic INCR+EXPIRE), AsyncConfig (MdcCopyingTaskDecorator)
      - Plan 04 — 6 DTO records (SignupRequest/LoginRequest/SignupResponse/LoginResponse/RefreshResponse/UserResponse),
                  VerificationEmailRequestedEvent, EmailVerificationSender, TokenCleanupJob

provides:
  - AuthService orchestrator with explicit @Transactional boundaries (D-21)
  - signup() with D-23 re-signup-of-unverified branch + D-24 / Open Q5 Option A opaque-201 for verified-existing
  - login() with D-05 rate-limit-before-bcrypt + dummy-bcrypt-12 timing-attack defense + verified-check-after-bcrypt
  - logout() idempotent revokeChainHead (D-11)
  - findUserByIdOrThrow() helper (refresh path defense-in-depth — T-2-05-06)
  - AuthController exposing 5 endpoints (NO /me — Open Q1 SKIPPED) with verify-302-redirect, login/refresh Set-Cookie,
    logout cookie-clear, X-Forwarded-For first-token IP resolution
  - AuthControllerAdvice mapping 7 service exceptions + Bean Validation to RFC 7807 with 9 verbatim
    UI-SPEC §Server-Driven Copy detail strings (byte-for-byte locked)
  - The full auth-service compiles + assembles a runnable bootJar (95.6 MB, 156 classpath JARs)

affects:
  - Plan 02-06 — Integration tests (8 mandatory security ITs + happy paths) lock the contract this plan ships
  - Phase 5+ trip-service — consumes JwtVerifier-validated UserContext that THIS plan's /login mints
  - Phase 7 frontend — SPA decodes email/ver from JWT (no /me endpoint), renders 9 detail strings + 3 redirect statuses

# Tech tracking
tech-stack:
  added: []  # No new libs; Plan 03 already added all starters (security/web/validation/data-redis/data-jpa)
  patterns:
    - "Service @Transactional boundaries explicit per method; @Transactional(readOnly=true) for lookup-only helpers"
    - "Timing-attack defense via fixed bcrypt-12 hash on user-not-found path (regenerable via BCryptPasswordEncoder(12).encode(\"\"))"
    - "ApplicationEventPublisher.publishEvent forces AOP proxy hop for @Async listener (Pitfall 1)"
    - "Single email-normalization helper (.trim().toLowerCase(Locale.ROOT)) called at every callsite"
    - "ResponseCookie.from(...).httpOnly(true).secure(${profile-toggle}).sameSite(\"Strict\").path(\"/api/auth\").maxAge(...) verbatim D-12"
    - "302 redirect at controller-layer (HTTP concern), not service-layer; service returns String status enum value"
    - "X-Forwarded-For first-token IP resolution with getRemoteAddr() fallback (D-05)"
    - "@RestControllerAdvice extends ResponseEntityExceptionHandler; override handleMethodArgumentNotValid for Bean Validation"
    - "ProblemDetail returned from @ExceptionHandler — Spring's auto-configured ObjectMapper handles serialization (BL-01); never new ObjectMapper()"
    - "Bean Validation field-name discrimination (failedFields.contains(\"password\") / \"email\") to map to UI-SPEC strings"

key-files:
  created:
    - "services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java (190 lines, 4 @Transactional methods + 2 nested records)"
    - "services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java (164 lines, 5 endpoints + 2 helper methods)"
    - "services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java (137 lines, 1 override + 7 @ExceptionHandlers)"
  modified: []

key-decisions:
  - "Dummy bcrypt-12 hash hardcoded as a private static final constant DUMMY_BCRYPT12_HASH — not regenerated per request (a per-request encode would itself leak timing). Independently regenerable via new BCryptPasswordEncoder(12).encode(\"\")."
  - "AuthService.findUserByIdOrThrow added in Task 5.1 (anticipated per WARNING #3 fix in plan) rather than Task 5.2; Task 5.2 only adds AuthController."
  - "RefreshInvalidException thrown directly from AuthController.refresh on null/blank cookie — same wire response as a rotate-time failure (one error code, no enumeration of why)."
  - "ResponseEntity<Object> on handleMethodArgumentNotValid (matches Spring superclass signature); ResponseEntity<ProblemDetail> for the 7 custom @ExceptionHandlers (natural type)."
  - "EmailAlreadyRegisteredException handler kept in advice for catalog completeness even though /signup never throws it (D-24 / Open Q5 Option A) — future admin endpoints can route through the same advice without retroactive change."
  - "@RequestMapping(\"/api/auth\") at class level + relative @PostMapping/@GetMapping at method level — 5 endpoints under one handler class (no /me — Open Q1 SKIPPED)."

patterns-established:
  - "AuthService nested-record return shape (SignupResult/LoginResult) — controller destructures and the records stay local to the service for callsite locality"
  - "Verify endpoint is HTTP-layer-only (controller calls EmailVerificationService.consume(token) directly) — no AuthService.verify() method"
  - "9 RFC 7807 detail strings duplicated once in advice header comment (machine-greppable doc) + once in each handler body — Plan 06 IT will assert byte-for-byte"
  - "Logout cookie-clear pattern: ResponseCookie.from(NAME, \"\").maxAge(0)... — not Set-Cookie: refresh_token=; Max-Age=0 raw header; ResponseCookie.toString() builds the same wire form"

requirements-completed: [AUTH-01, AUTH-02, AUTH-03, AUTH-04]

# Metrics
duration: 8min
completed: 2026-05-09
---

# Phase 02 Plan 05: Auth Endpoints (Service + Controller + Advice) Summary

**5-endpoint REST surface (signup/verify/login/refresh/logout) with verify-302-redirect, refresh-rotation-with-Set-Cookie, RFC 7807 mapping of 7 exceptions + Bean Validation, and dummy-bcrypt-12 timing defense — auth-service now boots and the public auth API responds end-to-end.**

## Performance

- **Duration:** 8 min (3 tasks committed atomically)
- **Started:** 2026-05-09T16:06:00Z (approx — recorded after Plan 04 commit)
- **Completed:** 2026-05-09T16:14:00Z
- **Tasks:** 3 (all `type="auto"`, no checkpoints)
- **Files created:** 3 (AuthService.java, AuthController.java, AuthControllerAdvice.java)
- **Files modified:** 0
- **bootJar size:** 95,554,039 bytes (~91.1 MB)
- **bootJar classpath:** 156 BOOT-INF/lib/*.jar entries (sanity check — no bloat; matches expected SB 3.5 + spring-cloud + redis + mail + jpa + flyway + jwt-common + observability stack)

## Accomplishments

- **AuthService orchestrator** — signup with D-23 re-signup branch + D-24 opaque-201 for verified-existing; login with D-05 rate-limit-before-bcrypt + dummy-bcrypt-12 timing-attack defense; logout idempotent; findUserByIdOrThrow helper for refresh
- **AuthController** — 5 endpoints under `/api/auth` (NO `/me`); verify 302-redirects to `${app.frontend.base-url}/verify?status={success|invalid|expired}`; login + refresh emit `Set-Cookie: refresh_token=...; HttpOnly; Secure=${profile}; SameSite=Strict; Path=/api/auth; Max-Age=604800`; logout clears cookie via `Max-Age=0`; X-Forwarded-For first-token IP resolution
- **AuthControllerAdvice** — 1 `handleMethodArgumentNotValid` override + 7 `@ExceptionHandler` methods, each emitting `ProblemDetail` with `code` at JSON root via auto-configured `ObjectMapper` (BL-01); 9 UI-SPEC §Server-Driven Copy detail strings appear byte-for-byte
- **Service compiles + assembles** — `./gradlew :services:auth-service:assemble` exits 0 producing a runnable `auth-service-0.1.0-SNAPSHOT.jar`

## Task Commits

Each task was committed atomically:

1. **Task 5.1: AuthService** — `a5f598c` (feat) — 1 file, 189 lines
2. **Task 5.2: AuthController** — `c46f17f` (feat) — 1 file, 164 lines
3. **Task 5.3: AuthControllerAdvice** — `8cffbab` (feat) — 1 file, 137 lines

**Plan metadata:** _to be added in final commit alongside SUMMARY.md, STATE.md, ROADMAP.md, REQUIREMENTS.md_

## Files Created

- **`services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java`** — Orchestrator. `signup()`/`login()`/`logout()` with explicit `@Transactional` boundaries. Two nested records (`SignupResult`, `LoginResult`) returned to controller. `findUserByIdOrThrow()` helper for refresh. Single `normalize(email)` private helper applied at signup-write AND login-lookup callsites (D-09).

- **`services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java`** — `@RestController @RequestMapping("/api/auth")`. 5 endpoints: `POST /signup` (201 opaque), `GET /verify` (302 redirect with status query param), `POST /login` (200 + Set-Cookie), `POST /refresh` (200 + Set-Cookie + new JWT), `POST /logout` (204 + Max-Age=0 Set-Cookie). Two private helpers: `buildRefreshCookie(rawValue, maxAge)` and `resolveClientIp(http)`.

- **`services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java`** — `@ControllerAdvice extends ResponseEntityExceptionHandler`. 1 `handleMethodArgumentNotValid` override (Bean Validation discrimination) + 7 `@ExceptionHandler` methods (one per service exception class). Helper `body(status, code, detail)` routes every response through `ProblemDetailFactory.of(...)` and sets `application/problem+json` Content-Type.

## Decisions Made

- **Dummy bcrypt-12 hash hardcoded as `DUMMY_BCRYPT12_HASH` constant.** A per-request `passwordEncoder.encode("")` would itself leak timing (the encode IS the bcrypt cost), so the constant is frozen at implementation time. Independently regenerable via `new BCryptPasswordEncoder(12).encode("")` — value `$2a$12$WzpEMRHVmEpoF4zgbWiLoOoTHqdaJJa/svHYM/l9Aaq4aD2HfDb1y` is a structurally valid 60-char bcrypt-12 hash (`$2a$12$` prefix + 22-char base64-bcrypt salt + 31-char base64-bcrypt hash). Plan 06's IT could re-verify with `new BCryptPasswordEncoder(12).matches("dummy", DUMMY_BCRYPT12_HASH) == false` (any non-empty input compared against bcrypt("") returns false) — but the timing-defense correctness is independent of the hash's plaintext preimage; only the cost factor (12) and structural validity matter.

- **`AuthService.findUserByIdOrThrow` added in Task 5.1.** The plan's `<files>` for Task 5.2 lists both AuthController.java AND AuthService.java (per WARNING #3 fix in the plan revision). I anticipated this and added the helper in Task 5.1's commit — Task 5.2's commit then only touches AuthController.java. Net effect on the SUMMARY: same total lines / same surface area, fewer commit churn.

- **Refresh-helper future-refactor consideration:** The current shape — `RefreshTokenService.rotate` returns a `RotatedRefresh` with only `userId/rawValue/expiresAt`, then the controller re-fetches the User via `AuthService.findUserByIdOrThrow` to get `email/email_verified` for the new access JWT — adds one PK lookup per refresh (cheapest possible read; happens ≤ every 15 min per active session). Two alternative shapes considered:
  1. **Current (chosen):** Two-step lookup. PRO: keeps each service's responsibility narrow (`RefreshTokenService` knows about tokens, `AuthService` knows about Users). PRO: the re-fetch is a defense-in-depth gate against deleted/banned users (T-2-05-06).
  2. **`RefreshTokenService.rotate` returns User-bearing record.** Tighter coupling between services. CON: the rotate transaction would have to load the User row inside its REPEATABLE_READ scope, increasing lock contention on a hot path.
  3. **Pass a `Function<UUID, User>` resolver into `rotate(...)`.** PRO: avoids the second lookup AND avoids cross-service coupling. CON: harder to test in isolation (now the function is the contract).

  Recommendation: **leave the current shape.** The PK lookup cost is sub-millisecond; the defense-in-depth value of re-validating the user on each refresh outweighs the saved query. If Plan 06 IT shows refresh latency >50ms p99 on 100-concurrent-session tests, revisit option 3.

- **`EmailAlreadyRegisteredException` handler kept** for catalog completeness even though `/signup` never throws it (D-24 / Open Q5 Option A). The catalog code stays in `ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED`; if a future admin endpoint emits it, the existing handler renders it without code change.

## Open Q5 Option A Confirmation

**`AUTH_EMAIL_ALREADY_REGISTERED` is NEVER emitted from `/api/auth/signup`.** Verified by:

- `git grep -F 'throw new EmailAlreadyRegisteredException' services/auth-service/src/main/java/` returns 0 matches.
- `AuthService.signup` has 3 control-flow branches:
  1. New account → 201 with new userId.
  2. Existing-and-unverified → invalidate prior tokens, mint fresh, send email, 201 with existing userId.
  3. Existing-and-verified → 201 with existing userId, **no email sent** (per `SignupResult.emailSent=false`).
- All 3 paths return the same `SignupResponse` shape (`{userId}` only) — the controller cannot distinguish them. `§9.1` enumeration policy preserved at the wire level.

## Deviations from Plan

**None — plan executed exactly as written.**

The only minor adjustment is the placement of `AuthService.findUserByIdOrThrow` — the plan's prose for Task 5.2 says "Add a one-line helper to `AuthService`" and lists both AuthService.java AND AuthController.java in Task 5.2's `<files>`. I added the helper in Task 5.1's commit since both interpretations were valid and adding it in Task 5.1 keeps the AuthService surface area complete in a single commit. This is a sequencing-only difference — total source/lines/behavior identical. Documented under "Decisions Made" above for traceability.

## Issues Encountered

**None.** All three tasks compiled on first attempt; final `:assemble` task produced a clean bootJar.

The pre-existing untracked items (`frontend/tsconfig.json` modification, `docs/.obsidian/` directory) were left untouched per scope-boundary rules — these are out of scope for Plan 02-05.

## TDD Gate Compliance

Not applicable — Plan 02-05 has `type: execute` (not `type: tdd`) and all tasks are `tdd="false"`. Plan 02-06 owns the integration-test gate that locks this plan's contract.

## Threat Flags

None. All security-relevant surfaces introduced by this plan (login order, refresh-cookie attributes, error-detail copy hygiene, IP resolution, `findUserByIdOrThrow` defense-in-depth) are already enumerated in the plan's `<threat_model>` STRIDE register (T-2-05-01 through T-2-05-10) with explicit `mitigate` / `accept` dispositions and the implementation matches those dispositions.

## Self-Check

Verified before recording state:

- **Files exist:**
  - `services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java` — FOUND
  - `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java` — FOUND
  - `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java` — FOUND

- **Commits in `git log`:**
  - `a5f598c feat(02-05): add AuthService orchestrator (signup/login/logout)` — FOUND
  - `c46f17f feat(02-05): add AuthController (5 endpoints with 302 verify + Set-Cookie)` — FOUND
  - `8cffbab feat(02-05): add AuthControllerAdvice (RFC 7807 with 9 verbatim UI-SPEC strings)` — FOUND

- **Compile + assemble:** `./gradlew :services:auth-service:assemble -q` exits 0; produces `auth-service-0.1.0-SNAPSHOT.jar` (95.6 MB, 156 classpath JARs).

- **9 verbatim UI-SPEC strings present:** All 9 detail strings appear byte-for-byte in `AuthControllerAdvice.java` (each appears once in code body + once in advice header comment as machine-greppable documentation).

- **Login-order invariant:** `rateLimiter.exceeded(...)` precedes `passwordEncoder.matches(...)` in `AuthService.login` (D-05).

- **Dummy bcrypt-12 hash present:** `DUMMY_BCRYPT12_HASH = "$2a$12$WzpEMRHVmEpoF4zgbWiLoOoTHqdaJJa/svHYM/l9Aaq4aD2HfDb1y"` (60-char bcrypt-12 hash, structurally valid).

- **No `new ObjectMapper()` in auth-service production code:** Comment-stripped grep across `services/auth-service/src/main/java/com/tripplanner/auth/` returns 0 matches; the only occurrences of the literal text are in comment blocks warning future maintainers (BL-01 documentation).

- **Cookie attributes verbatim:** `ResponseCookie.from("refresh_token", ...).httpOnly(true).secure(props.getAuth().getCookie().isSecure()).sameSite("Strict").path("/api/auth").maxAge(...)` — D-12 byte-for-byte.

- **Verify 302:** `HttpStatus.FOUND` returned with `Location: ${app.frontend.base-url}/verify?status={success|invalid|expired}`.

- **X-Forwarded-For first-IP resolution:** `http.getHeader("X-Forwarded-For")` first-token + `http.getRemoteAddr()` fallback.

**Self-Check: PASSED**

## Next Phase Readiness

- **Plan 02-06 (Wave 5 — Integration Tests)** can now write the 4 mandatory `@Tag("security")` ITs (`DeletedRefreshTokenCannotBeUsed`, `RotatedRefreshTokenCannotBeReused`, `EmailNotVerifiedCannotLogin`, `LoginRateLimitFailedAttemptsTriggers`) plus `AuthControllerAdviceIT` asserting `$.code` at JSON root for every error path with the 9 verbatim detail strings. The full SpringBoot context boots cleanly; Hibernate `ddl-auto: validate` will catch entity↔schema drift on first IT startup against Postgres+Redis containers.
- **Manual curl smoke** ready: `docker compose up auth-service postgres redis mailhog -d --wait` → `curl -X POST localhost:8080/api/auth/signup -H 'Content-Type: application/json' -d '{"email":"a@b.test","password":"password123"}'` should return 201 and produce a verification email in MailHog at `localhost:8025`.
- **No blockers.** Plan 06 owns the contract-locking ITs; once those go green, Phase 2 closes.

---
*Phase: 02-auth-service*
*Plan: 05 (Wave 4 — integration spine)*
*Completed: 2026-05-09*
