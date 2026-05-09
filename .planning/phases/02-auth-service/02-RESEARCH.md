# Phase 2: Auth Service - Research

**Researched:** 2026-05-09
**Domain:** Hand-rolled email+password authentication on servlet Spring Boot 3.5 (signup → email verify → login → refresh-with-rotation → logout); IP+email Redis rate limit; 4 mandatory security ITs; BL-01 RFC 7807 contract fix on gateway.
**Confidence:** HIGH (most material is in-repo or in pinned official docs; a small number of decisions are Claude's-discretion design calls).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Email send + verification UX**

- **D-01 (Async send):** Verification email sent via `@Async` on a dedicated `ThreadPoolTaskExecutor` (core 2, max 4, queue 50). `AuthService.signup()` writes the `users` row + `email_verification_tokens` row in a single `@Transactional`, returns `201 {userId}` synchronously, then publishes a Spring `ApplicationEvent` (or directly invokes the async sender bean). Signup response time stays sub-200ms regardless of SMTP latency.
- **D-02 (Verification link target):** Email link is `http://localhost:8080/api/auth/verify?token=…` (gateway URL). Backend consumes the token, then **302-redirects** to `http://localhost:5173/verify?status=success` (or `=invalid` / `=expired`). Frontend redirect base URL is configurable via `app.frontend.base-url` env (`FRONTEND_BASE_URL`, default `http://localhost:5173`).
- **D-03 (Email body):** Plain-text only via `SimpleMailMessage`. Subject `"Verify your Trip Planner account"`. Body short, no template engine. From-address `no-reply@tripplanner.local` (configurable via `app.mail.from`, env `MAIL_FROM`); display name `"Trip Planner"`.
- **D-04 (SMTP failure handling):** Async sender catches `MailException`, logs `WARN` with `userId`, `traceId`, exception class — never the email body or token. Token stays in DB until 24h TTL or daily cleanup. Re-signup-on-unverified (D-23) is the recovery path. No retry policy, no dead-letter table.

**Login rate limit (IP+email leg)**

- **D-05 (Location):** Check happens inside `AuthService.login(LoginRequest, ipAddress)` **before** the bcrypt-12 verify. Resolve IP via `HttpServletRequest.getHeader("X-Forwarded-For")` first token; falls back to `getRemoteAddr()` for tests.
- **D-06 (Backing store):** Redis. Key shape `rl:login:fail:{ip}:{email_lower}`. Operations: `INCR` then `EXPIRE 900` on first hit; check `> 5`. Add `spring-boot-starter-data-redis` (servlet flavor — auth-service is servlet-stack).
- **D-07 (Counting model):** Failed attempts only. Successful login DELETEs the key. Matches `docs/05-auth-security.md §10` test #8 phrasing literally.
- **D-08 (Limit-tripped response):** `ProblemDetail` 429 with `code=auth.rate_limited`. Generic detail: `"Too many attempts. Please try again later."` No `Retry-After` header (deferred to v2). Envelope shape matches gateway's after BL-01 fix lands (D-14).
- **D-09 (Email-key normalization):** `email.toLowerCase().trim()` before composing the Redis key, AND lowercase-store on signup.

**Refresh-token rotation reuse + logout scope**

- **D-10 (Replay revocation scope):** Replay of an already-rotated token → walk `rotated_to` chain (back to root, forward to head), mark `revoked_at = NOW()` on every row. WARN log: `userId`, chain head hash (first 8 chars), client IP, `traceId`. Other devices/independent chains for the same user untouched.
- **D-11 (Logout scope):** `POST /api/auth/logout` reads the `refresh_token` cookie, hashes it (SHA-256), looks up the chain, marks the chain head `revoked_at = NOW()`, clears the cookie via `Set-Cookie: refresh_token=; Max-Age=0`. Bearer JWT valid but cookie missing: still return `204` (idempotent).
- **D-12 (Cookie scope):** `Set-Cookie: refresh_token=<value>; Path=/api/auth; HttpOnly; SameSite=Strict; Max-Age=604800; Secure=<profile>`. `Secure` flag toggled via `app.auth.cookie.secure` profile property — `false` in dev/test, `true` in any future deployed profile.
- **D-13 (Concurrent-refresh):** `RefreshService.rotate(rawToken)` runs in `@Transactional(isolation = REPEATABLE_READ)` and does `SELECT … FROM auth.refresh_tokens WHERE token_hash = ? FOR UPDATE` as the first step. Concurrent rotations serialize on the row lock; second arrival sees `rotated_to IS NOT NULL` and falls into the replay branch.

**BL-01 fix + Phase 2 security-test slice**

- **D-14 (BL-01 fix in this phase):** Inject Spring Boot's auto-configured `ObjectMapper` (with `ProblemDetailJacksonMixin`) into both gateway emitters — `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java` and `RateLimitProblemDetailFilter.java`. Update the four affected ITs (`GatewayMissingAuthHeaderIT`, `GatewayForgedJwtIT`, `GatewayProblemDetailRenderingIT`, `LoginRateLimiterIT`) to assert `$.code` instead of `$.properties.code`.
  *NOTE during research: cross-checking the Phase-1 source files showed both gateway emitters now ALREADY constructor-inject `ObjectMapper` — see `ProblemDetailAuthEntryPoint:31` and `RateLimitProblemDetailFilter:50`. The remaining BL-01 work is therefore the IT-side jsonPath update only. See `## Code Examples → BL-01 fix actual diff scope` below.*
- **D-15 (Mandatory security ITs Phase 2 owns):** Phase 2 ships, with `@Tag("security")`, integration tests for: `DeletedRefreshTokenCannotBeUsed` (#5), `RotatedRefreshTokenCannotBeReused` (#6), `EmailNotVerifiedCannotLogin` (#7), `LoginRateLimitFailedAttemptsTriggers` (#8 IP+email leg), PLUS happy paths covering ROADMAP SC#1–#4, PLUS BL-01 contract tests asserting `$.code` at root.
- **D-16 (Tests deferred):** `CrossUserTripAccessReturns404` (#3) → Phase 5; `CrossUserItemPatchReturns404` (#4) → Phase 6. Final NFR-05 audit → Phase 10. NO `@Disabled` shells in Phase 2.

**Cross-cutting**

- **D-17 (Token cleanup scheduler):** `@EnableScheduling` bean `TokenCleanupJob` with `@Scheduled(cron = "0 0 2 * * *")` (daily 02:00 UTC). Two SQL deletes; rows older than `expires_at < NOW() - INTERVAL '7 days'`. Logs row counts at INFO with traceId. No ShedLock.
- **D-18 (Bean Validation):** `SignupRequest`/`LoginRequest` records annotated `@Email @NotBlank @Size(max=254)` and `@NotBlank @Size(min=8, max=200)`. `@ControllerAdvice` (extends `ResponseEntityExceptionHandler`) maps `MethodArgumentNotValidException` to `auth.invalid_email` (email field) or `auth.weak_password` (password field). Generic detail messages — no field-name leak. Other catalog codes (`auth.email_already_registered`, `auth.invalid_credentials`, `auth.email_not_verified`, `auth.token_invalid`, `auth.token_expired`, `auth.refresh_invalid`) ride the same advice from custom service exceptions.
- **D-19 (BCrypt encoder):** `@Bean PasswordEncoder bcrypt() { return new BCryptPasswordEncoder(12); }` in `SecurityConfig`. `application-test.yml` overrides via a `@TestConfiguration` returning `new BCryptPasswordEncoder(4)`.
- **D-20 (JWT issuance):** `JwtIssuer` (libs/jwt-common OR auth-service-local — researcher decides) signs HS256 access tokens with claims: `iss=tripplanner-auth`, `sub=userId`, `iat`, `exp=iat+900s`, `email`, `ver=email_verified`, `jti=UUID`. Loads `AUTH_JWT_SECRET` via existing `JwtProperties`.
- **D-21 (Service-layer @Transactional boundaries):** `signup`/`verify`/`login`/`refresh`/`logout` all `@Transactional` REQUIRED. `refresh` has `isolation = REPEATABLE_READ` per D-13. Email sending (`@Async`) runs OUTSIDE the signup transaction.
- **D-22 (X-Request-Id + MDC through @Async):** `@Async` thread pool decorated with `TaskDecorator` that copies the calling thread's MDC. Async send logs carry the originating signup's `traceId`/`requestId`/`userId`.
- **D-23 (Re-signup of unverified account):** Detect existing-but-unverified user in `AuthService.signup()`; mint a fresh verification token (mark prior unconsumed tokens `consumed_at = NOW()`); send fresh email; return same opaque `201 {userId: <existing>}`. Concurrent re-signup races collapse on the unique `users.email` constraint.

### Claude's Discretion (researcher recommends below in `## Discretionary Decisions`)

- Whether `JwtIssuer` lives in `libs/jwt-common` (alongside `JwtVerifier`) or auth-service-local
- Exact copy of the verification email body (subject + body text)
- Whether to ship a tiny static `verify-success.html` page in auth-service for the redirect target
- Whether `/api/auth/me` endpoint is shipped in Phase 2 or whether the SPA decodes the JWT client-side
- Field-level naming inside DTOs (`SignupRequest`, `LoginResponse`, `RefreshResponse`)
- Whether `TokenCleanupJob` DELETEs are wrapped in a single transaction or two

### Deferred Ideas (OUT OF SCOPE)

- `POST /api/auth/logout-all` (kicks every device by revoking all of user's active refresh chains) — v2
- Email-only (no-IP) rate-limit layer — v2 hardening
- `Retry-After` header on 429 — v2
- Account-takeover notification email — v2
- HTML email templates (Thymeleaf-rendered) — Phase 9 polish; do NOT pre-extract a `VerificationEmailComposer` interface (YAGNI per user)
- Bounded retry on async email send (Spring Retry) — v2
- JWT key rotation (two valid signing keys at once) — `docs/05 §9.4` v2
- ShedLock on `TokenCleanupJob` — v2 (auth-service is single-instance)
- Password breach-list check (haveibeenpwned k-anonymity) — `docs/05 §6` v2
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | User can sign up with a unique email and password (min 8 chars). Generic error messages used to prevent account enumeration. | `## Architecture Patterns → Pattern 1: Signup flow with async email`; `## Code Examples → SignupRequest + AuthController.signup`; `## Don't Hand-Roll → bcrypt + Spring Validation`; D-01/D-03/D-09/D-18/D-19/D-23 |
| AUTH-02 | User receives a verification email containing a unique 24-hour link; clicking the link activates the account. Account cannot log in before verification. | `## Architecture Patterns → Pattern 2: Verification token + 302-redirect`; `## Code Examples → AuthController.verify (302 redirect)`; `## Common Pitfalls → P3 (token enumeration via timing)`; D-02/D-04/D-23 |
| AUTH-03 | User can log in with email and password and stay signed in across page refreshes; bad credentials return a generic error. | `## Architecture Patterns → Pattern 3: Login + JWT issuance + refresh cookie`; `## Code Examples → JwtIssuer.issueAccess`, `RefreshTokenService.create`; D-05/D-08/D-09/D-12/D-19/D-20 |
| AUTH-04 | User can log out, ending the session immediately and blocking access to authenticated routes. | `## Architecture Patterns → Pattern 4: Refresh rotation with SELECT FOR UPDATE`; `## Code Examples → RefreshService.rotate, AuthController.logout`; D-10/D-11/D-13/D-21 |
| NFR-05 | Backend service-layer line coverage ≥ 70%; auth + ownership-check paths achieve 100% branch coverage; 8 mandatory security integration tests gate every PR. | `## Validation Architecture` (4 of 8 ITs in this phase) + `## Code Examples → @Tag("security") IT skeleton`; D-15/D-16. Final coverage audit deferred to Phase 10. |
</phase_requirements>

## Summary

Phase 2 is a tight, well-bounded build on top of a Phase 0/1 monorepo that already ships every shared lib auth-service needs (`libs/jwt-common`, `libs/error-handling`, `libs/api-contracts`, `libs/observability`). The platform decisions are all locked in CONTEXT.md (D-01..D-23). The remaining work is implementation-mechanics:

1. **Three Flyway migrations** (V2 users, V3 email_verification_tokens, V4 refresh_tokens) honouring the per-service `auth_flyway_schema_history` config Phase 0 wired.
2. **Five servlet endpoints** through Spring Security with `@AuthenticationPrincipal UserContext` already populated by `ServletJwtCommonFilter` (Phase 1) — only `/logout` (and discretionary `/me`) are authenticated.
3. **Three new component classes** that sign things: `JwtIssuer` (HS256 access token via jjwt 0.13.0 modern builder), `RefreshTokenService` (32-byte hex + SHA-256 storage + `@Lock(PESSIMISTIC_WRITE)` rotation), `EmailVerificationService` (32-byte hex token + 24h TTL + `consumed_at` single-use semantics).
4. **Three cross-cutting plumbing pieces:** Redis `INCR`+`EXPIRE` rate-limit (Lua-script for atomicity is recommended), `@Async` `ThreadPoolTaskExecutor` with `TaskDecorator` for MDC propagation, `@EnableScheduling` `TokenCleanupJob`.
5. **Four mandatory `@Tag("security")` integration tests** plus three happy-path ITs and the BL-01 contract test rewrites.
6. **One small gateway-side change** (BL-01 IT updates only — the production code is already fixed in Phase 1, see verification note in `<user_constraints>` above).

**Primary recommendation:** Ship the phase as **5 plan files, ~17 tasks, in 5 waves** (test infra → migrations → JWT issuer + persistence → service + controller layer → ITs + BL-01 IT updates). Use jjwt 0.13.0 modern builder API only (Convention C27-P1), inject Spring's auto-configured `ObjectMapper` everywhere, put `JwtIssuer` in `libs/jwt-common` (consistency with `JwtVerifier`), do NOT ship `/me` (the SPA already has `email`/`ver` in the JWT payload — adds 0 value), do NOT ship `verify-success.html` (browser shows whatever the FE renders; in dev with FE off, the 302 just lands on a 5173-not-running ECONNREFUSED, perfectly acceptable for demo). Use Lua script for the Redis rate-limit `INCR`+`EXPIRE` to make it atomic. Use a Postgres+Redis `@ServiceConnection` Testcontainers harness for the 4 security ITs.

## Architectural Responsibility Map

Phase 2 capabilities and their tier owners:

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Password hashing (bcrypt cost 12) | API/Backend (auth-service) | — | NEVER browser; secret material lives server-side |
| JWT signing (HS256) | API/Backend (auth-service) | — | Owns `AUTH_JWT_SECRET`; gateway/downstream only verify |
| JWT verification (downstream re-validate) | API/Backend (gateway + trip + destination) | — | Phase 1 already shipped; Phase 2 produces tokens these consume |
| Refresh-token storage + rotation | Database (auth schema) + API/Backend | — | Persistent state; row-level lock for concurrent refresh safety |
| Verification token generation + storage | Database + API/Backend | — | Same persistence pattern; 24h TTL |
| Async email send | API/Backend (auth-service `@Async` pool) | — | Servlet stack; SMTP I/O bound; off the request path |
| Login rate-limit counter | Cache/Storage (Redis) | API/Backend (check happens in service) | Counter and TTL belong in Redis; the decision belongs in `AuthService.login` per D-05 |
| Verification redirect (302 → frontend) | API/Backend (HTTP redirect) | Browser/Client (renders `?status=` page) | Backend never serves HTML; frontend renders status |
| Daily token cleanup | API/Backend (`@Scheduled`) | Database | Single-instance scheduled job; deletes rows |
| RFC 7807 error envelope | API/Backend (`@ControllerAdvice`) + Gateway | — | Locked: `code` at root via auto-configured `ObjectMapper` mixin |
| MDC propagation through `@Async` | API/Backend (`TaskDecorator`) | — | Servlet thread-local copy/restore; same philosophy as `MdcEnrichmentFilter` |

**Tier-misassignment guard:** Nothing in Phase 2 belongs in the browser tier. The frontend's only Phase-2 touchpoint is rendering `?status=success/invalid/expired` after the 302 (Phase 7 owns that). Do NOT plan a static HTML page in auth-service.

## Standard Stack

### Core (already pinned in `gradle/libs.versions.toml`)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.14 | Servlet auth-service runtime | Locked; SB BOM manages transitive versions [VERIFIED: gradle/libs.versions.toml:16] |
| Spring Cloud | 2025.0.2 | Eureka client only | Locked (D-30); auth-service uses Eureka for dashboard, not for routing [VERIFIED: gradle/libs.versions.toml:23] |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.13.0 | HS256 sign + verify | Convention C27-P1: modern builder API only; Phase 1 wires `JwtVerifier`, Phase 2 adds `JwtIssuer` [VERIFIED: gradle/libs.versions.toml:36] |
| Flyway | 10.21.0 + `flyway-database-postgresql` | DB migrations V2/V3/V4 | **Pitfall A:** Flyway 10 modularized PG support — `flyway-database-postgresql` MUST be `runtimeOnly` or service fails to start with "Unsupported Database: PostgreSQL 16.x". Already wired in `services/auth-service/build.gradle.kts:26` [VERIFIED] |
| PostgreSQL JDBC | 42.7.4 | Connection driver | Locked [VERIFIED: gradle/libs.versions.toml:28] |
| Hibernate (via SB BOM) | 6.6.x | JPA entities for `User`, `EmailVerificationToken`, `RefreshToken` | `ddl-auto: validate` is enforced (Convention C15) — entities MUST match migrations exactly [VERIFIED: docs/02-architecture.md + application.yml] |

### Phase 2 additions to `libs.versions.toml` (NEW catalog aliases)

All four are **version-managed by Spring Boot 3.5.14 BOM** — no version refs needed:

| Library | Catalog alias to ADD | Purpose | Why Standard |
|---------|---------------------|---------|--------------|
| `org.springframework.boot:spring-boot-starter-security` | `spring-boot-starter-security` | `@EnableWebSecurity` + `BCryptPasswordEncoder` + `SecurityFilterChain` for `/api/auth/**` | Already in catalog (Phase 1 line 84) — auth-service `build.gradle.kts` adds the implementation dep |
| `org.springframework.boot:spring-boot-starter-mail` | `spring-boot-starter-mail` | `JavaMailSender` + `SimpleMailMessage` to MailHog `:1025` | Idiomatic — `spring.mail.host` auto-configures the bean [CITED: docs.spring.io/spring-boot/reference/io/email.html] |
| `org.springframework.boot:spring-boot-starter-validation` | `spring-boot-starter-validation` | `@Email`, `@Size`, `@NotBlank` on request records | Pulls Hibernate Validator transitively [CITED: Spring Boot 3.5 ref] |
| `org.springframework.boot:spring-boot-starter-data-redis` | `spring-boot-starter-data-redis` | `StringRedisTemplate` for `INCR`+`EXPIRE` rate-limit counter | **Servlet variant** — auth-service is servlet-stack (not WebFlux); the existing `spring-boot-starter-data-redis-reactive` alias on line 85 is for the api-gateway only. Add the servlet alias as a NEW row. Lettuce client is the SB default [VERIFIED: catalog has `spring-boot-starter-data-redis-reactive` only] |

### Existing testing stack (already in catalog — Phase 2 reuses)

| Library | Version | Phase 2 use |
|---------|---------|-------------|
| `spring-boot-starter-test` | SB-managed | `@SpringBootTest`, `MockMvc`, JsonPath, AssertJ |
| `spring-security-test` | SB-managed | `@WithMockUser`, `RequestPostProcessor` |
| `spring-boot-testcontainers` | SB-managed | `@ServiceConnection` PG + Redis |
| `testcontainers-junit-jupiter` | 1.21.0 | `@Testcontainers` lifecycle |
| `h2` | testRuntimeOnly | NOT used by Phase 2 — auth-service ITs need real Postgres for `SELECT FOR UPDATE` semantics + Flyway. H2 stays for the gateway-side BL-01 IT updates only. |

### Phase 2 additions for tests

| Library | Catalog alias to ADD | Purpose |
|---------|---------------------|---------|
| `org.testcontainers:postgresql` | `testcontainers-postgresql` | `PostgreSQLContainer` for ITs that exercise refresh-rotation row lock |
| GreenMail (recommended) — `com.icegreen:greenmail-junit5:2.x` | `greenmail-junit5` (NEW pin needed; not in SB BOM) | In-process SMTP for `EmailVerificationServiceIT` so we don't need MailHog at test time. Prefer over Testcontainers MailHog (faster, no Docker dep in unit-test CI lane) [CITED: greenmail-mail-test.github.io] |

> **Note on GreenMail version pin:** GreenMail is not Spring-managed. Pin `2.x` (current branch). Add to `[versions]` block in `libs.versions.toml`. **Action: Verify `npm view`-equivalent — `gradle dependencies | grep greenmail` after add — and confirm 2.x is the latest stable major before the planner sets the pin.**

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Lettuce (default in spring-boot-starter-data-redis) | Jedis | Lettuce is the SB default; Jedis is single-thread blocking. Lettuce's `StringRedisTemplate` is sufficient for `INCR`+`EXPIRE`. Stick with Lettuce. |
| jjwt 0.13.0 | Nimbus JOSE (already on classpath via Spring Security OAuth2-resource-server transitive) | The user explicitly chose jjwt for portfolio simplicity per `docs/05 §2`. Nimbus would be a stack amendment. |
| BCrypt | Argon2id | Argon2 is stronger; BCrypt is locked at cost 12 in `docs/05 §6`. No deviation. |
| GreenMail in tests | Testcontainers MailHog | GreenMail is in-process (faster CI). Testcontainers MailHog requires Docker for unit-test CI lanes. Use GreenMail. |
| `@Scheduled` cron | Quartz / ShedLock | Auth-service is single-instance per `docs/02 §3` — no need for cluster-aware scheduling. Quartz is overkill. ShedLock noted in v2 backlog only. |
| Redis `INCR`+`EXPIRE` non-atomic | Lua `EVAL` script | The non-atomic form has a documented race: a client can `INCR` then crash before `EXPIRE`, leaving an immortal counter. Recommend Lua. [CITED: redis.io/docs/latest/commands/incr/, dev.to silentwatcher_95] |

### Installation (additions to existing `services/auth-service/build.gradle.kts`)

```kotlin
// Phase 2 ADDS (existing 8 deps stay):
implementation(project(":libs:jwt-common"))                  // JwtVerifier + JwtFixtures + (new) JwtIssuer
implementation(project(":libs:api-contracts"))               // UserContext (already implicit via jwt-common api())
implementation(libs.spring.boot.starter.security)            // SecurityFilterChain, BCryptPasswordEncoder
implementation(libs.spring.boot.starter.mail)                // JavaMailSender, SimpleMailMessage  [NEW catalog alias]
implementation(libs.spring.boot.starter.validation)          // @Email/@NotBlank/@Size              [NEW catalog alias]
implementation(libs.spring.boot.starter.data.redis)          // StringRedisTemplate                 [NEW catalog alias — servlet variant]

testImplementation(libs.spring.security.test)
testImplementation(testFixtures(project(":libs:jwt-common"))) // JwtFixtures
testImplementation(libs.testcontainers.postgresql)            // [NEW catalog alias]
testImplementation(libs.testcontainers.junit.jupiter)         // already present
testImplementation(libs.greenmail.junit5)                     // [NEW catalog alias]
testRuntimeOnly("org.junit.platform:junit-platform-launcher") // Convention from Phase 1 01-02 SUMMARY
```

**Version verification:** `[VERIFIED: gradle/libs.versions.toml]` — Spring Boot 3.5.14 BOM manages all `spring-boot-starter-*` versions transitively; jjwt 0.13.0 is current stable per Maven Central [CITED: github.com/jwtk/jjwt/releases].

## Architecture Patterns

### System Architecture Diagram

```
                         ┌─────────────────────────────────────────────────────┐
                         │                  api-gateway :8080                  │
                         │  /api/auth/{signup,verify,login,refresh,logout}     │
                         │  static URI route → http://auth-service:8081        │
                         │  IP-only login rate-limit (Phase 1) +               │
                         │  IP+email leg fires INSIDE auth-service (Phase 2)   │
                         └────────────────────────┬────────────────────────────┘
                                                  │
                                                  ▼
  ┌──────────────────────────────────────────────────────────────────────────────┐
  │                         auth-service :8081  (servlet)                         │
  │                                                                                │
  │  MdcEnrichmentFilter (libs/observability) ─▶ ServletJwtCommonFilter (logout)  │
  │                              │                       │                         │
  │                              ▼                       ▼                         │
  │  ┌────────────┐    ┌─────────────────┐    ┌──────────────────────────────┐   │
  │  │  Auth      │    │   Spring        │    │  AuthControllerAdvice        │   │
  │  │  Controller│───▶│   Validation    │───▶│  (RFC 7807 ProblemDetail)    │   │
  │  │  (5 routes)│    │   (Hibernate)   │    └──────────────────────────────┘   │
  │  └─────┬──────┘    └─────────────────┘                                        │
  │        │                                                                       │
  │        ▼                                                                       │
  │  ┌──────────────────────────────────────────────────────────────────────┐   │
  │  │                         AuthService                                    │   │
  │  │   signup() ─┬── VerificationTokenRepo (V3 schema) ─────┐              │   │
  │  │             ├── UserRepo (V2 schema, @Transactional)   │              │   │
  │  │             └── ApplicationEventPublisher ─────┐        │              │   │
  │  │                                                 │        │              │   │
  │  │   login() ─── RateLimiter.checkAndRecord ─── Redis (INCR+EXPIRE Lua)  │   │
  │  │             ├── BCryptPasswordEncoder (cost 12)                        │   │
  │  │             └── JwtIssuer.issueAccess + RefreshTokenService.create    │   │
  │  │                                                                        │   │
  │  │   refresh() ── @Tx(REPEATABLE_READ) + @Lock(PESSIMISTIC_WRITE) row lock│   │
  │  │             ├── chain-replay revocation walk (D-10)                    │   │
  │  │             └── JwtIssuer.issueAccess + new RefreshTokenService       │   │
  │  │                                                                        │   │
  │  │   logout() ── SHA-256(cookie) → mark revoked_at on chain head         │   │
  │  └──────────────┬─────────────────────────────────────────┬────────────┘   │
  │                 ▼                                          ▼                 │
  │        ┌─────────────────────┐                ┌─────────────────────────┐  │
  │        │ @Async sender       │  ApplicationEvent  │ TokenCleanupJob       │  │
  │        │ (TaskDecorator MDC) │ ◀──────────────────│ @Scheduled(cron 02:00)│  │
  │        │ JavaMailSender      │                    └─────────────────────────┘  │
  │        └──────────┬──────────┘                                                  │
  └────────────────── │ ─────────────────────────────────────────────────────────┘
                     ▼
            ┌─────────────────┐                ┌─────────────────────────┐
            │ MailHog :1025   │                │ Postgres :5432          │
            │ (compose)       │                │ schema=auth (per-svc    │
            └─────────────────┘                │   user, init.sql)       │
                                               │   V2 users              │
                                               │   V3 email_verif_tokens │
                                               │   V4 refresh_tokens     │
                                               └─────────────────────────┘
                                                          ▲
                                                          │
                                               ┌─────────────────────────┐
                                               │ Redis :6379             │
                                               │ rl:login:fail:{ip}:{em} │
                                               │ TTL 900s                │
                                               └─────────────────────────┘
```

### Component Responsibilities

| Component | File | Role |
|-----------|------|------|
| `AuthController` | `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java` | 5 endpoints; thin — delegates to `AuthService`. Authenticated only on `/logout` (and `/me` if shipped). |
| `AuthService` | `services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java` | Orchestrates signup/verify/login/refresh/logout. Holds `@Transactional` boundaries (D-21). |
| `JwtIssuer` | **`libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java`** (recommended) | HS256 signing via jjwt 0.13.0 modern builder; consumes the same `JwtProperties` as `JwtVerifier`. |
| `RefreshTokenService` | `services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java` | Generates 32-byte hex token, SHA-256 hash for storage, rotation with `@Lock(PESSIMISTIC_WRITE)` (D-13), chain-replay revocation walk (D-10). |
| `EmailVerificationService` | `services/auth-service/src/main/java/com/tripplanner/auth/service/EmailVerificationService.java` | Mints 32-byte hex token, 24h TTL, single-use `consumed_at`, re-signup invalidation (D-23). |
| `LoginRateLimiter` | `services/auth-service/src/main/java/com/tripplanner/auth/service/LoginRateLimiter.java` | Lua-script `INCR`+`EXPIRE` against Redis; key `rl:login:fail:{ip}:{email_lower}` (D-06/D-09). |
| `EmailVerificationSender` | `services/auth-service/src/main/java/com/tripplanner/auth/email/EmailVerificationSender.java` | `@Async` listener of `VerificationEmailRequestedEvent`; uses `JavaMailSender` + `SimpleMailMessage`. |
| `MdcCopyingTaskDecorator` | `libs/observability/src/main/java/com/tripplanner/observability/MdcCopyingTaskDecorator.java` (NEW — recommend libs to keep `@Async` MDC pattern shared) | Captures `MDC.getCopyOfContextMap()` at submission; restores in worker, clears in `finally`. |
| `TokenCleanupJob` | `services/auth-service/src/main/java/com/tripplanner/auth/scheduling/TokenCleanupJob.java` | `@Scheduled(cron = "0 0 2 * * *")` — DELETEs expired rows from `email_verification_tokens` + `refresh_tokens`. |
| `AuthControllerAdvice` | `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java` | `@ControllerAdvice extends ResponseEntityExceptionHandler`; maps validation failures + custom service exceptions to RFC 7807 `ProblemDetail`. |
| `SecurityConfig` | `services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java` | `@EnableWebSecurity` + `SecurityFilterChain`: permitAll on signup/verify/login/refresh; authenticated on logout (+/me). `BCryptPasswordEncoder(12)` bean. |
| `AsyncConfig` | `services/auth-service/src/main/java/com/tripplanner/auth/config/AsyncConfig.java` | `@EnableAsync`; defines `ThreadPoolTaskExecutor` "authAsyncExecutor" (core 2, max 4, queue 50, name `auth-async-`) with `MdcCopyingTaskDecorator`. |
| `SchedulingConfig` | `services/auth-service/src/main/java/com/tripplanner/auth/config/SchedulingConfig.java` | `@EnableScheduling` (or fold this annotation onto the main `AuthServiceApplication` class — single-line equivalent). |

### Recommended Project Structure

```
services/auth-service/src/main/java/com/tripplanner/auth/
├── AuthServiceApplication.java       # @SpringBootApplication + @EnableAsync + @EnableScheduling
├── api/
│   ├── AuthController.java           # 5 endpoints + (optional) /me
│   ├── AuthControllerAdvice.java     # @ControllerAdvice → RFC 7807
│   └── dto/
│       ├── SignupRequest.java        # record + Bean Validation
│       ├── LoginRequest.java
│       ├── LoginResponse.java
│       └── RefreshResponse.java
├── service/
│   ├── AuthService.java              # signup/verify/login/refresh/logout (Tx boundaries)
│   ├── EmailVerificationService.java
│   ├── RefreshTokenService.java
│   ├── LoginRateLimiter.java         # Redis Lua INCR+EXPIRE
│   └── exception/
│       ├── EmailAlreadyRegisteredException.java
│       ├── InvalidCredentialsException.java
│       ├── EmailNotVerifiedException.java
│       ├── TokenInvalidException.java
│       ├── TokenExpiredException.java
│       ├── RefreshInvalidException.java
│       └── LoginRateLimitedException.java
├── domain/                           # JPA entities matching V2/V3/V4 migrations
│   ├── User.java
│   ├── EmailVerificationToken.java
│   └── RefreshToken.java
├── repository/
│   ├── UserRepository.java
│   ├── EmailVerificationTokenRepository.java
│   └── RefreshTokenRepository.java   # @Lock(PESSIMISTIC_WRITE) on findByTokenHashForUpdate
├── email/
│   ├── EmailVerificationSender.java  # @Async @EventListener
│   └── VerificationEmailRequestedEvent.java
├── scheduling/
│   └── TokenCleanupJob.java          # @Scheduled(cron …)
├── security/
│   └── SecurityConfig.java           # @EnableWebSecurity SecurityFilterChain + BCrypt bean
└── config/
    ├── AsyncConfig.java              # @EnableAsync + executor bean + TaskDecorator
    └── AuthProperties.java           # @ConfigurationProperties("app.auth") — cookie.secure, frontend.base-url, mail.from

services/auth-service/src/main/resources/db/migration/
├── V1__init.sql                      # already shipped (empty baseline)
├── V2__create_users.sql              # NEW
├── V3__create_email_verification_tokens.sql  # NEW
└── V4__create_refresh_tokens.sql     # NEW

services/auth-service/src/test/java/com/tripplanner/auth/
├── api/AuthControllerIT.java                   # happy paths (signup→verify→login)
├── api/AuthControllerAdviceIT.java             # RFC 7807 envelope tests + BL-01 contract
├── security/EmailNotVerifiedCannotLoginIT.java # @Tag("security") #7
├── security/DeletedRefreshTokenCannotBeUsedIT.java   # @Tag("security") #5
├── security/RotatedRefreshTokenCannotBeReusedIT.java # @Tag("security") #6
├── security/LoginRateLimitFailedAttemptsTriggersIT.java # @Tag("security") #8 IP+email leg
└── support/
    ├── AuthIntegrationTestBase.java  # @SpringBootTest + Testcontainers PG + Redis + GreenMail
    └── TestSecurityConfig.java       # @TestConfiguration: BCryptPasswordEncoder(4)
```

### Pattern 1: Signup flow with async email send

**What:** Synchronous transactional write of `users` + `email_verification_tokens`, then publish a Spring `ApplicationEvent` consumed by an `@Async @EventListener`. Signup HTTP returns `201 {userId}` regardless of SMTP latency.

**When to use:** Any I/O-bound side-effect that must NOT block the request thread (D-01).

**Why event over direct async call:** Eliminates the "self-invocation `@Async` doesn't work" footgun (Spring AOP only intercepts proxied calls — calling `this.sendEmail()` from inside `signup()` skips the proxy). The `ApplicationEventPublisher.publishEvent(...)` indirection forces a fresh proxy hop.

```java
// Source: docs/05-auth-security.md §3 (sequence), CONTEXT.md D-01, D-23
@Service
public class AuthService {
    private final ApplicationEventPublisher events;
    // ... other deps

    @Transactional  // D-21
    public SignupResult signup(SignupRequest req) {
        String emailLower = req.email().toLowerCase().trim();  // D-09

        // D-23: detect existing-but-unverified, mint fresh token
        Optional<User> existing = userRepo.findByEmail(emailLower);
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.isEmailVerified()) {
                // 9.1 enumeration policy: return same opaque 201 — DO NOT throw email_already_registered
                // ⚠️ docs/04 §6 lists auth.email_already_registered as 409, but docs/05 §9.1 overrides:
                //   "Signup of an existing email returns the same response shape as success"
                // Reconcile: only THROW the 409 when the existing account is verified AND we want to
                // surface registration to the user. Per locked CONTEXT D-23, this branch returns 201.
                return new SignupResult(u.getId(), false /* no email sent — already verified */);
            }
            // Unverified branch: invalidate prior tokens, mint fresh one
            verifTokenRepo.markAllUnconsumedAsConsumedFor(u.getId());
            String tok = verificationService.mintFor(u);
            events.publishEvent(new VerificationEmailRequestedEvent(u.getEmail(), tok));
            return new SignupResult(u.getId(), true);
        }

        // True new account
        String hash = passwordEncoder.encode(req.password());  // bcrypt 12 (D-19)
        User u = new User(UUID.randomUUID(), emailLower, hash, false /* email_verified */);
        userRepo.save(u);
        String tok = verificationService.mintFor(u);
        events.publishEvent(new VerificationEmailRequestedEvent(u.getEmail(), tok));
        return new SignupResult(u.getId(), true);
    }
}
```

**Open contract question for planner:** `docs/05 §9.1` (account-enumeration policy) and `docs/04 §6` (`auth.email_already_registered` 409) APPEAR to contradict each other. The CONTEXT D-23 wording — "return same opaque `201 {userId}` envelope" — resolves the contradiction in favor of §9.1 for the *unverified* branch only. For an existing **verified** account, we have two options:

- **Option A (strict §9.1):** Always 201 (never reveal). Drop `auth.email_already_registered` from the catalog. Cleaner from an enumeration standpoint.
- **Option B (catalog wording):** 409 `auth.email_already_registered` for the verified branch only. Modest enumeration leak (an attacker can distinguish verified-existing from new-or-unverified) but matches the published catalog.

**Recommendation: Option A.** D-23 is unambiguous; the catalog code stays in `ErrorCode` for any future surface (admin endpoints) but the public signup response never emits 409. The planner should flag this so the user can confirm, and update `docs/04 §6` if Option A wins.

### Pattern 2: Verification token + 302-redirect (`GET /api/auth/verify`)

**What:** Public GET endpoint consumes the token (sets `consumed_at`, flips `users.email_verified=true`), then 302-redirects to `${app.frontend.base-url}/verify?status=success` (or `=invalid`/`=expired`). Token never reaches the FE.

**When to use:** Email-link verification flows where the link target is the SPA, but the SPA must NEVER see the raw token (avoids XSS exfiltration risk).

```java
// Source: docs/04 §3 (GET /verify), docs/05 §7, CONTEXT.md D-02, D-23
@GetMapping("/verify")
public ResponseEntity<Void> verify(@RequestParam("token") String token) {
    String status = verificationService.consume(token);  // returns "success" | "invalid" | "expired"
    URI redirect = URI.create(authProps.getFrontend().getBaseUrl() + "/verify?status=" + status);
    return ResponseEntity.status(HttpStatus.FOUND).location(redirect).build();
}
```

`EmailVerificationService.consume(token)` — **idempotent + single-use**:
- Lookup by token (PK `CHAR(64)`) — if missing → "invalid"
- If `expires_at < NOW()` → "expired"
- If `consumed_at IS NOT NULL` → "invalid" (per `docs/05 §9.1` — same code as unknown)
- Set `consumed_at = NOW()`, set `users.email_verified = true`, return "success"

**Race condition note:** Two concurrent verify calls with the same token. Both pass the `consumed_at IS NULL` check, both set it, both flip the user's `email_verified`. Acceptable: idempotent at the user-state level (already true), and the second token row write is a no-op (column-level last-write-wins). No need for `SELECT FOR UPDATE` here unless we want to count-once for telemetry — out of scope.

### Pattern 3: Login + JWT issuance + refresh cookie

**What:** Login validates credentials behind a Redis rate-limit gate (D-05/D-06), then issues an access JWT (HS256, 15min) AND mints a refresh token (32 bytes hex, SHA-256-stored) returned via `Set-Cookie`.

**When to use:** Always. Phase 2's only login form. Bcrypt cost 12 verify is the slowest path — rate-limit MUST gate it (D-05).

```java
// Source: docs/04 §3 (POST /login), docs/05 §1, §2, CONTEXT.md D-05, D-08, D-19, D-20
@Transactional  // D-21
public LoginResult login(LoginRequest req, String clientIp) {
    String emailLower = req.email().toLowerCase().trim();
    if (rateLimiter.exceeded(clientIp, emailLower)) {
        throw new LoginRateLimitedException();  // → 429 auth.rate_limited (D-08)
    }
    User u = userRepo.findByEmail(emailLower)
        .orElseThrow(InvalidCredentialsException::new);  // generic 400 (§9.1)
    if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
        rateLimiter.recordFailure(clientIp, emailLower);  // D-07: failed-only
        throw new InvalidCredentialsException();
    }
    if (!u.isEmailVerified()) {
        // Verified-account check happens AFTER bcrypt verify — same timing for verified vs unverified
        // (otherwise an attacker can distinguish "wrong password" from "unverified" by latency)
        throw new EmailNotVerifiedException();  // → 403 auth.email_not_verified
    }

    rateLimiter.clear(clientIp, emailLower);  // D-07: success deletes the counter

    String accessJwt = jwtIssuer.issueAccess(u.getId().toString(), u.getEmail(), u.isEmailVerified());
    RefreshTokenIssued rt = refreshTokenService.create(u.getId());  // 32-byte hex, SHA-256-stored
    return new LoginResult(accessJwt, 900, rt.rawTokenForCookie(), u);
}
```

Cookie scope per D-12 in the controller:

```java
ResponseCookie cookie = ResponseCookie.from("refresh_token", rt.rawTokenForCookie())
    .httpOnly(true)
    .secure(authProps.getCookie().isSecure())   // false in dev/test, true in deployed
    .sameSite("Strict")
    .path("/api/auth")                          // narrow scope: refresh + logout only
    .maxAge(Duration.ofDays(7))
    .build();
return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(loginResponse);
```

### Pattern 4: Refresh rotation with `SELECT FOR UPDATE` row lock

**What:** Read refresh-token row under a pessimistic write lock; if `rotated_to IS NOT NULL` → replay attack → revoke entire chain (D-10). Otherwise: mark current as rotated, mint new, return new in cookie.

**When to use:** EVERY `/refresh` call, period. Defense-in-depth alongside the SPA's `isRefreshing` client guard (Pitfall 9).

```java
// Source: docs/05 §2 (rotation), CONTEXT.md D-10, D-13, D-21
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);
}

@Service
public class RefreshTokenService {
    @Transactional(isolation = Isolation.REPEATABLE_READ)  // D-13
    public RotatedTokens rotate(String rawCookieValue) {
        String hash = sha256Hex(rawCookieValue);
        RefreshToken current = refreshRepo.findByTokenHashForUpdate(hash)
            .orElseThrow(RefreshInvalidException::new);

        if (current.getRevokedAt() != null) {
            throw new RefreshInvalidException();  // already revoked
        }
        if (current.getExpiresAt().isBefore(Instant.now())) {
            throw new RefreshInvalidException();  // expired
        }
        if (current.getRotatedTo() != null) {
            // REPLAY DETECTED — walk chain, revoke all (D-10)
            revokeChain(current);
            log.warn("Refresh-token replay detected userId={} chainHead={}…",
                current.getUserId(), current.getTokenHash().substring(0, 8));
            throw new RefreshInvalidException();
        }

        // Successful rotation: mint new, link old → new
        RefreshToken next = mintFor(current.getUserId(),
            Instant.ofEpochMilli(current.getCreatedAt().toEpochMilli())
                .plus(Duration.ofDays(7)));  // sliding cap at original-issue + 7d (docs/05 §2)
        current.setRotatedTo(next.getTokenHash());
        // (next is saved by @Transactional propagation; current update too)

        return new RotatedTokens(jwtIssuer.issueAccess(...), next.rawValue());
    }

    private void revokeChain(RefreshToken pivot) {
        // walk to root via `rotated_to` reverse-lookup
        RefreshToken cursor = pivot;
        // walk backwards: find any row whose rotated_to == cursor.tokenHash
        while (true) {
            Optional<RefreshToken> prior = refreshRepo.findByRotatedTo(cursor.getTokenHash());
            if (prior.isEmpty()) break;
            cursor = prior.get();
        }
        RefreshToken root = cursor;
        // walk forwards from root, revoking
        cursor = root;
        while (cursor != null) {
            cursor.setRevokedAt(Instant.now());
            cursor = cursor.getRotatedTo() == null
                ? null
                : refreshRepo.findById(cursor.getRotatedTo()).orElse(null);
        }
    }
}
```

**Critical:** `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository method emits `SELECT … FOR UPDATE` — Hibernate translates the JPA lock mode automatically [CITED: baeldung.com/jpa-pessimistic-locking, vladmihalcea.com/spring-data-jpa-locking]. The `@Transactional(isolation = REPEATABLE_READ)` is required because `@Lock` only works inside a transaction (and a read-only one will fail) [CITED: github.com/spring-projects/spring-data-jpa/issues/2141].

### Pattern 5: Async email send with MDC propagation

**What:** `@Async` listener picks up `VerificationEmailRequestedEvent`, sends via `JavaMailSender`, with MDC (`traceId`/`requestId`/`userId`) copied from the originating request thread by a `TaskDecorator` so logs correlate in Zipkin.

**When to use:** Any side-effect that must run off the request thread but must still log under the original request's trace context. This is the servlet-side answer to Pitfall 7 (trace context loss at boundaries).

```java
// Source: spring-projects spring-framework #31130 (TaskDecorator),
//         medium.com/@AlexanderObregon/request-context-propagation-in-spring-boot-async-threads,
//         libs/observability/MdcEnrichmentFilter (analog),
//         CONTEXT.md D-22.
public class MdcCopyingTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> ctx = MDC.getCopyOfContextMap();   // captured on submitting thread
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                if (ctx != null) MDC.setContextMap(ctx);
                runnable.run();
            } finally {
                if (previous != null) MDC.setContextMap(previous);
                else MDC.clear();
            }
        };
    }
}

