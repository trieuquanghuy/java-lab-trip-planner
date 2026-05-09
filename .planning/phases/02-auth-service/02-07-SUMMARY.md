---
phase: 02-auth-service
plan: 07
subsystem: smoke / phase-gate
tags: [smoke, mailhog, rate-limit, gateway, spring-security, runtime-vs-test, phase-gate]

requires:
  - phase: 02-auth-service
    provides: "Plans 02-01..02-06 — auth-service signup/verify/login/refresh/logout end-to-end + 4 @Tag('security') ITs + happy-path AuthControllerIT (the production code under test in this smoke run)"
  - phase: 01-api-gateway
    provides: "gateway IP-only login rate-limit + RateLimitProblemDetailFilter + WebFluxSecurityConfig (the filter chain that intermediates between the smoke and the auth-service)"
provides:
  - "scripts/smoke.sh extended with 5 auth-N criterion checks (1:1 to ROADMAP Phase 2 SC#1-#5)"
  - "scripts/README.md documents 5 new criteria + 8 phase-2-specific failure-modes + Phase 2 manual verifications section"
  - "infra/docker-compose.yml — AUTH_JWT_SECRET passthrough wired across api-gateway + trip-service + destination-service (Rule 2; the JWT secret was a Phase 1 D-16 contract that 3 of 4 services were not honoring)"
  - "services/api-gateway/src/main/resources/application.yml — gateway login + signup RequestRateLimiter math fixed (Rule 1; previous values made every request 429 permanently, masked by the phase-01-rate-limit smoke being trivially-satisfied)"
  - "services/api-gateway/.../security/RateLimitProblemDetailFilter.java — pass through downstream writeWith bodies on 429 (Rule 1; previous filter clobbered auth-service's verbatim UI-SPEC `auth.rate_limited` detail)"
  - "Quoted-printable + MailHog polling resilience in scripts/smoke.sh auth-1 helper (Rule 1; first pass failed when the 64-hex token was split across a MIME `=\\n` soft-line-break)"
affects: [phase-03-destination, phase-04-frontend-foundation, phase-05-trip-service, phase-06-itinerary, phase-07-frontend-auth-discovery, phase-10-hardening]

tech-stack:
  added:
    - "MailHog admin API (http://localhost:8025/api/v2/messages) consumption from smoke.sh"
    - "Quoted-printable decoder via python3 -c 'import quopri' with awk fallback"
  patterns:
    - "Smoke criteria poll MailHog for up to SMOKE_MAILHOG_TIMEOUT seconds rather than fixed-sleep — absorbs @Async (D-01) latency on busy hosts"
    - "Rule 1/2 fixes scoped MINIMUMLY (single-line env passthrough, requestedTokens=1, writeWith pass-through) — no architectural rewrites"
    - "Acceptance: every Phase-N SCs map to exactly one --criterion in scripts/smoke.sh — pattern locked since Phase 0 D-33"

key-files:
  created:
    - ".planning/phases/02-auth-service/02-07-SUMMARY.md (this file)"
  modified:
    - "scripts/smoke.sh (5 new auth-N criterion checks; quoted-printable decoder; MailHog polling helper)"
    - "scripts/README.md (5 criteria rows + Wave 8 invocation row + 8 phase-2 failure-modes + Phase 2 manual verifications section)"
    - "infra/docker-compose.yml (AUTH_JWT_SECRET passthrough for api-gateway / trip-service / destination-service — Rule 2)"
    - "services/api-gateway/src/main/resources/application.yml (login + signup RequestRateLimiter math fix — Rule 1)"
    - "services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java (writeWith downstream-body pass-through — Rule 1)"

key-decisions:
  - "auth-N criteria use `*@test.local` (RFC 6761 reserved TLD) + `smokepassword` literal; T-2-07-02 (smoke leaks credentials) accepted-disposition preserved verbatim"
  - "Quoted-printable decoding via python3 + awk fallback (no jq plugin, no extra deps); python3 ships on every CI runner ubuntu-24.04 + macos-14"
  - "Fix-attempt budget exhausted (5 deviation fixes in Task 7.2 environment); auth-3 / auth-4 Bearer-JWT 401 at /api/auth/logout deferred — the Plan 02-06 IT exercises the same code path GREEN, so the fix is a runtime-vs-test delta requiring deeper Phase 10 investigation"
  - "Phase 2 phase-gate signal is CHECKPOINT REACHED, not PLAN COMPLETE — visual MailHog verification + the 2 deferred auth-N criteria need user adjudication"

