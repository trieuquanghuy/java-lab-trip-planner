# Phase 1: API Gateway - Context

**Gathered:** 2026-05-08
**Status:** Ready for planning

<domain>
## Phase Boundary

The api-gateway becomes the single front-door for the SPA: it routes `/api/**` to the right downstream service, validates JWT signatures once, injects trusted user-context headers (`X-User-Id`, `X-User-Email`, `X-Request-Id`), enforces per-route rate limits via Redis, applies CORS, and emits trace context that propagates end-to-end to Zipkin.

This phase also stands up `libs/jwt-common` and wires the **defense-in-depth** JWT filter into trip-service and destination-service before either has real endpoints, so a request that bypasses the gateway and hits a downstream service port directly with a forged `X-User-Id` header is rejected with 401.

**Phase 1 owns success-criteria #1–#4 and #6 fully, plus the IP-only portion of #5.** Full IP+email keying for `/api/auth/login` is deferred to Phase 2 (auth-service owns the email-aware leg).

**In scope:**
- Spring Cloud Gateway configured with route table from `docs/04-api-spec.md`
- `libs/jwt-common`: shared `JwtVerifier` + `ReactiveJwtAuthFilter` (gateway) + `ServletJwtCommonFilter` (trip/destination)
- Spring Security WebFlux config at gateway: public allowlist + authenticated default
- Spring Security config at trip-service and destination-service: filter populates `SecurityContextHolder`
- Redis-backed `RequestRateLimiter` on all five rate-limited routes (login, signup, search, destinations, trips)
- Custom error WebFilter that translates gateway 401s and 429s into RFC 7807 ProblemDetail with stable codes (`auth.unauthorized`, `auth.rate_limited`)
- CORS allowlist `http://localhost:5173`, `allowCredentials: true`
- Static URL routing: gateway uses `http://service-name:port` URIs; all four services still register with Eureka for the dashboard demo
- HTTP client timeouts: connect=1s, response=10s
- `_ping` debug endpoints in trip-service and destination-service that return resolved `userId` + service name
- 4 security tests (anonymous-blocked, invalid-token-rejected, direct-service-bypass-blocked, login-rate-limit-IP-trips at 31st request)
- Zipkin trace continuity validated end-to-end (one traceId across gateway + downstream stub)

**Out of scope (deferred to other phases):**
- Email-aware login rate limit (Phase 2 inside auth-service)
- `lb://` Eureka-routed traffic (revisit in Phase 10 if desired)
- Per-route Resilience4j retry/circuit-breaker — Resilience4j is for external providers in Phase 4, not internal gateway→service hops
- Real auth-service / trip-service / destination-service endpoints (Phase 2 / Phase 5+ / Phase 3+)

</domain>

<decisions>
## Implementation Decisions

### libs/jwt-common library shape
- **D-01: Verifier + two framework-specific filters in one shared library.** `libs/jwt-common` ships:
  - `JwtVerifier` — parses `Authorization: Bearer ...`, validates HS256 signature against `AUTH_JWT_SECRET`, checks `exp`, extracts `sub` / `email` / `ver` into a `UserContext` record
  - `ReactiveJwtAuthFilter` (`org.springframework.web.server.WebFilter`) — for the WebFlux gateway
  - `ServletJwtCommonFilter` (`org.springframework.web.filter.OncePerRequestFilter`) — for trip-service and destination-service
- **D-02: JWT signature is validated twice on every authenticated request.** Gateway validates once (rejects bad tokens early), downstream filter re-validates as defense-in-depth (Pitfall 1). The `userId` is **always derived from the JWT `sub` claim**, never from `X-User-Id` alone — `X-User-Id` is a read-through convenience header and must match `sub` after validation.

### Public-route handling at gateway
- **D-03: Spring Security WebFlux `SecurityWebFilterChain` decides public vs. authenticated.** `permitAll()` for `/api/auth/login`, `/api/auth/signup`, `/api/auth/verify`, `/api/auth/refresh`, `/api/search/**`, `/api/destinations/**`, `/actuator/health`. Everything else is `authenticated()`. The `ReactiveJwtAuthFilter` runs as the `AuthenticationManager` source.