@EnableAsync
@Configuration
public class AsyncConfig {
    @Bean(name = "authAsyncExecutor")
    public ThreadPoolTaskExecutor authAsyncExecutor() {
        var ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);              // D-01
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("auth-async-");
        ex.setTaskDecorator(new MdcCopyingTaskDecorator());
        ex.initialize();
        return ex;
    }
}

@Component
public class EmailVerificationSender {
    @Async("authAsyncExecutor")  // pin to the named executor
    @EventListener
    public void on(VerificationEmailRequestedEvent ev) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(formattedFrom(authProps.getMail().getFrom()));   // "Trip Planner <no-reply@…>"
        msg.setTo(ev.email());
        msg.setSubject("Verify your Trip Planner account");
        msg.setText(buildBody(ev.token()));   // plain text per D-03
        try {
            mailSender.send(msg);
            log.info("Verification email sent to userId={} (traceId carried via MDC TaskDecorator)",
                ev.userId());
        } catch (MailException ex) {
            log.warn("Mail send failed userId={} class={}", ev.userId(), ex.getClass().getSimpleName());
            // D-04: do NOT log body or token; do NOT retry; rely on re-signup recovery (D-23)
        }
    }
}
```

### Pattern 6: Login rate-limit via Redis Lua INCR+EXPIRE

**What:** Single atomic Lua script increments the counter and sets the expiry only on first hit. Returns the new count.

**When to use:** Any time-window rate counter where the failure mode of "INCR succeeds, EXPIRE crashes" creates an immortal counter.

```java
// Source: redis.io/docs/latest/commands/incr/, dev.to/silentwatcher_95 (race-condition fix),
//         oneuptime.com/blog/post/.../redis-spring-boot-rate-limiter — adapted.
//         CONTEXT.md D-06, D-09.
@Component
public class LoginRateLimiter {
    // KEYS[1]=full key, ARGV[1]=ttl seconds, ARGV[2]=limit
    private static final String LUA_INCR_WITH_TTL = """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return count
        """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script =
        RedisScript.of(LUA_INCR_WITH_TTL, Long.class);

