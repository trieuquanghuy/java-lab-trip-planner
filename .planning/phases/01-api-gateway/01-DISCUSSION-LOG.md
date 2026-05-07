# Phase 1: API Gateway - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 01-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-08
**Phase:** 1-API Gateway
**Areas discussed:** jwt-common shape, Login rate-limit key (IP+email), Routing — Eureka lb:// vs static URL, Phase-1 test strategy

---

## jwt-common shape

### Q1 — Library structure given the reactive gateway / Servlet downstream split

| Option | Description | Selected |
|--------|-------------|----------|
| Verifier + 2 filters | Shared `JwtVerifier` + `ReactiveJwtAuthFilter` (WebFlux) + `ServletJwtCommonFilter` (Servlet) all packaged in `libs/jwt-common` | ✓ |
| Verifier only | Shared verifier; each service writes its own filter | |
| Skip shared lib | Inline JWT logic per service; refactor in Phase 2 | |

**User's choice:** Verifier + 2 filters
**Notes:** One crypto path; secret rotation and claim changes flow through one library.

### Q2 — Public vs. authenticated routes at the gateway

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Security WebFlux | `SecurityWebFilterChain` with `permitAll()` allowlist + `authenticated()` default | ✓ |
| Global filter + path matcher | Single `GlobalFilter` with hardcoded allowlist | |
| Per-route filter list | Each route declares its filters explicitly in YAML | |

**User's choice:** Spring Security WebFlux
**Notes:** Idiomatic, plays with CORS and CSRF, keeps allowlist auditable in one place.

### Q3 — How the servlet filter exposes user context to controllers

| Option | Description | Selected |
|--------|-------------|----------|
| Spring SecurityContextHolder | Filter populates `Authentication` whose principal is `UserContext`; controllers use `@AuthenticationPrincipal UserContext` | ✓ |
| Custom `@CurrentUser` argument resolver | Filter sets request attribute; custom `HandlerMethodArgumentResolver` exposes it | |
| Pass userId through method signature | Manual `request.getAttribute` in every controller | |

**User's choice:** Spring SecurityContextHolder
**Notes:** Idiomatic; integrates with `@WithMockUser`-style tests; audit logging picks up principal automatically.

---

## Login rate-limit key (IP+email)

### Q1 — Where does the IP+email keyed rate limit live?

| Option | Description | Selected |
|--------|-------------|----------|
| Split: gateway IP-only + auth-service per-email | Gateway runs a coarse IP-only limit; auth-service runs the strict 5/15min IP+email | ✓ |
| Body-caching custom KeyResolver at gateway | `AdaptCachedBodyGlobalFilter` + custom `KeyResolver` that JSON-parses the request body | |
| IP-only at gateway, accept the looser bound | Drop email key entirely; 5/15min per IP only | |

**User's choice:** Split
**Notes:** Avoids reactive body caching at gateway; places email-aware logic where the body already lives.

### Q2 — Phase 1 acceptance criterion #5 ("5/15min per IP+email; 6th = 429")

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 1 ships IP-only at 30/15min; criterion #5 full satisfaction is Phase 2 | Coarse IP gate now, email-aware leg in auth-service later; ROADMAP.md note updated | ✓ |
| Phase 1 ships IP-only at 5/15min, hit numbers literally | Risks blocking shared-IP users (cafes, NAT) on legitimate logins | |
| Cache request body at gateway after all | Reverse the prior decision; full spec from day one at the cost of gateway complexity | |

**User's choice:** Phase 1 ships IP-only at 30/15min
**Notes:** Documented as deliberate split; ROADMAP.md note for Phase 1 success criterion #5 needs updating.

### Q3 — Full rate-limit table scope for Phase 1

| Option | Description | Selected |
|--------|-------------|----------|
| All five routes ship in Phase 1 | Login, signup, search, destinations, trips all rate-limited at gateway with custom 429 ProblemDetail filter | ✓ |
| Only login + signup in Phase 1; rest follow downstream services | Limiters land when each owning phase ships | |

**User's choice:** All five routes
**Notes:** userId-keyed `*/api/trips/*` limiter must run after the JWT filter so userId is available in SecurityContext. 429s use RFC 7807 with `auth.rate_limited` code via custom error WebFilter.

