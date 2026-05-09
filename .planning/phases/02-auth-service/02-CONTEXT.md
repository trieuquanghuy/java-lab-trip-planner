# Phase 2: Auth Service - Context

**Gathered:** 2026-05-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2 ships the hand-rolled email+password auth-service (`:8081`) — owning the full credential lifecycle: signup → email verification → login (access JWT + refresh cookie) → refresh-with-rotation → logout. Phase 2 also closes Phase-1's deferred IP+email login rate-limit leg (D-05), fixes the BL-01 RFC 7807 contract divergence between gateway and downstream, and is the merge gate for **4 of the 8 mandatory security integration tests** (NFR-05). Schema additions: `auth.users`, `auth.email_verification_tokens`, and the new `auth.refresh_tokens` table called out in `docs/05-auth-security.md §2`.

**In scope:**
- 5 endpoints per `docs/04-api-spec.md §3` — `POST /api/auth/signup`, `GET /api/auth/verify`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/auth/logout`
- `auth.users` (V2 migration), `auth.email_verification_tokens` (V3 migration), `auth.refresh_tokens` (V4 migration) — schemas per `docs/03-data-model.md §3.1, §3.2, §2`
- Async email send via `JavaMailSender` + `@Async` thread pool (size 2-4) targeting MailHog at `mailhog:1025`
- IP+email rate limit (5 failed-attempts / 15min) inside `AuthService.login()` before bcrypt verify, backed by Redis `INCR`+`EXPIRE`
- Refresh-token rotation with `SELECT FOR UPDATE` row lock (concurrent-refresh safety) and chain-replay revocation
- Daily `@Scheduled` cleanup of both token tables
- BL-01 fix: gateway `ProblemDetailAuthEntryPoint` + `RateLimitProblemDetailFilter` adopt Spring Boot's auto-configured `ObjectMapper` so all services emit `code` at `$.code` (not `$.properties.code`)
- 4 of 8 mandatory security ITs: `DeletedRefreshTokenCannotBeUsed` (#5), `RotatedRefreshTokenCannotBeReused` (#6), `EmailNotVerifiedCannotLogin` (#7), `LoginRateLimitFailedAttemptsTriggers` (#8 IP+email leg)
- 4 ROADMAP success criteria: signup→verify→login E2E, unverified-cannot-login + opaque error, /me + logout invalidation, refresh-rotation + post-logout-revocation

**Out of scope (deferred to other phases):**
- `/api/auth/me` endpoint shape — confirm whether Phase 2 ships this OR Phase 7 frontend just decodes the JWT (`docs/04` doesn't list it; ROADMAP SC#3 implies it exists). Researcher decides.
- `CrossUserTripAccessReturns404` (#3) — Phase 5 (needs real `/api/trips/*`)
- `CrossUserItemPatchReturns404` (#4) — Phase 6 (needs real items endpoints)
- Final coverage audit (NFR-05 confirmation) — Phase 10
- Logout-everywhere endpoint, OAuth, MFA, password reset, breach-list k-anonymity — v2 backlog
- HTML email templates — Phase 9 polish (interface hook NOT pre-extracted; do it then)
- Web push, native mobile, JWT key rotation — v2

</domain>

<decisions>
## Implementation Decisions

### Email send + verification UX

- **D-01 (Async send):** Verification email sent via `@Async` on a dedicated `ThreadPoolTaskExecutor` (core 2, max 4, queue 50). `AuthService.signup()` writes the `users` row + `email_verification_tokens` row in a single `@Transactional`, returns `201 {userId}` synchronously, then publishes a Spring `ApplicationEvent` (or directly invokes the async sender bean). Signup response time stays sub-200ms regardless of SMTP latency.
- **D-02 (Verification link target):** Email link is `http://localhost:8080/api/auth/verify?token=…` (gateway URL). Backend consumes the token, then **302-redirects** to `http://localhost:5173/verify?status=success` (or `=invalid` / `=expired`). The token never leaves the server-side flow; only the status reaches the FE. Phase 7 owns the FE `/verify` page; Phase 2 ships a tiny static placeholder page only if needed for demo (researcher's call). Frontend redirect base URL is configurable via `app.frontend.base-url` env (`FRONTEND_BASE_URL`, default `http://localhost:5173`).
- **D-03 (Email body):** Plain-text only via `SimpleMailMessage`. Subject `"Verify your Trip Planner account"`. Body: short copy with verification URL on its own line and "expires in 24 hours" note. No template engine, no `spring-boot-starter-thymeleaf`. From-address `no-reply@tripplanner.local` (configurable via `app.mail.from`, env `MAIL_FROM`); display name `"Trip Planner"`.
- **D-04 (SMTP failure handling):** Async sender catches `MailException`, logs `WARN` with `userId`, `traceId`, and exception class — never the email body or token. Token stays in DB until 24h TTL or daily cleanup. The user has a recovery path via re-signup (per `docs/05-auth-security.md §9.1`: signup with an existing-but-unverified email returns the same opaque 201 and triggers a fresh verification email). No retry policy, no dead-letter table.

### Login rate limit (IP+email leg)

- **D-05 (Location):** Check happens inside `AuthService.login(LoginRequest, ipAddress)` **before** the bcrypt-12 verify. Avoids spending 250ms of CPU per attempt on a guaranteed-rejected request and avoids the body-caching gymnastics a Spring filter would need. Controller resolves IP via `HttpServletRequest.getHeader("X-Forwarded-For")` first token (gateway is the only source — never trust the IP from a direct service call, which is already a 401 per Phase 1). Falls back to `getRemoteAddr()` for tests.
- **D-06 (Backing store):** Redis. Key shape `rl:login:fail:{ip}:{email_lower}` (`rl:` prefix matches the gateway's existing keyspace from Phase 1 D-06). Operations: `INCR` then `EXPIRE 900` on first hit; check `> 5`. RedisTemplate (already wired by Spring Data Redis via observability libs's transitive deps; if not, add `spring-boot-starter-data-redis`).
- **D-07 (Counting model):** Failed attempts only. Successful login DELETEs the key (`redis.delete(key)` after issuing tokens). A legitimate user mistyping their password 4 times then succeeding does NOT get locked out on their next try. This matches `docs/05-auth-security.md §10` test #8 phrasing ("6 failed logins → 429") literally.
- **D-08 (Limit-tripped response):** `ProblemDetail` with `status=429`, `code=auth.rate_limited` (already in `docs/04-api-spec.md §6`). Generic detail: `"Too many attempts. Please try again later."` — same message regardless of whether the email exists, preserving `docs/05 §9.1` enumeration policy. No `Retry-After` header in v1 (deferred to v2 hardening). The 429 envelope shape matches the gateway's after BL-01 fix lands (D-13).
- **D-09 (Email-key normalization):** `email.toLowerCase().trim()` before composing the Redis key, AND lowercase-store on signup (per `docs/03-data-model.md §3.1` "lower-case stored"). Key collisions across casing variants are prevented at write time + at limit-check time.

### Refresh-token rotation reuse + logout scope

- **D-10 (Replay revocation scope):** When a presented refresh token's `rotated_to` is non-null (someone replayed an already-rotated token), the service walks back to the chain root via `rotated_to` reverse-lookup, then forward to the head, and marks `revoked_at = NOW()` on every row. Single user "session" is invalidated. WARN log: `userId`, chain head hash (first 8 chars), client IP, `traceId`. Other devices/independent chains for the same user are NOT touched. Matches `docs/05-auth-security.md §2` verbatim.
- **D-11 (Logout scope):** `POST /api/auth/logout` reads the `refresh_token` cookie, hashes it (SHA-256), looks up the chain, marks the chain head `revoked_at = NOW()`, and clears the cookie via `Set-Cookie: refresh_token=; Max-Age=0`. Other devices stay signed in. Edge case — Bearer JWT valid but cookie missing: still return `204` (idempotent), don't error. The access token will expire in <15min on its own.
- **D-12 (Cookie scope):** `Set-Cookie: refresh_token=<value>; Path=/api/auth; HttpOnly; SameSite=Strict; Max-Age=604800; Secure=<profile>`. Matches `docs/04-api-spec.md §3` and `docs/05 §9.3` verbatim. `Secure` flag toggled via profile property `app.auth.cookie.secure` — `false` in dev/test profiles (`http://localhost`), `true` in any future deployed profile. Cookie reaches `/api/auth/refresh` AND `/api/auth/logout`; not sent to `/api/auth/login` or `/api/auth/signup` (correct — login mints a fresh chain regardless of any old cookie).
- **D-13 (Concurrent-refresh):** `RefreshService.rotate(rawToken)` runs in `@Transactional(isolation = REPEATABLE_READ)` and does `SELECT … FROM auth.refresh_tokens WHERE token_hash = ? FOR UPDATE` as the first step. Concurrent rotations of the same token serialize on the row lock; the second arrival sees `rotated_to IS NOT NULL` and falls into the replay branch (D-10). This holds independent of the SPA's Pitfall-9 `isRefreshing` client guard — defense-in-depth.

### BL-01 fix + Phase 2 security-test slice

- **D-14 (BL-01 fix in this phase):** Inject Spring Boot's auto-configured `ObjectMapper` (with `ProblemDetailJacksonMixin`) into both gateway emitters — `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java` and `RateLimitProblemDetailFilter.java`. Replace `new ObjectMapper()` constructions. Update the four affected ITs (`GatewayMissingAuthHeaderIT`, `GatewayForgedJwtIT`, `GatewayProblemDetailRenderingIT`, `LoginRateLimiterIT`) to assert `$.code` instead of `$.properties.code`. Result: every ProblemDetail across the public API surface has `code` at root. Auth-service's new ITs assert the same `$.code` path. Fix is committed alongside Phase 2's auth-service work — BL-01 closes here.
- **D-15 (Mandatory security ITs Phase 2 owns):** Phase 2 ships, with `@Tag("security")`, integration tests for:
  - `DeletedRefreshTokenCannotBeUsed` (`docs/05 §10` #5) — login → logout → refresh returns 401 `auth.refresh_invalid`
  - `RotatedRefreshTokenCannotBeReused` (#6) — refresh once, then replay the OLD token → 401 + entire chain revoked + subsequent /refresh on the head also 401
  - `EmailNotVerifiedCannotLogin` (#7) — signup → login (no verify) → 403 `auth.email_not_verified`
  - `LoginRateLimitFailedAttemptsTriggers` (#8 IP+email leg) — 6th failed login from same `(ip, email)` within 15min → 429 `auth.rate_limited`
  - PLUS happy paths covering ROADMAP SC#1–#4: signup→verify(redirect)→login E2E, /me + logout, refresh+rotation
  - PLUS BL-01 contract tests: every error path renders `$.code` at root in both auth-service and gateway after fix
- **D-16 (Tests deferred):** `CrossUserTripAccessReturns404` (#3) is owned by **Phase 5** (TRIP-01/TRIP-02); `CrossUserItemPatchReturns404` (#4) is owned by **Phase 6** (TRIP-03/TRIP-04). Do NOT write `@Disabled` shells in Phase 2; instead add explicit entries in Phase 5 and Phase 6 plans + REQUIREMENTS traceability. Final coverage audit (NFR-05) lives in Phase 10.

### Cross-cutting

- **D-17 (Token cleanup scheduler):** Single `@EnableScheduling` bean `TokenCleanupJob` with `@Scheduled(cron = "0 0 2 * * *")` (daily 02:00 UTC). Two SQL deletes in one method: `DELETE FROM auth.email_verification_tokens WHERE expires_at < NOW() - INTERVAL '7 days'` and `DELETE FROM auth.refresh_tokens WHERE expires_at < NOW() - INTERVAL '7 days'`. Logs row counts at INFO with traceId. No ShedLock (auth-service is single-instance per docs/02 §3); add ShedLock note to v2 backlog only.
- **D-18 (Bean Validation):** `SignupRequest` record annotated `@Email @NotBlank @Size(max=254) String email`, `@NotBlank @Size(min=8, max=200) String password`. `LoginRequest` likewise. `@ControllerAdvice` (extends `ResponseEntityExceptionHandler`) maps `MethodArgumentNotValidException` to `auth.invalid_email` (when `email` field failed) or `auth.weak_password` (when `password` field failed) per `docs/04 §6`. Generic detail messages — no field-name leak. Other catalog codes (`auth.email_already_registered`, `auth.invalid_credentials`, `auth.email_not_verified`, `auth.token_invalid`, `auth.token_expired`, `auth.refresh_invalid`) ride through the same `ControllerAdvice` from custom service exceptions.
- **D-19 (BCrypt encoder):** `@Bean PasswordEncoder bcrypt() { return new BCryptPasswordEncoder(12); }` in `application` profile config (`SecurityConfig.java`). `application-test.yml` overrides via a `@TestConfiguration` that returns `new BCryptPasswordEncoder(4)` — keeps the 8 IT runs fast without touching the main profile (`docs/05 §6`).
- **D-20 (JWT issuance):** `JwtIssuer` (new component in `libs/jwt-common` or auth-service-local — researcher decides; consistency with Phase-1's `JwtVerifier` location is the call) signs HS256 access tokens with claims per `docs/05 §2`: `iss=tripplanner-auth`, `sub=userId`, `iat`, `exp=iat+900s`, `email`, `ver=email_verified`, `jti=UUID`. Loads `AUTH_JWT_SECRET` via the existing `JwtProperties` (D-16 from 01-CONTEXT.md) — single env var across all services.
- **D-21 (Service-layer @Transactional boundaries):** `signup` (REQUIRED), `verify` (REQUIRED), `login` (REQUIRED, isolation default; the rate-limit Redis call lives outside the transaction), `refresh` (REQUIRED, isolation = REPEATABLE_READ per D-13), `logout` (REQUIRED). Email sending (`@Async`) runs OUTSIDE the signup transaction by design (D-01).
- **D-22 (X-Request-Id + MDC through @Async):** `@Async` thread pool decorated with `TaskDecorator` that copies the calling thread's MDC + Reactor context. Pattern matches Phase-0 servlet `MdcEnrichmentFilter` philosophy. Async send logs carry the originating signup's `traceId`/`requestId`/`userId` so Zipkin spans correlate. (This is the servlet-side correction to Pitfall 7 reactive concerns; auth-service is servlet-stack per Phase-0 D-07.)
- **D-23 (Re-signup of unverified account):** Detect existing-but-unverified user in `AuthService.signup()`; mint a fresh verification token (invalidating any prior unconsumed token for that user via `consumed_at = NOW()` + WHERE `consumed_at IS NULL`); send fresh email; return same opaque `201 {userId: <existing>}` envelope (`docs/05 §7`). Concurrent re-signup races collapse on the unique `users.email` constraint.

### Claude's Discretion

- Whether `JwtIssuer` lives in `libs/jwt-common` (alongside `JwtVerifier`) or auth-service-local — researcher decides; consistency with Phase-1 placement is the call.
- Exact copy of the verification email body (subject + body text) — keep it short, plain, no marketing copy.
- Whether to ship a tiny static `verify-success.html` page in auth-service for the redirect target (defense if FE not running) or trust the FE base-url to be reachable — researcher decides based on demo-recording needs.
- Whether `/api/auth/me` endpoint is shipped in Phase 2 or whether the SPA decodes the JWT client-side — `docs/04` doesn't list `/me` but ROADMAP SC#3 mentions it. Recommend shipping it (`GET /api/auth/me` returns `{id, email, emailVerified}` from the validated JWT principal — three lines of controller code).
- Field-level naming inside DTOs (`SignupRequest`, `LoginResponse`, `RefreshResponse`) — pick whatever reads cleanest; match `docs/04 §3` shapes verbatim.
- Whether the `TokenCleanupJob` DELETEs are wrapped in a single transaction or two (no functional difference; cleaner logs with two).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Authentication design (the most important set)
- `docs/05-auth-security.md` — full auth-and-security design (skim the whole doc; it's the brain of Phase 2)
- `docs/05-auth-security.md §1` — authentication overview (locked decisions: email+password, verify-before-login, JWT 15min, refresh 7d httpOnly cookie + rotation)
- `docs/05-auth-security.md §2` — JWT design (claims, HS256 signing, refresh token shape, `auth.refresh_tokens` table called out)
- `docs/05-auth-security.md §3` — signup → verify → login sequence (Phase 2 implements this)
- `docs/05-auth-security.md §4` — authenticated request sequence (Phase 1 owns this; Phase 2 issues the JWTs that flow through)
- `docs/05-auth-security.md §6` — password policy (bcrypt cost 12, min 8 chars, no max <200, no complexity rules, no log-leak)
- `docs/05-auth-security.md §7` — email verification (32-byte hex token, 24h expiry, single-use, re-signup-on-unverified branch)
- `docs/05-auth-security.md §9.1` — account-enumeration policy (CRITICAL — every Phase-2 error response must obey this)
- `docs/05-auth-security.md §9.3` — CSRF mitigation (SameSite=Strict on refresh cookie, Origin check)
- `docs/05-auth-security.md §10` — 8 mandatory security tests (Phase 2 owns 4: #5, #6, #7, #8 IP+email leg)

### API contract
- `docs/04-api-spec.md §1` — conventions (UUIDs, ISO 8601, RFC 7807 errors)
- `docs/04-api-spec.md §2` — error contract (the JSON envelope shape — `code` at root, BL-01 unifies this)
- `docs/04-api-spec.md §3` — auth-service routes (5 endpoints, public except logout — verbatim contract for Phase 2)
- `docs/04-api-spec.md §6` — error code catalog (every code Phase 2 emits is here)
- `docs/04-api-spec.md §7` — rate limits (IP+email login leg lives here)
- `docs/04-api-spec.md §8` — CORS (gateway-level, but auth-service must not break CORS)

### Data model
- `docs/03-data-model.md §3.1` — `auth.users` table spec
- `docs/03-data-model.md §3.2` — `auth.email_verification_tokens` table spec
- `docs/03-data-model.md §2.1` — auth-service ERD
- `docs/03-data-model.md §4` — migration strategy (Flyway versioning, `db/migration/V2__create_users.sql`, `V3__create_email_verification_tokens.sql`; Phase 2 also adds `V4__create_refresh_tokens.sql` per `docs/05 §2`)
- `docs/03-data-model.md §5` — data lifecycle (cleanup expectations)

### Phase 1 carryover (must read for context — Phase 2 builds directly on these)
- `.planning/phases/01-api-gateway/01-CONTEXT.md` — full Phase-1 decision set; D-05 (login rate-limit split), D-16 (`AUTH_JWT_SECRET` env), D-19 (Zipkin trace continuity), D-12 (`_ping` controllers stay)
- `.planning/phases/01-api-gateway/01-REVIEW.md` BL-01 — RFC 7807 JSON-shape divergence between gateway and downstream; Phase 2 fixes it (D-14)
- `.planning/phases/01-api-gateway/01-PATTERNS.md` — Phase 1 established patterns (filter ordering, ProblemDetail envelope, SecurityContext usage)
- `.planning/phases/01-api-gateway/01-04-SUMMARY.md` — `ProblemDetailJacksonMixin` wiring on servlet stack (the pattern auth-service inherits)

### libs (already shipped in Phase 0/1 — Phase 2 consumes, doesn't modify)
- `libs/jwt-common/` — `JwtVerifier`, `JwtAutoConfiguration`, `JwtProperties` (`AUTH_JWT_SECRET`-backed), `JwtFixtures` testFixtures
- `libs/error-handling/` — `ProblemDetailFactory.of(...)` static helper + `ErrorCode` enum (Phase 2 expands the enum to cover the auth.* codes)
- `libs/observability/` — `MdcEnrichmentFilter` (servlet) + `ObservabilityAutoConfiguration` (auto-configured)
- `libs/api-contracts/` — `UserContext` record (Phase 2 doesn't modify; auth-service's controllers use `@AuthenticationPrincipal UserContext` to read the principal Phase 1 already populates)

### Pitfalls + risks
- `.planning/research/PITFALLS.md` Pitfall 1 — X-User-Id spoofing (Phase 1 mitigation; Phase 2 inherits and validates via security test #5/#6)
- `.planning/research/PITFALLS.md` Pitfall 7 — trace context lost at boundaries (Phase 2 must propagate MDC through `@Async` send — D-22)
- `.planning/research/PITFALLS.md` Pitfall 9 — Axios refresh-loop client-side (Phase 7 owns; Phase 2 server-side correctness via D-13 backs it up)
- `.planning/research/PITFALLS.md` (no specific Phase 2 server-side pitfall ID, but the cross-checklist warns: bcrypt cost <12 in any non-test profile, SameSite=Lax instead of Strict — both already locked here)

### Roadmap and requirements
- `.planning/ROADMAP.md` Phase 2 — success criteria #1–#5 + 4 explicit notes (jjwt 0.13.0, access-token-in-memory, bcrypt 12, scheduled cleanup)
- `.planning/REQUIREMENTS.md` — AUTH-01 (signup), AUTH-02 (verify), AUTH-03 (login + persist), AUTH-04 (logout); NFR-05 (coverage + 8 security ITs as merge gate)

### Stack and version pins
- `CLAUDE.md` — Spring Boot 3.5.x, jjwt 0.13.0, bcrypt cost 12, Spring Cloud 2025.0.x (D-30 from STATE.md), all DB tooling pinned via `gradle/libs.versions.toml`

### Existing code (Phase 0 + Phase 1 deliverables)
- `services/auth-service/build.gradle.kts` — already has `spring-boot-starter-web`, `data-jpa`, `actuator`, eureka-client, flyway-core + flyway-database-postgresql, postgresql jdbc, observability libs, spring-cloud BOM. **Phase 2 adds:** `spring-boot-starter-security`, `spring-boot-starter-mail`, `spring-boot-starter-validation`, `spring-boot-starter-data-redis`, `libs/jwt-common`, `libs/api-contracts` runtime project deps; `spring-security-test` for tests
- `services/auth-service/src/main/resources/application.yml` — Phase 0 base config (port 8081, DB/schema, Flyway history, JPA validate); **Phase 2 adds:** mail config block, redis connection, JWT secret binding, auth.cookie.secure profile-toggle, app.frontend.base-url, async executor config
- `services/auth-service/src/main/resources/db/migration/V1__init.sql` — empty baseline (intentional from Phase 0); **Phase 2 ships V2/V3/V4**
- `gradle/libs.versions.toml` — already has jjwt 0.13.0, spring-boot-starter-security alias, spring-security-test alias, h2 testRuntimeOnly. **Phase 2 adds:** `spring-boot-starter-mail`, `spring-boot-starter-validation`, `spring-boot-starter-data-redis` aliases (Spring Boot manages versions, no version refs needed)
- `infra/docker-compose.yml` — mailhog `:1025` SMTP loopback-bound, redis healthchecked. Phase 2 wires auth-service `depends_on: mailhog (started), redis (healthy)`

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets

- **`libs/jwt-common/JwtVerifier`** — already validates HS256 tokens; auth-service's new `JwtIssuer` MUST use the same `AUTH_JWT_SECRET` via `JwtProperties` so issued tokens verify cleanly through gateway and downstream filters.
- **`libs/jwt-common/JwtFixtures`** (testFixtures source set) — already mints arbitrary-claim JWTs for tests; Phase 2 ITs sign tokens with controlled `userId`/`email`/`ver` claims via this helper instead of running through `/login` for every test setup.
- **`libs/error-handling/ProblemDetailFactory.of(status, code, detail)`** — every Phase 2 error response goes through this. `ErrorCode` enum currently has 2 values (`AUTH_UNAUTHORIZED`, `AUTH_RATE_LIMITED`); Phase 2 expands to add `AUTH_EMAIL_ALREADY_REGISTERED`, `AUTH_INVALID_CREDENTIALS`, `AUTH_EMAIL_NOT_VERIFIED`, `AUTH_TOKEN_INVALID`, `AUTH_TOKEN_EXPIRED`, `AUTH_REFRESH_INVALID`, `AUTH_WEAK_PASSWORD`, `AUTH_INVALID_EMAIL`, `VALIDATION_FAILED` per `docs/04 §6`.
- **`libs/api-contracts/UserContext`** record (`userId`, `email`, `verified`) — auth-service's `@AuthenticationPrincipal` resolves to this; new `LoginResponse.user` and `/me` payload reuse the same shape.
- **`libs/observability/MdcEnrichmentFilter`** — already populates `traceId`/`spanId`/`requestId` in MDC for every servlet request; Phase 2's `@Async` task decorator (D-22) propagates this MDC into the email-send thread so Zipkin spans correlate.
- **Spring Boot auto-configured `ObjectMapper` with `ProblemDetailJacksonMixin`** — Phase 1's 01-04 SUMMARY established this. Phase 2 auth-service controllers DON'T construct `new ObjectMapper()`; just inject the auto-wired one (mirroring `ServletJwtCommonFilter`'s constructor pattern). Same fix applies to gateway emitters via D-14.

### Established Patterns

- **Servlet filter ordering (Phase 1 D-04):** `MdcEnrichmentFilter @ Integer.MIN_VALUE + 100` → `ServletJwtCommonFilter @ Integer.MIN_VALUE + 200` → controllers. Auth-service inherits both filters via library auto-config; logout endpoint authenticates via the JWT filter.
- **`@Tag("security")` ITs** — Phase 1 introduced this; Phase 2's 4 mandatory ITs use the same tag so the CI security-suite step continues to gate the merge.
- **Per-service Flyway history** (`auth_flyway_schema_history`) — already configured in `application.yml`; Phase 2 V2/V3/V4 migrations land into this table.
- **Hibernate `ddl-auto: validate`** — entities Phase 2 adds (`User`, `EmailVerificationToken`, `RefreshToken`) MUST match V2/V3/V4 migration columns exactly; Hibernate rejects on boot otherwise.
- **`gradle/libs.versions.toml` is the single source of truth** for backend versions (Convention C16) — Phase 2 adds aliases there, never inlines versions in `services/auth-service/build.gradle.kts`.
- **`junit-platform-launcher` testRuntimeOnly** (Phase 1 01-02 SUMMARY) — Phase 2 honors the same convention on auth-service's test source set.

### Integration Points

- **Gateway `/api/auth/**` routes** — already wired in Phase 1 with public-allowlist + IP-only rate limit on `/login`. Phase 2 just stands up the upstream service; no gateway route changes needed (the gateway routes statically to `http://auth-service:8081`).
- **Phase-1 `_ping` controllers in trip-service / destination-service** — stay; they still serve as runtime sanity checks. Phase 2 doesn't touch them.
- **Spring Security WebFlux config at gateway (`WebFluxSecurityConfig`)** — already permits `/api/auth/login`, `/signup`, `/verify`, `/refresh`. `/api/auth/logout` is `authenticated()` (downstream filter validates JWT). `/api/auth/me` (if shipped — Claude discretion) is `authenticated()`.
- **MailHog `:1025`** — auth-service's `JavaMailSender` connects to host `mailhog`, port `1025`, no auth, no STARTTLS, in dev/docker profiles.
- **Redis** — already in compose, healthchecked, used by gateway's `RedisRateLimiter`. Phase 2 reuses the same instance for the rate-limit counter (D-06) under the `rl:` keyspace prefix.
- **BL-01 fix files** (gateway-side, D-14): `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java`, `RateLimitProblemDetailFilter.java`, plus four IT files. Phase 2 includes a small wave of gateway changes alongside the auth-service work.

</code_context>

<specifics>
## Specific Ideas

- **Verification email link format:** `http://localhost:8080/api/auth/verify?token=<64hex>` (gateway URL). Backend handles the GET, sets `consumed_at`, then 302-redirects to `${app.frontend.base-url}/verify?status=success` (or `=invalid`/`=expired`). Token never reaches the FE.
- **Email subject:** `"Verify your Trip Planner account"` (plain). Body short — verification URL on its own line + 24-hour expiry note + sign-off. No marketing copy.
- **From-address:** `no-reply@tripplanner.local` with display name `"Trip Planner"`. Configurable via `app.mail.from` (`MAIL_FROM` env).
- **Rate-limit Redis keyspace:** `rl:login:fail:{ip}:{email_lower}` — extends the `rl:` namespace from Phase-1 D-06 cleanly; no collision risk with `SEARCH:` / `POI:` cache namespaces.
- **`/me` endpoint** (if shipped, recommended): `GET /api/auth/me` → `200 { "id": "<userId>", "email": "...", "emailVerified": true }` — reads the `UserContext` principal Phase 1's filter already populates.
- **Profiles in scope:** `application.yml` (default = dev), `application-docker.yml`, `application-test.yml` (new — overrides bcrypt cost to 4 via `@TestConfiguration`). No `prod` profile in v1.
- **Async executor config:** core 2, max 4, queue 50, name prefix `auth-async-`. One `TaskDecorator` for MDC propagation (D-22).
- **Cleanup job logging:** `INFO TokenCleanupJob: deleted {N} email_verification_tokens, {M} refresh_tokens (older than 7d past expiry)` — single line, parseable.
- **Phase-1 ROADMAP note** about success criterion #5: Phase 1's note already says "IP-only at gateway in this phase; full IP+email keying lands in Phase 2 within auth-service" (per 01-CONTEXT D-05). Phase 2's plan should validate it stays accurate when this phase closes.

</specifics>

<deferred>
## Deferred Ideas

- **Logout-everywhere endpoint** (`POST /api/auth/logout-all`) — kicks every device by revoking all of user's active refresh chains. Useful from a stolen-laptop standpoint. Deferred to v2 (UI surface to view active sessions also needed).
- **Email-only (no-IP) rate-limit layer** — for attackers rotating IPs through residential proxies. Defense-in-depth on top of D-05/D-06. Deferred to v2 hardening (or Phase 10 if scope permits).
- **Retry-After header on 429** — RFC 6585 §4 compliance. Small UX win in the SPA; not required by docs/04 catalog. Defer to v2.
- **Account-takeover notification email** — "someone tried to log into your account from a new IP". Requires reliable IP→geo resolution and email-deliverability infrastructure. Defer to v2.
- **HTML email templates** (Thymeleaf-rendered with branding) — Phase 9 polish. Phase 2 ships plain-text only and does NOT pre-extract a `VerificationEmailComposer` interface (YAGNI per the user's confirmed call).
- **Bounded retry on async email send** (Spring Retry, 3 tries exponential backoff) — useful for real SMTP providers, near-zero value with local MailHog. Defer to v2 if/when we point at SES/Mailgun.
- **JWT key rotation (two valid signing keys at once)** — `docs/05 §9.4` already flags v2.
- **ShedLock on `TokenCleanupJob`** — auth-service is single-instance per docs/02 §3, so not needed now. Add ShedLock note in v2 backlog only.
- **Password breach-list check** (haveibeenpwned k-anonymity) — `docs/05 §6` already flags v2.
- **Phase-2 stub of `/api/auth/me`** — recommended Claude's-discretion call (D's call, simple); if researcher decides against, defer to Phase 7 (FE decodes JWT).

</deferred>

---

*Phase: 2-Auth Service*
*Context gathered: 2026-05-09*