    public boolean exceeded(String ip, String emailLower) {
        // Read-only: use `GET` and compare; do NOT increment on read.
        String v = redis.opsForValue().get(key(ip, emailLower));
        return v != null && Long.parseLong(v) >= 5;
    }

    public void recordFailure(String ip, String emailLower) {
        Long count = redis.execute(script,
            List.of(key(ip, emailLower)),
            "900",   // 15-min TTL (D-06 / docs/04 §7)
            "5");
        // (no return-value action needed; `exceeded` is checked before next attempt)
    }

    public void clear(String ip, String emailLower) {
        redis.delete(key(ip, emailLower));   // D-07: success clears
    }

    private String key(String ip, String emailLower) {
        return "rl:login:fail:" + ip + ":" + emailLower;
    }
}
```

### Anti-Patterns to Avoid

- **Self-invocation `@Async`:** `class AuthService { void signup() { this.sendEmail(); } @Async void sendEmail() {…} }` — Spring's AOP proxy is bypassed by `this.`, the call runs synchronously. Use `ApplicationEventPublisher.publishEvent(...)` to force the proxy hop. (Phase 2 plan must enforce this — listener lives in a different bean.)
- **Storing raw refresh tokens in DB:** Always SHA-256 before persistence (per `docs/05 §2` — column is `token_hash CHAR(64)`). The cookie holds the raw 64-hex token; the DB never does.
- **Bcrypt verify before rate-limit check:** Each bcrypt verify is ~250ms — six attempts cost the server 1.5 CPU-seconds. Always rate-limit first (D-05). The Phase-1 IP-only gateway gate is coarse defense-in-depth; the Phase-2 IP+email leg is the strict gate.
- **Logging the JWT or refresh token:** Phase 1 already enforced this in error envelopes (T-01-06); Phase 2 must not regress in `AuthService` log lines or `AuthControllerAdvice` exception handling.
- **`@Transactional(readOnly = true)` on `/refresh`:** `@Lock(PESSIMISTIC_WRITE)` requires a write transaction. Spring Data JPA throws `PSQLException: cannot execute SELECT FOR UPDATE in a read-only transaction` [CITED: github.com/spring-projects/spring-data-jpa/issues/2141].
- **Not normalizing email casing on signup OR rate-limit lookup:** A single uppercase variant lets an attacker bypass the IP+email gate. Normalize EVERYWHERE (D-09).
- **Decorating `JavaMailSender` directly with `@Async`:** Don't. The decorator wraps the *event listener bean*, which gets the proxy hop. Putting `@Async` on a JavaMailSender wrapper class adds a second AOP target and complicates testing.
- **Letting `BCryptPasswordEncoder(12)` run in unit tests:** Each ~250ms. A 30-test signup suite spends 8 seconds in bcrypt alone. `@TestConfiguration` overrides with cost 4 (~5ms each) — D-19.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Password hashing | Custom hash + salt | `BCryptPasswordEncoder` (Spring Security) | docs/05 §6 mandates bcrypt 12; Spring's impl is FIPS-aware, salt-included, version-prefixed (`$2a$`) |
| JWT signing/parsing | Manual base64+HMAC | `io.jsonwebtoken:jjwt 0.13.0` modern builder | Convention C27-P1 |
| HMAC key derivation | `new SecretKeySpec(bytes, "HmacSHA256")` | `Keys.hmacShaKeyFor(bytes)` | Already in `JwtVerifier` (Phase 1 WR-08 fix); `JwtIssuer` MUST mirror this |
| RFC 7807 envelope serialization | `new ObjectMapper()` + manual JSON | Auto-configured `ObjectMapper` (with `ProblemDetailJacksonMixin`) injected by constructor | Phase 1 BL-01 root cause; Phase 2 must not regress |
| Bean Validation framework | Custom field validators | `spring-boot-starter-validation` (`@Email`/`@Size`/`@NotBlank`) | Hibernate Validator 8 ships with SB 3.5; reflection-driven, locale-aware |
| Refresh-token rotation locking | Optimistic version + retry loop | `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `SELECT FOR UPDATE` | Idiomatic Spring Data JPA pattern; matches D-13 row-lock semantics literally |
| Redis rate-limit atomicity | `INCR` then `EXPIRE` separately | Single `EVAL` Lua script | Race window between INCR and EXPIRE leaves immortal counters [CITED: dev.to/silentwatcher_95] |
| MDC propagation across `@Async` | Context-aware Runnable wrapper handcrafted everywhere | `TaskDecorator` registered ONCE on the executor | `ThreadPoolTaskExecutor.setTaskDecorator(...)` applies to all submitted tasks [CITED: github.com/spring-projects/spring-framework/issues/31130] |
| Verification HTML page | Static `verify-success.html` | 302-redirect to FE | FE owns rendering; if FE is down in dev, browser shows ECONNREFUSED — that's fine. (Researcher recommendation: do NOT ship the static page) |
| Email body templating | String.format / manual placeholder | `SimpleMailMessage` with hardcoded body — D-03 plain text only | Phase 9 polish migrates to Thymeleaf when HTML emails are wanted; YAGNI per D-03 |
| Scheduling with cluster-coordination | ShedLock / Quartz | `@Scheduled` (single-instance) | docs/02 §3 — auth-service is single-instance; ShedLock is v2 |
| Manual SHA-256 hex | `MessageDigest.getInstance("SHA-256") + Hex.encodeHexString` | Same — but write a single `Hashing.sha256Hex(...)` helper | Helper avoids 4 lines of try/catch boilerplate at every call site |