---

## Routing — Eureka lb:// vs static URL

### Q1 — How should Phase 1 route from gateway to downstream services?

| Option | Description | Selected |
|--------|-------------|----------|
| Eureka `lb://` discovery | Gateway resolves via Eureka; matches ADR-4 exactly | |
| Static `http://service-name:port` | Compose-internal DNS; simpler, deterministic, no cold-start window | ✓ |
| Hybrid: lb:// in compose, static in IDE-run dev | Two configs; reduces lb:// codepath exercise | |

**User's choice:** Static URLs
**Notes:** Deviates from ADR-4 in routing path only. Removes Pitfall 10 from Phase 1's risk surface.

### Q2 — Eureka's role in Phase 1 if routing is static

| Option | Description | Selected |
|--------|-------------|----------|
| Eureka still runs; gateway is a Eureka client for the dashboard but not for routing | All services register so the dashboard demo works; routing stays static | ✓ |
| Drop Eureka entirely — revisit ADR-4 | Removes registry from architecture; significant scope shift | |
| Static now, switch to lb:// in Phase 10 | Defers lb:// path coverage 9 phases | |

**User's choice:** Eureka still runs (dashboard only in Phase 1 routing)
**Notes:** Preserves Phase 0 acceptance criterion #2 ("all services visible in Eureka"). Switching to lb:// is parked as a possible Phase 10 hardening item.

### Q3 — Gateway-to-downstream HTTP timeouts

| Option | Description | Selected |
|--------|-------------|----------|
| Connect 1s / response 5s | Tight; fails fast on stuck downstream | |
| Connect 1s / response 10s | More headroom for cold-start and bcrypt-12 login | ✓ |
| Defer to planner with a note | Don't pin numbers in CONTEXT.md | |

**User's choice:** Connect 1s / response 10s
**Notes:** Comfortably covers the 250ms bcrypt-12 worst case + cold-cache search bound + Eureka registration tail.

---

## Phase-1 test strategy

### Q1 — How do we e2e test gateway routing + JWT validation without auth-service?

| Option | Description | Selected |
|--------|-------------|----------|
| Sign test JWTs with shared secret + add stub controllers | Tests use `AUTH_JWT_SECRET` directly; downstream services ship `_ping` controllers that return resolved principal | ✓ |
| WireMock fake auth-service | Stand up a WireMock-backed fake that issues real signed JWTs | |
| Unit/contract tests only; defer e2e to Phase 2 | Push integration tests to Phase 2 | |

**User's choice:** Sign test JWTs + stub controllers
**Notes:** Closest to production semantics with zero mock infrastructure; the 8 mandatory security tests target real routes.

### Q2 — Test stack and stub controller lifecycle

| Option | Description | Selected |
|--------|-------------|----------|
| Testcontainers + WebTestClient + @SpringBootTest; stubs as permanent debug endpoints | Real Redis for rate limiter; `_ping` controllers stay as ops debug endpoints, won't conflict with Phase 5/4 routes | ✓ |
| Same stack, stubs `@Profile("test")` only | Cleaner production surface; slightly more test config | |
| MockWebServer / no Testcontainers | Faster but Redis-backed limiter assertions become harder | |

**User's choice:** Testcontainers + permanent stubs
**Notes:** `_ping` returns `{userId, email, verified, service, traceId}` — useful runtime sanity check.

---

## Claude's Discretion

- Field names inside `UserContext` (`userId` vs `id`, etc.)
- Whether `JwtVerifier` returns `Optional<UserContext>` or throws — researcher aligns with Spring Security `AuthenticationManager` contract
- Whether `_ping` controllers live in `libs/observability` or per-service
- Logback pattern vs `logstash-logback-encoder` for MDC — already specified in `docs/02-architecture.md §6.3` (logstash JSON encoder)

---

## Deferred Ideas

- **`lb://` Eureka-routed traffic** — parked for possible Phase 10 hardening
- **JWT secret rotation (two valid keys at once)** — flagged as v2 in `docs/05-auth-security.md §9.4`
- **Custom `KeyResolver` with body caching at gateway** — only revisit if Phase 2's auth-service-side email-aware limit proves insufficient

---

*Discussion conducted: 2026-05-08*
