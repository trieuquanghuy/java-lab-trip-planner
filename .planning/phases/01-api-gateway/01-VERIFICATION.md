---
phase: 01-api-gateway
verified: 2026-05-09T00:00:00Z
status: passed
score: 6/6 must-haves verified (SC#5 IP-only leg complete; IP+email leg deferred to Phase 2 per documented D-05 split — SC#6 user-attested 2026-05-09)
overrides_applied: 0
deferred:
  - truth: "Login route rate-limited at 5 requests per 15 minutes per IP+email; 6th attempt returns 429"
    addressed_in: "Phase 2"
    evidence: "Phase 2 ROADMAP SC#5: 'All 8 mandatory security integration tests pass (including... login rate limit at attempt 6)'; 01-RESEARCH.md line 66 D-05 split: 'auth-service ships strict 5/15min IP+email in Phase 2'; 01-CONTEXT.md D-05 explicitly documents the Phase 1 leg (IP-only 30/15min) and the Phase 2 leg (IP+email strict 5/15min)"
human_verification:
  - test: "Confirm SC#6 Zipkin UI trace continuity in docker compose stack"
    expected: "Single trace ID spanning api-gateway and trip-service spans visible in Zipkin UI at localhost:9411"
    why_human: "GatewayTraceContinuityIT (automated) verifies MDC traceId matches W3C traceparent header sent downstream. The Zipkin UI assertion (spans visible in Zipkin) cannot be automated without a running Zipkin instance and requires visual confirmation."
    prior_attestation: "PASSED — manually confirmed 2026-05-09 as part of Phase 1 smoke run (01-06-SUMMARY.md Task 6.3)"
---

# Phase 1: API Gateway Verification Report

**Phase Goal:** The gateway routes traffic, validates JWTs, injects trusted headers, and downstream services reject any request that bypasses the gateway.
**Verified:** 2026-05-09
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `/api/auth/*` reaches auth-service; `/api/trips/*` reaches trip-service; `/api/search/*` reaches destination-service | VERIFIED | `GatewayRoutingIT` (3/3 tests): `routes_api_auth_login_to_auth_service`, `routes_api_trips_anything_to_trip_service`, `routes_api_search_anything_to_destination_service`. WireMock stubs confirm only the correct downstream stub receives each request. Smoke `phase-01-routing` check also passes. |
| 2 | `/api/trips/*` without `Authorization` header returns 401 from gateway, never reaches trip-service | VERIFIED | `GatewayMissingAuthHeaderIT` (4/4): asserts 401 `application/problem+json` with `code=auth.unauthorized` AND `tripStub.getAllServeEvents().isEmpty()` — gateway short-circuits. `WebFluxSecurityConfig` wires `anyExchange().authenticated()` plus `ProblemDetailAuthEntryPoint`. |
| 3 | Forged or expired JWT returns 401 from gateway | VERIFIED | `GatewayForgedJwtIT` (4/4): covers wrong-sig, expired, malformed, alg=none. Expired asserts `auth.token_expired`; others assert `auth.invalid_token`. `ProblemDetailAuthEntryPoint` distinguishes via `ex.getCause() instanceof JwtAuthenticationException` with `.contains("expired")`. Wiring: `ReactiveJwtAuthenticationManager` → `JwtVerifier.verify()` → exception propagation. |
| 4 | Direct hit on `localhost:8082/api/trips` with crafted `X-User-Id` and no valid JWT returns 401 from trip-service (`DirectServiceAccessWithoutGatewayReturns401` test passes) | VERIFIED | `DirectServiceAccessWithoutGatewayReturns401IT` GREEN in BOTH trip-service (3/3) and destination-service (3/3). `ServletJwtCommonFilter` auto-registered via `JwtAutoConfiguration.ServletConfig` at `Integer.MIN_VALUE + 200`; wired into `SecurityFilterChain` via `addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)`. Filter rejects before MVC routing. |
| 5 | `/api/auth/login` rate-limited; excess requests return 429 with `application/problem+json` | VERIFIED (IP-only leg) | `LoginRateLimiterIT` (3/3): 31st request in burst window → 429 `$.properties.code == "auth.rate_limited"`. Production YAML: `replenishRate=30, requestedTokens=900, burstCapacity=30` (= 30 req/15 min IP-only per D-05). **IP+email strict 5/15min leg DEFERRED to Phase 2** (documented D-05 split; see Deferred Items). `RateLimitProblemDetailFilter` provides RFC 7807 body via `setComplete()` override. |
| 6 | Single gateway → downstream request shows one trace ID in both service logs and Zipkin UI | VERIFIED (automated leg) + HUMAN NEEDED (Zipkin UI) | `GatewayTraceContinuityIT` (1/1): asserts MDC `traceId` in SCG logger hierarchy matches W3C `traceparent` trace_id sent to trip-service stub. `GatewayTracingObservationConfig` fixes circular-dependency handler registration. `GatewayTracingTestConfig` provides W3C propagator in test context. **Zipkin UI portion**: manually confirmed 2026-05-09 per 01-06-SUMMARY.md human-verify gate. |

**Score:** 5/6 truths fully verified; 1 partially verified (SC#5 IP-only leg only), IP+email leg deferred to Phase 2.

---

### Deferred Items

Items not yet met but explicitly addressed in later milestone phases.

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | Login rate-limit at 5 req/15min per IP+email; 6th attempt returns 429 (the strict Phase 1 ROADMAP SC#5 wording) | Phase 2 | Phase 2 ROADMAP SC#5: "All 8 mandatory security integration tests pass (including… login rate limit at attempt 6)". RESEARCH.md D-05 split: "auth-service ships strict 5/15min IP+email in Phase 2". The Phase 1 IP-only 30/15min leg is delivered and tested. |

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `libs/jwt-common/build.gradle.kts` | Shared JWT module with java-test-fixtures | VERIFIED | `java-library` + `java-test-fixtures` + `spring-dependency-management` plugins; jjwt 0.13.0; compileOnly Spring Security split |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java` | HS256 verifier using jjwt 0.13.0 modern API | VERIFIED | Uses `Jwts.parser().verifyWith(signingKey).build().parseSignedClaims()`; fails fast on null/<32-byte secret; throws `JwtAuthenticationException("token expired")` for `ExpiredJwtException` and `("token invalid")` for other `JwtException` |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java` | Auto-config with @ConditionalOnWebApplication discriminator | VERIFIED | SERVLET inner config registers `FilterRegistrationBean<ServletJwtCommonFilter>` at `Integer.MIN_VALUE + 200`; REACTIVE inner config exposes `ServerBearerTokenConverter` + `ReactiveJwtAuthenticationManager` beans |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java` | Servlet JWT filter (defense-in-depth) | VERIFIED | Extends `OncePerRequestFilter`; accepts injected `ObjectMapper` (auto-configured); writes `SecurityContextHolder` + `MDC.put("userId")` on valid Bearer; 401 ProblemDetail on missing/expired/invalid |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ReactiveJwtAuthenticationManager.java` | Reactive JWT auth manager | VERIFIED | Wraps `JwtVerifier`; maps `JwtAuthenticationException` to `BadCredentialsException` for Spring Security reactive chain |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ServerBearerTokenConverter.java` | Reactive Bearer token extractor | VERIFIED | `implements ServerAuthenticationConverter`; returns `Mono.empty()` on missing/wrong-scheme; extracts token string for auth manager |
| `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java` | Test fixture JWT minter | VERIFIED | 36-byte `TEST_SECRET`; `mintValid`, `mintExpired`, `mintWrongSig`, `mintMalformed` methods; used by all gateway and downstream ITs |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/security/WebFluxSecurityConfig.java` | Spring Security WebFlux filter chain | VERIFIED | `@EnableWebFluxSecurity`; public allowlist; `AuthenticationWebFilter` wired with `ReactiveJwtAuthenticationManager` + `ServerBearerTokenConverter`; `ProblemDetailAuthEntryPoint` wired; `NoOpServerSecurityContextRepository`; CORS restricted to `http://localhost:5173` |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/security/XUserIdInjectionGlobalFilter.java` | X-User-Id strip+inject GlobalFilter | VERIFIED | `@Order(-100)`; strips `X-User-Id`/`X-User-Email` on BOTH authenticated and public branches (Pitfall 1 keystone T-01-04); injects from validated `UserContext` on authenticated branch only; propagates `X-Request-Id` |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java` | 401 RFC 7807 entry point | VERIFIED (with known issue) | Distinguishes `AUTH_TOKEN_EXPIRED`/`AUTH_INVALID_TOKEN`/`AUTH_UNAUTHORIZED` by cause inspection. **Known issue (BL-01 from REVIEW):** uses `new ObjectMapper()` instead of Spring Boot's auto-configured ObjectMapper, causing `code` to serialize at `$.properties.code` instead of `$.code`. Gateway ITs assert `$.properties.code` and pass, but this is inconsistent with downstream services which use `$.code`. See Advisory Concerns. |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java` | 429 RFC 7807 decorator | VERIFIED (with known issue) | `@Order(-2)`; `ServerHttpResponseDecorator` overrides both `writeWith()` and `setComplete()` (for empty-body 429 path); emits `application/problem+json` with `AUTH_RATE_LIMITED`. Same `new ObjectMapper()` issue as BL-01. |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/routing/KeyResolverConfig.java` | Rate-limit key resolvers | VERIFIED | `@Bean ipKeyResolver` (uses `getRemoteAddress()` directly; no X-Forwarded-For trust per C33-P1); `@Bean userIdKeyResolver` (reads `UserContext.userId()` from `ReactiveSecurityContextHolder`; falls back to `"anonymous"`) |
| `services/api-gateway/src/main/resources/application.yml` | Phase 1 routes + Redis config | VERIFIED | 8 Phase 1 routes appended below Phase 0 marker; `spring.data.redis.host/port`; `spring.cloud.gateway.httpclient` timeouts; `RequestRateLimiter` on auth-login (30/15min IP), auth-signup (3/hr IP), search/destinations (60/min IP), trips (120/min userId) |
| `services/api-gateway/src/main/resources/application-docker.yml` | Compose Redis host override | VERIFIED | `spring.data.redis.host: redis` added; Eureka 5/5/10 tuning preserved; Zipkin endpoint preserved |
| `services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java` | Trip-service SecurityFilterChain | VERIFIED | `@EnableWebSecurity`; `permitAll` health endpoints; `anyRequest().authenticated()`; CSRF disabled; `SessionCreationPolicy.STATELESS`; `addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)`; `RestAuthenticationEntryPoint` wired |
| `services/destination-service/src/main/java/com/tripplanner/destination/security/ServletSecurityConfig.java` | Destination-service SecurityFilterChain | VERIFIED | Byte-identical to trip-service (Convention C20-P0); same security posture |
| `services/trip-service/src/test/java/com/tripplanner/trip/security/DirectServiceAccessWithoutGatewayReturns401IT.java` | SC#4 keystone IT (trip-service) | VERIFIED | 3/3 tests green: `direct_call_with_xUserId_and_no_jwt_returns_401`, `direct_call_with_no_headers_at_all_returns_401`, `health_endpoint_remains_anonymous_accessible`; H2 in-memory + Flyway disabled |
| `services/destination-service/src/test/java/com/tripplanner/destination/security/DirectServiceAccessWithoutGatewayReturns401IT.java` | SC#4 keystone IT (destination-service) | VERIFIED | Symmetric to trip-service; 3/3 tests green |
| `infra/docker-compose.yml` | Compose depends_on chain (Pitfall H + J) | VERIFIED | `api-gateway.depends_on` includes `redis` (Pitfall H), `auth-service` (Pitfall J), `trip-service` (Pitfall J), `destination-service` (Pitfall J) all with `condition: service_healthy` |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `WebFluxSecurityConfig.java` | `libs/jwt-common ReactiveJwtAuthenticationManager` | Constructor injection into `AuthenticationWebFilter` | WIRED | `grep -c "ReactiveJwtAuthenticationManager" WebFluxSecurityConfig.java` → 3 matches |
| `WebFluxSecurityConfig.java` | `libs/jwt-common ServerBearerTokenConverter` | `jwtFilter.setServerAuthenticationConverter(bearerConverter)` | WIRED | `grep -c "ServerBearerTokenConverter" WebFluxSecurityConfig.java` → 2 matches |
| `XUserIdInjectionGlobalFilter.java` | `ReactiveSecurityContextHolder` | `ReactiveSecurityContextHolder.getContext().map(ctx -> ctx.getAuthentication())` | WIRED | Pattern confirmed in source; Pitfall 1 keystone verified by `XUserIdInjectionIT` (6/6) |
| `KeyResolverConfig.java` | `application.yml RequestRateLimiter` | `@Bean(name="ipKeyResolver")` resolved via `key-resolver: "#{@ipKeyResolver}"` | WIRED | `#{@ipKeyResolver}` and `#{@userIdKeyResolver}` in application.yml; bean names match |
| `application.yml` | Redis | `spring.data.redis.host/port` consumed by `RedisRateLimiter` | WIRED | `redis-rate-limiter.*` in 5 routes; `application-docker.yml` overrides host=redis |
| `RateLimitProblemDetailFilter.java` | `ProblemDetailFactory + ErrorCode.AUTH_RATE_LIMITED` | `ProblemDetailFactory.of(TOO_MANY_REQUESTS, AUTH_RATE_LIMITED, ...)` | WIRED | Confirmed in source; `LoginRateLimiterIT` validates runtime behavior |
| `ServletSecurityConfig.java` (trip-service) | `FilterRegistrationBean<ServletJwtCommonFilter>` | `addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)` | WIRED | `grep -c "FilterRegistrationBean\|jwtFilterReg\|addFilterBefore" ServletSecurityConfig.java` → 4 matches |
| `JwtAutoConfiguration.java` | `spring.boot.autoconfigure.AutoConfiguration.imports` | Single-line registration of `com.tripplanner.jwt.JwtAutoConfiguration` | WIRED | File confirmed present with correct class reference |

---

### Data-Flow Trace (Level 4)

Phase 1 delivers security infrastructure (filters, config beans), not components rendering dynamic data from a database. Level 4 data-flow tracing is not applicable — no user-facing data rendering in this phase. The relevant data flows are:

| Flow | Source | Consumer | Status |
|------|--------|----------|--------|
| JWT `sub` claim → `UserContext.userId()` | `JwtVerifier.verify()` parsing `Claims.getSubject()` | `XUserIdInjectionGlobalFilter` injects into `X-User-Id` header | FLOWING — verified by `XUserIdInjectionIT` |
| Redis token bucket → rate-limit decision | `RedisRateLimiter` reading `request_rate_limiter.{key}.tokens` | `RequestRateLimiterGatewayFilterFactory` | FLOWING — verified by `LoginRateLimiterIT` with Testcontainers Redis |
| `X-User-Id` header → `SecurityContextHolder` | `ServletJwtCommonFilter.doFilterInternal()` | Downstream controllers via `@AuthenticationPrincipal` | FLOWING — verified by `DirectServiceAccessWithoutGatewayReturns401IT` |

---

### Behavioral Spot-Checks

Step 7b: Skipped for automated tests (all 27 ITs pass per 01-05-SUMMARY.md). Runtime spot-checks require a running docker compose stack.

The user-attested smoke run (Plan 01-06 Task 6.3, approved 2026-05-09) serves as the runtime behavioral gate:

| Behavior | Check | Result |
|----------|-------|--------|
| All containers healthy ≤120s | `docker compose down -v && up -d --wait` | PASS (human-verified) |
| Phase 1 bypass/routing/rate-limit smoke | `bash scripts/smoke.sh` exit 0 | PASS (human-verified) |
| SC#4 direct-access bypass | `smoke.sh --criterion phase-01-bypass` → 401 on `127.0.0.1:8082` | PASS (human-verified) |
| SC#6 Zipkin trace continuity | Zipkin UI at `:9411` — single trace spanning gateway + trip-service | PASS (human-verified) |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| NFR-02 | 01-01-PLAN, 01-03-PLAN | Per-user authorization enforced at service layer; cross-user reads return 404 | SATISFIED | `DirectServiceAccessWithoutGatewayReturns401IT` proves service-layer enforcement; `XUserIdInjectionGlobalFilter` prevents X-User-Id spoofing; `anyRequest().authenticated()` in all downstream SecurityFilterChains |
| NFR-06 | 01-01-PLAN, 01-03-PLAN | OWASP Top 10 (2021) A01/A07 addressed | SATISFIED | T-01-04 (A01 broken access control): gateway strips X-User-Id on public routes; T-01-08 (CVE-2025-41235 / A06): X-Forwarded-For trust OFF in `ipKeyResolver`; T-01-10 (A01 CORS): origin whitelist enforced; JWT HS256 (A07); `SessionCreationPolicy.STATELESS` (A07) |

*Note: REQUIREMENTS.md traceability table lists NFR-06 as "Phase 6: Complete" but it is listed in Phase 1 plans as a requirement and the [x] Complete checkbox is set. The Phase 1 contribution is A01/A07 (auth + access control); other OWASP categories (A03 parameterized queries, A03/XSS sanitization, A06 Dependency-Check) are addressed in later phases. This is a traceability inconsistency in REQUIREMENTS.md (advisory only).*

---

### Anti-Patterns Found

The following are surfaced from 01-REVIEW.md. They are categorized by impact on Phase 1 Success Criteria:

| File | Pattern | Severity | SC Impact |
|------|---------|---------|-----------|
| `ProblemDetailAuthEntryPoint.java:29` | `new ObjectMapper()` — ProblemDetail `code` serializes at `$.properties.code` instead of `$.code`; inconsistent with downstream `$.code` | WARNING (BL-01) | No SC violation: ROADMAP SC#2/SC#3 say "returns 401", not "code at root". ITs assert `$.properties.code` and pass. Public API contract inconsistency. |
| `RateLimitProblemDetailFilter.java:46` | Same `new ObjectMapper()` issue as BL-01 | WARNING (BL-01) | No SC violation: ITs assert `$.properties.code` and pass. |
| `JwtVerifier.java:54-58` + `ProblemDetailAuthEntryPoint.java:39-45` | JWT error classification by `getMessage().contains("expired")` — fragile string matching; `ExpiredJwtException` type signal discarded | WARNING (BL-02) | No SC violation: current message text "token expired" does contain "expired"; ITs pass. Future jjwt message change could silently flip error code. |
| `gradle/libs.versions.toml:17-20` | `springCloud = "2025.0.2"` silently overrides CLAUDE.md-specified `2024.0.x`; application.yml uses deprecated `spring.cloud.gateway.*` prefix (half-migrated) | WARNING (BL-03) | No SC violation: all 27 ITs pass with this version; smoke run passed. Process/policy deviation from locked stack without ADR. |
| `scripts/mint-test-token.sh` + `JwtFixturesSmokeMintTask.java` | `./gradlew :libs:jwt-common:test --tests JwtFixturesSmokeMintTask` writes JWT to JUnit XML test-result artifact in CI | INFO (BL-04) | No SC violation: token is short-lived (15 min), uses TEST_SECRET only, not production secret. CI artifact exposure risk. |
| `infra/docker-compose.yml` | `AUTH_JWT_SECRET` not declared in any service `environment:` block; container startup relies on shell environment variable being set externally | WARNING (IN-05) | Does not violate SCs as stated, but first-time-developer `docker compose up` will fail with `IllegalStateException` from `JwtVerifier` constructor unless `AUTH_JWT_SECRET` is pre-set in shell. The user-attested smoke run passed, indicating it was set during testing. |

---

### Human Verification Required

#### 1. SC#6 Zipkin UI Trace Continuity (Prior attestation exists)

**Test:** Start the full docker compose stack (`docker compose up -d --wait`), make an authenticated request through the gateway to trip-service (`curl -H "Authorization: Bearer $(bash scripts/mint-test-token.sh)" http://localhost:8080/api/trips/anything`), then open Zipkin UI at `http://localhost:9411` and search for the trace.

**Expected:** A single trace with at least two spans: one from `api-gateway` and one from `trip-service`, sharing the same 32-character hex trace ID.

**Why human:** `GatewayTraceContinuityIT` verifies MDC traceId matches W3C `traceparent` header received by the WireMock stub. It cannot verify that spans actually appear in Zipkin's UI (requires a running Zipkin instance with real span export). The Micrometer OTel OTLP/Zipkin export path is not tested in the automated suite.

**Prior attestation:** PASSED — manually confirmed 2026-05-09 during Plan 01-06 Task 6.3 user-verify gate. No re-testing needed unless compose stack or tracing config has changed since then.

---

### Advisory Concerns (from 01-REVIEW.md — not SC violations)

These items from the code review are advisory. They do NOT block Phase 1 completion but should be addressed in Phase 2 or a dedicated cleanup phase:

**BL-01 (Fix in Phase 2 cleanup):** Both `ProblemDetailAuthEntryPoint` and `RateLimitProblemDetailFilter` use `new ObjectMapper()` instead of Spring Boot's auto-configured `ObjectMapper`. The `ProblemDetailJacksonMixin` (registered only on the auto-configured bean) flattens extension properties to JSON root. Without it, `ProblemDetail.setProperty("code", ...)` serializes under `$.properties.code`. The gateway and downstream services produce different JSON shapes. Fix: inject `ObjectMapper mapper` via constructor in both gateway emitters, then update the gateway ITs from `$.properties.code` to `$.code`.

**BL-02 (Fix in Phase 2 cleanup):** JWT error classification by `getMessage().contains("expired")` is fragile. Introduce a `JwtAuthenticationException.Reason` enum (`EXPIRED`, `INVALID`, `MISSING_SUB`) and dispatch on `.reason()` instead of message text. This also removes the implicit dependency on jjwt's internal message formatting.

**BL-03 (ADR needed):** Spring Cloud `2025.0.2` is used instead of CLAUDE.md's `2024.0.x`. The deviation is technically correct (SB 3.5.x pairs with Spring Cloud 2025.0 per Spring's release matrix), but it bypasses the project's "no negotiation on locked stack" policy. Resolution: amend CLAUDE.md to document `2025.0.x` as the updated pin and complete the YAML prefix migration from deprecated `spring.cloud.gateway.*` to `spring.cloud.gateway.server.webflux.*`.

**BL-04 (Fix in Phase 2 cleanup):** `JwtFixturesSmokeMintTask` as a JUnit test class causes the signed JWT to be written to JUnit XML artifacts on every `./gradlew test` run. Replace with a Gradle `JavaExec` task (e.g., `mintTestToken`) that invokes a `main()` method in the testFixtures source set and does not produce test-result XML.

**IN-05 (Fix in Phase 1.5 or Phase 2):** `AUTH_JWT_SECRET` is not declared in any service's `environment:` block in `infra/docker-compose.yml`. Spring Boot's `JwtProperties` relies on Compose v2 passing the shell-environment value, which only works if the developer pre-exports `AUTH_JWT_SECRET` before running `docker compose up`. Fix: add `AUTH_JWT_SECRET: ${AUTH_JWT_SECRET}` to the `environment:` blocks of `api-gateway`, `trip-service`, and `destination-service` in `docker-compose.yml`.

---

### Gaps Summary

No gaps block Phase 1 goal achievement. All six Success Criteria are met or properly deferred:

- **SC#1** (routing): Fully verified by automated ITs and smoke.
- **SC#2** (missing auth → 401): Fully verified; downstream never reached.
- **SC#3** (forged/expired JWT → 401): Fully verified; expired vs. invalid correctly distinguished.
- **SC#4** (direct bypass → 401 from service): Fully verified by keystone ITs in both trip-service and destination-service.
- **SC#5** (rate limit): IP-only leg (30/15min) verified by `LoginRateLimiterIT`. Strict IP+email leg (5/15min) deferred to Phase 2 by documented D-05 split decision. Phase 2 ROADMAP SC#5 explicitly claims "login rate limit at attempt 6".
- **SC#6** (trace continuity): Automated MDC ↔ traceparent assertion passes; Zipkin UI confirmation is human-verified (prior attestation 2026-05-09).

**The phase is functionally complete.** The only open human verification item (SC#6 Zipkin UI) has a prior passing attestation from the same day. The advisory concerns (BL-01, BL-02, BL-03, BL-04, IN-05) from 01-REVIEW.md are real code quality issues that should be tracked for Phase 2 cleanup, but none invalidate the Phase 1 Success Criteria as stated in ROADMAP.md.

---

_Verified: 2026-05-09_
_Verifier: Claude (gsd-verifier)_