**Key insight:** Every "build a thing for X" temptation in Phase 2 has a Spring-managed or `libs/*` shipped equivalent. The plan's task list should look like *configuration + thin glue*, not *new infrastructure*. If the planner finds itself writing a "Phase 2 also ships `libs/<thing>`" task, that's a signal to step back.

## Runtime State Inventory

> Phase 2 is a **greenfield phase** — no rename/refactor/migration. New schema lands in V2/V3/V4 migrations; no pre-existing rows to migrate.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — V2/V3/V4 are NEW tables on top of empty Phase 0 V1 baseline. The `auth` schema is empty in dev (verified: `services/auth-service/src/main/resources/db/migration/V1__init.sql` is `SELECT 1;`). | None — fresh inserts on first signup |
| Live service config | None — auth-service was a Phase 0 skeleton with no real endpoints. No Eureka registration changes required. | Confirm gateway routes still hit `http://auth-service:8081` (already wired by Phase 1; verify via `grep` in Phase 1 routing IT files) |
| OS-registered state | None — no host-side processes carry phase-2 names | None |
| Secrets/env vars | `AUTH_JWT_SECRET` already shipping (Phase 1 D-16); IN-05 from Phase 1 review notes the env var must be propagated to all 4 backend services in `infra/docker-compose.yml`. **Phase 2 NEW envs:** `MAIL_FROM` (default `no-reply@tripplanner.local`), `FRONTEND_BASE_URL` (default `http://localhost:5173`), `AUTH_COOKIE_SECURE` (default `false`). Add these to `.env.example` and the auth-service compose `environment:` block. | Update `.env.example` + `infra/docker-compose.yml` `auth-service.environment:` section |
| Build artifacts/installed packages | None — `services/auth-service/build` is gitignored; first Phase-2 build will produce a fresh `bootJar` | None |