requirements-completed: []  # Phase 2 requirements (AUTH-01..AUTH-04, NFR-05) NOT marked complete here — they completed in Plan 02-06 which shipped the IT suite. Plan 02-07 is the operational gate that the user must sign off before declaring Phase 2 done in STATE.md.

duration: ~85min
completed: 2026-05-10
---

# Phase 02 Plan 07: Smoke Phase-Gate Summary

**Plan 02-07 ships scripts/smoke.sh extension covering ROADMAP Phase 2 SC#1-#5 + uncovered 4 pre-existing Phase 1/Plan-02-03 wiring/correctness bugs that prevented the wired stack from supporting the auth flow end-to-end. 3 of 5 auth-N criteria pass; auth-3 / auth-4 are blocked on a Spring Security filter-chain runtime delta that requires user adjudication before the Phase 2 phase-gate can close.**

## Tasks Completed

- **Task 7.1 — scripts/smoke.sh extension** (commit `7047020`)
  - 5 new `--criterion auth-N` branches mapping 1:1 to ROADMAP SC#1-#5
  - Verbatim UI-SPEC §Server-Driven Copy strings asserted at JSON envelope (`$.code` + `$.detail`)
  - JQ-required for auth-N (MailHog body parsing); `require_jq_for_auth` fail-clear if absent
  - scripts/README.md gained 5 criteria rows + Wave 8 invocation table entry + 8 phase-2-specific failure-modes + Phase 2 manual verifications section
  - `bash -n scripts/smoke.sh` exits 0; all 18 acceptance grep checks pass

- **Task 7.2 — fresh-stack smoke + MailHog visual checkpoint** (commit `e50e675` for the unblocking fixes; visual gate awaiting user)
  - Fresh `docker compose down -v && up -d --wait` against 9 of 10 services (frontend skipped — pre-existing `frontend/tsconfig.json` `ignoreDeprecations: "6.0"` value rejected by TS 5.8.x; this is OUT OF SCOPE for Plan 02-07, see "Out of Scope" section)
  - All 9 services healthy: postgres, redis, mailhog, zipkin, eureka-server, api-gateway, auth-service, trip-service, destination-service
  - 3/5 auth-N criteria PASS at runtime
  - 2/5 auth-N criteria BLOCKED on a Spring Security filter-chain delta (see Deferred Issues)
  - MailHog UI populated with verification emails — visual checkpoint ready for user

## Final auth-N Smoke Status

| Criterion | Status | Notes |
|-----------|--------|-------|
| `auth-1`  | **PASS** | signup → MailHog → verify (302 ?status=success) → login (200 + accessToken + refresh_token cookie) — full happy path through the wired gateway |
| `auth-2`  | **PASS** | unverified login → 403 auth.email_not_verified verbatim "Please verify your email before logging in."; unknown email → 400 auth.invalid_credentials (opaque per docs/05 §9.1) |
| `auth-3`  | **BLOCKED** | Authenticated `/api/auth/logout` returns 401 auth.unauthorized "Authentication required" at runtime. The same code path is GREEN in Plan 02-06's `AuthControllerIT.signup_verify_login_refresh_logout_full_flow()` (verified locally — see Investigation Notes). |
| `auth-4`  | **BLOCKED** | Same root cause as auth-3 (refresh succeeds via cookie auth, but logout(B) requires Bearer auth and 401s). |
| `auth-5`  | **PASS** | 5 wrong-password attempts → 400 auth.invalid_credentials; 6th → 429 auth.rate_limited + verbatim "Too many attempts. Please try again later." (after Rule 1 fix to `RateLimitProblemDetailFilter`) |

## Deviations from Plan

### Rule 2 — auto-add missing critical wiring

**1. [Rule 2 — Wiring] AUTH_JWT_SECRET env passthrough on api-gateway / trip-service / destination-service**

- **Found during:** Task 7.2 fresh-stack `up --wait` — trip-service / destination-service both crashed at boot with `IllegalStateException: AUTH_JWT_SECRET must be set; got null`
- **Issue:** `libs/jwt-common/JwtVerifier` validates the secret at construction (Pitfall B / D-16); `JwtAutoConfiguration` is unconditionally web-aware on every consuming service. Plan 02-03 wired `AUTH_JWT_SECRET: ${AUTH_JWT_SECRET}` only into `auth-service`'s compose env block. `api-gateway`, `trip-service`, and `destination-service` lacked the same passthrough — three of four services that consume `libs/jwt-common`. Plan 01-06 SUMMARY's "runtime gate cleared" claim must have relied on the developer's shell exporting `AUTH_JWT_SECRET` into compose's variable resolution scope at the time. On a fresh checkout via `docker compose --env-file .env up`, those services crash immediately.
- **Fix:** Added `AUTH_JWT_SECRET: ${AUTH_JWT_SECRET}` to all three service blocks in `infra/docker-compose.yml`, with inline comments referencing this plan + Pitfall B.
- **Files modified:** `infra/docker-compose.yml`
- **Commit:** `e50e675`

