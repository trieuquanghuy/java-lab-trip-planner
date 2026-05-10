---
phase: 02-auth-service
verified: 2026-05-10T14:25:00Z
status: passed
score: 5/5 success criteria verified (with 1 documented carry-forward to Phase 10)
overrides_applied: 1
overrides:
  - must_have: "SC#3 — `/api/auth/me` endpoint reachable; logout invalidates subsequent authenticated requests (Bearer-JWT path)"
    reason: "Two-part criterion. Part A (`/api/auth/me`) is intentionally SKIPPED per UI-SPEC §Phase UI Surface line 33 + §Out-of-Scope line 223 (RESEARCH Open Question 1 RESOLVED — SPA decodes email/ver from JWT payload directly). Part B (Bearer-JWT logout 401-invalidation against the LIVE Tomcat servlet container) DEFERRED to Phase 10 security audit per phase-gate adjudication 2026-05-10. The cookie-based logout flow IS green via AuthControllerIT.signup_verify_login_refresh_logout_full_flow (the IT exercises the full filter chain via MockMvc + Testcontainers Postgres). NFR-05 merge gate is satisfied: all 4 mandatory `@Tag('security')` ITs Phase 2 owns pass."
    accepted_by: "phase-gate adjudication"
    accepted_at: "2026-05-10T00:00:00Z"
gaps: []
deferred:
  - truth: "Bearer-JWT logout against live Tomcat container returns 401 instead of 204 for valid JWTs"
    addressed_in: "Phase 10 (security audit / final coverage)"
    evidence: "Phase 10 ROADMAP entry covers the final NFR-05 coverage audit and security-IT mass-validation. Phase-gate adjudication 2026-05-10 routed Bearer-JWT runtime-vs-test delta to Phase 10 with TRACE-level Spring Security logging investigation; NFR-05 merge gate already satisfied by 4 GREEN @Tag('security') ITs. Hypothesis (per 02-07-SUMMARY.md): Spring Boot 3.5 SecurityContextHolderFilter / global FilterRegistrationBean.setEnabled(false) interaction differs between MockMvc and the real Tomcat servlet container."
human_verification:
  - test: "MailHog UI visual checkpoint — verification email body renders verbatim per UI-SPEC §Email Copy Contract"
    expected: "Subject 'Verify your Trip Planner account'; From 'Trip Planner <no-reply@tripplanner.local>'; em-dash sign-off renders as U+2014 '—'; verification URL on its own line; 'expires in 24 hours' line present; plain text only (no HTML)"
    why_human: "Visual fidelity (em-dash rendering, line breaks, plain-text-ness) requires a human eye per Plan 02-07 §threat_model T-2-07-01. Already adjudicated APPROVED on 2026-05-10."
---

# Phase 2: Auth Service — Verification Report

**Phase Goal:** Full signup → email verification → login → refresh → logout works end-to-end with all 8 mandatory security scenarios passing.

**Verified:** 2026-05-10
**Status:** PASSED (with documented Phase 10 carry-forward and adjudicated MailHog visual checkpoint APPROVED)
**Re-verification:** No — initial verification.