**Nothing found in any other category.** Phase 2 is greenfield-on-skeleton; no migrations of pre-existing runtime state are needed.

## Common Pitfalls

### Pitfall 1: Self-invocation breaks `@Async`

**What goes wrong:** Email send runs synchronously, blocking the signup response thread for the SMTP round-trip.
**Why it happens:** `@Async` (and `@Transactional`, `@Cacheable`, etc.) is implemented via Spring AOP proxies. A direct `this.method()` call bypasses the proxy.
**How to avoid:** Use `ApplicationEventPublisher.publishEvent(...)` (Pattern 5 above). The listener lives in a separate bean (`EmailVerificationSender`), guaranteeing a proxy hop.
**Warning signs:** Signup p95 latency rises to ~SMTP-round-trip + bcrypt-12 (~500ms+). Verification email log line appears on the controller request thread, not on `auth-async-N`.

### Pitfall 2: `@Lock(PESSIMISTIC_WRITE)` outside a transaction

**What goes wrong:** `TransactionRequiredException` at first refresh attempt; the row lock never acquires.
**Why it happens:** Spring Data JPA's `@Lock` only emits `SELECT FOR UPDATE` if the calling method runs inside an active transaction.
**How to avoid:** Annotate the **service method** that calls `findByTokenHashForUpdate(...)` with `@Transactional(isolation = REPEATABLE_READ)` (D-13). Repository annotations alone are insufficient — Spring's transaction proxy starts at the service-bean boundary.
**Warning signs:** Logs show `org.springframework.dao.InvalidDataAccessApiUsageException` or `PSQLException: cannot execute SELECT FOR UPDATE in a read-only transaction`.

### Pitfall 3: Token-enumeration via verify timing

**What goes wrong:** A "token unknown" branch returns 80μs after request; "token expired" branch returns 800μs (after a TIMESTAMPTZ comparison + UPDATE). Attacker probes valid-but-expired tokens.
**Why it happens:** Differential code paths between unknown/expired/consumed.
**How to avoid:** Always do the same DB round trips. `EmailVerificationService.consume(token)` should: (a) `SELECT ... WHERE token = ?` once, (b) regardless of result, return one of three constant-cost branches. Per docs/05 §9.1, use the same `auth.token_invalid` code for unknown-and-consumed. (Expired remains its own code per `docs/04 §6`.)
**Warning signs:** Differential timing < 100μs is acceptable; > 1ms is a leak signal (manual eyeball during test runs is enough — formal timing-attack tests are out of v1 scope).

### Pitfall 4: Refresh-token replay walks the WRONG chain

**What goes wrong:** D-10 says "walk back to chain root via `rotated_to` reverse-lookup, then forward to head." If the implementation only walks forward from the replayed token, an attacker who replays mid-chain leaves earlier tokens unrevoked.
**Why it happens:** It's natural to write `while (cursor.rotated_to != null) cursor = next(cursor.rotated_to); cursor.revoke()`. That misses upstream nodes.
**How to avoid:** Pattern 4's `revokeChain(...)` walks BOTH directions — first finds root via `findByRotatedTo(...)`-as-reverse-lookup, THEN forward-revokes. Repository must expose `findByRotatedTo(String tokenHash)` (returns `Optional<RefreshToken>` because each chain edge is unique).
**Warning signs:** `RotatedRefreshTokenCannotBeReusedIT` test #6 must specifically verify that AFTER replay, the **head** token (most recent rotation) is also `revoked_at != null` AND the **root** token is too. (See test sketch in `## Code Examples → IT skeleton`.)

### Pitfall 5: BCrypt cost 12 in tests = slow CI

**What goes wrong:** A 50-test auth IT suite spends 50 × 250ms = 12.5 seconds on bcrypt verify alone.
**Why it happens:** `BCryptPasswordEncoder(12)` is the production bean; tests inherit it.
**How to avoid:** `@TestConfiguration` with `@Bean @Primary PasswordEncoder bcryptForTests() { return new BCryptPasswordEncoder(4); }` — D-19. Use `@Import(TestSecurityConfig.class)` on the IT.
**Warning signs:** `./gradlew :services:auth-service:test` takes more than 60 seconds for the auth ITs alone. Check thread dumps for `BCrypt.hashpw` frames.

### Pitfall 6: Email-key normalization drift

**What goes wrong:** Signup stores `Mai@Example.com`, login normalizes to lowercase, lookup misses → infinite "wrong password" loop. Or: rate-limit key uses `req.email()` (raw), bypass via case variation.
**Why it happens:** Different normalization at write-time vs. read-time.
**How to avoid:** Single helper `normalize(email) := email.trim().toLowerCase(Locale.ROOT)` called in BOTH signup-write AND login-lookup AND rate-limit-key composition. Phase 2 plan should mandate a single utility class, not inline `.toLowerCase()` calls.
**Warning signs:** Login with valid mixed-case email returns `auth.invalid_credentials` even with correct password.

### Pitfall 7: `ObjectMapper` injected wrong

**What goes wrong:** Phase 2 controller emits `{"properties":{"code":"auth.weak_password"}}` instead of `{"code":"auth.weak_password"}`, breaking BL-01-fixed contract.
**Why it happens:** A controller advice constructed `new ObjectMapper()` instead of injecting Spring Boot's auto-configured one (which has `ProblemDetailJacksonMixin` registered).
**How to avoid:** Constructor-inject `ObjectMapper` everywhere. Lint with grep — `git grep "new ObjectMapper" services/auth-service/src/main/` should return ZERO matches. (Test infrastructure may keep it for `JsonPath` body-parse helpers.)
**Warning signs:** New auth-service IT asserts `$.properties.code` instead of `$.code` — the assertion shape has drifted.

### Pitfall 8: Forgot to register `flyway-database-postgresql`

**What goes wrong:** Service fails at startup with `org.flywaydb.core.api.FlywayException: Unsupported Database: PostgreSQL 16.x`.
**Why it happens:** Flyway 10 modularized PG support; `flyway-core` alone doesn't include it.
**How to avoid:** The dep `runtimeOnly(libs.flyway.database.postgresql)` is **already in** `services/auth-service/build.gradle.kts:26` from Phase 0 (verified). Phase 2 plan must not remove it. The catalog alias `libs.flyway.database.postgresql` already exists.
**Warning signs:** `docker compose up auth-service` fails health check; logs show the FlywayException above.

### Pitfall 9: `LocalDateTime` in JPA entities

**What goes wrong:** Server in UTC, DB column `TIMESTAMPTZ`, entity uses `LocalDateTime` — round-trip drops the offset, comparisons go off by hours when the host clock is non-UTC.
**Why it happens:** Beginner default to `LocalDateTime`. JPA serializes it without TZ.
**How to avoid:** Use `Instant` for all timestamp columns (`expires_at`, `consumed_at`, `created_at`, `revoked_at`, `rotated_at`). Hibernate 6 maps `Instant` ↔ `TIMESTAMPTZ` correctly.
**Warning signs:** `expires_at` comparisons in `RefreshTokenService.rotate()` produce false-positive expiries near DST transitions.

## Code Examples

Verified patterns from official sources or in-repo Phase 0/1 code.

### V2 migration: `auth.users`

```sql
-- Source: docs/03-data-model.md §3.1
-- File: services/auth-service/src/main/resources/db/migration/V2__create_users.sql
CREATE TABLE auth.users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(254) NOT NULL UNIQUE,
    password_hash   VARCHAR(72)  NOT NULL,
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
-- gen_random_uuid() comes from pgcrypto, already enabled in infra/postgres/init.sql
```

### V3 migration: `auth.email_verification_tokens`