### Downstream user-context exposure
- **D-04: `SecurityContextHolder` + `@AuthenticationPrincipal UserContext`.** Servlet filter populates `SecurityContextHolder` with an `Authentication` whose principal is a `UserContext(userId, email, verified)` record. Controllers consume it via `@AuthenticationPrincipal UserContext`. Tests use Spring Security test slices (`@WithMockUser`-style fixtures bound to `UserContext`).

### Rate-limit topology
- **D-05: Split between gateway and auth-service for login.** Gateway enforces **IP-only** coarse limit on `POST /api/auth/login` at 30/15min (replenishRate ~2/min, burstCapacity 30). Phase 2's auth-service adds the strict **5/15min IP+email** layer once it can read the request body. Avoids reactive request-body caching at the gateway. **Update ROADMAP.md note for Phase 1**: success criterion #5 is partial in Phase 1 (IP gate ships); fully met in Phase 2 inside auth-service.
- **D-06: All five rate-limited routes ship in Phase 1.** Gateway runs `RedisRateLimiter` on:
  - `POST /api/auth/login` — 30/15min keyed by IP (replenishRate 2/min, burst 30)
  - `POST /api/auth/signup` — 3/h keyed by IP (replenishRate 1/1200s, burst 3)
  - `GET /api/search` — 60/min keyed by IP (replenishRate 1/sec, burst 60)
  - `GET /api/destinations*` — 60/min keyed by IP (replenishRate 1/sec, burst 60)
  - `* /api/trips/*` — 120/min keyed by `userId` (replenishRate 2/sec, burst 120) — limiter runs **after** the JWT filter so userId is in SecurityContext
- **D-07: 429 and 401 responses use RFC 7807 ProblemDetail.** Custom error WebFilter on the gateway translates the Spring Cloud Gateway default 429 (and 401 from Security) into `application/problem+json` with stable codes from `docs/04-api-spec.md §6` (`auth.unauthorized` for 401, `auth.rate_limited` for 429).

### Routing and discovery
- **D-08: Static URL routing in Phase 1.** Gateway routes use `uri: http://auth-service:8081`, `uri: http://trip-service:8082`, `uri: http://destination-service:8083` (compose-internal DNS). Eureka is **not** in the routing path.
- **D-09: All four services still register with Eureka.** Preserves ADR-4 demo signal and Phase 0 acceptance criterion #2 ("all services visible at `localhost:8761`"). Pitfall 10 (Eureka cold-start 503) does not apply to Phase 1 because routing doesn't depend on registry resolution. Switching to `lb://` is parked as a possible Phase 10 hardening item.
- **D-10: Gateway HTTP client timeouts: `connect-timeout: 1000ms`, `response-timeout: 10s`.** Tight connect for fail-fast on stuck downstream; generous response window for cold-cache search and bcrypt-12 login.

### Phase-1 testing
- **D-11: Sign test JWTs with the shared `AUTH_JWT_SECRET` using jjwt 0.13.0.** Tests use the same secret the production filter uses. Real JWT lifecycle (signup → verify → login → refresh → rotate) lands in Phase 2 once auth-service exists.
- **D-12: Permanent `_ping` stub controllers in trip-service and destination-service.** Each returns `{userId, email, verified, route, traceId}` after the filter resolves the principal:
  - `GET /api/trips/_ping` → trip-service
  - `GET /api/destinations/_ping` → destination-service
  These are operational debug endpoints (not test-only) so Phase 5+ real routes coexist with them. They're cheap and guard against silent route regressions.