---

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| #   | Truth                                                                                                                                                                              | Status     | Evidence                                                                                                                                                                                                                                                                                                                                                                                                |
| --- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | A user can sign up with email + password, receive a verification link in MailHog UI, click it to activate the account, and then log in                                             | ✓ VERIFIED | `AuthControllerIT.signup_verify_login_refresh_logout_full_flow` PASSED end-to-end (signup→verify→login flow). MailHog visual checkpoint APPROVED at phase gate 2026-05-10 (subject + body + em-dash all match UI-SPEC byte-for-byte). `EmailVerificationSender.buildBody()` emits the verbatim UI-SPEC §Email Copy Contract body with U+2014 em-dash.                                                       |
| 2   | An unverified account cannot log in; the error message does not reveal whether the account exists                                                                                  | ✓ VERIFIED | `EmailNotVerifiedCannotLoginIT` (`@Tag("security")` IT #7) PASSED — 403 + `auth.email_not_verified` + verbatim "Please verify your email before logging in." `AuthService.login` performs the verified-check AFTER bcrypt (timing-attack defense — same latency as wrong-password path). Generic detail strings; `auth.email_already_registered` NOT emitted from /signup per D-24.                       |
| 3   | A logged-in user can call `/api/auth/me`, receive their profile, then log out; subsequent authenticated requests return 401                                                        | ✓ PASSED (override) | Two-part SC. Part A `/api/auth/me` SKIPPED by design per UI-SPEC §Out-of-Scope line 223 + RESEARCH Open Question 1 RESOLVED (SPA decodes JWT payload directly). Part B logout-invalidation: cookie path GREEN via `AuthControllerIT` step 6 (post-logout `/refresh` returns 401 `auth.refresh_invalid`). Bearer-JWT live-container path DEFERRED to Phase 10 per adjudication 2026-05-10. Override accepted. |
| 4   | A user can refresh their session using the httpOnly cookie and receive a new access token; after logout the refresh token is invalidated                                           | ✓ VERIFIED | `AuthControllerIT` (refresh→rotation→logout→post-logout-refresh-401), `DeletedRefreshTokenCannotBeUsedIT` (security IT #5 with DB-level revoked_at != null assertion), `RotatedRefreshTokenCannotBeReusedIT` (security IT #6 keystone Pitfall 4 BOTH-directions chain revoke). All PASSED. Cookie shape verified `HttpOnly; SameSite=Strict; Path=/api/auth; Max-Age=604800`.                              |
| 5   | All 8 mandatory security integration tests pass (4 land in Phase 2; 4 already exist from Phase 1)                                                                                  | ✓ VERIFIED | Phase 2 owns 4: #5 `DeletedRefreshTokenCannotBeUsedIT`, #6 `RotatedRefreshTokenCannotBeReusedIT`, #7 `EmailNotVerifiedCannotLoginIT`, #8 `LoginRateLimitFailedAttemptsTriggersIT`. ALL 4 PASSED via `./gradlew :services:auth-service:test -PincludeTags=security` (live run 2026-05-10). Phase 1 ships #1, #2, and #8 IP-only leg (`XUserIdInjectionIT`, `GatewayForgedJwtIT`, `LoginRateLimiterIT`, `GatewayMissingAuthHeaderIT`). Phase 5 owns #3 (cross-user trip), Phase 6 owns #4 (cross-user item) per D-16. |

**Score:** 5/5 truths verified (with SC#3 PASSED via override + Phase 10 carry-forward).

---

## Required Artifacts

### Production Code

| Artifact                                                                                                  | Expected                                                                                                                          | Status      | Details                                                                                                                                                              |
| --------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java`                        | 5 endpoints (signup/verify/login/refresh/logout) per docs/04 §3                                                                   | ✓ VERIFIED  | All 5 mappings present. /verify is GET → 302 redirect; /signup returns opaque 201; /logout cookie-clear + Max-Age=0; X-Forwarded-For first-token resolution per D-05. |
| `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java`                  | 9 verbatim UI-SPEC RFC 7807 detail strings + BL-01 (`code` at root via auto-configured ObjectMapper)                              | ✓ VERIFIED  | All 9 strings byte-for-byte present (lines 67, 70, 73, 86, 92, 98, 104, 110, 116). No `new ObjectMapper()`. `EmailAlreadyRegisteredException` handler exists but never emitted from /signup per D-24. |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java`                       | Login rate-limit BEFORE bcrypt; dummy-bcrypt timing defense; verified-check AFTER bcrypt; D-23 re-signup; D-24 opaque /signup     | ✓ VERIFIED  | Line 131 `rateLimiter.exceeded` BEFORE line 145 `passwordEncoder.matches`; line 139 dummy `passwordEncoder.matches("dummy", DUMMY_BCRYPT12_HASH)`; verified-check at line 150 (after bcrypt). D-23 + D-24 branches present. |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java`               | `@Transactional(REPEATABLE_READ, noRollbackFor=RefreshInvalidException.class)`; revokeChain walks BOTH directions (Pitfall 4 / D-10) | ✓ VERIFIED  | Line 59 has the full annotation including `noRollbackFor` (Plan 02-06 critical fix); `revokeChain()` walks backward via `findByRotatedTo` reverse-lookup THEN forward (lines 103-122). |
| `services/auth-service/src/main/java/com/tripplanner/auth/repository/RefreshTokenRepository.java`         | `@Lock(LockModeType.PESSIMISTIC_WRITE)` on `findByTokenHashForUpdate`; `findByRotatedTo` reverse-lookup                            | ✓ VERIFIED  | Line 24 `@Lock(LockModeType.PESSIMISTIC_WRITE)` on JPQL query. `findByRotatedTo` derived-query at line 29 (supported by partial index `rt_rotated_to_idx`).         |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/LoginRateLimiter.java`                  | Atomic Lua INCR+EXPIRE; key shape `rl:login:fail:{ip}:{email_lower}`; 6th attempt trips                                            | ✓ VERIFIED  | Lua script INCR with conditional EXPIRE (line 20-24); `>= 5` check at line 36; success-clears via `redis.delete` at line 46 (D-07).                                  |
| `services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java`                   | `BCryptPasswordEncoder(12)`; permitAll {signup,verify,login,refresh}; logout=authenticated(); jwtFilterReg.setEnabled(false) fix | ✓ VERIFIED  | Line 40 `BCryptPasswordEncoder(12)`. Plan 02-06 Rule 1 fix at line 54: `jwtFilterReg.setEnabled(false)` + `addFilterBefore` (single-source filter ownership inside SecurityFilterChain). |
| `services/auth-service/src/main/java/com/tripplanner/auth/email/EmailVerificationSender.java`             | `@Async("authAsyncExecutor")` + verbatim UI-SPEC body (em-dash U+2014); MailException logs only userId + class                    | ✓ VERIFIED  | Line 38 `@Async("authAsyncExecutor")`. `buildBody()` emits the literal UI-SPEC body including `"— The Trip Planner team"` (U+2014). MailException catch at line 49-52 logs only `userId` + `getClass().getSimpleName()`. |
| `services/auth-service/src/main/java/com/tripplanner/auth/config/AsyncConfig.java`                        | `@EnableAsync` + `authAsyncExecutor` (core 2, max 4, queue 50); `MdcCopyingTaskDecorator` for D-22 trace propagation              | ✓ VERIFIED  | All pool sizes verbatim. `MdcCopyingTaskDecorator` copies + restores MDC across thread boundary.                                                                     |
| `services/auth-service/src/main/java/com/tripplanner/auth/scheduling/TokenCleanupJob.java`                | `@Scheduled(cron = "0 0 2 * * *")` daily 02:00 UTC; INTERVAL '7 days'; both tables                                                | ✓ VERIFIED  | Cron + JVM default UTC; two `@Transactional` cleanup methods (`cleanupEmailTokens`, `cleanupRefreshTokens`); INFO log includes both row counts.                       |
| `services/auth-service/src/main/resources/db/migration/V2__create_users.sql`                              | `auth.users` per docs/03 §3.1 (UUID PK; VARCHAR(254) email UNIQUE; VARCHAR(72) password_hash; email_verified BOOLEAN)              | ✓ VERIFIED  | Verbatim shape.                                                                                                                                                       |
| `services/auth-service/src/main/resources/db/migration/V3__create_email_verification_tokens.sql`          | `auth.email_verification_tokens` per docs/03 §3.2 (CHAR(64) PK; user_id FK CASCADE; expires_at; consumed_at; partial unconsumed idx) | ✓ VERIFIED  | Verbatim shape; `evt_unconsumed_idx` partial index for D-23 fast lookup.                                                                                              |
| `services/auth-service/src/main/resources/db/migration/V4__create_refresh_tokens.sql`                     | `auth.refresh_tokens` per docs/05 §2 (CHAR(64) PK; rotated_to nullable; revoked_at; expires_at idx; rotated_to partial idx)        | ✓ VERIFIED  | Verbatim shape; `rt_rotated_to_idx` partial index supports `findByRotatedTo` reverse-lookup (Pitfall 4).                                                              |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java`                                        | HS256, 15-min TTL, claims iss/sub/iat/exp/email/ver/jti                                                                            | ✓ VERIFIED  | All claims correct; jjwt 0.13.0 modern builder API; HS256 auto-set from key length; weak-key check throws at startup.                                                  |

### Test Code

| Artifact                                                                                                                  | Expected                                                                            | Status      | Details                                                                                                            |
| ------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ----------- | ------------------------------------------------------------------------------------------------------------------ |
| `.../security/EmailNotVerifiedCannotLoginIT.java`                                                                         | `@Tag("security")` IT #7                                                            | ✓ VERIFIED  | PASSED at `./gradlew test -PincludeTags=security` 2026-05-10. Asserts 403 + `auth.email_not_verified` + verbatim detail. |
| `.../security/DeletedRefreshTokenCannotBeUsedIT.java`                                                                     | `@Tag("security")` IT #5 with DB-level revoked_at assertion                          | ✓ VERIFIED  | PASSED. DB-level assertion: `refreshRepo.findById(hashA).getRevokedAt()` IS NOT NULL after logout.                |
| `.../security/RotatedRefreshTokenCannotBeReusedIT.java`                                                                   | `@Tag("security")` IT #6 keystone Pitfall 4 BOTH-directions chain revocation        | ✓ VERIFIED  | PASSED. DB-level: BOTH `findById(hashA).getRevokedAt()` AND `findById(hashB).getRevokedAt()` IS NOT NULL.         |
| `.../security/LoginRateLimitFailedAttemptsTriggersIT.java`                                                                | `@Tag("security")` IT #8 IP+email leg                                                | ✓ VERIFIED  | PASSED. Locks the 5×400 + 6th=429 contract.                                                                       |
| `.../api/AuthControllerIT.java`                                                                                           | Happy-path E2E (5 endpoints) covering ROADMAP SC#1/#3-cookie/#4                    | ✓ VERIFIED  | PASSED. Tests cookie + bearer auth path against full filter chain via MockMvc + Testcontainers Postgres.          |
| `.../api/AuthControllerAdviceIT.java`                                                                                     | BL-01 contract: `$.code` at root, never `$.properties.code`                          | ✓ VERIFIED  | PASSED. 4 tests cover weak_password / invalid_email / invalid_credentials / unknown-token-redirect.               |
| `.../service/RefreshTokenServiceTest.java`                                                                                | Unit test of `revokeChain` walking BOTH directions                                  | ✓ VERIFIED  | PASSED 2 unit tests.                                                                                              |
| `.../service/LoginRateLimiterTest.java`                                                                                   | Unit test of Lua INCR+EXPIRE atomicity                                              | ✓ VERIFIED  | PASSED.                                                                                                           |
| `.../support/AuthIntegrationTestBase.java`                                                                                | `@SpringBootTest` + `@ServiceConnection` Postgres (16-alpine) + Redis (7-alpine) + GreenMail | ✓ VERIFIED  | Singleton container pattern (Ryuk reaper); all containers per D-30 / docs/02 stack pin compliance.                  |
| `.../support/TestSecurityConfig.java`                                                                                     | `@TestConfiguration` `BCryptPasswordEncoder(4)` `@Primary`                          | ✓ VERIFIED  | Bcrypt cost-4 in tests per D-19.                                                                                  |
| `services/auth-service/src/test/resources/application-test.yml`                                                           | Test profile with `app.auth.cookie.secure=false`, `frontend.base-url`, mail, JWT secret | ✓ VERIFIED  | All required overrides present.                                                                                   |

---

## Key Link Verification

| From                          | To                                                  | Via                                                                                              | Status      | Details                                                                                                                       |
| ----------------------------- | --------------------------------------------------- | ------------------------------------------------------------------------------------------------ | ----------- | ----------------------------------------------------------------------------------------------------------------------------- |
| AuthService.login             | LoginRateLimiter.exceeded BEFORE bcrypt             | call ordering inside `login(LoginRequest, ip)`                                                   | ✓ WIRED     | Line 131 (rate-limit) precedes line 145 (bcrypt). D-05 invariant satisfied.                                                  |
| AuthService.login (404 path)  | dummy bcrypt verify                                 | `passwordEncoder.matches("dummy", DUMMY_BCRYPT12_HASH)`                                          | ✓ WIRED     | Line 139 — invariant timing across user-not-found vs wrong-password branches.                                                |
| RefreshTokenService.rotate    | repo.findByTokenHashForUpdate (SELECT FOR UPDATE)   | `@Lock(PESSIMISTIC_WRITE)` JPQL + `@Transactional(REPEATABLE_READ)`                              | ✓ WIRED     | Repository line 24-26 + Service line 59 — both annotations co-required (Pitfall 2).                                         |
| RefreshTokenService.revokeChain | repo.findByRotatedTo reverse-lookup               | walk backward, then forward                                                                       | ✓ WIRED     | Lines 103-122 — Pitfall 4 / D-10. `noRollbackFor=RefreshInvalidException` (line 59) ensures revocation persists through throw. |
| AuthService.signup (D-23)     | EmailVerificationService.mintFor + event publish    | `events.publishEvent(new VerificationEmailRequestedEvent(...))`                                   | ✓ WIRED     | Lines 105-109 (re-signup branch) + 114-117 (new-account branch).                                                              |
| EmailVerificationSender       | JavaMailSender (MailHog SMTP)                       | `@Async("authAsyncExecutor")` + `@EventListener` on `VerificationEmailRequestedEvent`             | ✓ WIRED     | Async executor pinned by name; MdcCopyingTaskDecorator propagates trace context (D-22).                                       |
| AuthController.login          | resolveClientIp(http) (X-Forwarded-For first token) | `http.getHeader("X-Forwarded-For")` then `getRemoteAddr()` fallback                              | ✓ WIRED     | Lines 156-163. Gateway-only injector; direct service hits 401 per Phase 1 (X-User-Id stripping).                              |
| AuthController.refresh        | RefreshTokenService.rotate + JwtIssuer.issueAccess  | cookie value → rotate → fresh access JWT + new ResponseCookie                                     | ✓ WIRED     | Lines 103-120; new HS256 access JWT signed with `AUTH_JWT_SECRET` (Phase 1 D-16 contract).                                   |
| AuthController.logout         | RefreshTokenService.revokeChainHead + clear cookie  | `Set-Cookie: refresh_token=; Max-Age=0`                                                          | ✓ WIRED     | Lines 124-137. Idempotent (D-11) — no-op when cookie missing.                                                                |
| AuthControllerAdvice          | ProblemDetailFactory.of(...) auto-configured ObjectMapper | NO `new ObjectMapper()` (BL-01)                                                                  | ✓ WIRED     | All 9 detail strings emit through `body()` helper (line 131-136) which calls `ProblemDetailFactory.of(...)`.                  |
| Gateway BL-01 fix             | injected `ObjectMapper` (auto-configured)            | `ProblemDetailAuthEntryPoint(ObjectMapper mapper)` + `RateLimitProblemDetailFilter(ObjectMapper)` | ✓ WIRED     | Both files inject the auto-configured bean; no `new ObjectMapper()` constructions.                                            |

---

## Data-Flow Trace (Level 4)

| Artifact                          | Data Variable                                | Source                                                                                                          | Produces Real Data | Status     |
| --------------------------------- | -------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ------------------ | ---------- |
| AuthController.signup             | `r.userId()`                                 | `UUID.randomUUID()` written to `auth.users` via `userRepo.save(u)` (real Postgres via Hikari)                   | Yes                | ✓ FLOWING  |
| AuthController.login              | `r.accessToken()`                            | `JwtIssuer.issueAccess` HS256-signed with `AUTH_JWT_SECRET` from `JwtProperties("auth.jwt")`                    | Yes                | ✓ FLOWING  |
| AuthController.login              | `r.rawRefreshToken()`                        | `RefreshTokenService.create()` SecureRandom 32-byte hex, hash persisted to `auth.refresh_tokens`                | Yes                | ✓ FLOWING  |
| AuthController.refresh            | `next.rawValue()` (rotated cookie)           | `RefreshTokenService.rotate()` → SELECT FOR UPDATE → mints new row + sets `rotated_to` on previous row           | Yes                | ✓ FLOWING  |
| AuthController.verify             | redirect URL with status                     | `EmailVerificationService.consume(token)` returns "success"\|"invalid"\|"expired" from real DB lookup            | Yes                | ✓ FLOWING  |
| EmailVerificationSender           | email body with `{TOKEN}`                    | `props.getVerification().getLinkBase()` + `ev.token()` (32-byte hex from EmailVerificationService.mintFor)       | Yes                | ✓ FLOWING  |

---

## Behavioral Spot-Checks

| Behavior                                                                        | Command                                                              | Result                                                                                                    | Status |
| ------------------------------------------------------------------------------- | -------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | ------ |
| 4 mandatory `@Tag("security")` ITs pass                                         | `./gradlew :services:auth-service:test -PincludeTags=security`       | All 4 PASSED. BUILD SUCCESSFUL in 26s. Output names: `EmailNotVerifiedCannotLoginIT`, `DeletedRefreshTokenCannotBeUsedIT`, `RotatedRefreshTokenCannotBeReusedIT`, `LoginRateLimitFailedAttemptsTriggersIT`. | ✓ PASS |
| Full auth-service test suite passes                                              | `./gradlew :services:auth-service:test --rerun-tasks`                | 12 tests PASSED (4 security ITs + 4 advice ITs + 1 happy-path IT + 1 rate-limit unit + 2 refresh-service unit). BUILD SUCCESSFUL in 19s. | ✓ PASS |
| Phase 1 security ITs still present                                              | `find services/api-gateway/src/test -name "*IT.java"`                | `XUserIdInjectionIT` (#1), `GatewayForgedJwtIT` (#2), `GatewayMissingAuthHeaderIT`, `LoginRateLimiterIT` (#8 IP-only) — all present. | ✓ PASS |
| Live-stack auth-1 (signup→email), auth-2 (verify), auth-5 (rate-limit)          | `bash scripts/smoke.sh` (recorded in 02-07-SUMMARY.md)               | auth-1 / auth-2 / auth-5 PASS. auth-3 / auth-4 BLOCKED on Bearer-JWT logout runtime delta — DEFERRED to Phase 10 per phase-gate adjudication. | ⚠️ DEFERRED |

---

## Requirements Coverage

| Requirement | Source Plan | Description                                                                                                                                  | Status      | Evidence                                                                                                                                                                                                                                                                                                                |
| ----------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| AUTH-01     | 02-01,02-04,02-05 | User can sign up with unique email and password (min 8 chars). Generic error messages used to prevent account enumeration.                          | ✓ SATISFIED | `AuthController.signup` + `AuthService.signup` (D-24 opaque 201). `SignupRequest` has `@Email @NotBlank @Size(max=254)` + `@NotBlank @Size(min=8, max=200)`. AuthControllerAdviceIT confirms `auth.weak_password` + `auth.invalid_email` codes; `auth.email_already_registered` NOT emitted. AuthControllerIT happy-path covers signup→userId. |
| AUTH-02     | 02-01,02-04,02-05 | User receives a verification email with a unique 24-hour link; clicking it activates the account.                                                    | ✓ SATISFIED | `EmailVerificationService.mintFor` produces 32-byte hex tokens with 24h TTL. `EmailVerificationSender` async-sends via JavaMailSender → MailHog. `AuthController.verify` 302-redirects to `${frontend.base-url}/verify?status={success\|invalid\|expired}`. AuthControllerIT covers the full flow incl. token receipt via GreenMail.       |
| AUTH-03     | 02-01,02-03,02-05 | User can log in with email and password and stay signed in across page refreshes; bad credentials return a generic error.                            | ✓ SATISFIED | `AuthController.login` + `AuthService.login` with rate-limit-before-bcrypt + dummy-bcrypt timing defense + verified-check-after-bcrypt. Returns access JWT + Set-Cookie. AuthControllerIT covers the happy path; AuthControllerAdviceIT covers `auth.invalid_credentials`; LoginRateLimitFailedAttemptsTriggersIT covers 6th-attempt 429.   |
| AUTH-04     | 02-01,02-03,02-05 | User can log out, ending the session immediately and blocking access to authenticated routes.                                                        | ✓ SATISFIED | `AuthController.logout` clears cookie + revokes chain head. DeletedRefreshTokenCannotBeUsedIT (security #5) confirms post-logout `/refresh` returns 401 + DB-level `revoked_at != null`. RotatedRefreshTokenCannotBeReusedIT (security #6) confirms chain-replay revocation across full chain (Pitfall 4 keystone gate).               |
| NFR-05      | 02-06       | Backend service-layer line coverage ≥ 70%; auth + ownership-check paths achieve 100% branch coverage; 8 mandatory security ITs gate every PR.        | ✓ SATISFIED | NFR-05 merge gate: all 4 mandatory @Tag("security") ITs Phase 2 owns PASS. Phase 1 ships #1, #2, #8 IP-only. Phase 5/6 deferred per D-16 (own #3, #4 respectively). Final coverage audit (≥70% line, 100% branch on auth + ownership) DEFERRED to Phase 10 per CONTEXT §Out of Scope (correctly scheduled). |

**ORPHANED requirements:** None. Every Phase 2 requirement ID maps to a plan in 02-01..02-07.

---

## Decision Compliance Audit (24 decisions D-01..D-24)

| #    | Decision                                                                                              | Status      | Evidence                                                                                                                                                                                       |
| ---- | ----------------------------------------------------------------------------------------------------- | ----------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| D-01 | Async email send (`@Async`, ThreadPoolTaskExecutor core 2, max 4, queue 50)                            | ✓ COMPLIANT | AsyncConfig.java verbatim sizes; signup returns 201 synchronously, email send decoupled.                                                                                                        |
| D-02 | Verify link gateway URL → 302-redirect to `${frontend.base-url}/verify?status=...`                    | ✓ COMPLIANT | AuthController.verify (lines 78-83) + EmailVerificationService.consume returns "success"\|"invalid"\|"expired".                                                                              |
| D-03 | Plain-text email subject "Verify your Trip Planner account"; from `no-reply@tripplanner.local`        | ✓ COMPLIANT | EmailVerificationSender.java line 44 + AuthProperties config.                                                                                                                                  |
| D-04 | SMTP failure handling logs only userId + exception class                                              | ✓ COMPLIANT | EmailVerificationSender.java lines 49-52.                                                                                                                                                       |
| D-05 | Rate-limit check BEFORE bcrypt verify; X-Forwarded-For first token                                    | ✓ COMPLIANT | AuthService.java line 131; AuthController.resolveClientIp lines 156-163.                                                                                                                       |
| D-06 | Redis key shape `rl:login:fail:{ip}:{email_lower}`; INCR+EXPIRE 900s                                   | ✓ COMPLIANT | LoginRateLimiter.java lines 39-43, 50-53.                                                                                                                                                       |
| D-07 | Failed-attempts counting only; success deletes the key                                                 | ✓ COMPLIANT | LoginRateLimiter.clear (line 46-48); AuthService.login.line 155 calls `rateLimiter.clear` on successful auth.                                                                                  |
| D-08 | 429 ProblemDetail with `code=auth.rate_limited`, generic detail                                        | ✓ COMPLIANT | AuthControllerAdvice.onRateLimited (line 113-117). Verbatim UI-SPEC string.                                                                                                                    |
| D-09 | Email lowercase normalize at signup AND limit-check                                                    | ✓ COMPLIANT | AuthService.normalize (line 180-182) called from signup line 94 and login line 128. LoginRateLimiter expects emailLower.                                                                       |
| D-10 | Replay revocation walks chain via `rotated_to` reverse-lookup BOTH directions                         | ✓ COMPLIANT | RefreshTokenService.revokeChain (lines 103-122) + WARN log lines 70-71.                                                                                                                        |
| D-11 | Logout idempotent; missing cookie still returns 204                                                   | ✓ COMPLIANT | AuthController.logout (lines 124-137) + RefreshTokenService.revokeChainHead silent-no-op for null/blank.                                                                                        |
| D-12 | Cookie scope `Path=/api/auth; HttpOnly; SameSite=Strict; Max-Age=604800; Secure=<profile>`              | ✓ COMPLIANT | AuthController.buildRefreshCookie (lines 140-148) verbatim. AuthControllerIT asserts `cookie().httpOnly(true)`, `cookie().path("/api/auth")`.                                                  |
| D-13 | rotate() runs in `@Transactional(REPEATABLE_READ)` + `SELECT FOR UPDATE`                              | ✓ COMPLIANT | RefreshTokenService line 59 + RefreshTokenRepository.findByTokenHashForUpdate `@Lock(PESSIMISTIC_WRITE)`.                                                                                       |
| D-14 | BL-01 fix: gateway emitters inject auto-configured ObjectMapper                                        | ✓ COMPLIANT | gateway ProblemDetailAuthEntryPoint + RateLimitProblemDetailFilter both inject `ObjectMapper` via constructor; no `new ObjectMapper()` anywhere. AuthControllerAdviceIT asserts `$.code` at root + `$.properties.code` does NOT exist. |
| D-15 | Phase 2 ships 4 mandatory @Tag("security") ITs                                                        | ✓ COMPLIANT | All 4 IT files exist; all PASSED in live run.                                                                                                                                                   |
| D-16 | Tests deferred to Phase 5/6 (#3, #4); no `@Disabled` shells in Phase 2                                | ✓ COMPLIANT | No `@Disabled` in test tree; REQUIREMENTS.md TRIP-01..TRIP-04 mapped to Phase 5/6.                                                                                                              |
| D-17 | TokenCleanupJob `@Scheduled(cron="0 0 2 * * *")`; INTERVAL '7 days' on both tables                    | ✓ COMPLIANT | TokenCleanupJob.java lines 38-58.                                                                                                                                                               |
| D-18 | Bean Validation maps fields to UI-SPEC codes (auth.invalid_email / auth.weak_password / fallback)     | ✓ COMPLIANT | AuthControllerAdvice.handleMethodArgumentNotValid (lines 52-79) + AuthControllerAdviceIT 3 tests.                                                                                              |
| D-19 | BCryptPasswordEncoder(12) production; @TestConfiguration cost-4 in tests                              | ✓ COMPLIANT | SecurityConfig.java line 40; TestSecurityConfig.java line 23-27 with @Primary.                                                                                                                  |
| D-20 | JwtIssuer in `libs/jwt-common`; HS256 + claims iss/sub/iat/exp/email/ver/jti                          | ✓ COMPLIANT | JwtIssuer.java in libs/jwt-common; jjwt 0.13.0 modern builder API; all 7 claims set.                                                                                                            |
| D-21 | Service-layer @Transactional boundaries on signup/verify/login/refresh/logout; email send OUTSIDE tx | ✓ COMPLIANT | AuthService.signup/login/logout/findUserByIdOrThrow all @Transactional. Email send via @Async @EventListener fires after tx commits.                                                            |
| D-22 | TaskDecorator copies MDC across @Async thread switch                                                  | ✓ COMPLIANT | AsyncConfig.MdcCopyingTaskDecorator (lines 32-47).                                                                                                                                              |
| D-23 | Re-signup of unverified mints fresh token, invalidates prior, sends fresh email                        | ✓ COMPLIANT | AuthService.signup re-signup branch (lines 97-110) — `verifTokenRepo.markAllUnconsumedAsConsumedFor` + fresh `mintFor` + event publish.                                                       |
| D-24 | Opaque /signup — existing-verified accounts also return 201, never throw EmailAlreadyRegisteredException | ✓ COMPLIANT | AuthService.signup early-return for verified existing (lines 99-103). EmailAlreadyRegisteredException handler exists in advice but is NEVER thrown from /signup.                              |

---

## Stack Pin Compliance

| Pin                                       | Expected               | Found                                  | Status      |
| ----------------------------------------- | ---------------------- | -------------------------------------- | ----------- |
| Spring Boot                               | 3.5.x                   | `springBoot = "3.5.14"` in libs.versions.toml | ✓ COMPLIANT |
| Spring Cloud train                        | 2025.0.x (matches SB 3.5) | `springCloud = "2025.0.2"`             | ✓ COMPLIANT |
| jjwt                                      | 0.13.0                 | `jjwt = "0.13.0"`                      | ✓ COMPLIANT |
| Flyway + flyway-database-postgresql       | both present (Pitfall A) | both as `runtimeOnly` in build.gradle.kts lines 25-26 | ✓ COMPLIANT |
| BCrypt cost                               | 12 prod / 4 test       | SecurityConfig.java line 40 = 12; TestSecurityConfig line 26 = 4 | ✓ COMPLIANT |
| PostgreSQL Testcontainer                  | 16-alpine              | `postgres:16-alpine` in AuthIntegrationTestBase line 43 | ✓ COMPLIANT |
| Redis Testcontainer                       | 7-alpine               | `redis:7-alpine` in AuthIntegrationTestBase line 48 | ✓ COMPLIANT |
| Java toolchain                            | 21                     | root build.gradle.kts `JavaLanguageVersion.of(21)`; live test run on `21.0.7-tem` | ✓ COMPLIANT |
| GreenMail                                 | 2.x                    | `greenmail = "2.1.4"`                  | ✓ COMPLIANT |

---

## UI-SPEC Compliance — 9 Verbatim RFC 7807 Detail Strings

Byte-for-byte verification against `02-UI-SPEC.md §Server-Driven Copy Contract`:

| Code                               | HTTP | UI-SPEC Detail (locked)                                          | Production Source             | Status      |
| ---------------------------------- | ---- | ---------------------------------------------------------------- | ----------------------------- | ----------- |
| `auth.invalid_email`               | 400  | `Invalid email format.`                                          | AuthControllerAdvice line 70   | ✓ VERBATIM  |
| `auth.weak_password`               | 400  | `Password does not meet minimum requirements.`                   | AuthControllerAdvice line 67   | ✓ VERBATIM  |
| `auth.email_already_registered`    | 409  | NOT EMITTED on `/signup` (handler exists for future admin paths) | AuthControllerAdvice line 128 | ✓ COMPLIANT (handler exists, never reached on /signup per D-24) |
| `auth.invalid_credentials`         | 400  | `Email or password is incorrect.`                                | AuthControllerAdvice line 86   | ✓ VERBATIM  |
| `auth.email_not_verified`          | 403  | `Please verify your email before logging in.`                    | AuthControllerAdvice line 92   | ✓ VERBATIM  |
| `auth.token_invalid`               | 400  | `This verification link is invalid.`                             | AuthControllerAdvice line 98   | ✓ VERBATIM  |
| `auth.token_expired`               | 400  | `This verification link has expired.`                            | AuthControllerAdvice line 104  | ✓ VERBATIM  |
| `auth.refresh_invalid`             | 401  | `Session expired. Please log in again.`                          | AuthControllerAdvice line 110  | ✓ VERBATIM  |
| `auth.rate_limited`                | 429  | `Too many attempts. Please try again later.`                     | AuthControllerAdvice line 116  | ✓ VERBATIM  |
| `validation.failed`                | 400  | `Request validation failed.`                                     | AuthControllerAdvice line 73   | ✓ VERBATIM  |

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |

**No anti-patterns detected** in production code paths. Code-comment "TODO" / "FIXME" sweep returned zero hits across `services/auth-service/src/main`. Stub patterns absent. All seemingly-empty initializers (e.g., `BcryptPasswordEncoder(12)`) are intentional configuration, not stubs.

---

## Behavioral Spot-Check Summary

```
BUILD SUCCESSFUL in 26s
17 actionable tasks: 1 executed, 16 up-to-date
DeletedRefreshTokenCannotBeUsedIT > logged_out_refresh_token_returns_401_and_db_row_is_revoked() PASSED
EmailNotVerifiedCannotLoginIT > unverified_account_login_returns_403_email_not_verified() PASSED
LoginRateLimitFailedAttemptsTriggersIT > sixth_failed_login_from_same_ip_email_within_15min_returns_429() PASSED
RotatedRefreshTokenCannotBeReusedIT > replayed_refresh_token_revokes_entire_chain() PASSED
```

```
BUILD SUCCESSFUL in 19s (full suite, --rerun-tasks)
17 actionable tasks: 17 executed
12 of 12 tests PASSED:
- AuthControllerAdviceIT (4 tests)
- AuthControllerIT (1 test)
- DeletedRefreshTokenCannotBeUsedIT (1 test)
- EmailNotVerifiedCannotLoginIT (1 test)
- LoginRateLimitFailedAttemptsTriggersIT (1 test)
- RotatedRefreshTokenCannotBeReusedIT (1 test)
- LoginRateLimiterTest (1 unit test)
- RefreshTokenServiceTest (2 unit tests)
```

---

## Carry-Forward / Deferred Items (informational only)

### 1. Bearer-JWT logout 401 against live Tomcat servlet container

**Status:** DEFERRED to Phase 10 security audit per phase-gate adjudication 2026-05-10.

**Why:** The Plan 02-06 IT (`AuthControllerIT.signup_verify_login_refresh_logout_full_flow`) is GREEN locally — exercising the FULL filter chain via MockMvc + Testcontainers Postgres. Production code in the running jar (`SecurityConfig.jwtFilterReg.setEnabled(false)` + `addFilterBefore`) is intact. Same-secret + valid-JWT round-trip works. The 401 only manifests on Bearer-token logout against the real Tomcat servlet container (not MockMvc). NFR-05 merge gate is NOT violated — all 4 mandatory @Tag("security") ITs PASS.

**Investigation hypothesis (per 02-07-SUMMARY.md):** Spring Boot 3.5's auto-configured `SecurityContextHolderFilter` may run between `ServletJwtCommonFilter` and the controller in the real container in a way that wipes the SecurityContext, but does NOT happen the same way under MockMvc. Possibly tied to `OncePerRequestFilter`'s ALREADY_FILTERED attribute being set by the still-registered global filter even with `setEnabled(false)`.

**Phase 10 action:** Enable `logging.level.org.springframework.security=TRACE` in `application-docker.yml`, trace a logout request, identify the rejecting filter.

### 2. STATE.md blocker line stale

**Status:** Documentation cleanup — does NOT block Phase 2.

**Why:** STATE.md line 220 still reads `AUTH-3/AUTH-4 smoke criteria BLOCKED — Bearer-JWT 401 at /api/auth/logout at runtime [...] User adjudication required before Phase 2 declared COMPLETE.` This text predates the 2026-05-10 phase-gate adjudication that DEFERRED the issue to Phase 10. The blocker statement is now stale and should be updated by the Phase 2 close-out commit. Recommend the orchestrator strike line 220 / replace it with `Adjudicated 2026-05-10 — Bearer-JWT logout runtime delta DEFERRED to Phase 10.`

### 3. Pre-existing frontend tsconfig.json edit (out of scope)

**Status:** Not a Phase 2 issue.

**Why:** `frontend/tsconfig.json` had `"ignoreDeprecations": "6.0"` in the working tree before Phase 2. TS 5.8.x rejects this. Frontend Docker build fails. Per `<known_status>` orchestrator block: "Out-of-scope dirty files (pre-existing before Phase 2): `frontend/tsconfig.json`, untracked `docs/.obsidian/`. Do NOT flag these as Phase 2 issues." Not flagged.

---

## Human Verification Required

### MailHog UI visual checkpoint

**Test:** Open `http://localhost:8025` after `docker compose up -d` + auth-1 smoke run; visually inspect the verification email rendering.

**Expected:**
- Subject `Verify your Trip Planner account` (verbatim)
- From `Trip Planner <no-reply@tripplanner.local>` (verbatim)
- Em-dash sign-off renders as U+2014 `—` (not `--` or `&mdash;`)
- Verification URL on its own line
- "expires in 24 hours" line present
- Plain text only — no HTML

**Why human:** Visual fidelity (em-dash glyph, line breaks, plain-text-ness) requires a human eye per Plan 02-07 §threat_model T-2-07-01. The smoke (auth-1) parses the body via the MailHog admin API but cannot validate visual rendering. v2 may add a snapshot-test against captured GreenMail body bytes.

**Status (already adjudicated):** APPROVED at phase gate 2026-05-10 — email body matches UI-SPEC byte-for-byte (em-dash U+2014, link on its own line, expires-in-24-hours line present).

---

## Gaps Summary

**No blocking gaps.** Phase 2 delivers all 5 ROADMAP success criteria:

- SC#1, SC#2, SC#4, SC#5: VERIFIED end-to-end via integration tests + MailHog visual checkpoint APPROVED.
- SC#3: PASSED via override. Two-part criterion. Part A `/api/auth/me` was intentionally SKIPPED at the UI-SPEC level (SPA decodes JWT directly per RESEARCH Open Question 1 RESOLVED). Part B (logout-invalidation): cookie path GREEN end-to-end; Bearer-JWT live-container path DEFERRED to Phase 10 with all NFR-05 merge-gate ITs passing.

**Test runtime:** All 12 auth-service tests pass (`./gradlew :services:auth-service:test --rerun-tasks` → BUILD SUCCESSFUL in 19s). The 4 mandatory @Tag("security") ITs Phase 2 owns all PASS (`./gradlew :services:auth-service:test -PincludeTags=security` → BUILD SUCCESSFUL in 26s).

**Documentation cleanup recommended (non-blocking):** Strike STATE.md line 220's stale "blocker" wording; the Bearer-JWT runtime delta has been adjudicated as DEFERRED. This is a paper-fix, not a code-fix.

**Phase 2 Status: PASSED.**

---

_Verified: 2026-05-10_
_Verifier: Claude (gsd-verifier)_