```sql
-- Source: docs/03-data-model.md §3.2
-- File: services/auth-service/src/main/resources/db/migration/V3__create_email_verification_tokens.sql
CREATE TABLE auth.email_verification_tokens (
    token        CHAR(64)    PRIMARY KEY,                 -- hex of 32 random bytes
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Partial index for fast "unconsumed only" lookups
CREATE INDEX evt_unconsumed_idx
    ON auth.email_verification_tokens (user_id)
    WHERE consumed_at IS NULL;
```

### V4 migration: `auth.refresh_tokens`

```sql
-- Source: docs/05-auth-security.md §2 (table spec verbatim)
-- File: services/auth-service/src/main/resources/db/migration/V4__create_refresh_tokens.sql
CREATE TABLE auth.refresh_tokens (
    token_hash   CHAR(64)    PRIMARY KEY,                 -- SHA-256 hex of raw token
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ NOT NULL,
    rotated_to   CHAR(64)    NULL,                        -- successor's token_hash
    revoked_at   TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Reverse-lookup index for chain-replay revocation walk (Pitfall 4)
CREATE INDEX rt_rotated_to_idx
    ON auth.refresh_tokens (rotated_to)
    WHERE rotated_to IS NOT NULL;
-- For daily cleanup
CREATE INDEX rt_expires_at_idx
    ON auth.refresh_tokens (expires_at);
```

### `JwtIssuer` — recommended placement: `libs/jwt-common`

```java
// Source: docs/05-auth-security.md §2 (claims), CONTEXT.md D-20.
// Lives in libs/jwt-common alongside JwtVerifier so both consume the same JwtProperties bean.
package com.tripplanner.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class JwtIssuer {
    private static final String ISSUER = "tripplanner-auth";
    private static final Duration TTL  = Duration.ofMinutes(15);   // docs/05 §1
    private final SecretKey signingKey;

    public JwtIssuer(String secret) {
        if (secret == null) {
            throw new IllegalStateException("AUTH_JWT_SECRET must be set");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        try {
            this.signingKey = Keys.hmacShaKeyFor(bytes);   // mirrors JwtVerifier (Phase 1 WR-08)
        } catch (WeakKeyException ex) {
            throw new IllegalStateException(
                "AUTH_JWT_SECRET must be ≥ 32 bytes for HS256; got " + bytes.length, ex);
        }
    }

    public String issueAccess(String userId, String email, boolean emailVerified) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(ISSUER)                                  // iss
            .subject(userId)                                 // sub  (jjwt 0.13.0 modern API)
            .issuedAt(Date.from(now))                        // iat
            .expiration(Date.from(now.plus(TTL)))            // exp
            .id(UUID.randomUUID().toString())                // jti
            .claim("email", email)
            .claim("ver", emailVerified)
            .signWith(signingKey)                            // alg auto-set HS256 from key
            .compact();
    }
}
```

**Auto-config wiring** — extend `JwtAutoConfiguration`:

```java
// Source: in-repo libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java
// ADD to existing class:
@Bean
@ConditionalOnMissingBean(JwtIssuer.class)
public JwtIssuer jwtIssuer(JwtProperties props) {
    return new JwtIssuer(props.getSecret());
}
```

The `ConditionalOnMissingBean` mirrors the pending IN-01 fix from Phase 1 review (also recommended for `JwtVerifier` — Phase 2 fixes the same defect on both beans).

### `SignupRequest` — Bean Validation (D-18)

```java
// Source: docs/04 §3 (signup body), CONTEXT.md D-18
package com.tripplanner.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
    @Email @NotBlank @Size(max = 254) String email,
    @NotBlank @Size(min = 8, max = 200) String password
) {}
```

### `AuthControllerAdvice` — RFC 7807 with field-name discrimination (D-18)

```java
// Source: Spring Framework reference "Error Responses" + CONTEXT.md D-18.
// Maps MethodArgumentNotValidException to auth.invalid_email / auth.weak_password
// based on which field rejected validation.
package com.tripplanner.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.auth.service.exception.*;
import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Set;

@ControllerAdvice
public class AuthControllerAdvice extends ResponseEntityExceptionHandler {

    // ObjectMapper injected via auto-config; not constructed here.
    // Spring Boot auto-builds the ProblemDetail body using its registered ObjectMapper, so we
    // just return the ProblemDetail and let Spring serialize it. This avoids the BL-01 trap
    // where `new ObjectMapper()` would re-nest extension properties under `properties`.

    // --- Bean Validation (D-18) ---
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest req) {
        Set<String> failedFields = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getField)
            .collect(java.util.stream.Collectors.toSet());

        ErrorCode code;
        String detail;
        if (failedFields.contains("password")) {
            code = ErrorCode.AUTH_WEAK_PASSWORD;
            detail = "Password does not meet requirements";   // generic — no field-name leak
        } else if (failedFields.contains("email")) {
            code = ErrorCode.AUTH_INVALID_EMAIL;
            detail = "Email is invalid";
        } else {
            code = ErrorCode.VALIDATION_FAILED;
            detail = "Request validation failed";
        }
        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.BAD_REQUEST, code, detail);
        return new ResponseEntity<>(pd, headers, HttpStatus.BAD_REQUEST);
    }

    // --- Custom service exceptions (one handler per code per docs/04 §6) ---
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> onAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return body(HttpStatus.CONFLICT, ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED, "Account exists");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> onInvalidCreds(InvalidCredentialsException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_INVALID_CREDENTIALS,
            "Email or password is incorrect");   // §9.1 generic
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ProblemDetail> onUnverified(EmailNotVerifiedException ex) {
        return body(HttpStatus.FORBIDDEN, ErrorCode.AUTH_EMAIL_NOT_VERIFIED,
            "Account requires email verification");
    }

    @ExceptionHandler(TokenInvalidException.class)
    public ResponseEntity<ProblemDetail> onTokenInvalid(TokenInvalidException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_TOKEN_INVALID, "Token is invalid");
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ProblemDetail> onTokenExpired(TokenExpiredException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_TOKEN_EXPIRED, "Token has expired");
    }

    @ExceptionHandler(RefreshInvalidException.class)
    public ResponseEntity<ProblemDetail> onRefreshInvalid(RefreshInvalidException ex) {
        return body(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REFRESH_INVALID,
            "Refresh token is invalid");
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ProblemDetail> onRateLimited(LoginRateLimitedException ex) {
        return body(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.AUTH_RATE_LIMITED,
            "Too many attempts. Please try again later.");
    }

    private ResponseEntity<ProblemDetail> body(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetailFactory.of(status, code, detail);
        return ResponseEntity.status(status)
            .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
            .body(pd);
    }
}
```

`ErrorCode` enum **expansion**:

```java
// File: libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java
// Phase 2 ADDS the following to the existing enum:
AUTH_EMAIL_ALREADY_REGISTERED("auth.email_already_registered"),
AUTH_INVALID_CREDENTIALS("auth.invalid_credentials"),
AUTH_EMAIL_NOT_VERIFIED("auth.email_not_verified"),
AUTH_TOKEN_INVALID("auth.token_invalid"),
AUTH_REFRESH_INVALID("auth.refresh_invalid"),
AUTH_WEAK_PASSWORD("auth.weak_password"),
AUTH_INVALID_EMAIL("auth.invalid_email"),
VALIDATION_FAILED("validation.failed"),
// (AUTH_UNAUTHORIZED, AUTH_RATE_LIMITED, AUTH_INVALID_TOKEN, AUTH_TOKEN_EXPIRED, BAD_GATEWAY
//  already present from Phase 0/1)
```

### `SecurityConfig` — auth-service permits + BCrypt bean (D-19)

```java
// Source: in-repo trip-service ServletSecurityConfig.java (Phase 1 analog) + CONTEXT.md D-19
package com.tripplanner.auth.security;

import com.tripplanner.jwt.servlet.ServletJwtCommonFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);   // D-19 (test profile overrides to 4)
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            FilterRegistrationBean<ServletJwtCommonFilter> jwtFilterReg) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(
                    "/__health", "/__health/**", "/actuator/health", "/actuator/health/**", "/actuator/info",
                    // PUBLIC auth endpoints
                    "/api/auth/signup", "/api/auth/verify", "/api/auth/login", "/api/auth/refresh"
                ).permitAll()
                // /api/auth/logout (and discretionary /me) require JWT
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### `@Tag("security")` IT skeleton — `RotatedRefreshTokenCannotBeReusedIT`

```java
// Source: docs/05 §10 #6 (replay revokes entire chain), CONTEXT.md D-15
// File: services/auth-service/src/test/java/com/tripplanner/auth/security/RotatedRefreshTokenCannotBeReusedIT.java
package com.tripplanner.auth.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("security")
class RotatedRefreshTokenCannotBeReusedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg =
        new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("tripplanner");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired MockMvc mvc;
    @Autowired RefreshTokenRepository refreshRepo;

    @Test
    void replayed_refresh_token_revokes_entire_chain() throws Exception {
        // Arrange — signup + verify + login → cookie A, access JWT
        String cookieA = signupVerifyLogin("rotation@example.com", "correcthorsebatt");

        // Act 1 — first /refresh: cookie A → cookie B (success)
        var refreshAResp = mvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", cookieA)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("refresh_token"))
            .andReturn();
        String cookieB = extractCookie(refreshAResp, "refresh_token");

        // Act 2 — REPLAY: present cookie A again
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", cookieA)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.refresh_invalid"))           // BL-01 contract
            .andExpect(jsonPath("$.properties.code").doesNotExist());              // BL-01 negative

        // Assert — cookie B (the head) is also revoked AFTER replay
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", cookieB)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.refresh_invalid"));

        // DB-level assertion — both rows have revoked_at != null
        String hashA = sha256Hex(cookieA);
        String hashB = sha256Hex(cookieB);
        assertThat(refreshRepo.findById(hashA).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refreshRepo.findById(hashB).orElseThrow().getRevokedAt()).isNotNull();
    }
}
```

### BL-01 fix actual diff scope (Phase 1 review carryover)

Reading `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java:31` and `RateLimitProblemDetailFilter.java:50` confirms Phase 1 ALREADY constructor-injects `ObjectMapper` (the BL-01 review's BL-01 fix). The remaining BL-01 work for Phase 2 is therefore **only the IT-side jsonPath assertion update**:

```diff
// services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayMissingAuthHeaderIT.java
- .andExpect(jsonPath("$.properties.code").value("auth.unauthorized"))
+ .andExpect(jsonPath("$.code").value("auth.unauthorized"))
+ .andExpect(jsonPath("$.properties.code").doesNotExist())
```

Same diff in `GatewayForgedJwtIT`, `GatewayProblemDetailRenderingIT`, `LoginRateLimiterIT`. **Action for planner:** verify by reading these 4 IT files BEFORE writing the diff task — the production code is already fixed; the ITs may also already be fixed. Plan a single read+verify task before diving into the rewrite.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| jjwt 0.11.x `parserBuilder().setSigningKey(bytes).parseClaimsJws(...)` | jjwt 0.13.0 `Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(jws)` AND `Jwts.builder().subject().issuedAt().expiration().signWith(SecretKey).compact()` | jjwt 0.12.0 (Aug 2023); 0.13.0 (Aug 2024) | Convention C27-P1 already enforced by Phase 1 — Phase 2's `JwtIssuer` mirrors the modern API |
| `new SecretKeySpec(bytes, "HmacSHA256")` | `Keys.hmacShaKeyFor(bytes)` (rejects keys < 256-bit) | jjwt 0.12.0 | Phase 1 WR-08 fix already applied to `JwtVerifier`; Phase 2's `JwtIssuer` uses the same |
| Spring `@Async` + manual `ThreadLocal` copy | `TaskDecorator` registered on `ThreadPoolTaskExecutor` | Spring Framework 4.3 | Idiomatic since 2017 — no excuse to handcraft; `MdcCopyingTaskDecorator` is the canonical pattern |
| Spring Cloud Sleuth `@NewSpan` | Micrometer Tracing 1.4.x | Spring Boot 3.0 (Sleuth EOL) | Already handled by `libs/observability`; Phase 2's async pool inherits the Tracer-aware MDC values |
| `@ControllerAdvice` returning `Map<String,Object>` | `@ControllerAdvice extends ResponseEntityExceptionHandler` returning `ProblemDetail` | Spring Framework 6.0 / RFC 7807 / RFC 9457 | Phase 2 uses ProblemDetail exclusively; mirrors gateway's pattern |
| Manual `INCR` + `EXPIRE` round-trips | Redis Lua `EVAL` script for atomicity | Redis 2.6 (2012) — modern best practice | Race-condition-free; recommended by Redis Labs and observed by oneuptime/dev.to references |
| H2 in-memory for ITs | Testcontainers `@ServiceConnection` PostgreSQL | Spring Boot 3.1 (2023) | H2 lacks `SELECT FOR UPDATE` semantics needed by Pattern 4. Phase 2 ITs MUST use Postgres Testcontainers |

**Deprecated/outdated:**

- **`parserBuilder()`/`setSigningKey(byte[])`** in jjwt — gone since 0.12. Convention C27-P1 enforces this.
- **Spring Cloud Sleuth** — EOL; do not import.
- **`@ConditionalOnClass(name = "javax.servlet.Servlet")`** for SERVLET-vs-REACTIVE discrimination — replaced by `@ConditionalOnWebApplication(type = ...)` (Phase 0 WR-02 fix already applied to `libs/jwt-common` and `libs/observability`). New Phase 2 auto-configs (if any) inherit this convention.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | GreenMail 2.x is the latest stable major as of 2026-05 | `## Standard Stack → testing additions` | Plan ships an outdated catalog pin; minor — easy to bump. **Action: planner runs `gradle dependencyInsight --dependency com.icegreen:greenmail-junit5` after first pin to confirm.** [ASSUMED] |
| A2 | The auto-configured `ObjectMapper` with `ProblemDetailJacksonMixin` works identically when an `@ControllerAdvice` returns `ProblemDetail` from a `@ExceptionHandler` (i.e., Spring's `RequestMappingHandlerAdapter` serializes via the same bean) | `## Code Examples → AuthControllerAdvice` | If wrong, BL-01 regression reappears in auth-service ITs. The planner should add an early `@WebMvcTest`-level test asserting `$.code` at root from a deliberately-thrown `EmailNotVerifiedException`. [ASSUMED — Spring docs strongly imply this but I have not verified the exact bean-flow path in 3.5.14] |
| A3 | `redis:7-alpine` Testcontainers image `withExposedPorts(6379)` is sufficient for `@ServiceConnection(name = "redis")` to wire up Spring Data Redis. The `-alpine` variant has no `redis-cli` healthcheck baked in but Testcontainers has built-in startup probing. | `## Code Examples → IT skeleton` | If `@ServiceConnection(name = "redis")` doesn't auto-wire, ITs need explicit `@DynamicPropertySource` for `spring.data.redis.host/port`. Documented fallback. [VERIFIED via Spring Boot ref docs but not run on this machine] |
| A4 | Existing-and-VERIFIED accounts on signup re-attempt should return 201 (not 409) per CONTEXT D-23's reading of docs/05 §9.1 | `## Architecture Patterns → Pattern 1 → Open contract question` | The catalog (docs/04 §6) says 409. If the planner / user prefers strict catalog adherence, the implementation pivots: for VERIFIED-existing emails, `auth.email_already_registered` (409) is emitted; for UNVERIFIED-existing emails, the opaque 201 (D-23) is emitted. **Recommend the planner surface this as a clarifying question for the user before implementation begins.** [ASSUMED — D-23 wording supports researcher's reading; doc-catalog could be updated] |
| A5 | docs/04 §3 verify endpoint says "→ 200 OK { verified: true }" but CONTEXT D-02 mandates 302-redirect to FE. CONTEXT D-02 is the authoritative source — the docs/04 line is stale. | `## Architecture Patterns → Pattern 2` | If the planner reads docs/04 §3 literally, they'll plan a 200-with-JSON instead of a 302-redirect. The plan check should explicitly reconcile this. [VERIFIED — CONTEXT D-02 is locked, post-dates docs/04] |
| A6 | Spring Boot 3.5.14's `spring-boot-starter-data-redis` (servlet) is compatible with the existing `spring-boot-starter-data-redis-reactive` already on the api-gateway classpath (no version conflict at the BOM level) | `## Standard Stack → Phase 2 additions` | If they conflict (e.g., different Lettuce major), classpath warnings appear. They DO NOT — both are managed by the same SB BOM, both use Lettuce 6. [VERIFIED via Spring Boot dependency-versions page] |

