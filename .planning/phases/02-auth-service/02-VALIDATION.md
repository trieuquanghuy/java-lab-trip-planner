---
phase: 2
slug: auth-service
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-09
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5.12.0 + Mockito 5.x + AssertJ + Spring Boot Test (managed by SB 3.5.14 BOM) |
| **Config file** | `services/auth-service/build.gradle.kts` test block (`useJUnitPlatform { includeTags("security") }` for security suite) |
| **Quick run command** | `./gradlew :services:auth-service:test --tests <task-scoped-IT>` |
| **Full suite command** | `./gradlew :services:auth-service:check` |
| **Estimated runtime** | ~60s warm (full); ~10s warm (per task unit); ~30s warm (per task IT touching containers) |

Security suite: `./gradlew :services:auth-service:test -PincludeTags=security`

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :services:auth-service:test --tests <task-scoped-IT>`
- **After every plan wave:** Run `./gradlew :services:auth-service:check`
- **Before `/gsd-verify-work`:** Full suite + `-PincludeTags=security` + `bash scripts/smoke.sh` exit 0 + MailHog UI visual check
- **Max feedback latency:** ~30 seconds (task), ~60 seconds (wave)

---

## Per-Task Verification Map

> Filled in during planning — each PLAN.md task references the matching row.
> Key: `❌ W0` = produced by Wave 0 test infra plan; `✅` = file exists pre-phase.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 02-XX-YY | TBD | TBD | AUTH-01 | — | Signup with valid email returns 201 + userId | integration | `./gradlew :services:auth-service:test --tests SignupHappyPathIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-01 | T-2-V5 | Signup with weak password returns 400 `auth.weak_password` | integration | `./gradlew :services:auth-service:test --tests AuthValidationIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-01 | T-2-V5 | Signup with malformed email returns 400 `auth.invalid_email` | integration | `./gradlew :services:auth-service:test --tests AuthValidationIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-01 | T-2-V9.1 | Re-signup of unverified account returns 201 + fresh email (D-23) | integration | `./gradlew :services:auth-service:test --tests ResignupOfUnverifiedIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-02 | — | Verify with valid token 302-redirects `?status=success` and flips `email_verified` | integration | `./gradlew :services:auth-service:test --tests VerifyHappyPathIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-02 | T-2-V7 | Verify with consumed token 302-redirects `?status=invalid` | integration | `./gradlew :services:auth-service:test --tests VerifyHappyPathIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-02 | T-2-V7 | Verify with expired token 302-redirects `?status=expired` | integration | `./gradlew :services:auth-service:test --tests VerifyHappyPathIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-03 | T-2-V2 | Login with wrong password returns 400 `auth.invalid_credentials` AND increments rate-limit counter | integration | `./gradlew :services:auth-service:test --tests LoginHappyAndFailIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-03 | T-2-V2 (sec #7) | Login with unverified account returns 403 `auth.email_not_verified` | integration `@Tag("security")` | `./gradlew :services:auth-service:test --tests EmailNotVerifiedCannotLoginIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-03 | — | Login success returns 200 + JWT + `Set-Cookie: refresh_token` | integration | `./gradlew :services:auth-service:test --tests LoginHappyAndFailIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-03 | T-2-V2 (sec #8) | 6th failed login from same IP+email within 15min returns 429 `auth.rate_limited` | integration `@Tag("security")` | `./gradlew :services:auth-service:test --tests LoginRateLimitFailedAttemptsTriggersIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-04 | T-2-V3 (sec #5) | Logout invalidates refresh; subsequent /refresh returns 401 `auth.refresh_invalid` | integration `@Tag("security")` | `./gradlew :services:auth-service:test --tests DeletedRefreshTokenCannotBeUsedIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-04 | T-2-V3 (sec #6) | Replay rotated token revokes entire chain | integration `@Tag("security")` | `./gradlew :services:auth-service:test --tests RotatedRefreshTokenCannotBeReusedIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-04 | — | /refresh with valid cookie rotates the token and returns new access JWT | integration | `./gradlew :services:auth-service:test --tests RefreshHappyPathIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | NFR-05 | — | Service-layer line coverage ≥ 70% (Phase 2 surface check) | jacoco | `./gradlew :services:auth-service:jacocoTestCoverageVerification` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | NFR-05 (BL-01) | T-2-V7 | Every gateway error path renders `$.code` at root | integration | `./gradlew :services:api-gateway:test --tests GatewayMissingAuthHeaderIT --tests GatewayForgedJwtIT --tests GatewayProblemDetailRenderingIT --tests LoginRateLimiterIT` | ❌ Wave 5 | ⬜ pending |
| 02-XX-YY | TBD | TBD | NFR-05 (BL-01) | T-2-V7 | Every auth-service error path renders `$.code` at root | integration | `./gradlew :services:auth-service:test --tests AuthControllerAdviceIT` | ❌ W0 | ⬜ pending |
| 02-XX-YY | TBD | TBD | AUTH-* | — | Unit-level coverage on `JwtIssuer`, `RefreshTokenService.revokeChain`, `LoginRateLimiter` | unit | `./gradlew :services:auth-service:test --tests *Test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

Wave 0 lands BEFORE production code (RED-by-design). Production code lands in Waves 1–4.

- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java` — `@SpringBootTest` + `@Testcontainers` + `@ServiceConnection` Postgres + `@ServiceConnection(name="redis")` Redis + `@RegisterExtension GreenMailExtension`
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/support/TestSecurityConfig.java` — `@TestConfiguration` `@Bean @Primary BCryptPasswordEncoder(4)` per D-19
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerIT.java` — happy-path signup + verify + login + refresh + logout (AUTH-01..04 SC#1)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerAdviceIT.java` — RFC 7807 `$.code` at root for 9 error codes (BL-01)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/security/EmailNotVerifiedCannotLoginIT.java` — `@Tag("security")` #7
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/security/DeletedRefreshTokenCannotBeUsedIT.java` — `@Tag("security")` #5
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/security/RotatedRefreshTokenCannotBeReusedIT.java` — `@Tag("security")` #6 (DB-level chain-revocation assertion)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/security/LoginRateLimitFailedAttemptsTriggersIT.java` — `@Tag("security")` #8 IP+email leg
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/service/RefreshTokenServiceTest.java` — unit test of `revokeChain` walking both directions
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/service/LoginRateLimiterTest.java` — unit test of Lua-script atomicity
- [ ] `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtIssuerTest.java` — issued tokens round-trip through `JwtVerifier`
- [ ] `services/auth-service/src/test/resources/application-test.yml` — test profile (`app.auth.cookie.secure=false`, `app.frontend.base-url=http://localhost:5173`, `app.mail.from=test@…`)
- [ ] BL-01 IT updates (Wave 5): `git grep "\$.properties.code" services/api-gateway/src/test` must return zero after the wave; assertions migrate to `$.code` plus negative `$.properties.code.doesNotExist()`.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| MailHog UI shows the verification email rendering with correct subject + clickable URL | AUTH-02 | Visual confirmation — body formatting, no leaked secrets | After `docker compose up`, run signup happy-path IT, open `http://localhost:8025`, confirm subject = `"Verify your Trip Planner account"`, body contains the verification URL on its own line with 24h expiry note |
| End-to-end signup→verify→login→refresh→logout demo recording | NFR-05 (Phase gate) | Reviewer-facing portfolio artifact | Follow `scripts/smoke.sh` (TBD) end-to-end against full docker-compose stack |
| Final coverage audit (NFR-05 ≥ 70% line, 100% on auth + ownership-check) | NFR-05 | Cross-phase aggregate (auth + trip + destination); Phase 2 only validates auth slice | Deferred to Phase 10 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s (per wave)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