### Rule 1 — auto-fix bugs

**2. [Rule 1 — Gateway Rate-Limiter Math] login + signup `RequestRateLimiter` permanently denied every request**

- **Found during:** Task 7.2 — first signup request to `/api/auth/signup` returned 429 `auth.rate_limited` even on a clean Redis DB. Diagnostic curl confirmed `X-RateLimit-Remaining: 3` was reported but every request still 429'd.
- **Issue:** Plan 01-03's gateway YAML used:
  - `/api/auth/login`: `replenishRate=30, burstCapacity=30, requestedTokens=900`
  - `/api/auth/signup`: `replenishRate=3, burstCapacity=3, requestedTokens=3600`

  Spring Cloud Gateway denies when `bucket_available < requestedTokens`. With bucket capped at `burstCapacity` (30 or 3) and each request demanding 900 or 3600 tokens, **every request 429'd permanently** — no auth flow could ever complete via the gateway. The Phase 1 `phase-01-rate-limit` smoke ("≥1 × 429 in 35 requests") was trivially-satisfied because every request 429'd, masking the bug. Plan 01-03's inline math claim ("steady-state allowed rate = 30 tokens / 900 token-cost = 1 req per 30 s") misread SCG's `requestedTokens` semantics — it is "tokens deducted per request from the current bucket," not "tokens needed in the bucket before this request can run."
- **Fix:** `requestedTokens=1` (SCG default; each request costs 1 token). Burst+replenish unchanged. Real anti-abuse policy is owned by `auth-service`'s `LoginRateLimiter` (D-05/D-06/D-08) which uses Lua `INCR+EXPIRE` on `rl:login:fail:{ip}:{email}` — the Phase 2 SC#5 / NFR-05 compliance gate. Comments now point at this layered approach.
- **Files modified:** `services/api-gateway/src/main/resources/application.yml`
- **Commit:** `e50e675`

**3. [Rule 1 — Gateway 429 Body] `RateLimitProblemDetailFilter` clobbered downstream `auth.rate_limited` body**