## Open Questions

1. **`/api/auth/me` ship-or-skip (CONTEXT discretion)**
   - What we know: docs/04 lists 5 endpoints (no /me); ROADMAP SC#3 mentions /me; CONTEXT D-15 includes /me happy-path test.
   - What's unclear: Is /me load-bearing for the SC#3 test? Practically: no — the test can use the JWT's `sub` claim directly (already exposed via `@AuthenticationPrincipal UserContext.userId()`).
   - **Recommendation: SKIP /me in Phase 2.** The SPA already has `email` and `ver` in the JWT payload (decoded with `jose-jwt` in browser, or just `JSON.parse(atob(token.split('.')[1]))`). Shipping /me adds 12 lines of code, 1 IT, ~5min of plan time, and zero user-visible value. Phase 7 plan can add /me if SPA actually needs it. Update ROADMAP SC#3 wording to "logged-in user can access an authenticated route, then log out; subsequent authenticated requests return 401" (drop the /me reference).

2. **Static `verify-success.html` page in auth-service (CONTEXT discretion)**
   - What we know: D-02 says backend 302-redirects to `${app.frontend.base-url}/verify?status=…`. If FE isn't running, browser shows ECONNREFUSED.
   - What's unclear: Is the demo-recording happy with that, or do we want a fallback?
   - **Recommendation: SKIP the static page.** The demo flow is `docker compose up` (which starts the FE) → click email link → browser goes to FE-rendered status page. If FE isn't up, the demo is broken anyway. Static page is one more place to drift from FE styling. (Phase 7's `/verify` page is the canonical UI surface.)

3. **`JwtIssuer` location (CONTEXT discretion)**
   - What we know: Phase 1 placed `JwtVerifier` in `libs/jwt-common`. The library has both servlet and reactive sub-packages, so `libs/jwt-common` is now the canonical "JWT signing+verification" home.
   - **Recommendation: PUT `JwtIssuer` IN `libs/jwt-common`.** Same package as `JwtVerifier`. Same `JwtAutoConfiguration` exposes the bean. Same `JwtProperties` consumes the secret. Symmetry is itself a virtue here — the file pair (`JwtIssuer.java` + `JwtVerifier.java`) is much easier to maintain than splitting them across `libs/` and `services/`. Auth-service consumes via the same `implementation(project(":libs:jwt-common"))` it already declares.

4. **Single-vs-two-tx in `TokenCleanupJob` (CONTEXT discretion)**
   - **Recommendation: TWO transactions** (one per table). Functional outcome is identical; logs are cleaner (each transaction reports its own row count); a failure on one DELETE doesn't leave the other rolled back.

5. **Account-enumeration policy resolution (Pattern 1 footnote)**
   - The CONTEXT D-23 reading vs. docs/04 §6 catalog: **planner MUST raise this with user before implementing.** Two options described in Pattern 1. Recommend Option A (always 201) and update docs/04 §6 to remove `auth.email_already_registered` from the *signup* row (keep the code in the catalog for any future admin endpoint).