- **D-13: Test stack — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient` for gateway, `@SpringBootTest` + `MockMvc` for downstream stubs, Testcontainers for Redis (rate limiter), Postgres only if a test exercises persistence.**
- **D-14: Phase 1 owns 4 security tests from docs/05-auth-security.md §10:**
  - `AnonymousCannotAccessAuthedEndpoints` (#1) — gateway 401 with no Auth header
  - `InvalidTokenIsRejected` (#2) — gateway 401 with malformed/expired/wrong-signature JWT
  - `LoginRateLimitTriggers` (#8 partial) — IP-only path: 31st `POST /api/auth/login` from same IP within 15min returns 429 with `auth.rate_limited`
  - `DirectServiceAccessWithoutGatewayReturns401` (Pitfall 1) — direct call to `localhost:8082/api/trips/_ping` with crafted `X-User-Id` and no JWT returns 401
  Tests #3–#7 belong to Phase 2 / Phase 5 / Phase 6 owners.
- **D-15: Each Phase 1 ROADMAP success criterion #1–#6 gets at least one integration test.**

### Cross-cutting (researcher confirms specifics)
- **D-16: JWT secret config.** Single `AUTH_JWT_SECRET` env var (32+ bytes) loaded via `@ConfigurationProperties("auth.jwt")` in `libs/jwt-common`. All four services consume the same property; sample value in `.env.example`.
- **D-17: MDC enrichment.** Both filters write `userId`, `traceId`, `spanId`, `requestId` to MDC after authentication so JSON logs auto-include them per Logback config (NFR-09 prep).
- **D-18: `X-Request-Id` propagation.** Gateway generates a UUID `X-Request-Id` when missing; passes it untouched when client sends one. Downstream filter copies it into MDC.
- **D-19: Zipkin trace continuity.** Set `management.tracing.sampling.probability: 1.0` in dev profile across all services. Phase 1 acceptance criterion #6 is asserted by an integration test that calls a stub through the gateway and verifies one shared `traceId` appears in both gateway and trip-service log events (or via Zipkin REST API in a Testcontainers-backed test).

### Claude's Discretion
- Exact field names inside `UserContext` (`userId` vs `id`, `email` vs `userEmail`) — pick whatever reads cleanest.
- Whether `JwtVerifier` returns `Optional<UserContext>` or throws `JwtAuthenticationException` — researcher should align with Spring Security's `AuthenticationManager` contract.
- Whether `_ping` controllers live in a shared `libs/observability` module or per-service — researcher decides based on dependency graph.
- Exact log-format details for MDC enrichment (Logback pattern vs `logstash-logback-encoder` JSON encoder) — `docs/02-architecture.md §6.3` already specifies JSON via logstash encoder.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Authentication and security
- `docs/05-auth-security.md` — JWT design (claims, HS256, secret loading), authorization model (resource-ownership at service layer), §10 mandatory security tests
- `docs/05-auth-security.md §3` — signup → verify → login sequence (Phase 2 context, helps clarify what Phase 1 must support upstream)
- `docs/05-auth-security.md §4` — authenticated request sequence (gateway validates → injects → downstream re-validates)
- `docs/05-auth-security.md §10` — 8 mandatory security tests (Phase 1 owns 4 of them, #1, #2, #8 partial, plus Pitfall-1's `DirectServiceAccessWithoutGatewayReturns401`)
- `.planning/research/PITFALLS.md` Pitfall 1 — X-User-Id header spoofing; Phase 1 must wire `JwtCommonFilter` into trip-service and destination-service even before they have real endpoints; the test `DirectServiceAccessWithoutGatewayReturns401` is the gate

### Architecture
- `docs/02-architecture.md §3` — service decomposition diagram (gateway :8080, services :8081/8082/8083, eureka :8761)
- `docs/02-architecture.md §3.1` — service responsibilities (gateway routes, JWT validation, header injection, rate limiting, CORS)
- `docs/02-architecture.md §4.2` — trust model (gateway validates JWT, injects headers, downstream re-validates)
- `docs/02-architecture.md §5` — repo layout (`services/api-gateway/`, `libs/jwt-common/`)
- `docs/02-architecture.md §6.3` — observability (Logback JSON, Micrometer Tracing, Zipkin)

### API contract
- `docs/04-api-spec.md §3` — auth-service routes (which are public)
- `docs/04-api-spec.md §4` — destination-service routes (public)
- `docs/04-api-spec.md §5` — trip-service routes (authenticated)
- `docs/04-api-spec.md §6` — error code catalog (stable codes the gateway and filter must emit)
- `docs/04-api-spec.md §7` — rate-limit table (gateway implementation)
- `docs/04-api-spec.md §8` — CORS spec (allowlist, methods, headers, credentials)

### Pitfalls and operational risks
- `.planning/research/PITFALLS.md` Pitfall 7 — trace context lost at gateway boundary (Phase 0 + Phase 1; sets `management.tracing.sampling.probability` and confirms single trace ID end-to-end)
- `.planning/research/PITFALLS.md` Pitfall 10 — Eureka registration lag (does **not** apply to Phase 1 since routing is static, but Eureka registration timing still matters for Phase 0 dashboard health)

### Roadmap and requirements
- `.planning/ROADMAP.md` Phase 1 — success criteria #1–#6 (note: #5 wording needs an update to reflect the IP-only / IP+email split)
- `.planning/REQUIREMENTS.md` NFR-02 (per-user authorization, 404 not 403), NFR-06 (OWASP A01/A07 contributions from Phase 1)

### Stack and version pins
- `CLAUDE.md` — locked tech stack with 2026 pin overrides (Spring Boot 3.5.x, Spring Cloud 2024.0.x, jjwt 0.13.0)

</canonical_refs>

<code_context>
## Existing Code Insights

This is a greenfield project — no source files exist yet. Phase 0 (Monorepo Scaffolding) ships:
- `services/api-gateway/`, `services/auth-service/`, `services/trip-service/`, `services/destination-service/`, `services/eureka-server/` skeletons that boot empty
- `libs/api-contracts/`, `libs/error-handling/`, `libs/observability/` empty modules
- `docker-compose.yml` with postgres, redis, mailhog, zipkin, eureka, four services, frontend
- Gradle Kotlin DSL multi-module setup with version catalog

Phase 1 is the **first phase that lands real domain logic** — specifically the security gate. No existing patterns to follow yet; Phase 1's choices set the conventions.

### Reusable assets (about to be created in Phase 0)
- `libs/error-handling/` — RFC 7807 ProblemDetail shape and `ErrorCode` constants. Phase 1's gateway error WebFilter and downstream `JwtCommonFilter` both consume this.
- `libs/observability/` — Micrometer/OTel autoconfig. Phase 1 confirms it propagates traceId across gateway → downstream.
- `libs/api-contracts/` — shared DTOs. Phase 1 doesn't add domain DTOs but defines the `UserContext` record (consumed by every downstream service).

### Patterns this phase establishes
- **Filter ordering at the gateway:** CORS → trace context → JWT auth → rate limit → route. Documented in `services/api-gateway/src/main/resources/application.yml` and `WebFluxSecurityConfig`.
- **Servlet filter ordering at downstream:** trace context → JWT common → MDC enrichment → controller.
- **Test fixture for signed JWTs:** a `JwtFixtures` helper in `libs/jwt-common`'s `testFixtures` source set that signs tokens with arbitrary claims.

### Integration points
- Auth-service (Phase 2) plugs into the gateway's existing `/api/auth/**` routes; the public-route allowlist in WebFluxSecurityConfig already permits them.
- Trip-service (Phase 5+) replaces `/api/trips/_ping` with real CRUD; the JWT filter and rate limiter remain unchanged.
- Destination-service (Phase 3+) replaces `/api/destinations/_ping` and `/api/search` stubs with real handlers.

</code_context>

<specifics>
## Specific Ideas

- The `_ping` stub controllers should return `{userId, email, verified, service, traceId}` — useful as a runtime sanity check ("is the gateway routing to the right service with the right principal?").
- `application-dev.yml` and `application-docker.yml` are the two profiles in scope. No prod profile in v1.
- Redis keyspace prefix for the rate limiter: `rl:` (e.g. `rl:login:ip:198.51.100.7`). Keeps it from colliding with destination-service's L1 cache (`search:` / `dest:`).
- Phase 1 work updates the ROADMAP.md note for Phase 1 to reflect the split ("IP-only at gateway in this phase; full IP+email keying lands in Phase 2 within auth-service") and adjusts the success-criterion #5 wording.

</specifics>

<deferred>
## Deferred Ideas

- **`lb://` Eureka-routed traffic** — parked for possible Phase 10 hardening if we want load-balanced path coverage. Not required for v1's portfolio scope.
- **JWT secret rotation (two valid keys at once)** — `docs/05-auth-security.md §9.4` flags this as v2.
- **Custom `KeyResolver` with body caching at gateway** — only revisit if Phase 2's auth-service-side limit proves too coarse in practice.

</deferred>

---

*Phase: 1-API Gateway*
*Context gathered: 2026-05-08*