- **Found during:** Task 7.2 auth-5 — auth-service's `LoginRateLimiter` correctly emitted 429 `auth.rate_limited` with the verbatim UI-SPEC detail "Too many attempts. Please try again later.", but the user-visible response had detail "Rate limit exceeded for this route" (the gateway's internal string).
- **Issue:** `RateLimitProblemDetailFilter`'s `writeWith()` override unconditionally rewrote any 429 response body, including downstream-emitted ProblemDetail bodies. This violated UI-SPEC §Server-Driven Copy Contract for `auth.rate_limited` (Phase 2 SC#5 / NFR-05).
- **Fix:** Pass `writeWith` bodies through unchanged (downstream is already `application/problem+json` per BL-01 contract). Gateway-internal `RequestRateLimiter` uses `setComplete()` (empty-body path, overridden separately) — Phase 1 D-07 contract preserved (gateway empty-body 429s still gain a problem+json envelope).
- **Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java`
- **Commit:** `e50e675`

**4. [Rule 1 — Smoke MIME Decode] auth-1 token extraction failed on quoted-printable-encoded MailHog bodies**

- **Found during:** Task 7.2 — first auth-1 invocation extracted `token=3D7d27bcaac269e91492328475f990b=` (truncated, with `=3D` still encoded).
- **Issue:** MailHog stores `SimpleMailMessage` bodies in MIME quoted-printable encoding (each line ≤76 chars; soft line breaks are `=\n`; literal `=` is `=3D`; multi-byte UTF-8 like the U+2014 em-dash is `=E2=80=94`). The 64-hex token in the verify URL was being split across a soft-line-break, defeating the smoke's `grep -oE 'token=[a-f0-9]{64}'` regex.
- **Fix:** Decode via `python3 -c 'import quopri; ...'` with an `awk`-based fallback decoder. Then grep the decoded body. Python ships on every CI runner (`ubuntu-24.04` / `macos-14`); the awk fallback covers minimal containers.
- **Files modified:** `scripts/smoke.sh`
- **Commit:** `e50e675`

**5. [Rule 1 — Smoke Polling] auth-1 raced with @Async email send under sequential test pressure**

- **Found during:** Task 7.2 — auth-1 sometimes failed with "No MailHog message found for smoke-1@test.local" even though the signup returned 201. Direct curl + `sleep 4` showed the email did arrive, just outside the smoke's fixed `sleep 2` window.
- **Issue:** D-01 `@Async` send is decoupled from the signup transaction; under sequential smoke pressure the queued send took 3-7 seconds to land. Fixed-sleep is fragile.
- **Fix:** Replaced `sleep 2` + single MailHog API hit with a polling loop (1-second tick, configurable via `SMOKE_MAILHOG_TIMEOUT`, default 15s).
- **Files modified:** `scripts/smoke.sh`
- **Commit:** `e50e675`

## Deferred Issues

### Bearer-JWT 401 at runtime on `/api/auth/logout` (auth-3 / auth-4 blocker)

**Status:** Out-of-scope blocker — surfaces ONLY in compose runtime; Plan 02-06 IT (`AuthControllerIT.signup_verify_login_refresh_logout_full_flow`) GREEN locally with the same code.

**Symptom:**
- POST `/api/auth/login` → 200 + valid HS256 JWT.
- POST `/api/auth/logout` with `Authorization: Bearer <jwt>` → 401 `auth.unauthorized` "Authentication required".
- A bad JWT (wrong signature) returns 401 `auth.invalid_token` "Token is invalid" — confirming `ServletJwtCommonFilter` is reachable and verifies signatures.
- A valid JWT triggers no `auth.invalid_token` → the filter accepts it but the controller never runs (no `Logout invoked` log).

**Investigation notes:**
1. **Same secret confirmed:** `docker compose exec api-gateway env | grep AUTH_JWT_SECRET` and same on auth-service both return `dev-only-32-byte-secret-replace-in-prod`. JWT round-trips correctly through `JwtVerifier` (verified by writing the bad-JWT path's auth.invalid_token vs valid-JWT's auth.unauthorized differential).
2. **Plan 02-06 fix is in the running jar:** `unzip -p /app/app.jar BOOT-INF/classes/com/tripplanner/auth/security/SecurityConfig.class | strings | grep setEnabled` returns the expected symbols. The `jwtFilterReg.setEnabled(false)` + `addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)` pattern is intact.
3. **Plan 02-06 IT is GREEN locally:** `JAVA_HOME=jdk17 ./gradlew :services:auth-service:test --tests AuthControllerIT --rerun` reports `signup_verify_login_refresh_logout_full_flow PASSED`. The IT exercises the FULL filter chain via MockMvc + Testcontainers Postgres, so this is a real integration test — yet runtime fails identically.
4. **Hypothesis (unverified):** Spring Boot 3.5's auto-configured `SecurityContextHolderFilter` may be running between `ServletJwtCommonFilter` and the controller in the real servlet container in a way that wipes the SecurityContext, but does NOT happen the same way under MockMvc. Possibly tied to `OncePerRequestFilter`'s "ALREADY_FILTERED" attribute being set by the still-registered global filter even with `setEnabled(false)`. SB 3.5+ may register `@Bean`-defined `FilterRegistrationBean`s differently than 3.3.x. This is speculation — the next investigator should enable `logging.level.org.springframework.security=TRACE` in `application-docker.yml` and trace one logout request to see exactly which filter rejects.

**User adjudication path:**
1. **Accept and verify by manual test:** the user can verify the logout flow via direct call to auth-service `:8081` (which I confirmed ALSO 401s — so the bug is in auth-service, not the gateway). The IT covers it. Phase 10 will catch it during the 8-test merge gate.
2. **Investigate now:** trace logging + Spring Security filter chain dump. Likely a 1-2 hour investigation.
3. **Workaround for the smoke:** auth-3 / auth-4 could be rewritten to use `/api/auth/refresh` (cookie-based auth) for the "authenticated route + invalidation" semantics, which DOES work end-to-end at runtime. This trades NFR-05 IT alignment for runtime smoke coverage.

**Why I stopped fixing:** I had already applied 5 deviation fixes during Task 7.2; per the deviation rules' fix-attempt limit (≤3 per task), I escalate the remaining issue to user adjudication via this CHECKPOINT REACHED.

### Frontend Docker build failure (out of scope)

**Status:** Pre-existing local edit to `frontend/tsconfig.json` (`"ignoreDeprecations": "6.0"`); TS 5.8.x rejects the value (only `"5.0"` accepted). The compose `frontend` service fails its build stage. Skipped via explicit service-list to `docker compose up`. Task 7.2 smoke targets the auth-relevant subset (postgres / redis / mailhog / zipkin / eureka / gateway / auth / trip / destination — 9 of 10 services), which is fully sufficient for all 5 auth-N criteria.

**Out-of-scope reason:** This is not caused by Plan 02-07's task; it's an unrelated frontend tsconfig edit in the working tree. Documented here so it doesn't get rolled into Phase 2 carryover; tracked for whoever picks up frontend work next.

## MailHog Visual Checkpoint Status

**Ready for user adjudication.** After running auth-1 (which IS passing), MailHog UI at `http://localhost:8025/` contains a verification email addressed to `smoke-1@test.local`:

- **Subject:** `Verify your Trip Planner account` (verbatim UI-SPEC)
- **From:** `Trip Planner <no-reply@tripplanner.local>` (verbatim UI-SPEC)
- **Body** (plain-text rendering): matches UI-SPEC §Email Copy Contract verbatim — em-dash before "The Trip Planner team" should render as `—` (U+2014); verification URL on its own line; "expires in 24 hours" line present.

The smoke (auth-1) parses the body via the MailHog admin API but visual fidelity (em-dash rendering, line breaks, no HTML, plain-text-ness) requires a human eye per Plan 02-07 §threat_model T-2-07-01. v2 may add a snapshot-test against captured GreenMail body bytes.

## Plan-Level TDD Gate Compliance

N/A — Plan 02-07 is `type: execute` (not `type: tdd`). Smoke-script extensions are inherently after-the-fact verification.

## Cross-Cutting Handoff Notes for Phase 3

The following auth artifacts are NOW STABLE CONTRACTS for Phase 3+ (Destination Service — Search) consumers:

- **JWT shape (Plan 02-02):** `iss=tripplanner-auth`, `sub=userId`, `iat`, `exp=iat+900s`, `email`, `ver`, `jti=UUID` — HS256-signed with `AUTH_JWT_SECRET`. Phase 3+ services accept these via `libs/jwt-common`'s `ServletJwtCommonFilter` (servlet) or `ReactiveJwtAuthenticationManager` (gateway).
- **RFC 7807 envelope (BL-01 / Plan 02-06):** `code` at JSON root, NEVER `$.properties.code`. All Phase 2 endpoints emit this shape; gateway's BL-01 fix is in place; Phase 3+ services inherit the same `ProblemDetailJacksonMixin` via `libs/error-handling`.
- **`/api/auth/*` route prefixes:** locked at the gateway. Phase 3 SHOULD NOT duplicate them; Phase 7 SPA targets the same routes.
- **`AUTH_JWT_SECRET` env passthrough:** **NEW REQUIREMENT** — Phase 2-07 fixed the gap on api-gateway/trip-service/destination-service, but any FUTURE service consuming `libs/jwt-common` MUST add the same passthrough to its `infra/docker-compose.yml` env block. This is a Phase 1 D-16 lint-checkable convention: every service that imports `libs/jwt-common` needs `AUTH_JWT_SECRET: ${AUTH_JWT_SECRET}` in its compose env. Suggested follow-up: add a smoke criterion or pre-commit lint that asserts this invariant.

## Self-Check: PASSED

- [x] `scripts/smoke.sh` syntactically valid (`bash -n` exits 0)
- [x] All 5 auth-N criterion identifiers grep-present in smoke.sh
- [x] All 5 verbatim UI-SPEC `$.code` strings grep-present (`auth.email_not_verified`, `auth.invalid_credentials`, `auth.refresh_invalid`, `auth.rate_limited`)
- [x] Both verbatim UI-SPEC `$.detail` strings grep-present ("Please verify your email before logging in.", "Too many attempts. Please try again later.")
- [x] All 5 endpoint paths grep-present (`/api/auth/{signup,verify,login,refresh,logout}`)
- [x] MailHog UI URL grep-present (`localhost:8025`)
- [x] JQ-availability detection follows Phase 0 pattern (HAVE_JQ + grep fallback) PLUS auth-N-specific `require_jq_for_auth` strict gate
- [x] PHASE 2 SMOKE final-line message present
- [x] Both Plan 02-07 commits exist: `7047020` (Task 7.1 smoke extension) + `e50e675` (Task 7.2 unblocking fixes)
- [x] 3 of 5 auth-N criteria PASS at runtime (auth-1, auth-2, auth-5)
- [x] MailHog UI populated with verification emails ready for user visual verification
- [x] 02-07-SUMMARY.md created at correct path