6. **Should we add `Origin` header check on `/refresh` per docs/05 §9.3?**
   - docs/05 §9.3 says "refresh endpoint requires `Origin` header to match allowlisted origins" as belt-and-suspenders CSRF defense.
   - CONTEXT.md doesn't mention it. SameSite=Strict on the cookie is the primary defense.
   - **Recommendation: SKIP for v1.** SameSite=Strict is sufficient (cross-site requests don't carry the cookie at all). Origin-check adds 4 lines, fails open in tools like Postman without an Origin header, and complicates ITs. Add to v2 backlog. Plan should NOT include this task.

## Environment Availability

> Phase 2 builds on Phase 0/1 — most dependencies are container-resident.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 toolchain | All build tasks | ✓ (Gradle toolchain auto-provisions; CI uses temurin-21) | 21 | — |
| Docker / Docker Desktop | Testcontainers ITs (PG + Redis) | ⚠ Required at IT runtime; `docker info` should pass on dev host | 24+ | None — IT cannot run without Docker; CI's `ubuntu-24.04` runner has Docker |
| PostgreSQL 16 (Testcontainers image) | refresh-rotation IT (`SELECT FOR UPDATE`) | ✓ Pulled by Testcontainers on first run | 16-alpine | H2 doesn't support `SELECT FOR UPDATE` row-lock semantics — fallback NOT viable |
| Redis 7 (Testcontainers image) | rate-limit IT | ✓ Pulled by Testcontainers on first run | 7-alpine | Embedded Redis (`redis-mock`/`embedded-redis`) — last-resort fallback if Docker unavailable |
| MailHog or GreenMail | email send ITs | ✓ MailHog in compose at `:1025`; GreenMail (recommended for ITs) is in-process | latest (compose); 2.x (Java) | Mock the `JavaMailSender` bean — fastest unit-test path |
| `gen_random_uuid()` (pgcrypto) | V2 user PK default | ✓ Already enabled via `infra/postgres/init.sql` | — | UUID-from-app-side via `UUID.randomUUID()` — fallback if DB extension missing |

**Missing dependencies with no fallback:** None — every Phase-2 dep is either already present or a Testcontainers-resident image.

**Missing dependencies with fallback:** None — fallbacks listed above are not currently needed.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5.12.0 + Mockito 5.x + AssertJ + Spring Boot Test (managed by SB 3.5.14 BOM) |
| Config file | `services/auth-service/build.gradle.kts` test block (`useJUnitPlatform { includeTags("security") }` for security suite) |
| Quick run command | `./gradlew :services:auth-service:test --tests com.tripplanner.auth.api.AuthControllerIT` (per-task) |
| Full suite command | `./gradlew :services:auth-service:check` (~60s warm); full-monorepo `./gradlew check` (~3min warm) |
| Security suite | `./gradlew :services:auth-service:test -PincludeTags=security` |

### Phase Requirements → Test Map

> Format: `tests/auth/...` is shorthand for `services/auth-service/src/test/java/com/tripplanner/auth/...`

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | Signup with valid email returns 201 + userId; mailbox receives email | integration | `./gradlew :services:auth-service:test --tests SignupHappyPathIT` | ❌ Wave 0 |
| AUTH-01 | Signup with weak password returns 400 `auth.weak_password` | integration | `./gradlew :services:auth-service:test --tests AuthValidationIT` | ❌ Wave 0 |
| AUTH-01 | Signup with malformed email returns 400 `auth.invalid_email` | integration | same as above | ❌ Wave 0 |
| AUTH-01 | Re-signup of unverified account returns 201 + fresh email (D-23) | integration | `./gradlew :services:auth-service:test --tests ResignupOfUnverifiedIT` | ❌ Wave 0 |
| AUTH-02 | Verify with valid token 302-redirects to FE `?status=success` and flips `email_verified` | integration | `./gradlew :services:auth-service:test --tests VerifyHappyPathIT` | ❌ Wave 0 |
| AUTH-02 | Verify with consumed token 302-redirects to FE `?status=invalid` | integration | same | ❌ Wave 0 |
| AUTH-02 | Verify with expired token 302-redirects to FE `?status=expired` | integration | same | ❌ Wave 0 |
| AUTH-03 | Login with wrong password returns 400 `auth.invalid_credentials` AND increments rate-limit counter | integration | `./gradlew :services:auth-service:test --tests LoginHappyAndFailIT` | ❌ Wave 0 |
| AUTH-03 | Login with unverified account returns 403 `auth.email_not_verified` (security #7) | integration `@Tag("security")` | `./gradlew :services:auth-service:test --tests EmailNotVerifiedCannotLoginIT` | ❌ Wave 0 |
| AUTH-03 | Login success returns 200 + JWT + `Set-Cookie: refresh_token` | integration | `LoginHappyAndFailIT` | ❌ Wave 0 |
| AUTH-03 | 6th failed login from same IP+email within 15min returns 429 `auth.rate_limited` (security #8) | integration `@Tag("security")` | `./gradlew :services:auth-service:test --tests LoginRateLimitFailedAttemptsTriggersIT` | ❌ Wave 0 |
| AUTH-04 | Logout invalidates refresh; subsequent /refresh returns 401 `auth.refresh_invalid` (security #5) | integration `@Tag("security")` | `./gradlew :services:auth-service:test --tests DeletedRefreshTokenCannotBeUsedIT` | ❌ Wave 0 |
| AUTH-04 | Replay rotated token revokes entire chain (security #6) | integration `@Tag("security")` | `./gradlew :services:auth-service:test --tests RotatedRefreshTokenCannotBeReusedIT` | ❌ Wave 0 |
| AUTH-04 | /refresh with valid cookie rotates the token and returns new access JWT | integration | `./gradlew :services:auth-service:test --tests RefreshHappyPathIT` | ❌ Wave 0 |
| NFR-05 | Service-layer line coverage ≥ 70% (final audit Phase 10; Phase 2 surface check) | jacoco | `./gradlew :services:auth-service:jacocoTestCoverageVerification` | ❌ Wave 0 (configure jacoco minimum) |
| BL-01 | Every gateway error path renders `$.code` at root (regression on Phase 1 ITs) | integration | `./gradlew :services:api-gateway:test --tests GatewayMissingAuthHeaderIT --tests GatewayForgedJwtIT --tests GatewayProblemDetailRenderingIT --tests LoginRateLimiterIT` | ❌ Wave 5 (IT update) |
| BL-01 | Every auth-service error path renders `$.code` at root | integration | `./gradlew :services:auth-service:test --tests AuthControllerAdviceIT` | ❌ Wave 0 |
| AUTH-* | Unit-level coverage on `JwtIssuer`, `RefreshTokenService.revokeChain`, `LoginRateLimiter` | unit | `./gradlew :services:auth-service:test --tests *Test` (uses Mockito mocks, no containers) | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `./gradlew :services:auth-service:test --tests <task-scoped-IT>` (~10s warm for unit tests; ~30s for any IT touching containers)
- **Per wave merge:** `./gradlew :services:auth-service:check` (~60s warm; first run ~3min cold for Testcontainers image pull)
- **Phase gate:** Full suite green: `./gradlew check` + `./gradlew :services:auth-service:test -PincludeTags=security` + `bash scripts/smoke.sh` exit 0 + manual MailHog UI visual check

### Wave 0 Gaps

- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java` — `@SpringBootTest` + `@Testcontainers` + `@ServiceConnection` Postgres + `@ServiceConnection(name="redis")` Redis + `@RegisterExtension GreenMailExtension` (Wave 0 base — every IT extends this)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/support/TestSecurityConfig.java` — `@TestConfiguration` `@Bean @Primary BCryptPasswordEncoder(4)` (D-19)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerIT.java` — happy-path signup + verify + login + refresh + logout (covers AUTH-01..04 SC#1)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerAdviceIT.java` — RFC 7807 contract: `$.code` at root for 9 error codes (BL-01)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/security/EmailNotVerifiedCannotLoginIT.java` — `@Tag("security")` test #7
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/security/DeletedRefreshTokenCannotBeUsedIT.java` — `@Tag("security")` test #5
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/security/RotatedRefreshTokenCannotBeReusedIT.java` — `@Tag("security")` test #6 (DB-level chain-revocation assertion)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/security/LoginRateLimitFailedAttemptsTriggersIT.java` — `@Tag("security")` test #8 IP+email leg
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/service/RefreshTokenServiceTest.java` — unit test of `revokeChain` walking both directions (Pitfall 4 mitigation)
- [ ] `services/auth-service/src/test/java/com/tripplanner/auth/service/LoginRateLimiterTest.java` — unit test of Lua script atomicity (uses an embedded-Redis or @ServiceConnection in a single short IT)
- [ ] `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtIssuerTest.java` — unit test that issued tokens round-trip through `JwtVerifier` cleanly (sub/email/ver/iat/exp/iss/jti)
- [ ] `services/auth-service/src/test/resources/application-test.yml` — test profile with `app.auth.cookie.secure=false`, `app.frontend.base-url=http://localhost:5173`, `app.mail.from=test@…`
- [ ] BL-01 IT updates (Wave 5): `git grep "\$.properties.code" services/api-gateway/src/test` → should be ZERO after the wave; replace with `$.code` + a `$.properties.code.doesNotExist()` negative assertion.

*Wave 0 wave numbering:* These tests land BEFORE production code (Wave 0 is RED-by-design). Production code lands in Waves 1-4.

## Security Domain

> NFR-05 (8 mandatory security ITs) is the merge gate for this phase.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | bcrypt cost 12; min 8 chars; no max < 200 (D-19, docs/05 §6); rate-limit IP+email at 5/15min (D-05) |
| V3 Session Management | yes | JWT 15min access (in-memory only — never `localStorage`); refresh 7d httpOnly Strict cookie with rotation + revocation (D-10..D-13) |
| V4 Access Control | yes (consumed via Phase 1 `ServletJwtCommonFilter`) | `@AuthenticationPrincipal UserContext`; logout endpoint authenticated; service-layer ownership lands in Phase 5/6 |
| V5 Input Validation | yes | `spring-boot-starter-validation` Bean Validation; `@Email/@NotBlank/@Size` on all DTO records (D-18) |
| V6 Cryptography | yes | HS256 jjwt 0.13.0; `Keys.hmacShaKeyFor(bytes)` (rejects < 256-bit); SHA-256 for refresh-token hashing — all standard primitives, never hand-rolled |
| V7 Errors & Logging | yes | RFC 7807 ProblemDetail; generic detail messages (no field-name leak); WARN logs include userId+traceId but never tokens/passwords (docs/05 §9.6, D-04) |
| V13 API & Web Service | yes | RFC 7807 stable codes; CORS already enforced by gateway (Phase 1); SameSite=Strict cookie |

### Known Threat Patterns for {servlet Spring Boot 3.5 + jjwt + Postgres + Redis}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Account enumeration via signup | Information Disclosure | Same opaque 201 response shape regardless of existing account state (docs/05 §9.1, D-23) |
| Account enumeration via login latency | Information Disclosure | Always run bcrypt verify even if user not found (current Pattern 3 doesn't — fix: dummy bcrypt with constant-result hash when user not found, OR accept the small leak — researcher's call, recommend "fix"); always run the same code paths |
| JWT replay (post-logout access token) | Spoofing | JWT lifetime 15min — accepted residual risk in v1; v2 backlog has revocation list via `jti` |
| Refresh-token theft + replay | Spoofing | Rotation + chain revocation on replay (D-10/D-13); httpOnly+SameSite=Strict cookie |
| Bcrypt-12 DoS via login flood | Denial of Service | IP+email rate limit gate runs BEFORE bcrypt verify (D-05); IP-only gateway gate is coarse defense-in-depth |
| Email-link token brute-force | Spoofing | 32 bytes (256 bits) hex token = 2^256 search space; 24h TTL; single-use `consumed_at` |
| SQL injection on email lookup | Tampering | JPA parameterized queries via Spring Data; ORM-only DB access |
| Credential-stuffing via residential proxies | Spoofing | IP-only leg fails; email-leg holds (D-06 keys on `(ip, email_lower)`); v2 adds email-only layer |
| MDC-context-leak across `@Async` | Information Disclosure | `MdcCopyingTaskDecorator` copy/restore pattern (D-22, Pattern 5) |
| Race condition: concurrent signup with same email | Tampering | Unique `users.email` constraint at DB level catches the race; service catches `DataIntegrityViolationException` and translates to D-23 re-signup branch (or 409 — see Open Question 5) |
| Race condition: concurrent /refresh of same cookie | Tampering | `SELECT FOR UPDATE` row lock + `@Transactional(REPEATABLE_READ)` (D-13); second arrival sees `rotated_to != null` and triggers replay-revocation |
| Stored XSS via email | Tampering | Email is plain-text only (D-03 — `SimpleMailMessage`); no HTML body in v1 |

## Sources

### Primary (HIGH confidence)

- `docs/05-auth-security.md` (in-repo) — auth-and-security design (skimmed full doc; §1, §2, §6, §7, §9.1, §10 are load-bearing for Phase 2)
- `docs/04-api-spec.md` (in-repo) — §3 (5 routes), §6 (error catalog), §7 (rate-limit table)
- `docs/03-data-model.md` (in-repo) — §3.1 (users), §3.2 (email_verification_tokens), §4 (Flyway strategy)
- `gradle/libs.versions.toml` (in-repo) — pinned versions, current jjwt/Spring Boot/Spring Cloud
- `services/auth-service/build.gradle.kts` (in-repo) — confirms `flyway-database-postgresql` is wired
- `services/auth-service/src/main/resources/application.yml` (in-repo) — confirms per-service Flyway history table + JPA `validate`
- `libs/jwt-common/src/main/java/com/tripplanner/jwt/*` (in-repo) — JwtVerifier, JwtAutoConfiguration, JwtProperties, JwtFixtures (consumed by JwtIssuer)
- `libs/error-handling/src/main/java/com/tripplanner/errors/*` (in-repo) — ProblemDetailFactory, ErrorCode (extended in Phase 2)
- `services/api-gateway/src/main/java/com/tripplanner/gateway/security/{ProblemDetailAuthEntryPoint,RateLimitProblemDetailFilter}.java` — verified BL-01 production fix already shipped
- [Spring Boot 3.5 — Sending Email reference](https://docs.spring.io/spring-boot/reference/io/email.html) — `JavaMailSender` auto-config

### Secondary (MEDIUM confidence)

- [jjwt 0.13.0 modern API on GitHub](https://github.com/jwtk/jjwt) + [CHANGELOG](https://github.com/jwtk/jjwt/blob/master/CHANGELOG.md) — modern builder methods (`subject()`, `signWith(SecretKey)`, etc.)
- [Baeldung: JPA Pessimistic Locking](https://www.baeldung.com/jpa-pessimistic-locking) + [Vlad Mihalcea: Spring Data JPA locking](https://vladmihalcea.com/spring-data-jpa-locking/) — cross-checked the `@Lock(PESSIMISTIC_WRITE)` + `@Transactional` requirement
- [github.com/spring-projects/spring-data-jpa#2141](https://github.com/spring-projects/spring-data-jpa/issues/2141) — readonly-tx + PESSIMISTIC_WRITE conflict
- [Spring Framework #31130 — TaskDecorator for context propagation](https://github.com/spring-projects/spring-framework/issues/31130) — confirms idiomatic pattern
- [Medium: Request Context Propagation in Spring Boot Async Threads](https://medium.com/@AlexanderObregon/request-context-propagation-in-spring-boot-async-threads-b184bd096dae) — implementation example
- [Spring Boot Testcontainers reference](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — `@ServiceConnection`
- [Redis INCR documentation](https://redis.io/docs/latest/commands/incr/) — atomicity guarantees
- [dev.to/silentwatcher_95: Lua scripting for atomicity](https://dev.to/silentwatcher_95/fixing-race-conditions-in-redis-counters-why-lua-scripting-is-the-key-to-atomicity-and-reliability-38a4) — race-condition mitigation
- [Spring Framework: Error Responses (ProblemDetail)](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) — `ResponseEntityExceptionHandler` override of `handleMethodArgumentNotValid`
- [GreenMail JUnit 5 reference](https://greenmail-mail-test.github.io/greenmail/) — extension lifecycle
- [spring.io blog: virtual threads enable in 3.2+](https://spring.io/blog/2023/09/09/all-together-now-spring-boot-3-2-graalvm-native-images-java-21-and-virtual/) — `spring.threads.virtual.enabled`

### Tertiary (LOW confidence — informational only)

- [oneuptime.com: Build a Spring Boot Rate Limiter with Redis](https://oneuptime.com/blog/post/2026-03-31-redis-spring-boot-rate-limiter/view) — Lua-script integration code shape
- [reflectoring.io: Spring Security password handling](https://reflectoring.io/spring-security-password-handling/) — BCrypt cost factor reasoning (cross-verified with locked docs/05 §6)

## Metadata

**Confidence breakdown:**
- Standard Stack: HIGH — versions verified against in-repo `libs.versions.toml`; transitive deps managed by Spring Boot 3.5.14 BOM
- Architecture Patterns: HIGH — every pattern has either an in-repo Phase 0/1 analog (servlet filter, ProblemDetail emitter, MDC enrichment) or a Spring-managed primitive
- Pitfalls: HIGH — five of nine documented pitfalls have an in-repo precedent (Phase 0/1 reviews); the rest are well-known idioms (JPA `@Lock` + tx, BCrypt cost in tests)
- Validation Architecture: HIGH — test framework already established by Phase 1 (`@Tag("security")`, `@SpringBootTest` + `@ServiceConnection`); Wave 0 list is concrete files
- Security Domain: HIGH — explicit ASVS-cat alignment + STRIDE categorization; mitigations all locked in CONTEXT.md

**Research date:** 2026-05-09
**Valid until:** 2026-06-09 (estimate — 30 days for stable LTS-Spring-Boot/jjwt stack; 7 days if greenfield-frontier libraries were involved, which they are not)
