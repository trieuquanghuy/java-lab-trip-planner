# Phase 1: API Gateway - Research

**Researched:** 2026-05-08
**Domain:** Spring Cloud Gateway 2025.0 (Northfields) — JWT validation gate, header injection, defense-in-depth, Redis rate limiting, end-to-end trace continuity
**Confidence:** HIGH (Spring Cloud / jjwt / Spring Security WebFlux are well-trodden ground; locked decisions in CONTEXT.md remove most ambiguity)

## Summary

Phase 1 is a security gate, not a feature. It turns the inert Phase 0 scaffold into the project's only public surface: the gateway authenticates every authenticated request once, mutates `X-User-Id` from the JWT `sub` claim (stripping any client-supplied value), and downstream servlet services re-validate the same JWT for defense-in-depth (Pitfall 1). The locked decisions in `01-CONTEXT.md` collapse most architecture choices — Spring Security WebFlux on the gateway, `SecurityContextHolder` on downstream, Redis-backed `RequestRateLimiter` on five routes, static-URI routing — so research focuses on three things the planner needs concrete code for: the jjwt 0.13.0 verifier, the WebFlux `AuthenticationWebFilter` + `ServerSecurityContextRepository` pattern, and the `redis-rate-limiter` math for sub-1-rps quotas.

Two version landmines surfaced. First, Spring Cloud 2025.0.2 (Northfields, the train Phase 0 already pinned via D-30) ships Spring Cloud Gateway **5.0.x as the new aligned line**, but the legacy `4.2.x` / `4.3.x` lines are still maintained for Spring Boot 3.4/3.5 users — and the artifact `spring-cloud-starter-gateway` was renamed to `spring-cloud-starter-gateway-server-webflux` in 4.3 (use of the old name now logs a deprecation warning). Phase 0's `build.gradle.kts` uses the deprecated name (`libs.spring.cloud.starter.gateway`); leave it as-is in Phase 1 (it still works) but log this for Phase 10 hardening. Second, two recent Spring Cloud Gateway CVEs (CVE-2025-41235 spoofed X-Forwarded headers, CVE-2025-41253 SpEL injection via misconfigured actuator) were patched in 4.2.5 / 4.3.2 — the 2025.0.2 BOM picks up patched versions, but it's worth asserting in plan acceptance.

**Primary recommendation:** Implement `libs/jwt-common` as Verifier + `ReactiveJwtAuthFilter` (`AuthenticationWebFilter` wired into `SecurityWebFilterChain`) + `ServletJwtCommonFilter` (`OncePerRequestFilter`) in three classes. Use jjwt 0.13.0's modern `Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(jws).getPayload()` API (NOT the deprecated `setSigningKey`). Drive `RedisRateLimiter` with `replenishRate / requestedTokens / burstCapacity` per the sub-1-rps formula (1 token per request, replenish at the per-second equivalent, burst = window count). Verify trace continuity in a Testcontainers-backed integration test that asserts a single `traceId` in both gateway and downstream MDC after one routed call.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| JWT signature + expiry validation (request entry) | API gateway (WebFlux) | — | Single crypto check at the edge; rejects bad tokens before they consume downstream resources. |
| JWT re-validation (defense-in-depth) | API / Backend (trip + destination servlet services) | — | Pitfall 1 mitigation: a request that bypasses the gateway and hits `localhost:8082` directly with a crafted `X-User-Id` is rejected because the servlet filter re-runs the same `JwtVerifier` and finds no JWT. |
| `X-User-Id` / `X-User-Email` / `X-Request-Id` injection | API gateway (WebFlux) | — | Gateway is the only component with the validated principal; it strips any client-supplied `X-User-Id` and writes its own. |
| Public-route allowlist (`/api/auth/*`, `/api/search/*`, `/api/destinations/*`) | API gateway (Spring Security WebFlux) | — | Centralized in `SecurityWebFilterChain` so the audit trail is one file (D-03). |
| Redis-backed rate limiting (5 routes) | API gateway (WebFlux) | Database / Storage (Redis) | `RequestRateLimiter` filter; Redis stores the token bucket. Strict IP+email login gate is split to auth-service (Phase 2). |
| RFC 7807 ProblemDetail emission for 401/429 | API gateway (custom error WebFilter) | `libs/error-handling` | `ProblemDetailFactory` already exists; Phase 1 adds a `WebExceptionHandler`/`WebFilter` that translates Security's `AuthenticationException` and the rate limiter's 429 into `application/problem+json`. |
| `UserContext` exposure to controllers | API / Backend (trip + destination, via `SecurityContextHolder`) | `libs/api-contracts` | Servlet filter sets `Authentication.getPrincipal() == UserContext`; controllers use `@AuthenticationPrincipal UserContext`. |
| MDC enrichment of `userId` / `requestId` | Cross-cutting (libs/observability + jwt-common collab) | All API tiers | `libs/observability`'s MDC filter already writes `traceId`/`spanId`/`requestId`. Phase 1 adds `userId` write **after** auth resolves. |
| Trace continuity (gateway → downstream) | Cross-cutting (Micrometer Tracing → OTel → Zipkin) | All API tiers | Auto-config in `libs/observability` is already pinned (Phase 0 D-04). Phase 1 only validates that one routed request shows one `traceId` end-to-end. |
| CORS allowlist (`http://localhost:5173`) | API gateway (Spring Cloud Gateway global CORS config) | — | Single source of truth at the edge; downstream services don't need CORS config. |

**Why this matters:** Misassigning the JWT re-validation to Phase 2 (the most common failure mode — "auth-service owns auth, so the filter goes there") is exactly Pitfall 1's trigger. Locking the responsibility map to "gateway validates AND downstream filters re-validate" before Phase 2 even ships is the discipline that makes `DirectServiceAccessWithoutGatewayReturns401` pass on day one.

## Standard Stack

### Core (Backend — Phase 1 additions on top of Phase 0)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Cloud Gateway (server-webflux) | 5.0.x or 4.2.5+/4.3.2+ (managed by Spring Cloud 2025.0.2 BOM) | Routing + filter chain | Already on classpath via Phase 0's `spring-cloud-starter-gateway` (deprecated artifact name still works; see Pitfall E2). [VERIFIED: spring.io 2025.0.0 announcement; mvnrepository] |
| Spring Boot Security Starter (WebFlux) | managed by SB 3.5.14 (Spring Security 6.4.x) | `SecurityWebFilterChain`, `AuthenticationWebFilter`, `ServerSecurityContextRepository` | Spring-native auth pattern for reactive gateway. Add `org.springframework.boot:spring-boot-starter-security` to api-gateway. [CITED: docs.spring.io/spring-security/reference/reactive] |
| Spring Boot Security Starter (Servlet) | managed by SB 3.5.14 | Filter chain registration on trip-service / destination-service (`SecurityFilterChain`) | Adds `OncePerRequestFilter` ordering + `SecurityContextHolder` integration. Add to trip-service + destination-service. [CITED: docs.spring.io/spring-security/reference/servlet] |
| jjwt | 0.13.0 (`jjwt-api` + `jjwt-impl` + `jjwt-jackson`) | JWT parsing + HS256 verification | Already in `libs.versions.toml` (Phase 0 forward-loaded via D-26). Use the **modern** API: `Jwts.parser().verifyWith(key).build().parseSignedClaims(jws)`. The 0.12.x → 0.13.0 jump is API-compatible. [VERIFIED: javadoc.io/static/io.jsonwebtoken/jjwt-api/0.13.0; jjwt GitHub releases] |
| Spring Data Redis Reactive | managed by SB 3.5.14 | Backing store for `RedisRateLimiter` | Required for `RequestRateLimiter` filter; `spring-boot-starter-data-redis-reactive`. [CITED: docs.spring.io/spring-cloud-gateway/.../requestratelimiter-factory] |
| Lettuce (Redis client) | managed by SB 3.5.14 | Reactive Redis driver | Default pulled by `spring-boot-starter-data-redis-reactive`. [CITED: SB 3.5 dep mgmt] |
| Testcontainers Redis | managed by SB 3.5.14 (`testcontainers-bom`) | Integration test fixture for rate limiter | Use `@ServiceConnection` (SB 3.1+) to auto-wire. [VERIFIED: SB 3.1+ Testcontainers integration] |

### Core (No frontend changes in Phase 1)

Phase 1 ships zero frontend code. The only frontend-side adjacency is that the existing `frontend/src/lib/axios.ts` already sets `withCredentials: true` and stamps `X-Request-Id` (Phase 0 SUMMARY 00-09); both are correct for Phase 2's cookie + tracing flow.

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot Test (WebFlux) | managed by SB 3.5.14 | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient` | Gateway integration tests (D-13). |
| Spring Security Test | managed by SB 3.5.14 (`spring-security-test`) | `@WithMockUser`-style Reactive auth fixtures | Helper for unit tests on `SecurityWebFilterChain` allowlist; full e2e uses real signed JWTs (D-11). |
| WireMock Spring Boot | 3.9.0 (already in catalog) | Stub downstream services in gateway tests | Optional — D-11 prefers real signed JWTs hitting real `_ping` controllers, which removes WireMock from Phase 1's critical path. WireMock is still useful for "downstream returns 5xx, gateway surfaces 502" cases. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring Security WebFlux `SecurityWebFilterChain` | Single custom `GlobalFilter` with hardcoded allowlist | DISCUSSION-LOG.md Q2 rejected — Security gives idiomatic CORS/CSRF integration and a single auditable allowlist. Custom GlobalFilter scatters auth logic. |
| jjwt 0.13.0 | Spring Security OAuth2 Resource Server (Nimbus JOSE) | ADR-5 in PROJECT.md explicitly chose hand-rolled jjwt over `oauth2-resource-server`. v2 will revisit — for v1 jjwt is the locked path. |
| `RedisRateLimiter` (built-in) | Resilience4j RateLimiter | DISCUSSION-LOG.md Q3 / Q1 selected gateway's built-in `RequestRateLimiter`. Resilience4j is reserved for Phase 4 external-provider circuit breaking; using it here would split rate-limit knowledge across two libraries. |
| Eureka `lb://` routing | Static `http://service:port` | D-08 / DISCUSSION-LOG Q1 selected static. Removes Pitfall 10 from Phase 1's risk surface. Phase 0 already enforces this via Convention C9 hard rule. |
| Body-caching `KeyResolver` for `IP+email` login key | Phase 1 ships **IP-only at 30/15min**; auth-service ships strict `5/15min IP+email` in Phase 2 | D-05 / DISCUSSION-LOG Q1 — avoids reactive request-body caching complexity at the gateway. ROADMAP SC#5 wording must be updated to reflect the split (D-05 already calls this out). |

**Installation (Phase 1 dep additions per service):**

```kotlin
// services/api-gateway/build.gradle.kts (additions to Phase 0 deps)
dependencies {
    implementation(project(":libs:jwt-common"))                          // NEW Phase 1 module
    implementation(libs.spring.boot.starter.security)                    // NEW
    implementation(libs.spring.boot.starter.data.redis.reactive)         // NEW (rate limiter backing store)
    // Existing: spring-cloud-starter-gateway, eureka-client, observability bundle, error-handling, api-contracts
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)                        // NEW
    testImplementation(libs.spring.boot.testcontainers)                  // NEW
    testImplementation(libs.testcontainers.junit.jupiter)                // NEW (Redis container)
}

// services/trip-service/build.gradle.kts (additions)
dependencies {
    implementation(project(":libs:jwt-common"))                          // NEW
    implementation(libs.spring.boot.starter.security)                    // NEW (servlet)
    // Existing servlet web + jpa + flyway + observability + ...
    testImplementation(libs.spring.security.test)                        // NEW
}

// services/destination-service/build.gradle.kts — same as trip-service additions
```

**Catalog additions to `gradle/libs.versions.toml`:**

```toml
[libraries]
spring-boot-starter-security        = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-redis-reactive = { module = "org.springframework.boot:spring-boot-starter-data-redis-reactive" }
spring-security-test                = { module = "org.springframework.security:spring-security-test" }
testcontainers-junit-jupiter        = { module = "org.testcontainers:junit-jupiter" }
testcontainers-redis                = { module = "com.redis:testcontainers-redis", version = "2.2.2" }  # optional convenience wrapper; or use plain GenericContainer<>
```

**Version verification (planner: run before Wave 1):**

```bash
# All managed by SB 3.5 BOM — confirm against latest 3.5.x patch
./gradlew :services:api-gateway:dependencies --configuration runtimeClasspath | grep -E '(spring-security|jjwt|spring-data-redis|micrometer-tracing)'
# Expected: jjwt-* 0.13.0, spring-security-* 6.4.x, spring-data-redis 3.4.x (or whatever SB 3.5.14 manages)
```

## Architecture Patterns

### System Architecture Diagram

```
                            ┌─────────────────────────────────┐
   Browser (SPA)            │            REDIS                │
   (Phase 7+)               │   rl:login:ip:<ip>      bucket  │
        │                   │   rl:signup:ip:<ip>             │
        │ HTTPS-not-yet     │   rl:search:ip:<ip>             │
        │ Authorization:    │   rl:dest:ip:<ip>               │
        │   Bearer eyJ…     │   rl:trips:user:<userId>        │
        ▼                   └────────────▲────────────────────┘
┌──────────────────────────────────────────┼─────────────────┐
│ api-gateway (WebFlux, port 8080)         │                 │
│                                           │                 │
│  ServerWebExchange entry                  │                 │
│       │                                   │                 │
│  ┌────▼──────────────────────────────┐    │                 │
│  │ ReactiveMdcEnrichmentFilter       │    │                 │
│  │   (libs/observability — already   │    │                 │
│  │    in place; writes traceId/      │    │                 │
│  │    spanId/requestId to MDC)       │    │                 │
│  └────┬──────────────────────────────┘    │                 │
│       │                                   │                 │
│  ┌────▼──────────────────────────────┐    │                 │
│  │ Spring Security WebFilterChain    │    │                 │
│  │  permitAll: /api/auth/{login,     │    │                 │
│  │    signup,verify,refresh},        │    │                 │
│  │    /api/search/**, /api/dest*/**, │    │                 │
│  │    /actuator/health,              │    │                 │
│  │    /__health/**                   │    │                 │
│  │  authenticated: everything else   │    │                 │
│  │                                   │    │                 │
│  │  AuthenticationWebFilter          │    │                 │
│  │   ↓ converter: Bearer header →    │    │                 │
│  │     UsernamePasswordAuthToken     │    │                 │
│  │   ↓ ReactiveAuthenticationManager │    │                 │
│  │     → JwtVerifier (jjwt 0.13.0,   │    │                 │
│  │        verifyWith(secretKey))     │    │                 │
│  │   ↓ on failure → 401 + RFC 7807   │    │                 │
│  │     {code: auth.unauthorized}     │    │                 │
│  └────┬──────────────────────────────┘    │                 │
│       │                                   │                 │
│  ┌────▼──────────────────────────────┐    │                 │
│  │ XUserIdInjectionGlobalFilter      │    │                 │
│  │   .mutate().headers(h -> {        │    │                 │
│  │     h.remove("X-User-Id")         │  ← STRIPS client-    │
│  │     h.set("X-User-Id", ctx.sub)   │     supplied value!  │
│  │     h.set("X-User-Email", ...)    │     (Pitfall 1)      │
│  │     h.set("X-Request-Id", reqId)  │                      │
│  │   })                               │                      │
│  └────┬──────────────────────────────┘    │                 │
│       │                                   │                 │
│  ┌────▼──────────────────────────────┐    │                 │
│  │ RequestRateLimiter (built-in)     │────┘ HINCRBY/SCRIPT  │
│  │   redis-rate-limiter:             │                       │
│  │   replenishRate/requestedTokens/  │                       │
│  │   burstCapacity per route         │                       │
│  │   keyResolver = IP or userId      │                       │
│  │   429 → ProblemDetail              │                       │
│  └────┬──────────────────────────────┘                      │
│       │                                                      │
│  ┌────▼──────────────────────────────┐                      │
│  │ Spring Cloud Gateway routing      │                      │
│  │  static URI per D-08:             │                      │
│  │   /api/auth/**   → :8081          │                      │
│  │   /api/trips/**  → :8082          │                      │
│  │   /api/search/** → :8083          │                      │
│  │   /api/destinations*/** → :8083   │                      │
│  │  (existing /__health/<svc>        │                      │
│  │   stays at top of route table)    │                      │
│  │  HttpClient timeouts: connect=1s, │                      │
│  │   response=10s (D-10)             │                      │
│  └────┬──────────────────────────────┘                      │
└───────┼──────────────────────────────────────────────────────┘
        │ HTTP + traceparent + X-User-Id (gateway-injected)
        ▼
┌─────────────────────────────────────────┐  ┌──────────────────────┐
│ trip-service (Servlet, port 8082)       │  │ destination-service  │
│                                          │  │ (port 8083, same    │
│  MdcEnrichmentFilter (existing)         │  │  shape as trip)     │
│       │                                  │  └──────────────────────┘
│  ServletJwtCommonFilter                  │
│   (OncePerRequestFilter):                │
│   - Authorization header REQUIRED        │
│   - JwtVerifier.verify() — same shared   │
│     instance from libs/jwt-common        │
│   - On success: SecurityContextHolder    │
│     <- UserContext(userId, email, ver)   │
│     MDC.put("userId", userId)            │
│   - On failure: 401 + RFC 7807           │
│       │                                  │
│  Spring Security FilterChain             │
│   (authenticated() default)              │
│       │                                  │
│  Controllers: GET /api/trips/_ping       │
│   @AuthenticationPrincipal UserContext   │
│   → returns {userId, email, verified,    │
│      service: "trip-service",            │
│      traceId, requestId}                 │
└──────────────────────────────────────────┘
```

### Recommended Project Structure

```
libs/
├── jwt-common/                       # NEW Phase 1 module
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/tripplanner/jwt/
│       │   ├── JwtAutoConfiguration.java       # @AutoConfiguration, @ConditionalOnWebApplication(REACTIVE|SERVLET) split
│       │   ├── JwtProperties.java              # @ConfigurationProperties("auth.jwt") — secret, expiry tolerance
│       │   ├── JwtVerifier.java                # core jjwt 0.13.0 parse+verify; pure (no Spring)
│       │   ├── JwtAuthenticationException.java # extends AuthenticationException
│       │   ├── reactive/
│       │   │   ├── ReactiveJwtAuthFilter.java  # AuthenticationWebFilter wired into chain
│       │   │   ├── ReactiveJwtAuthenticationManager.java
│       │   │   └── ServerBearerTokenConverter.java  # Authorization → Authentication
│       │   └── servlet/
│       │       ├── ServletJwtCommonFilter.java # OncePerRequestFilter
│       │       └── ServletJwtSecurityConfig.java # @ConditionalOnWebApplication(SERVLET) marker
│       ├── main/resources/
│       │   └── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│       └── testFixtures/java/com/tripplanner/jwt/                  # Gradle java-test-fixtures plugin
│           └── JwtFixtures.java                # mintToken(userId, email, verified, ttl), mintExpired(...), mintWrongSig(...)

libs/api-contracts/                   # ADD UserContext record
└── src/main/java/com/tripplanner/contracts/
    └── UserContext.java              # public record UserContext(String userId, String email, boolean verified) implements Principal

libs/error-handling/                  # ADD new ErrorCode constants (non-breaking enum extension)
└── src/main/java/com/tripplanner/errors/
    └── ErrorCode.java                # adds: AUTH_INVALID_TOKEN, AUTH_TOKEN_EXPIRED, BAD_GATEWAY (502)
                                       # — keeps existing AUTH_UNAUTHORIZED, AUTH_RATE_LIMITED

services/api-gateway/
└── src/main/
    ├── java/com/tripplanner/gateway/
    │   ├── ApiGatewayApplication.java                  # existing
    │   ├── health/GatewayHealthController.java         # existing
    │   ├── security/
    │   │   ├── WebFluxSecurityConfig.java              # NEW: SecurityWebFilterChain + permitAll allowlist + AuthenticationWebFilter wiring
    │   │   ├── XUserIdInjectionGlobalFilter.java       # NEW: GlobalFilter (Ordered, after Security) — strip + inject
    │   │   ├── ProblemDetailAuthEntryPoint.java        # NEW: ServerAuthenticationEntryPoint → ProblemDetailFactory
    │   │   └── RateLimitProblemDetailFilter.java       # NEW: WebFilter wrapping RequestRateLimiter to translate 429 → ProblemDetail
    │   └── routing/
    │       └── KeyResolverConfig.java                  # NEW: @Bean ipKeyResolver, @Bean userIdKeyResolver
    └── resources/
        ├── application.yml                              # extend route table (append /api/* below /__health/*); add CORS; rate-limiter config
        ├── application-docker.yml                       # add Redis host=redis (docker), keep eureka tuning
        └── logback-spring.xml                           # existing

services/trip-service/                # symmetric with destination-service
└── src/main/java/com/tripplanner/trip/
    ├── security/
    │   └── ServletSecurityConfig.java                  # NEW: SecurityFilterChain authenticated() default + filter ordering
    ├── health/
    │   ├── HealthPlaceholderController.java            # existing /__health
    │   └── PingController.java                         # NEW: @GetMapping("/api/trips/_ping") — returns {userId, email, verified, service, traceId, requestId}
```

### Pattern 1: jjwt 0.13.0 — HS256 Verifier (the keystone)

**What:** Pure-Java verifier with no Spring dependency, used by both reactive and servlet filters.
**When to use:** Phase 1 — every JWT validation site in the project.

```java
// libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java
package com.tripplanner.jwt;

import com.tripplanner.contracts.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class JwtVerifier {

    private final SecretKey signingKey;

    public JwtVerifier(String secret) {
        // RFC 7518 §3.2: HS256 requires key >= 256 bits. AUTH_JWT_SECRET MUST be >= 32 bytes (UTF-8).
        // Validate at construction time — fail-fast on misconfigured prod-like envs.
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                "AUTH_JWT_SECRET must be at least 32 bytes (256 bits) for HS256; got " + bytes.length);
        }
        this.signingKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Parse and verify a compact JWS string.
     * Throws JwtAuthenticationException for any failure (malformed, bad sig, expired, missing claim).
     */
    public UserContext verify(String compactJws) throws JwtAuthenticationException {
        try {
            // jjwt 0.13.0 modern API — verifyWith() replaces deprecated setSigningKey().
            // parseSignedClaims() returns Jws<Claims>; getPayload() returns Claims.
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(compactJws);

            Claims c = jws.getPayload();
            String sub = c.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new JwtAuthenticationException("token missing 'sub' claim");
            }
            String email = c.get("email", String.class);
            Boolean ver = c.get("ver", Boolean.class);
            return new UserContext(sub, email, Boolean.TRUE.equals(ver));

        } catch (ExpiredJwtException ex) {
            throw new JwtAuthenticationException("token expired", ex);
        } catch (JwtException ex) {
            throw new JwtAuthenticationException("token invalid", ex);
        }
    }
}
```

**Source:** [VERIFIED: javadoc.io/static/io.jsonwebtoken/jjwt-api/0.13.0/io/jsonwebtoken/JwtParserBuilder.html] — `verifyWith(SecretKey)` is the documented method; `parseSignedClaims(String)` returns `Jws<Claims>`.

**Pitfall A — do NOT use the deprecated builder pattern.** Older blog posts show `Jwts.parserBuilder().setSigningKey(...).build().parseClaimsJws(...)`. Both methods are deprecated in 0.12+; they'll be removed before 1.0. The above code uses the current API and will not need a rewrite at the next major.

### Pattern 2: WebFlux `AuthenticationWebFilter` Wiring

**What:** The idiomatic Spring Security WebFlux pattern is `AuthenticationWebFilter` + `ServerAuthenticationConverter` + `ReactiveAuthenticationManager`. Per DISCUSSION-LOG Q2, this lives in `WebFluxSecurityConfig.SecurityWebFilterChain`.

```java
// services/api-gateway/src/main/java/com/tripplanner/gateway/security/WebFluxSecurityConfig.java
package com.tripplanner.gateway.security;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.jwt.JwtVerifier;
import com.tripplanner.jwt.JwtAuthenticationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class WebFluxSecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            JwtVerifier verifier,
            ProblemDetailAuthEntryPoint entryPoint
    ) {
        AuthenticationWebFilter jwtFilter =
                new AuthenticationWebFilter(authenticationManager(verifier));
        jwtFilter.setServerAuthenticationConverter(bearerConverter());
        // Stateless gateway — no session, no SecurityContext persistence between requests.
        jwtFilter.setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance());

        return http
                .cors(cors -> cors.configurationSource(corsSource()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)        // bearer tokens, not cookies, on /api/**
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/__health/**", "/actuator/health", "/actuator/info").permitAll()
                        .pathMatchers("/api/auth/login", "/api/auth/signup",
                                      "/api/auth/verify", "/api/auth/refresh").permitAll()
                        .pathMatchers("/api/search/**").permitAll()
                        .pathMatchers("/api/destinations/**").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtFilter, org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(eh -> eh.authenticationEntryPoint(entryPoint))
                .build();
    }

    /** Pulls Bearer token off the Authorization header. Returns Mono.empty() for missing/wrong scheme so the chain falls through to permitAll() routes. */
    private ServerAuthenticationConverter bearerConverter() {
        return exchange -> {
            String h = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (h == null || !h.startsWith("Bearer ")) return Mono.empty();
            String token = h.substring("Bearer ".length()).trim();
            if (token.isEmpty()) return Mono.empty();
            return Mono.just(new BearerTokenAuthentication(token));
        };
    }

    private ReactiveAuthenticationManager authenticationManager(JwtVerifier verifier) {
        return auth -> {
            String token = (String) auth.getCredentials();
            try {
                UserContext principal = verifier.verify(token);
                Authentication authenticated = new PreAuthenticatedAuthentication(
                        principal, token, AuthorityUtils.createAuthorityList("ROLE_USER"));
                return Mono.just(authenticated);
            } catch (JwtAuthenticationException ex) {
                return Mono.error(new org.springframework.security.authentication.BadCredentialsException(
                        ex.getMessage(), ex));
            }
        };
    }

    private UrlBasedCorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("http://localhost:5173"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        cfg.setExposedHeaders(List.of("X-Request-Id"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }

    static class BearerTokenAuthentication extends AbstractAuthenticationToken {
        private final String token;
        BearerTokenAuthentication(String token) { super(List.of()); this.token = token; setAuthenticated(false); }
        @Override public Object getCredentials() { return token; }
        @Override public Object getPrincipal()   { return null; }
    }

    static class PreAuthenticatedAuthentication extends AbstractAuthenticationToken {
        private final UserContext principal;
        private final String token;
        PreAuthenticatedAuthentication(UserContext p, String tok, java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> auths) {
            super(auths); this.principal = p; this.token = tok; setAuthenticated(true);
        }
        @Override public Object getCredentials() { return token; }
        @Override public Object getPrincipal()   { return principal; }
    }
}
```

**Source:** [CITED: docs.spring.io/spring-security/reference/reactive/configuration/webflux.html] — `AuthenticationWebFilter` wiring with `ServerAuthenticationConverter` + `ReactiveAuthenticationManager` + `setSecurityContextRepository(NoOpServerSecurityContextRepository.getInstance())` is the documented stateless pattern.

### Pattern 3: `XUserIdInjectionGlobalFilter` — Strip + Inject (Pitfall 1 keystone)

```java
// services/api-gateway/src/main/java/com/tripplanner/gateway/security/XUserIdInjectionGlobalFilter.java
package com.tripplanner.gateway.security;

import com.tripplanner.contracts.UserContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Runs AFTER Spring Security AuthenticationWebFilter so the SecurityContext is populated.
 * Strips any client-supplied X-User-Id / X-User-Email and replaces them with values
 * derived from the validated JWT principal. Pitfall 1 keystone.
 *
 * Order: AUTHENTICATION (Spring Security) is at -200 in SecurityWebFiltersOrder; we run
 * after Security but before the routing filter (NettyRoutingFilter is at Ordered.LOWEST).
 */
@Component
public class XUserIdInjectionGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated()
                                && auth.getPrincipal() instanceof UserContext)
                .map(auth -> (UserContext) auth.getPrincipal())
                .flatMap(user -> {
                    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
                    if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
                    final String reqId = requestId;
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(h -> {
                                // STRIP first, then inject — order matters per Pitfall 1.
                                h.remove("X-User-Id");
                                h.remove("X-User-Email");
                                h.set("X-User-Id", user.userId());
                                if (user.email() != null) h.set("X-User-Email", user.email());
                                h.set("X-Request-Id", reqId);
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                // No SecurityContext (public route) — still strip any client-supplied X-User-Id
                // so a public route can never spoof identity onto a downstream call later.
                .switchIfEmpty(Mono.defer(() -> {
                    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
                    if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
                    final String reqId = requestId;
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(h -> {
                                h.remove("X-User-Id");
                                h.remove("X-User-Email");
                                h.set("X-Request-Id", reqId);
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                }));
    }

    /** Run AFTER Spring Security (which is at SecurityWebFiltersOrder.AUTHENTICATION = -200). */
    @Override public int getOrder() { return -100; }
}
```

**Source:** [CITED: medium.com/javarevisited/spring-cloud-gateway-route-and-mutate-request-headers] — `exchange.getRequest().mutate().headers(...)` + `exchange.mutate().request(...)` is the documented mutation pattern.

### Pattern 4: Servlet `OncePerRequestFilter` — Defense-in-Depth Re-validation

```java
// libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java
package com.tripplanner.jwt.servlet;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import com.tripplanner.jwt.JwtAuthenticationException;
import com.tripplanner.jwt.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class ServletJwtCommonFilter extends OncePerRequestFilter {

    private static final List<String> WHITELIST = List.of("/__health", "/actuator/health");

    private final JwtVerifier verifier;
    private final ObjectMapper mapper = new ObjectMapper();

    public ServletJwtCommonFilter(JwtVerifier verifier) { this.verifier = verifier; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();
        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            chain.doFilter(req, resp);
            return;
        }

        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            // Pitfall 1: a request that bypasses the gateway and hits localhost:8082 directly
            // with a crafted X-User-Id but no Authorization header lands here. 401.
            writeProblem(resp, HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED,
                    "Authorization Bearer token required");
            return;
        }

        try {
            UserContext user = verifier.verify(header.substring("Bearer ".length()).trim());
            var auth = new ServletAuthToken(user, AuthorityUtils.createAuthorityList("ROLE_USER"));
            auth.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(auth);

            // MDC enrichment — observability filter already wrote traceId/spanId/requestId; we add userId.
            MDC.put("userId", user.userId());

            chain.doFilter(req, resp);
        } catch (JwtAuthenticationException ex) {
            ErrorCode code = ex.getMessage().contains("expired")
                    ? ErrorCode.AUTH_TOKEN_EXPIRED
                    : ErrorCode.AUTH_INVALID_TOKEN;
            writeProblem(resp, HttpStatus.UNAUTHORIZED, code, ex.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
            MDC.remove("userId");
        }
    }

    private void writeProblem(HttpServletResponse resp, HttpStatus status, ErrorCode code, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetailFactory.of(status, code, detail);
        resp.setStatus(status.value());
        resp.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(resp.getOutputStream(), pd);
    }

    static class ServletAuthToken extends AbstractAuthenticationToken {
        private final UserContext principal;
        ServletAuthToken(UserContext p, java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> auths) {
            super(auths); this.principal = p;
        }
        @Override public Object getCredentials() { return null; }
        @Override public Object getPrincipal()   { return principal; }
    }
}
```

The companion `ServletSecurityConfig` (per service) wires this filter at `UsernamePasswordAuthenticationFilter` order:

```java
// services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java
@Configuration
@EnableWebSecurity
public class ServletSecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ServletJwtCommonFilter jwtFilter) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/__health", "/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

### Pattern 5: `RedisRateLimiter` — sub-1-rps quotas

Spring Cloud Gateway's `RedisRateLimiter` uses a token bucket. For windows longer than 1 second, the docs explicitly cover the "rate limits below 1 request/s" formula:

```yaml
# services/api-gateway/src/main/resources/application.yml — append BELOW the existing /__health/* routes
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}    # docker profile overrides to "redis"
      port: ${REDIS_PORT:6379}
  cloud:
    gateway:
      httpclient:
        connect-timeout: 1000          # D-10
        response-timeout: 10s          # D-10
      routes:
        # --- AUTH (public; rate-limited) -----------------------------------
        - id: auth-login
          uri: http://auth-service:8081
          predicates:
            - Path=/api/auth/login
            - Method=POST
          filters:
            # IP-only login limit per D-05 (Phase 1 ships 30/15min IP-only;
            # auth-service ships strict 5/15min IP+email in Phase 2)
            # 30 req / 900 sec = 1 token per 30 sec.
            # Token bucket: replenishRate=2, requestedTokens=60 → 2 tokens per minute,
            # but we want 30/900s = 30 tokens budget refilling slowly.
            # Configuration: replenishRate=30, requestedTokens=900, burstCapacity=30
            #   = 30 tokens budget every 900 seconds, burst up to 30
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 30
                redis-rate-limiter.burstCapacity: 30
                redis-rate-limiter.requestedTokens: 900
                key-resolver: "#{@ipKeyResolver}"
        - id: auth-signup
          uri: http://auth-service:8081
          predicates:
            - Path=/api/auth/signup
            - Method=POST
          filters:
            # 3 / hour by IP — replenishRate=3, requestedTokens=3600, burst=3
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 3
                redis-rate-limiter.burstCapacity: 3
                redis-rate-limiter.requestedTokens: 3600
                key-resolver: "#{@ipKeyResolver}"
        - id: auth-verify
          uri: http://auth-service:8081
          predicates:
            - Path=/api/auth/verify
        - id: auth-refresh
          uri: http://auth-service:8081
          predicates:
            - Path=/api/auth/refresh
        - id: auth-other
          uri: http://auth-service:8081
          predicates:
            - Path=/api/auth/**
        # --- SEARCH (public; IP-rate-limited) ------------------------------
        - id: search
          uri: http://destination-service:8083
          predicates:
            - Path=/api/search/**
          filters:
            # 60 / minute by IP — 1 rps replenish, burst 60 (token=1 default works at this rate)
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 60
                key-resolver: "#{@ipKeyResolver}"
        # --- DESTINATIONS (public; IP-rate-limited) ------------------------
        - id: destinations
          uri: http://destination-service:8083
          predicates:
            - Path=/api/destinations/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 1
                redis-rate-limiter.burstCapacity: 60
                key-resolver: "#{@ipKeyResolver}"
        # --- TRIPS (authenticated; userId-rate-limited) --------------------
        - id: trips
          uri: http://trip-service:8082
          predicates:
            - Path=/api/trips/**
          filters:
            # 120 / minute by userId — 2 rps replenish, burst 120
            # MUST run after AuthenticationWebFilter so userId is in SecurityContext (D-06).
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 2
                redis-rate-limiter.burstCapacity: 120
                key-resolver: "#{@userIdKeyResolver}"
```

**Note on math:** `RequestRateLimiter` docs explicitly call out the sub-1-rps technique. For 30 req per 900 s:
- `replenishRate=30, burstCapacity=30, requestedTokens=900` means each request costs 900 tokens, and the bucket replenishes at 30 tokens/sec — so it takes 30 seconds to accrue one request's worth of tokens. After burst, the steady-state allowed rate is `30/900 = 1 req per 30 s = 30 req/15 min`. ✓

[VERIFIED: docs.spring.io/spring-cloud-gateway/.../requestratelimiter-factory.html — "Rate limits below 1 request/s are accomplished by setting replenishRate to the wanted number of requests, requestedTokens to the timespan in seconds, and burstCapacity to the product of replenishRate and requestedTokens."]

**`KeyResolver` beans:**

```java
// services/api-gateway/src/main/java/com/tripplanner/gateway/routing/KeyResolverConfig.java
@Configuration
public class KeyResolverConfig {

    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // X-Forwarded-For is OFF by default per CVE-2025-41235 mitigation.
            // Use the actual remote address. In compose, this is the gateway container's IP for
            // requests proxied through Docker's network, which means all requests hash to the
            // same key — acceptable for portfolio scope; Phase 10 may revisit with
            // spring.cloud.gateway.server.webflux.trusted-proxies if needed.
            var remote = exchange.getRequest().getRemoteAddress();
            return Mono.just(remote != null ? remote.getAddress().getHostAddress() : "unknown");
        };
    }

    @Bean
    public KeyResolver userIdKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated()
                                && auth.getPrincipal() instanceof UserContext)
                .map(auth -> ((UserContext) auth.getPrincipal()).userId())
                .switchIfEmpty(Mono.just("anonymous"));    // unreachable on /api/trips/** (authenticated)
    }
}
```

### Pattern 6: 401 / 429 → RFC 7807 ProblemDetail

```java
// services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java
@Component
public class ProblemDetailAuthEntryPoint implements ServerAuthenticationEntryPoint {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.UNAUTHORIZED);
        resp.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        ErrorCode code = (ex.getCause() instanceof JwtAuthenticationException jae
                          && jae.getMessage().contains("expired"))
                ? ErrorCode.AUTH_TOKEN_EXPIRED
                : ErrorCode.AUTH_UNAUTHORIZED;
        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.UNAUTHORIZED, code, ex.getMessage());
        try {
            byte[] bytes = mapper.writeValueAsBytes(pd);
            return resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException jpe) {
            return Mono.error(jpe);
        }
    }
}
```

For 429 (rate-limit hit), `RequestRateLimiter` writes a 429 with no body by default. Override via a `WebFilter` ordered AFTER the gateway's filter chain that detects 429 status + missing body and writes the ProblemDetail, OR (cleaner) configure `RedisRateLimiter` with `statusCode: TOO_MANY_REQUESTS` and emit a custom error WebFilter:

```java
// Translates any 429 response with empty body into RFC 7807 problem+json.
@Component
@Order(-2)   // run AFTER NettyWriteResponseFilter (which is -1)
public class RateLimitProblemDetailFilter implements WebFilter {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                    ProblemDetail pd = ProblemDetailFactory.of(
                            HttpStatus.TOO_MANY_REQUESTS,
                            ErrorCode.AUTH_RATE_LIMITED,
                            "Rate limit exceeded for this route");
                    try {
                        byte[] bytes = mapper.writeValueAsBytes(pd);
                        return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
                    } catch (JsonProcessingException ex) {
                        return Mono.error(ex);
                    }
                }
                return super.writeWith(body);
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }
}
```

### Anti-Patterns to Avoid

- **Reading `userId` from `X-User-Id` header alone** in any downstream controller. The header is a *convenience* read-through after JWT validation confirms it matches `sub`. Source of truth is `SecurityContextHolder` (D-02 / D-04).
- **Skipping `JwtCommonFilter` on trip-service / destination-service** because "they have no real endpoints yet." This is exactly the Pitfall-1 trap. Wire the filter and the `_ping` controller in Phase 1; defense-in-depth is non-negotiable.
- **Calling `Jwts.parserBuilder().setSigningKey(...).build()`** — deprecated 0.12+ API. Use `Jwts.parser().verifyWith(key).build()`.
- **Using `lb://` URIs** in routes. D-08 hard rule. Static URIs only in Phase 1; Phase 10 may revisit.
- **Manually registering `ServerHttpObservationFilter`.** Auto-configured by SB 3.2+; manual registration produces duplicate spans (Pitfall 7). Phase 0 Convention C7 still applies.
- **Trusting `X-Forwarded-For` headers** by default. CVE-2025-41235 mitigation: SCG 2025.0 disables `X-Forwarded-*` header trust by default. Do NOT enable `spring.cloud.gateway.server.webflux.trusted-proxies` without thinking through the spoofing surface. For Phase 1 IP-keyed limiting, use `getRemoteAddress()` directly.
- **Caching the request body at the gateway** to extract `email` from `/api/auth/login`. DISCUSSION-LOG Q1 explicitly rejected this. Body-caching breaks reactive backpressure and makes the gateway stateful. The IP+email split (gateway IP / auth-service email) is the chosen path.
- **Putting the JWT secret in `application.yml`.** D-16 / D-24: only `AUTH_JWT_SECRET` env var; `.env.example` has the dev placeholder. Loading via `@ConfigurationProperties("auth.jwt")`.
- **Returning bare 429 with empty body from rate limiter.** Phase 1 D-07 requires RFC 7807 wrapping. Use the `RateLimitProblemDetailFilter` pattern above.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JWT signature verification | Custom HMAC + Base64 + JSON parsing | `jjwt 0.13.0` (`Jwts.parser().verifyWith(...).build()`) | Subtle constant-time comparison and clock-skew handling; jjwt is widely-audited. |
| Reactive auth filter | Custom `WebFilter` doing `header.startsWith("Bearer ")` + JWT parsing inline | `Spring Security AuthenticationWebFilter + ReactiveAuthenticationManager + ServerAuthenticationConverter` | Plays with CORS / CSRF / `ServerAuthenticationEntryPoint` / `SecurityContextRepository` automatically. Custom filters break the chain for /actuator/health and other allowlisted paths. |
| Servlet auth filter | `Filter` with manual SecurityContextHolder population | `OncePerRequestFilter` + `SecurityFilterChain.addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class)` | `OncePerRequestFilter` handles forward / async / dispatch correctly; ad-hoc Filters double-fire on async dispatches. |
| Token bucket / leaky bucket rate limiter | Custom Redis Lua script | Spring Cloud Gateway's built-in `RedisRateLimiter` | Already ships a battle-tested Lua script; supports the sub-1-rps formula natively. |
| RFC 7807 ProblemDetail JSON | Hand-rolling the JSON envelope | `ProblemDetailFactory.of(status, code, detail)` from `libs/error-handling` (Phase 0) | One factory, one type-URI prefix, audited shape. |
| `X-Request-Id` UUID generation | Manually patching every controller | A single filter (already exists in `libs/observability`) — Phase 1 only adds gateway-side mutation in `XUserIdInjectionGlobalFilter` | Single-source-of-truth means a future change to ID format edits one file. |
| CORS handling | Custom `WebFilter` setting `Access-Control-*` headers | Spring Security's `cors(c -> c.configurationSource(...))` integration | Handles preflight (`OPTIONS`) automatically. Custom filters often forget the preflight path. |
| W3C trace context propagation across gateway → downstream | Manual `traceparent` header copying | Auto-config in `libs/observability` (Phase 0 D-04) — Spring Cloud Gateway's `NettyRoutingFilter` already propagates `traceparent` because it uses an instrumented WebClient | Already wired. Phase 1 only **verifies** the propagation. |

**Key insight:** Phase 0 already shipped 80% of the cross-cutting plumbing (`libs/observability`, `libs/error-handling`, route table convention, MDC enrichment). Phase 1's job is to NOT re-invent any of it — just compose what's there and add the JWT verifier + filters + Spring Security configs.

## Runtime State Inventory

> Phase 1 is greenfield (first phase that lands real domain logic per CONTEXT.md `<code_context>`). No rename / refactor / migration. Section omitted by exception.

The only "runtime state" Phase 1 introduces is the Redis keyspace prefix `rl:*` (e.g. `rl:login:ip:198.51.100.7`). This is documented in CONTEXT.md `<specifics>`. No data migration concerns; Redis container is wiped on `docker compose down -v`.

## Common Pitfalls

### Pitfall A: jjwt deprecated builder pattern

**What goes wrong:** Code following pre-0.12 jjwt blog posts uses `Jwts.parserBuilder().setSigningKey(secret).build().parseClaimsJws(token)`. This still compiles in 0.13.0 but emits deprecation warnings and will be removed before 1.0. New code that mixes patterns produces confusing IDE warnings.
**Why it happens:** Most StackOverflow / Medium posts still show the old API; the modern `Jwts.parser().verifyWith(...).build().parseSignedClaims(...)` pattern is only ~2 years old.
**How to avoid:** Use the modern API exclusively (Pattern 1 above). Code review rule: any `setSigningKey` / `parseClaimsJws` / `parserBuilder()` is a fail.
**Warning signs:** Build log shows deprecation warnings on `JwtParserBuilder`. javadoc IDE inspection underlines the methods.

### Pitfall B: HS256 secret < 32 bytes silently fails OR throws WeakKeyException

**What goes wrong:** `AUTH_JWT_SECRET` set to a short string (e.g., "secret"). jjwt 0.12+ throws `WeakKeyException` on first verify; some older code suppressed this.
**Why it happens:** Developers paste a placeholder secret without thinking about the 256-bit RFC 7518 minimum.
**How to avoid:** Validate length at `JwtVerifier` construction (the constructor in Pattern 1 throws `IllegalStateException` on `< 32` bytes). Document in `.env.example` that `AUTH_JWT_SECRET` must be ≥ 32 bytes. Phase 0's `.env.example` already ships a 39-char placeholder (`dev-only-32-byte-secret-replace-in-prod`). Phase 2's `JwtService` will add a startup assertion that the secret is NOT the placeholder when `SPRING_PROFILES_ACTIVE` ≠ `dev`.
**Warning signs:** `WeakKeyException: The signing key's size is N bits which is not secure enough...`.

### Pitfall C: Pitfall 1 (X-User-Id spoofing) — the keystone

**What goes wrong:** Phase 1 ships only the gateway filter and assumes downstream services are safe behind compose-internal DNS. Eventually trip-service is reachable on `localhost:8082` (which Phase 0 D-22 already binds to loopback only — a *partial* mitigation), and a developer running an integration test directly against `:8082` with a crafted `X-User-Id` proves the worst case in production.
**Why it happens:** "We'll add downstream filter in Phase 2." Then Phase 2's auth-service work has its own complexity and the filter never lands.
**How to avoid:** Phase 1 ships `ServletJwtCommonFilter` AND `_ping` controllers AND the `DirectServiceAccessWithoutGatewayReturns401` integration test (D-14). The test runs trip-service standalone (no gateway), hits `/api/trips/_ping` directly with a forged `X-User-Id` header, and asserts 401. If that test is missing, the phase is incomplete.
**Warning signs:** trip-service `SecurityFilterChain` is permissive. `/api/trips/_ping` returns 200 when called directly without Authorization header.

### Pitfall D: Spring Cloud Gateway artifact rename — deprecated `spring-cloud-starter-gateway`

**What goes wrong:** Spring Cloud 2025.0 (Northfields) renamed `spring-cloud-starter-gateway` → `spring-cloud-starter-gateway-server-webflux` in 4.3+. The old artifact still works but emits a deprecation warning at startup. Phase 0's `libs.versions.toml` uses the old name (`spring-cloud-starter-gateway`).
**Why it happens:** Spring Cloud has bifurcated into "server (WebFlux | WebMVC)" + "proxy-exchange (WebFlux | WebMVC)" variants. The old name was ambiguous.
**How to avoid:** Phase 1 may leave the old artifact name as-is (functional) and document the rename for Phase 10 hardening. Renaming requires editing `libs.versions.toml` + `services/api-gateway/build.gradle.kts`. Property prefix migration (`spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*`) can be deferred to Phase 10 with `spring-boot-properties-migrator` providing backward compatibility.
**Warning signs:** Startup log shows `WARN o.s.boot.context.properties.migrator: spring-cloud-starter-gateway is deprecated, use spring-cloud-starter-gateway-server-webflux`.
[CITED: docs.openrewrite.org/recipes/java/spring/cloud2025/springcloudgatewaydeprecatedmodulesandstarters; spring.io 2025.0.0 announcement]

### Pitfall E: Spring Cloud Gateway CVEs (CVE-2025-41235 / CVE-2025-41253 / CVE-2025-41243)

**What goes wrong:**
- **CVE-2025-41235** — spoofed `X-Forwarded-For` / `Forwarded` headers via untrusted proxy (incorrect client IP). Patched in SCG 4.2.3+, 4.3.0+.
- **CVE-2025-41253** — SpEL injection in route configuration when actuator gateway endpoints are misconfigured + permissive SpEL eval. Patched in 4.2.5+, 4.3.2+.
- **CVE-2025-41243** — critical (CVSS 10) Spring Environment property modification via Server WebFlux. Worth tracking; check the latest SCG release notes for patched versions in 5.0.x.

**Why it happens:** Default actuator exposure + default `X-Forwarded-*` trust were too permissive in 4.0–4.2 / 5.0-RC.
**How to avoid:**
1. Phase 0 SUMMARY 00-06 already restricted actuator exposure to `health,info,prometheus` only (T-00-29 / T-00-20 mitigation). CVE-2025-41253 is NOT exploitable in this project's posture because actuator gateway endpoints are not exposed.
2. Phase 1 default keeps `X-Forwarded-For` trust OFF. Don't set `spring.cloud.gateway.server.webflux.trusted-proxies` without considering the spoofing surface. CVE-2025-41235 is mitigated.
3. Pin the Spring Cloud BOM at 2025.0.2 (already locked via D-30). If 2025.0.3 ships before Phase 1 merges with additional fixes, bump.
4. Plan acceptance grep: `grep -r 'gateway.actuator\|gateway/actuator' services/api-gateway/src/main/resources/` returns 0 matches.

[VERIFIED: spring.io/blog/2025/05/29/spring-cloud-gateway-2025-05-29-releases/; spring.io/blog/2025/10/15/spring-cloud-gateway-4/; ZeroPath blog on CVE-2025-41253]

### Pitfall F: WebFlux MDC propagation across thread switches (carryover from Phase 0 WR-01)

**What goes wrong:** Phase 0's `ReactiveMdcEnrichmentFilter` uses `doOnEach` + `doFinally` to set MDC. The 00-REVIEW.md WR-01 finding is that this leaks MDC across requests on the gateway's small Reactor-Netty event-loop pool. Phase 1 introduces `userId` writes into MDC after auth — extending the surface area of the leak.
**Why it happens:** The "right" WebFlux MDC pattern (`contextWrite` + `Hooks.enableAutomaticContextPropagation()`) is non-trivial; the Phase-0 minimal pattern was acknowledged as imperfect.
**How to avoid:** Phase 1 does **NOT** re-add `userId` writes into the existing reactive MDC filter. Instead, the gateway's logs MAY have empty `userId` (acceptable for portfolio scope — gateway already logs the route id; userId attribution is on the downstream service log line). The servlet filter populates MDC `userId` in its `try` block and clears it in `finally` (Pattern 4) — servlet MDC has no thread-pool leak risk. Phase 10 lifts WebFlux MDC to the contextWrite pattern.
**Warning signs:** Log lines from gateway show another user's `userId` after a separate request. (Phase 1 sidesteps by not writing `userId` to gateway MDC at all.)

### Pitfall G: `RedisRateLimiter` keyResolver returns Mono.empty() = 200 OK pass-through

**What goes wrong:** If a `KeyResolver` returns `Mono.empty()`, `RequestRateLimiter` does NOT block — it lets the request through. A `KeyResolver` that throws or returns empty on edge cases (no remote address, no SecurityContext) is a **rate-limit bypass**.
**Why it happens:** Sample code online uses `Mono.empty()` for "no key". The default behavior is "allow on empty," which is the wrong default.
**How to avoid:** All KeyResolvers in Pattern 5 use `switchIfEmpty(Mono.just("anonymous"))` / `Mono.just("unknown")` so a key always exists. Alternatively, configure `spring.cloud.gateway.filter.request-rate-limiter.deny-empty-key=true` (default since 2.x, but verify on 2025.0). Plan acceptance: integration test asserts a request with a deliberately-broken key resolver returns 429, not 200.
**Warning signs:** Hammering `/api/search` from the same IP returns 200 indefinitely; Redis shows no `rl:search:*` keys being incremented.
[CITED: docs.spring.io/spring-cloud-gateway/.../requestratelimiter-factory.html]

### Pitfall H: `condition: service_healthy` in compose blocks api-gateway on Redis

**What goes wrong:** Phase 0's `infra/docker-compose.yml` has `api-gateway` depending only on `eureka-server: condition: service_healthy`. With Redis required for rate limiting, Phase 1 must add `redis: condition: service_healthy`. Forgetting this means the gateway can boot before Redis, and the first request panics with a Redis connection refused.
**Why it happens:** Phase 0 didn't need Redis at the gateway; it's easy to overlook adding the dep.
**How to avoid:** Phase 1 plan task: edit `infra/docker-compose.yml` to add `redis: { condition: service_healthy }` under `api-gateway.depends_on`. Verify Redis healthcheck is the existing `redis-cli ping | grep -q PONG` (already in place per Phase 0 SUMMARY 00-08).
**Warning signs:** First request after `docker compose up` returns 500 with `RedisConnectionFailureException`.

### Pitfall I: Reactive vs servlet `ConditionalOnClass` autoconfig (carryover from Phase 0 WR-02)

**What goes wrong:** `libs/jwt-common`'s autoconfig uses `@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")` (which lives in `spring-web`, present on every Spring Boot web app — both stacks) → registers reactive filter on servlet apps too.
**Why it happens:** Same root cause as Phase 0 WR-02 in `libs/observability`. The conditional discriminator picks the wrong class.
**How to avoid:** Use `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)` for the reactive filter config + `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)` for the servlet config. This is the documented Spring Boot idiomatic switch and is more readable. Apply the same fix retroactively to `libs/observability` (Phase 0 carryover) — see WR-02 in 00-REVIEW.md.
**Warning signs:** trip-service / destination-service container starts with a `ReactiveJwtAuthFilter` bean in the context (visible via `actuator/beans` in dev — but Phase 0 disabled `beans` exposure, so this is silent).

### Pitfall J: `condition: service_healthy` ordering — gateway before downstream services

**What goes wrong:** With static-URI routing, the gateway can boot before downstream services are healthy. The first request returns a 502 from Spring Cloud Gateway's `NettyRoutingFilter` because the connection is refused. This is distinct from Pitfall 10 (Eureka-based) but has the same UX.
**Why it happens:** Phase 0 didn't gate gateway on auth/trip/destination because Phase 0 used `/__health/*` routes through gateway only as a smoke test, not real traffic. Phase 1's `/api/**` routes will see real traffic on container start.
**How to avoid:** Either (a) add `auth-service: condition: service_healthy`, `trip-service: ...`, `destination-service: ...` to api-gateway's `depends_on` in `infra/docker-compose.yml`, OR (b) accept the first ~5s of 502s and document. Recommendation: (a). The compose orchestrator already supports cascading healthchecks; the cost is ~10s longer compose startup, well within ROADMAP SC#1's "<60s warm" budget.
**Warning signs:** `curl localhost:8080/api/auth/anything` immediately after `docker compose up` returns 502; succeeds 5s later.

## Code Examples

### `_ping` controller (servlet — same shape on trip-service and destination-service)

```java
// services/trip-service/src/main/java/com/tripplanner/trip/health/PingController.java
package com.tripplanner.trip.health;

import com.tripplanner.contracts.UserContext;
import io.micrometer.tracing.Tracer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class PingController {

    private final Tracer tracer;

    public PingController(Tracer tracer) { this.tracer = tracer; }

    @GetMapping("/api/trips/_ping")
    public Map<String, Object> ping(@AuthenticationPrincipal UserContext user,
                                     @org.springframework.web.bind.annotation.RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("service", "trip-service");
        body.put("userId", user.userId());
        body.put("email", user.email());
        body.put("verified", user.verified());
        body.put("requestId", requestId);
        var span = tracer.currentSpan();
        body.put("traceId", span != null ? span.context().traceId() : null);
        return body;
    }
}
```

### `JwtFixtures` (testFixtures source set in libs/jwt-common)

```java
// libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java
package com.tripplanner.jwt;

import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class JwtFixtures {

    public static final String TEST_SECRET = "test-secret-must-be-at-least-32-bytes-long!";   // 43 chars
    private static final SecretKey KEY = new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private JwtFixtures() {}

    public static String mintValid(String userId, String email, boolean verified) {
        return mint(userId, email, verified, Duration.ofMinutes(15), KEY);
    }

    public static String mintExpired(String userId, String email) {
        return mint(userId, email, true, Duration.ofMinutes(-5), KEY);
    }

    public static String mintWrongSig(String userId, String email) {
        SecretKey wrong = new SecretKeySpec(
                "different-secret-also-at-least-32-bytes-long!".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return mint(userId, email, true, Duration.ofMinutes(15), wrong);
    }

    public static String mintMalformed() {
        return "not.a.valid.jwt";
    }

    private static String mint(String userId, String email, boolean verified, Duration ttl, SecretKey key) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("tripplanner-auth-test")
                .subject(userId)
                .claim("email", email)
                .claim("ver", verified)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}
```

### Integration test — DirectServiceAccessWithoutGatewayReturns401

```java
// services/trip-service/src/test/java/com/tripplanner/trip/security/DirectServiceAccessIT.java
@SpringBootTest(webEnvironment = RANDOM_PORT,
                properties = "auth.jwt.secret=" + JwtFixtures.TEST_SECRET)
@AutoConfigureMockMvc
class DirectServiceAccessIT {

    @Autowired MockMvc mvc;

    @Test  // Pitfall 1 keystone: spoofed X-User-Id without JWT → 401
    void direct_call_with_xUserId_no_jwt_returns_401() throws Exception {
        mvc.perform(get("/api/trips/_ping").header("X-User-Id", "spoofed-uuid"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("auth.unauthorized"));
    }

    @Test
    void valid_jwt_resolves_userId_from_sub_not_xUserId() throws Exception {
        String jwt = JwtFixtures.mintValid("real-user-id", "user@example.com", true);
        mvc.perform(get("/api/trips/_ping")
                .header("Authorization", "Bearer " + jwt)
                .header("X-User-Id", "spoofed-different-id"))    // gateway would have stripped this
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("real-user-id"));    // sub claim wins
    }

    @Test
    void expired_jwt_returns_401_with_token_expired_code() throws Exception {
        String expired = JwtFixtures.mintExpired("u", "u@e.com");
        mvc.perform(get("/api/trips/_ping").header("Authorization", "Bearer " + expired))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.token_expired"));
    }

    @Test
    void wrong_sig_jwt_returns_401_with_invalid_token_code() throws Exception {
        String bad = JwtFixtures.mintWrongSig("u", "u@e.com");
        mvc.perform(get("/api/trips/_ping").header("Authorization", "Bearer " + bad))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("auth.invalid_token"));
    }
}
```

### Trace continuity test (Phase 1 SC#6)

```java
// services/api-gateway/src/test/java/com/tripplanner/gateway/observability/TraceContinuityIT.java
@SpringBootTest(webEnvironment = RANDOM_PORT,
                properties = {"auth.jwt.secret=" + JwtFixtures.TEST_SECRET,
                              "management.tracing.sampling.probability=1.0"})
@Testcontainers
@AutoConfigureWebTestClient
class TraceContinuityIT {

    @Container @ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    // Inject a test downstream stub that captures incoming traceparent header.
    // (Pattern: spin up a tiny WireMock or @LocalServerPort spring-app on a random port,
    //  configure routes[].uri to point there.)

    @Test
    void single_traceId_propagates_gateway_to_downstream() {
        // 1. Mint JWT
        // 2. Hit gateway: GET /api/trips/_ping with Bearer token
        // 3. Inspect captured downstream request: assert traceparent header present, parse traceId
        // 4. Inspect gateway log MDC (via in-memory ListAppender attached to logger): assert same traceId appears
        // 5. Assertion: gateway's traceId == downstream's traceparent traceId
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `Jwts.parserBuilder().setSigningKey(secret).build().parseClaimsJws(...)` | `Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(...)` | jjwt 0.12.0 (2023) | Old API still works; emits deprecation warnings; will be removed before 1.0. |
| `spring-cloud-starter-gateway` (artifact) | `spring-cloud-starter-gateway-server-webflux` | Spring Cloud 2025.0 / SCG 4.3 (May 2025) | Old name still resolves but logs a deprecation warning at startup. Phase 0 still uses old name; Phase 10 may rename. |
| `ServerHttpObservationFilter` registered manually | Auto-configured via `WebHttpHandlerBuilder` | Spring Boot 3.2 (Nov 2023) | Manual registration produces duplicate spans. Phase 0 Convention C7 already enforces no-manual rule. |
| Spring Cloud Sleuth | Micrometer Tracing (built into SB 3.x) | Spring Boot 3.0 (Nov 2022) | Sleuth is EOL. SB 3.x ships `micrometer-tracing-bridge-otel` natively. |
| `@Bean ServerCodecConfigurer` for custom JSON | Auto-configured Spring HTTP codecs | SB 3.0 | Manual registration duplicates the codec; never do this. |
| HS256 + shared secret for JWT | RS256 + JWKS endpoint | (deferred to v2) | v1 uses HS256 per ADR-5 — simpler key distribution at the cost of single-secret blast radius. |

**Deprecated/outdated (and thus avoided):**
- jjwt `parserBuilder().setSigningKey(...)` — use `parser().verifyWith(...)`.
- `spring-cloud-sleuth` — use Micrometer Tracing.
- `org.springframework.web.cors.reactive.CorsWebFilter` registered separately — use Security's `cors()` integration.
- Manual `@Bean WebClient.Builder` without auto-instrumented version — use auto-configured builder so trace headers propagate.

## Project Constraints (from CLAUDE.md)

These are repo-wide directives the planner MUST verify against. Phase 1 plans should not contradict these.

- **Tech stack — locked:** Java 21, Spring Boot 3.5.x, PostgreSQL 16, Redis 7, Gradle Kotlin DSL multi-module. (CLAUDE.md says SB 3.3.x — out of date; Phase 0 corrected to 3.5.14 via D-30.)
- **Spring Cloud train:** CLAUDE.md says 2024.0.x — out of date; D-30 (binding) corrects to 2025.0.2 (Northfields). Phase 1 uses 2025.0.2.
- **Architecture — locked:** 5 services + 4 shared libs + frontend. Phase 1 lands the 4th shared lib (`libs/jwt-common`).
- **JWT — HS256 / 15 min access / 7-day refresh / refresh-rotation / bcrypt cost 12 / email-verified-before-login.** Phase 1 implements only the access-token verification side; signup/refresh are Phase 2.
- **Auth library: `jjwt`** (NOT Spring Authorization Server). Phase 1 uses jjwt 0.13.0.
- **Cost — free tier only / no paid SaaS.** Phase 1 introduces no paid deps (Spring Security, jjwt, spring-data-redis, all OSS).
- **Test discipline — ≥70% backend service-layer line coverage; 100% on auth + ownership-check paths; 8 mandatory security integration tests gate every PR.** Phase 1 owns 4 of those 8 tests (D-14). Coverage threshold tooling lands in Phase 10 (per IN-05 in 00-REVIEW.md) but the test count is already enforceable.
- **GSD Workflow Enforcement — entry through `/gsd-execute-phase`.** Phase 1 plans ship through standard GSD flow.
- **Local-only deployment in v1 — `docker compose up` is the ship target.** Phase 1's `docker-compose.yml` edits (add Redis dep on api-gateway, add health-gating on downstream services) must keep `docker compose up --wait` healthy in <60s.

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| **NFR-02** | Per-user authorization enforced at the service layer for every authenticated endpoint; cross-user reads return `404` (not `403`) so resource existence is not leaked. | Phase 1 ships the *machinery* that enables NFR-02: `ServletJwtCommonFilter` populates `SecurityContextHolder` with `UserContext`, exposing `currentUserId` to controllers via `@AuthenticationPrincipal UserContext`. The actual `findByIdAndUserId` enforcement lands in Phase 5 (trip) / Phase 6 (items). Phase 1's contribution is making `currentUserId` available, untrusted-header-free, in every authenticated downstream request. The `_ping` controller demonstrates the pattern. (See `docs/05-auth-security.md §5` Authorization model.) |
| **NFR-06** | OWASP Top 10 (2021) explicitly addressed: parameterized queries (A03), bcrypt + JWT rotation (A07), CSP + sanitization (A03/XSS), service-layer ownership (A01), Dependency-Check + Dependabot (A06). | Phase 1 owns Phase-1-specific OWASP contributions: **A01 Broken Access Control** — gateway JWT validation + downstream re-validation prevent IDOR via spoofed `X-User-Id`; **A05 Security Misconfiguration** — actuator restricted to `health,info,prometheus` (Phase 0 carryover), CORS allowlist not wildcard (D-03), stack traces not returned in docker profile, downstream ports loopback-bound (Phase 0 D-22); **A07 Identification & Authentication Failures** — IP-only login rate limit (30/15min), generic 401 ProblemDetail (no enumeration), JWT short-lived (15 min). The remaining items (A02 bcrypt, A03 parameterized queries / sanitization, A06 Dependency-Check, A04 Insecure Design) land in Phase 2 / Phase 6 / Phase 10. NFR-06's REQUIREMENTS.md mapping says "Phase 6" — that's accurate for the *completion gate*; Phase 1 contributes the access-control + rate-limit + auth-failure pillars. |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The 2025.0.2 BOM resolves to a Spring Cloud Gateway version (5.0.x or 4.2.5+ / 4.3.2+) that includes patches for CVE-2025-41235 and CVE-2025-41253 | Pitfall E | If the BOM happens to pull an unpatched 5.0-RC version, gateway is vulnerable to SpEL injection. Plan acceptance: `./gradlew dependencies | grep spring-cloud-gateway` — assert the resolved version is in the patched set. |
| A2 | Phase 0's `spring-cloud-starter-gateway` artifact (legacy name) still works under the 2025.0.2 BOM | Pitfall D | If the legacy name is fully removed in 2025.0.x (not just deprecated), gateway won't compile. Verify by running `./gradlew :services:api-gateway:dependencies` before starting Wave 1. Mitigation: rename to `spring-cloud-starter-gateway-server-webflux` in `libs.versions.toml`. |
| A3 | The reactive WebFlux `RedisRateLimiter` works correctly with the legacy `spring-cloud-starter-gateway` artifact (rather than the new server-webflux variant) | Pattern 5 | Low risk — the underlying class is `RedisRateLimiter` either way; only the starter wrapper changed. |
| A4 | `@AuthenticationPrincipal UserContext` works seamlessly when `UserContext` is a `record` (not a `class`) | Pattern 4 | Spring 6 supports `record`-typed principals via `@AuthenticationPrincipal`. Verified empirically in Spring Security 6.x docs. |
| A5 | Compose-internal IP visible to gateway is a stable per-client IP (not just the Docker bridge IP) | KeyResolver `ipKeyResolver` | If all client traffic appears to come from the same bridge IP, the IP-keyed rate limiter is effectively a global gate. Acceptable for portfolio scope (single dev hitting `localhost`). Phase 10 may revisit. |
| A6 | The 30 req / 900 sec sub-1-rps formula (`replenishRate=30, requestedTokens=900, burstCapacity=30`) produces correct steady-state behavior | Pattern 5 | Documented by Spring as the canonical sub-1-rps technique. Plan acceptance: integration test that fires 31 logins from same IP within 15 min and asserts the 31st returns 429. |
| A7 | jjwt 0.13.0 is API-compatible with 0.12.x verifier code (only deprecation warnings, no compilation breaks) | Pattern 1, Pitfall A | jjwt release notes confirm. Mitigation: pin 0.13.0 (already done in catalog) and code only against the modern API. |
| A8 | The 8 mandatory security tests in `docs/05-auth-security.md §10` distribute as Phase 1 owns 4, Phase 2 owns 4 | D-14 | Phase 1: AnonymousCannotAccessAuthedEndpoints (#1), InvalidTokenIsRejected (#2), LoginRateLimitTriggers IP-only (#8 partial), DirectServiceAccessWithoutGatewayReturns401 (Pitfall 1). Phase 2: CrossUserTripAccessReturns404 (#3 — needs trip data → Phase 5), CrossUserItemPatchReturns404 (#4 — Phase 6), DeletedRefreshTokenCannotBeUsed (#5), RotatedRefreshTokenCannotBeReused (#6), EmailNotVerifiedCannotLogin (#7), LoginRateLimitTriggers full IP+email (#8 strict). |

**If A1 or A2 fail at scaffold time, escalate before Wave 1.** The other assumptions are low-risk verification chores.

## Open Questions

1. **`@ConditionalOnWebApplication(SERVLET|REACTIVE)` vs the existing `@ConditionalOnClass` pattern in `libs/observability`**
   - What we know: Phase 0 WR-02 documents the bug; the recommended fix is `@ConditionalOnWebApplication`. Phase 1's `libs/jwt-common` faces the same choice.
   - What's unclear: Should Phase 1 also fix `libs/observability`'s `ReactiveConfig` discriminator while we're touching the autoconfig family, or leave it as Phase 10 carry-over?
   - Recommendation: Fix both in Phase 1 (one-line change + comment update). Bundling reduces the "10 minor lib bugs" tail.

2. **`UserContext` as `record` vs `class implements Principal`**
   - What we know: D-04 Claude's Discretion ("Whether `JwtVerifier` returns `Optional<UserContext>` or throws...").
   - What's unclear: Should `UserContext` implement `java.security.Principal` to make `@AuthenticationPrincipal Principal user` work generically?
   - Recommendation: `record UserContext(String userId, String email, boolean verified) implements Principal { @Override public String getName() { return userId; } }`. Tiny annotation surface; plays with Spring Security audit logging out of the box.

3. **Should `_ping` controllers be excluded from rate limiting?**
   - What we know: D-12 says `_ping` is a permanent debug endpoint. Phase 1's rate-limit table doesn't list `/api/trips/_ping`.
   - What's unclear: Does the catch-all `/api/trips/**` route's rate limiter apply to `/api/trips/_ping`?
   - Recommendation: Yes, let it apply. `_ping` is authenticated and the userId-keyed limiter is generous (120/min). If `_ping` becomes a hot probe path in Phase 5+, the rate limiter wisely caps it.

4. **Single shared `JwtVerifier` bean across services or per-service instance?**
   - What we know: D-16 says single `AUTH_JWT_SECRET` env var loaded via `@ConfigurationProperties("auth.jwt")` in `libs/jwt-common`.
   - What's unclear: Auto-configuration creates one `JwtVerifier` per Spring context. The "shared" piece is the secret + class, not the bean instance.
   - Recommendation: Per-service bean (auto-configured). The verifier is stateless; sharing the instance across JVMs is impossible anyway.

5. **Rename `spring-cloud-starter-gateway` to `spring-cloud-starter-gateway-server-webflux` in Phase 1 or defer to Phase 10?**
   - What we know: Pitfall D — old artifact emits deprecation warning at startup.
   - What's unclear: Does the rename require any property migration in our `application.yml` route table?
   - Recommendation: **Defer to Phase 10.** The deprecation warning is cosmetic; the route YAML works with both. Phase 1 has enough surface area; rename is a clean Phase 10 hardening task with `spring-boot-properties-migrator` providing automated property rename.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 (Temurin) | Build / runtime | ✓ via SDKMAN; CI uses `actions/setup-java@v4 java-version: 21` | local: 17 in `JAVA_HOME` (developer needs to set sdk env per Phase 0 SUMMARY 00-06 finding) | Use Compose's eclipse-temurin:21-jre image for runtime smoke; CI is unaffected |
| Docker Compose v2 (2.20+ for `include:`) | Compose stack | ✓ | v5.1.0 detected | none — already canonical |
| Redis 7 container | Rate-limiter backing store + tests | ✓ from Phase 0 compose file | redis:7-alpine | none — Phase 0 added it |
| `spring-boot-starter-data-redis-reactive` | Reactive Redis client at gateway | ✗ NOT yet on classpath; Phase 1 adds | managed by SB 3.5.14 | none — must add |
| `spring-boot-starter-security` | Spring Security WebFlux + Servlet filter chains | ✗ NOT yet on classpath; Phase 1 adds | managed by SB 3.5.14 | none — must add |
| `jjwt 0.13.0` | JWT parsing | ✓ catalogued in `libs.versions.toml` (Phase 0 D-26 forward-loaded) | 0.13.0 | none |
| `spring-security-test` | Test fixtures | ✗ Phase 1 adds | managed by SB 3.5.14 | none |
| `testcontainers-junit-jupiter` + redis container | Rate-limiter integration tests | ✗ Phase 1 adds | managed by SB 3.5.14 | Use ephemeral docker-compose Redis from outside the test JVM (less convenient) |
| Phase 0 `_ping` ports loopback-binding | Pitfall 1 mitigation in dev | ✓ from `infra/docker-compose.yml` D-22 | already in place | none |

**Missing dependencies with no fallback:** None — all are Spring-managed Maven coordinates that resolve from the existing repo's classpath.

**Missing dependencies with fallback:** None.

**Action for plan:** Wave 1 first task should add the new starter coordinates to `libs.versions.toml` and to the per-service `build.gradle.kts` files. Verify with `./gradlew :services:api-gateway:dependencies | grep -E '(spring-security|spring-data-redis|jjwt)'` returning expected versions before proceeding to Wave 2 (filter wiring).

## Validation Architecture

> Per `.planning/config.json` workflow.nyquist_validation = true (default; key absent), this section is required and consumed by the workflow to derive `01-VALIDATION.md`.

### Test Framework

| Property | Value |
|----------|-------|
| Backend test framework | JUnit 5 (managed SB 3.5.14 = 5.12.x) + Mockito 5.x + Spring Boot Test |
| Backend integration framework | Testcontainers 1.21.x via `spring-boot-testcontainers` + `@ServiceConnection` (Redis container only — Phase 1 has no Postgres-touching tests) |
| Backend test config file | each service `build.gradle.kts` `test { useJUnitPlatform() }` (already from Phase 0); add `testFixtures` source set on `libs/jwt-common` (`java-test-fixtures` plugin) |
| Backend quick run command | `./gradlew :libs:jwt-common:test :services:api-gateway:test` (or `:services:trip-service:test` etc) |
| Backend full suite command | `./gradlew check` |
| Phase gate full suite | `./gradlew check && bash scripts/smoke.sh` (smoke.sh extended in Phase 1 — see Wave 0 Gaps below) |
| Frontend test framework | n/a — Phase 1 has no frontend changes |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| **ROADMAP SC#1** (`/api/auth/*` → auth-service routing) | Gateway routes `/api/auth/foo` to `http://auth-service:8081/api/auth/foo` | integration (gateway, WireMock downstream) | `./gradlew :services:api-gateway:test --tests RoutingIT.routes_api_auth_to_auth_service` | ❌ Wave 0 — `RoutingIT.java` |
| **ROADMAP SC#1** (`/api/trips/*` → trip-service) | Gateway routes `/api/trips/foo` to trip-service | integration | same `RoutingIT` class, separate `@Test` | ❌ Wave 0 |
| **ROADMAP SC#1** (`/api/search/*` → destination-service) | Gateway routes `/api/search/foo` to destination-service | integration | same `RoutingIT` class | ❌ Wave 0 |
| **ROADMAP SC#2** (no Authorization → 401 from gateway, never reaches downstream) | `AnonymousCannotAccessAuthedEndpoints` (security test #1) | integration (gateway, WebTestClient) | `./gradlew :services:api-gateway:test --tests AnonymousAccessIT` | ❌ Wave 0 — `AnonymousAccessIT.java` |
| **ROADMAP SC#3** (forged/expired JWT → 401 from gateway) | `InvalidTokenIsRejected` (security test #2) | integration (gateway) | `./gradlew :services:api-gateway:test --tests InvalidTokenIT` | ❌ Wave 0 — `InvalidTokenIT.java` (3 cases: malformed, wrong-sig, expired) |
| **ROADMAP SC#4** (direct `localhost:8082` w/ X-User-Id, no JWT → 401) | `DirectServiceAccessWithoutGatewayReturns401` (Pitfall 1 keystone) | integration (trip-service standalone, MockMvc) | `./gradlew :services:trip-service:test --tests DirectServiceAccessIT` | ❌ Wave 0 — `DirectServiceAccessIT.java` (lives in trip-service, NOT gateway, because it tests bypass) |
| **ROADMAP SC#4** (same test for destination-service) | Symmetric Pitfall 1 mitigation in destination-service | integration (destination-service standalone) | `./gradlew :services:destination-service:test --tests DirectServiceAccessIT` | ❌ Wave 0 — same class, in destination-service |
| **ROADMAP SC#5** (`/api/auth/login` rate-limited at 5/15min/IP+email; 6th → 429) | **Phase 1 ships IP-only at 30/15min** per D-05 (split). Test: 31st request from same IP within 15min → 429 with `auth.rate_limited`. Strict 5/email gate is Phase 2. | integration (gateway + Testcontainers Redis) | `./gradlew :services:api-gateway:test --tests LoginRateLimitIT` | ❌ Wave 0 — `LoginRateLimitIT.java` |
| **ROADMAP SC#6** (single trace ID gateway → downstream → Zipkin) | One routed call produces one `traceId` visible in both gateway and downstream MDC | integration (gateway + Testcontainers Redis + log capture or stub server with traceparent assertion) | `./gradlew :services:api-gateway:test --tests TraceContinuityIT` | ❌ Wave 0 — `TraceContinuityIT.java` |
| **NFR-02** machinery | `_ping` controller resolves `userId` from JWT `sub`, NOT from `X-User-Id` (which is gateway-stripped/injected, but defense-in-depth filter doesn't trust it) | integration | covered by `DirectServiceAccessIT.valid_jwt_resolves_userId_from_sub_not_xUserId` test method | ❌ Wave 0 — see above |
| **NFR-06 A01** machinery | Gateway re-injection wins over client-supplied `X-User-Id` (gateway tests it; downstream re-validates and wins anyway) | integration (gateway, mutate-test) | `./gradlew :services:api-gateway:test --tests XUserIdInjectionIT` | ❌ Wave 0 — `XUserIdInjectionIT.java` |
| **CORS** allowlist | Preflight `OPTIONS` for `http://localhost:5173` returns `Access-Control-Allow-Origin: http://localhost:5173`; preflight from `http://evil.example.com` does NOT | integration (gateway, WebTestClient OPTIONS) | `./gradlew :services:api-gateway:test --tests CorsIT` | ❌ Wave 0 — `CorsIT.java` |
| **JWT Verifier unit** | parses valid; rejects malformed / expired / wrong-sig / missing-sub | unit | `./gradlew :libs:jwt-common:test --tests JwtVerifierTest` | ❌ Wave 0 — `JwtVerifierTest.java` |
| **Compose smoke (extension)** | After Phase 1, `bash scripts/smoke.sh` still passes all Phase 0 SCs + has new SC: `curl -X GET localhost:8080/api/trips/_ping` (no Auth) returns 401 ProblemDetail | smoke (compose + curl) | `bash scripts/smoke.sh` | ⚠ Wave 0 — extend existing script with Phase-1 cases |

### Sampling Rate

- **Per task commit:** Build only — `./gradlew :services:<svc>:compileJava` for service edits; `./gradlew :libs:jwt-common:compileJava` for lib edits. Avoid Testcontainers spin-up on every commit.
- **Per wave merge:** `./gradlew check` (runs all unit + integration tests including Testcontainers Redis). Wall-clock: ~90s on warm Gradle daemon; first cold run ~3 min.
- **Phase gate (Phase 1 done):** `./gradlew check && bash scripts/smoke.sh && docker compose down -v && docker compose up -d --wait && bash scripts/smoke.sh` (extended). Manual: hit `localhost:8080/api/trips/_ping` with curl + a minted JWT in DevTools to eyeball Zipkin tracing at `localhost:9411`.

### Wave 0 Gaps

**Wave 0 (test infrastructure) must land before Wave 1 (filter wiring).** Existing test infra (Phase 0) covers nothing for Phase 1 — Phase 0 ships only `frontend/src/App.test.tsx` for the React mount + `scripts/smoke.sh` for compose health.

- [ ] `libs/jwt-common/build.gradle.kts` — add `java-test-fixtures` plugin so `JwtFixtures` is shared across consumer services' test classpath
- [ ] `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java` — see Code Examples
- [ ] `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java` — unit tests for verifier (valid / malformed / expired / wrong-sig / weak-key / missing-sub)
- [ ] `services/api-gateway/build.gradle.kts` — `testImplementation(testFixtures(project(":libs:jwt-common")))` + spring-security-test + spring-boot-testcontainers + testcontainers junit-jupiter + redis testcontainer
- [ ] `services/trip-service/build.gradle.kts` — same testImplementation deps minus the testcontainers redis (trip-service tests don't need it)
- [ ] `services/destination-service/build.gradle.kts` — same as trip-service
- [ ] `services/api-gateway/src/test/java/com/tripplanner/gateway/AbstractGatewayIT.java` — abstract base class with Testcontainers Redis `@ServiceConnection` + WebTestClient injection
- [ ] `services/api-gateway/src/test/java/com/tripplanner/gateway/RoutingIT.java` — covers SC#1 routing (3 routes; uses WireMock-Spring-Boot per Phase 0 catalog `wiremock-spring-boot:3.9.0` to stand up downstream stubs)
- [ ] `services/api-gateway/src/test/java/com/tripplanner/gateway/security/AnonymousAccessIT.java` — SC#2
- [ ] `services/api-gateway/src/test/java/com/tripplanner/gateway/security/InvalidTokenIT.java` — SC#3 (3 cases)
- [ ] `services/api-gateway/src/test/java/com/tripplanner/gateway/security/XUserIdInjectionIT.java` — NFR-06 A01 machinery
- [ ] `services/api-gateway/src/test/java/com/tripplanner/gateway/security/CorsIT.java` — CORS preflight
- [ ] `services/api-gateway/src/test/java/com/tripplanner/gateway/ratelimit/LoginRateLimitIT.java` — SC#5 IP-only (Wave 3 deliverable; needs Redis container)
- [ ] `services/api-gateway/src/test/java/com/tripplanner/gateway/observability/TraceContinuityIT.java` — SC#6
- [ ] `services/trip-service/src/test/java/com/tripplanner/trip/security/DirectServiceAccessIT.java` — SC#4 / Pitfall 1 keystone (lives in trip-service)
- [ ] `services/destination-service/src/test/java/com/tripplanner/destination/security/DirectServiceAccessIT.java` — Pitfall 1 in destination-service (symmetric)
- [ ] `scripts/smoke.sh` — extend with Phase 1 cases:
  - new case: `curl -fs -o /dev/null -w '%{http_code}' http://localhost:8080/api/trips/_ping` returns `401`
  - new case: `curl -fs -X OPTIONS -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: GET' http://localhost:8080/api/trips/_ping` returns ACAO header for localhost:5173

### Health Gate Thresholds (Phase 1 done)

- ROADMAP SC#1: `/api/auth/*` `/api/trips/*` `/api/search/*` route correctly ✓ (`RoutingIT` asserts)
- ROADMAP SC#2: missing Authorization → 401 from gateway ✓ (`AnonymousAccessIT`)
- ROADMAP SC#3: forged/expired JWT → 401 from gateway ✓ (`InvalidTokenIT`)
- ROADMAP SC#4: direct `:8082` with crafted X-User-Id, no JWT → 401 from trip-service ✓ (`DirectServiceAccessIT`)
- ROADMAP SC#5 (revised — D-05 split): IP-only 30/15min → 31st request 429 ✓ (`LoginRateLimitIT`); strict IP+email 5/15min deferred to Phase 2
- ROADMAP SC#6: single traceId end-to-end ✓ (`TraceContinuityIT` + manual Zipkin verification)
- 4 mandatory security tests pass ✓ (D-14 list)
- All Phase 0 SCs still pass (no regression) ✓ (`scripts/smoke.sh` re-run)

## Security Domain

> `security_enforcement` is not explicitly disabled in `.planning/config.json`; section required.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | Gateway as single trust boundary; downstream services re-validate (defense-in-depth) — D-02. |
| V2 Authentication | yes | jjwt 0.13.0 HS256 verification; secret length validated at startup (Pitfall B). Phase 2 owns signup/login/refresh. |
| V3 Session Management | partial | JWT is the "session"; httpOnly refresh cookie is Phase 2. Phase 1 stateless gateway (`NoOpServerSecurityContextRepository`). |
| V4 Access Control | yes | `permitAll` allowlist + `authenticated` default at gateway; `SecurityFilterChain` `authenticated()` at downstream. NFR-02 ownership filter is Phase 5+. |
| V5 Input Validation | partial | Bean Validation runs on Phase 2+ controllers; Phase 1 only validates JWT shape via jjwt. |
| V6 Cryptography | yes | HS256 with SecretKeySpec; key length validated. Constant-time comparison handled by jjwt internals. |
| V7 Error Handling & Logging | yes | RFC 7807 ProblemDetail responses (no internal class names leaked); Logback redaction patterns for `Authorization`/`token` field names (Phase 0 reserved; Phase 1 may need explicit converter — flagged as open). |
| V8 Data Protection | yes | `AUTH_JWT_SECRET` from env (D-16, D-24); `.env.example` ships placeholder only (IN-02 in 00-REVIEW). |
| V9 Communication | partial | TLS is out-of-scope for v1 local; CORS allowlist enforced (D-03). |
| V11 Business Logic | n/a | No business endpoints in Phase 1. |
| V14 Configuration | yes | Actuator exposure restricted to `health,info,prometheus` (Phase 0 carryover); SCG actuator endpoints NOT exposed (mitigates CVE-2025-41253). |

### Known Threat Patterns for Spring Boot 3.5 + Spring Cloud Gateway 2025.0 + Spring Security WebFlux 6.4 stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| **X-User-Id header spoofing** (Pitfall 1) | Spoofing | Gateway strips + injects from JWT `sub` (`XUserIdInjectionGlobalFilter`); downstream re-validates JWT (`ServletJwtCommonFilter`); downstream service ports loopback-bound (Phase 0 D-22). |
| **JWT forgery / expired token replay** | Spoofing | `JwtVerifier.verify` rejects on bad sig / expired / malformed. `InvalidTokenIT` integration test asserts. |
| **`X-Forwarded-For` spoofing for IP-rate-limit bypass** (CVE-2025-41235) | Spoofing | Default `X-Forwarded-*` trust is OFF in SCG 2025.0; KeyResolver uses `getRemoteAddress()` directly. |
| **SpEL injection via misconfigured gateway actuator** (CVE-2025-41253) | Tampering | Actuator gateway endpoints NOT exposed (Phase 0 SUMMARY 00-06 actuator restriction). |
| **Authorization header logged in plaintext** | Information Disclosure | Logback masking converter for `Authorization`/`password`/`token` field names (Phase 0 reserved; Phase 1 ships the converter — see Open Question). |
| **Rate-limit bypass via empty KeyResolver** (Pitfall G) | DoS | All KeyResolvers return non-empty Mono; `deny-empty-key=true` is the SCG default. |
| **JWT secret < 256 bits weakening HS256** | Cryptographic Failure | `JwtVerifier` constructor throws `IllegalStateException` on `< 32` bytes; Phase 2 adds startup assertion that secret is NOT placeholder when `SPRING_PROFILES_ACTIVE` ≠ `dev`. |
| **Cross-request MDC leak in WebFlux** (Phase 0 WR-01 carryover) | Information Disclosure | Phase 1 does NOT write `userId` to gateway MDC (sidesteps the leak); servlet MDC is per-thread, no leak. Phase 10 lifts WebFlux MDC to `contextWrite` pattern. |
| **CORS wildcard misconfig** | Spoofing (cross-origin) | `CorsConfiguration` allows `http://localhost:5173` ONLY; `allowCredentials: true` requires non-wildcard origin. |
| **Stack trace leakage in error responses** | Information Disclosure | `server.error.include-stacktrace=never`; `ProblemDetailFactory` shape doesn't include trace details. |

## Sources

### Primary (HIGH confidence)

- [Spring Cloud 2025.0.0 (Northfields) release announcement](https://spring.io/blog/2025/05/29/spring-cloud-2025-0-0-is-abvailable/) — confirms 2025.0 is the SB 3.5 train; Gateway module rename
- [Spring Cloud 2025.0.2 (Northfields) release announcement (April 2026)](https://spring.io/blog/2026/04/02/spring-cloud-2025-0-2-aka-northfields-has-been-released/) — confirms latest 2025.0.2 patch
- [Spring Cloud Gateway 4.3.2/4.2.5/4.1.12/3.1.12 release notes (Oct 2025)](https://spring.io/blog/2025/10/15/spring-cloud-gateway-4/) — CVE-2025-41253 patches
- [Spring Cloud Gateway 4.2.3/4.3.0 release notes (May 2025)](https://spring.io/blog/2025/05/29/spring-cloud-gateway-2025-05-29-releases/) — CVE-2025-41235 patches
- [RequestRateLimiter GatewayFilter Factory docs](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/requestratelimiter-factory.html) — `replenishRate`/`burstCapacity`/`requestedTokens` math; sub-1-rps formula
- [JwtParserBuilder javadoc 0.13.0](https://javadoc.io/static/io.jsonwebtoken/jjwt-api/0.13.0/io/jsonwebtoken/JwtParserBuilder.html) — `verifyWith(SecretKey)` signature
- [Spring Security WebFlux config reference](https://docs.spring.io/spring-security/reference/reactive/configuration/webflux.html) — `SecurityWebFilterChain` + `AuthenticationWebFilter`
- [Spring Boot Tracing reference](https://docs.spring.io/spring-boot/reference/actuator/tracing.html) — auto-config of W3C trace context propagation
- `./.planning/phases/00-monorepo-scaffolding/00-CONTEXT.md` — Phase 0 D-01..D-33 (still in force)
- `./.planning/phases/00-monorepo-scaffolding/00-RESEARCH.md` — patterns inherited
- `./.planning/phases/00-monorepo-scaffolding/00-REVIEW.md` — BL-01 (Postgres init.sql role names — applies to Phase 2, not Phase 1; documented for awareness), WR-01 / WR-02 (libs/observability lifts — Phase 1 may opportunistically fix)
- `./.planning/phases/01-api-gateway/01-CONTEXT.md` — locked decisions D-01..D-19 + Claude's discretion items
- `./.planning/phases/01-api-gateway/01-DISCUSSION-LOG.md` — alternatives considered (audit trail)
- `./.planning/research/PITFALLS.md` Pitfall 1 + Pitfall 7 + Pitfall 10
- `./docs/02-architecture.md §3.1, §4.2, §5, §6.3`
- `./docs/04-api-spec.md §6 (error code catalog), §7 (rate limits), §8 (CORS)`
- `./docs/05-auth-security.md §2 (JWT design), §4 (auth request sequence), §10 (8 mandatory tests)`

### Secondary (MEDIUM confidence)

- [Spring Cloud 2025.0 Release Notes wiki](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes) — module rename detail
- [OpenRewrite recipe for SCG module rename](https://docs.openrewrite.org/recipes/java/spring/cloud2025/springcloudgatewaydeprecatedmodulesandstarters) — Pitfall D detail
- [CVE-2025-41253 advisory (ZeroPath)](https://zeropath.com/blog/cve-2025-41253-spring-cloud-gateway-spel-exposure)
- [CVE-2025-41235 advisory (Miggo)](https://www.miggo.io/vulnerability-database/cve/CVE-2025-41235)
- [Tracing in Spring Boot 3 WebFlux (Better Programming, Jonas TM)](https://medium.com/better-programming/tracing-in-spring-boot-3-webflux-d432d0c78d3e) — context propagation specifics
- [Rate Limiting With Client IP in Spring Cloud Gateway (Baeldung)](https://www.baeldung.com/spring-cloud-gateway-rate-limit-by-client-ip) — KeyResolver patterns
- [Spring Cloud Gateway: Route and Mutate Request Headers (Medium / Javarevisited)](https://medium.com/javarevisited/spring-cloud-gateway-route-and-mutate-request-headers-e44a843b7437) — `exchange.mutate()` patterns

### Tertiary (LOW confidence — validate before relying)

- jjwt usage examples on Medium / Stack Overflow — many show the deprecated 0.11/0.12 API; treat as historical, prefer the javadoc.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Spring Cloud 2025.0.2 + jjwt 0.13.0 + Spring Security 6.4 are all well-documented, cross-verified against official release notes.
- Architecture: HIGH — Phase 0 already locked the conventions (static URI routing, MDC enrichment, ProblemDetail factory). Phase 1 composes them.
- Pitfalls: HIGH — Pitfall 1 is the project's keystone; Phase 0 partial mitigations (loopback bind, no manual ObservationFilter) make Phase 1's gates well-scoped. CVE coverage (Pitfall E) is HIGH because Phase 0 already restricted actuator surface.
- Code examples: MEDIUM — code snippets compile against the documented API but have not been executed end-to-end against the project's exact dependency tree. Plan should treat them as templates to validate at Wave 1.

**Research date:** 2026-05-08
**Valid until:** 2026-06-08 (30 days for stable Spring stack; bump if a new SCG CVE drops, in which case revisit Pitfall E)
