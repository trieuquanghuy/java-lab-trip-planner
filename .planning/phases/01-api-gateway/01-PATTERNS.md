# Phase 1: API Gateway - Pattern Map

**Mapped:** 2026-05-08
**Files analyzed:** 28 new + 12 modified (40 total Phase 1 deliverables)
**Analogs found:** 40 / 40 (100% — Phase 0 shipped real code; every Phase 1 file has a concrete in-repo analog)

---

## Greenfield Status Update

Unlike Phase 0's PATTERNS.md (greenfield → all patterns from RESEARCH.md excerpts), Phase 1 has **real shipped code** to copy patterns from. Phase 0 delivered:

- 5 service skeletons (gateway WebFlux + 3 servlet DB-backed + Eureka)
- 3 shared libs (`libs/observability` fully wired, `libs/error-handling` minimal stub, `libs/api-contracts` empty)
- Phase 0 conventions enforced (C1–C25)
- Working `scripts/smoke.sh`

**Phase 1's job is to compose what's there**, not invent new patterns. Every new file in this phase has a Phase 0 sibling that defines the file-shape conventions (package, header comments referencing source, `// Source: ...` excerpt-citation discipline).

**One key carry-over fix:** Phase 0 ships `ObservabilityAutoConfiguration` with `@ConditionalOnClass(name = "...")` — Pitfall I/WR-02 calls for `@ConditionalOnWebApplication(REACTIVE|SERVLET)` instead. Research recommends bundling this fix into Phase 1 (Open Question 1 → "Fix both in Phase 1"). New `libs/jwt-common` autoconfig MUST use the corrected pattern from day one; legacy `libs/observability` is opportunistically refactored at the same time.

---

## File Inventory

Wave hints align to RESEARCH.md `## Validation Architecture / Wave 0 Gaps` (test infra first) and the GSD execution order recommended for Phase 1: catalog → libs → gateway filters → downstream filters → routing/yaml → tests → smoke extension.

### Wave 0: Test Infrastructure (lands before any production code)

| Path | Wave | Role | Data Flow | Closest Analog | Match Quality |
|------|------|------|-----------|----------------|---------------|
| `gradle/libs.versions.toml` (modify) | 0 | build/config | n/a | `gradle/libs.versions.toml` (Phase 0 same file) | exact |
| `settings.gradle.kts` (modify — add `:libs:jwt-common`) | 0 | build/config | n/a | `settings.gradle.kts` (existing 8-module include) | exact |
| `libs/jwt-common/build.gradle.kts` (NEW) | 0 | build/config | n/a | `libs/observability/build.gradle.kts` | exact |
| `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java` (NEW) | 0 | test/fixture | n/a | (no Phase 0 analog — first testFixtures source set in repo) | research-only (RESEARCH.md lines 1021-1076) |
| `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java` (NEW) | 0 | test/unit | n/a | (no Phase 0 unit test — first JUnit 5 lib test) | research-only (RESEARCH.md lines 1287) |

### Wave 1: Shared Libs Extension

| Path | Wave | Role | Data Flow | Closest Analog | Match Quality |
|------|------|------|-----------|----------------|---------------|
| `libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java` (NEW) | 1 | source/record (Principal) | request-response | `libs/error-handling/.../ErrorCode.java` (small pure-data class with header) | role-match |
| `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` (modify — extend enum) | 1 | source/enum | n/a | itself (Phase 0 stub with 2 codes — extend with 3 new constants) | exact |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtProperties.java` (NEW) | 1 | source/config-properties | config-load | `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java` (lib-side @AutoConfiguration pattern; properties pattern is research-only) | partial |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java` (NEW) | 1 | source/utility (pure Java) | transform | (no Phase 0 pure-Java utility analog — closest is `libs/error-handling/.../ProblemDetailFactory.java` for "no Spring deps, header-cite source" shape) | role-match (factory shape only) |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAuthenticationException.java` (NEW) | 1 | source/exception | n/a | (no Phase 0 exception class — first one) | research-only |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java` (NEW) | 1 | source/auto-config | n/a | `libs/observability/.../ObservabilityAutoConfiguration.java` | exact (with Pitfall I correction: use `@ConditionalOnWebApplication`) |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ReactiveJwtAuthFilter.java` (NEW) | 1 | source/webflux-filter | request-response | `libs/observability/.../ReactiveMdcEnrichmentFilter.java` | role-match (WebFilter shape) |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ReactiveJwtAuthenticationManager.java` (NEW) | 1 | source/security-component | request-response | (no Phase 0 analog) | research-only (RESEARCH.md Pattern 2) |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ServerBearerTokenConverter.java` (NEW) | 1 | source/security-component | transform | (no Phase 0 analog) | research-only (RESEARCH.md Pattern 2) |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java` (NEW) | 1 | source/servlet-filter | request-response | `libs/observability/.../MdcEnrichmentFilter.java` | exact (OncePerRequestFilter shape) |
| `libs/jwt-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (NEW) | 1 | infra/auto-config-registration | n/a | `libs/observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | exact |

### Wave 2: API Gateway Wiring

| Path | Wave | Role | Data Flow | Closest Analog | Match Quality |
|------|------|------|-----------|----------------|---------------|
| `services/api-gateway/build.gradle.kts` (modify — add 4 deps) | 2 | build/config | n/a | itself (Phase 0 `services/api-gateway/build.gradle.kts`) | exact |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/security/WebFluxSecurityConfig.java` (NEW) | 2 | source/security-config | request-response | (no Phase 0 Security config analog — first one) | research-only (RESEARCH.md Pattern 2) |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/security/XUserIdInjectionGlobalFilter.java` (NEW) | 2 | source/gateway-filter | request-response | `libs/observability/.../ReactiveMdcEnrichmentFilter.java` (WebFlux filter shape only; gateway-specific GlobalFilter is research-only) | role-match |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java` (NEW) | 2 | source/security-component | request-response | (no Phase 0 analog) | research-only (RESEARCH.md Pattern 6) |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java` (NEW) | 2 | source/webflux-filter | request-response | `libs/observability/.../ReactiveMdcEnrichmentFilter.java` (WebFilter shape) | role-match |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/routing/KeyResolverConfig.java` (NEW) | 2 | source/config (beans) | n/a | (no Phase 0 @Configuration analog — first @Bean factory) | research-only (RESEARCH.md Pattern 5) |
| `services/api-gateway/src/main/resources/application.yml` (modify — append routes; add CORS+rate-limit YAML) | 2 | config | n/a | itself (Phase 0 application.yml — append below `# Phase 1 will append /api/...` marker) | exact |
| `services/api-gateway/src/main/resources/application-docker.yml` (modify — add Redis host) | 2 | config | n/a | itself | exact |

### Wave 3: Downstream Servlet Wiring (trip-service + destination-service, identical shape)

For each `<service>` ∈ {trip, destination} with `<port>` ∈ {8082, 8083}:

| Path (templated) | Wave | Role | Data Flow | Closest Analog | Match Quality |
|------------------|------|------|-----------|----------------|---------------|
| `services/<service>-service/build.gradle.kts` (modify — add 3 deps) | 3 | build/config | n/a | `services/trip-service/build.gradle.kts` (existing) | exact |
| `services/<service>-service/src/main/java/com/tripplanner/<service>/security/ServletSecurityConfig.java` (NEW) | 3 | source/security-config | request-response | (no Phase 0 Security config — first one) | research-only (RESEARCH.md Pattern 4 lines 647-662) |
| `services/<service>-service/src/main/java/com/tripplanner/<service>/health/PingController.java` (NEW) | 3 | source/controller | request-response | `services/trip-service/src/main/java/com/tripplanner/trip/health/HealthPlaceholderController.java` | exact (servlet @RestController returning Map) |

### Wave 4: Integration Tests (gates the phase)

| Path | Wave | Role | Data Flow | Closest Analog | Match Quality |
|------|------|------|-----------|----------------|---------------|
| `services/api-gateway/src/test/java/com/tripplanner/gateway/AbstractGatewayIT.java` (NEW) | 4 | test/abstract-base | n/a | (no Phase 0 IT — first one) | research-only |
| `services/api-gateway/src/test/java/com/tripplanner/gateway/RoutingIT.java` (NEW) | 4 | test/integration | request-response | (no Phase 0 IT) | research-only |
| `services/api-gateway/src/test/java/com/tripplanner/gateway/security/AnonymousAccessIT.java` (NEW) | 4 | test/integration | request-response | (no Phase 0 IT) | research-only (D-14 mandatory) |
| `services/api-gateway/src/test/java/com/tripplanner/gateway/security/InvalidTokenIT.java` (NEW) | 4 | test/integration | request-response | (no Phase 0 IT) | research-only (D-14 mandatory) |
| `services/api-gateway/src/test/java/com/tripplanner/gateway/security/XUserIdInjectionIT.java` (NEW) | 4 | test/integration | request-response | (no Phase 0 IT) | research-only |
| `services/api-gateway/src/test/java/com/tripplanner/gateway/security/CorsIT.java` (NEW) | 4 | test/integration | request-response | (no Phase 0 IT) | research-only |
| `services/api-gateway/src/test/java/com/tripplanner/gateway/ratelimit/LoginRateLimitIT.java` (NEW) | 4 | test/integration | request-response | (no Phase 0 IT — Testcontainers Redis) | research-only (D-14 mandatory) |
| `services/api-gateway/src/test/java/com/tripplanner/gateway/observability/TraceContinuityIT.java` (NEW) | 4 | test/integration | request-response | (no Phase 0 IT) | research-only (RESEARCH.md lines 1125-1153) |
| `services/trip-service/src/test/java/com/tripplanner/trip/security/DirectServiceAccessIT.java` (NEW) | 4 | test/integration | request-response | (no Phase 0 IT) | research-only (D-14 mandatory) |
| `services/destination-service/src/test/java/com/tripplanner/destination/security/DirectServiceAccessIT.java` (NEW) | 4 | test/integration | request-response | mirror of trip-service file above | exact (after Wave 4 lands trip's) |

### Wave 5: Compose + Smoke Script Extension

| Path | Wave | Role | Data Flow | Closest Analog | Match Quality |
|------|------|------|-----------|----------------|---------------|
| `infra/docker-compose.yml` (modify — add Redis dep on api-gateway, add downstream `service_healthy` gating) | 5 | infra/orchestration | n/a | itself (existing infra/docker-compose.yml) | exact |
| `scripts/smoke.sh` (modify — append Phase 1 cases) | 5 | script/verification | n/a | itself (existing 423-line script with `--criterion <N>` dispatch) | exact |

---

## Bucket A — Test Infrastructure (Wave 0)

### `gradle/libs.versions.toml` (modify)

**Analog:** itself (existing Phase 0 file, 93 lines).

**Imports/header pattern** (lines 1-9):
```toml
# SINGLE SOURCE OF TRUTH for every dep version (D-26 / Convention C16).
# No version literals outside this file. Every service / lib build.gradle.kts
# MUST reference these via type-safe accessors (libs.spring.boot.starter.web,
# libs.bundles.observability, etc.) or via mavenBom("...:${libs.versions.<key>.get()}").
```

**Existing JWT pin to consume** (lines 32-33, 69-72):
```toml
# Security (catalogued for Phase 1 — Phase 0 does not yet use these)
jjwt = "0.13.0"

# --- JWT (catalogued for Phase 1; Phase 0 does not consume) ---
jjwt-api = { module = "io.jsonwebtoken:jjwt-api", version.ref = "jjwt" }
jjwt-impl = { module = "io.jsonwebtoken:jjwt-impl", version.ref = "jjwt" }
jjwt-jackson = { module = "io.jsonwebtoken:jjwt-jackson", version.ref = "jjwt" }
```

**Pattern to add (Phase 1 additions):**
```toml
[libraries]
# --- Spring Boot Security (Phase 1 — JWT filter chains on gateway + servlet services) ---
spring-boot-starter-security = { module = "org.springframework.boot:spring-boot-starter-security" }
spring-boot-starter-data-redis-reactive = { module = "org.springframework.boot:spring-boot-starter-data-redis-reactive" }
spring-security-test = { module = "org.springframework.security:spring-security-test" }
testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter" }
```

> **Convention C16 enforcement:** every NEW dep added in Phase 1 lands here first; service `build.gradle.kts` files reference the catalog accessor only.

### `settings.gradle.kts` (modify)

**Analog:** itself (existing 31-line file).

**Pattern to extend** (line 21-30, append `:libs:jwt-common`):
```kotlin
include(
    ":libs:observability",
    ":libs:error-handling",
    ":libs:api-contracts",
    ":libs:jwt-common",            // NEW Phase 1 module
    ":services:eureka-server",
    ":services:api-gateway",
    ":services:auth-service",
    ":services:trip-service",
    ":services:destination-service",
)
```

### `libs/jwt-common/build.gradle.kts` (NEW)

**Analog:** `libs/observability/build.gradle.kts`

**Header-comment pattern** (lines 1-7 of analog):
```kotlin
// Source: 00-CONTEXT.md D-04, 00-PATTERNS.md Bucket B, 00-RESEARCH.md Pattern 1.
//
// Pitfall 7: micrometer-tracing-bom pinned ONCE here. Consumers get it transitively.
// Do NOT register ServerHttpObservationFilter manually anywhere.
//
// This is a java-library — do NOT apply org.springframework.boot (that plugin is for
// applications; it would try to package this lib as an executable jar with a main class).
```

**Plugins block** (lines 8-11 of analog — direct copy):
```kotlin
plugins {
    `java-library`
    alias(libs.plugins.spring.dependency.management)
    `java-test-fixtures`                  // NEW for Phase 1 — JwtFixtures consumed by service tests
}
```

**Dependencies pattern** (lines 13-31 of analog — adapt for JWT):
```kotlin
dependencies {
    api(project(":libs:api-contracts"))                  // for UserContext
    api(project(":libs:error-handling"))                 // for ErrorCode + ProblemDetailFactory

    api(libs.jjwt.api)                                   // jjwt 0.13.0 from catalog
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    implementation("org.springframework.boot:spring-boot-autoconfigure")  // for @AutoConfiguration

    // Servlet filter consumers (auth/trip/destination) need spring-security-web; gateway gets it via WebFlux variant
    compileOnly("org.springframework.security:spring-security-web")
    compileOnly("org.springframework.security:spring-security-config")
    compileOnly("jakarta.servlet:jakarta.servlet-api")
    compileOnly("org.springframework:spring-webflux")    // for ReactiveJwtAuthFilter / ServerWebExchange

    testImplementation(libs.spring.boot.starter.test)

    // testFixtures classpath: jjwt + Spring core for JwtFixtures
    testFixturesApi(libs.jjwt.api)
    testFixturesImplementation(libs.jjwt.impl)
    testFixturesImplementation(libs.jjwt.jackson)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springBoot.get()}")
    }
}
```

> **Cross-reference:** the `compileOnly` split between servlet and reactive deps mirrors `libs/observability/build.gradle.kts` lines 24-30 exactly — the same pattern of "consumers bring their own runtime stack" applies.

---

## Bucket B — Shared Lib Source (Wave 1)

### `libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java` (NEW)

**Analog:** `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` (Phase 0 stub — pure data type with header citation).

**Header-comment pattern from analog** (lines 1-4):
```java
// Source: 00-CONTEXT.md D-05 — Phase 0 ships ONLY these 2 baseline codes.
// Phase 1+ extends this enum as new codes arise (auth.invalid_credentials,
// trip.not_found, validation.required_field, etc.). Adding more codes in
// Phase 0 is scope creep and contradicts D-05.
package com.tripplanner.errors;
```

**Pattern to write (Phase 1 — record implementing Principal per Open Question 2):**
```java
// Source: 01-CONTEXT.md D-04 (UserContext shape), Open Question 2 recommendation
// (record + Principal so @AuthenticationPrincipal Principal works generically and
// audit logging picks up getName() = userId).
//
// Phase 1's ServletJwtCommonFilter and ReactiveJwtAuthFilter populate this principal
// after JWT verification. Controllers consume via @AuthenticationPrincipal UserContext.
package com.tripplanner.contracts;

import java.security.Principal;

public record UserContext(String userId, String email, boolean verified) implements Principal {
    @Override
    public String getName() { return userId; }
}
```

### `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` (modify)

**Analog:** itself (existing 16-line enum).

**Existing pattern** (lines 5-16):
```java
package com.tripplanner.errors;

public enum ErrorCode {
    AUTH_UNAUTHORIZED("auth.unauthorized"),
    AUTH_RATE_LIMITED("auth.rate_limited");

    private final String code;
    ErrorCode(String code) { this.code = code; }
    public String code() { return code; }
}
```

**Pattern to extend (3 new constants per RESEARCH.md "Recommended Project Structure" line 247):**
```java
public enum ErrorCode {
    AUTH_UNAUTHORIZED("auth.unauthorized"),
    AUTH_RATE_LIMITED("auth.rate_limited"),
    AUTH_INVALID_TOKEN("auth.invalid_token"),     // NEW Phase 1
    AUTH_TOKEN_EXPIRED("auth.token_expired"),     // NEW Phase 1
    BAD_GATEWAY("gateway.bad_gateway");           // NEW Phase 1 (502 from gateway when downstream unreachable)

    private final String code;
    ErrorCode(String code) { this.code = code; }
    public String code() { return code; }
}
```

> **Update header comment** to remove the "Phase 0 ships ONLY these 2 baseline codes" sentence; replace with "Phase 1 adds AUTH_INVALID_TOKEN / AUTH_TOKEN_EXPIRED / BAD_GATEWAY per docs/04-api-spec.md §6 catalog."

### `libs/jwt-common/.../JwtVerifier.java` (NEW)

**Analog:** `libs/error-handling/.../ProblemDetailFactory.java` (Phase 0 — pure-Java factory utility, no Spring annotations).

**Source-citation header pattern from analog** (lines 1-3):
```java
// Source: 00-CONTEXT.md D-05, docs/04-api-spec.md §6 — Phase 0 minimal stub.
// Phase 1's gateway error WebFilter and Phase 2+ services call this to produce
// RFC 7807 ProblemDetail responses with stable `type` URIs and `code` extension.
```

**File-shape pattern from analog** (lines 4-23 — final class, private constructor, package-private statics):
```java
package com.tripplanner.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

public final class ProblemDetailFactory {

    private static final String TYPE_BASE = "https://tripplanner.example.com/errors/";

    private ProblemDetailFactory() {}

    public static ProblemDetail of(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + code.code()));
        pd.setProperty("code", code.code());
        return pd;
    }
}
```

**Pattern to write (per RESEARCH.md Pattern 1 lines 282-340):** This is research-driven — no Phase 0 analog has the same shape. Apply the analog's header-citation discipline + final/private-ctor/static-method shape, then paste from RESEARCH.md Pattern 1.

> **Critical gotchas (from RESEARCH.md Pitfalls A and B):**
> - Use `Jwts.parser().verifyWith(key).build().parseSignedClaims(jws)` — NOT the deprecated `parserBuilder().setSigningKey()`.
> - Validate secret length ≥ 32 bytes at constructor (throw `IllegalStateException`).

### `libs/jwt-common/.../servlet/ServletJwtCommonFilter.java` (NEW)

**Analog:** `libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java` (50 lines).

**Header-comment + package pattern from analog** (lines 1-8):
```java
// Source: docs/02-architecture.md §6.3, 00-RESEARCH.md lines 1037-1083, 00-CONTEXT.md D-04.
// Copies traceId/spanId/requestId into MDC after request begins so JSON logs emitted by
// libs/observability's logback-spring-base.xml carry trace context.
//
// userId is left empty in Phase 0 — Phase 1's JwtCommonFilter will populate it.
// Pitfall 7 (Convention C7): do NOT register Spring's HTTP observation filter manually
// — it is auto-configured by Spring Boot 3.2+ via WebHttpHandlerBuilder.
package com.tripplanner.observability;
```

**OncePerRequestFilter shape from analog** (lines 21-49 — direct shape match):
```java
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MdcEnrichmentFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public MdcEnrichmentFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        // ... read header, set MDC, doFilter, finally MDC.clear() ...
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.clear();
        }
    }
}
```

**Pattern to write (per RESEARCH.md Pattern 4 lines 549-642):** Apply the analog's:
- Class extends `OncePerRequestFilter` (not raw `Filter`).
- Constructor-inject `JwtVerifier` (mirror analog's constructor-injected `Tracer`).
- `try { chain.doFilter(req, resp); } finally { ... }` discipline — analog clears MDC; Phase 1's filter ALSO clears `SecurityContextHolder` and removes `userId` from MDC.
- Header citation references CONTEXT.md / RESEARCH.md sections.

> **Cross-reference (RESEARCH.md Pitfall F):** Servlet MDC is per-thread — no leak risk. The reactive filter at the gateway must NOT write `userId` to MDC (sidesteps Pitfall F / WR-01 carry-over). This is a servlet-only addition.

### `libs/jwt-common/.../reactive/ReactiveJwtAuthFilter.java` (NEW)

**Analog:** `libs/observability/src/main/java/com/tripplanner/observability/ReactiveMdcEnrichmentFilter.java` (50 lines).

**WebFilter shape from analog** (lines 23-49):
```java
package com.tripplanner.observability;

import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class ReactiveMdcEnrichmentFilter implements WebFilter {

    private final Tracer tracer;

    public ReactiveMdcEnrichmentFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        // ...
        return chain.filter(exchange)
                .doOnEach(signal -> { /* MDC.put */ })
                .doFinally(signalType -> MDC.clear());
    }
}
```

> **Note on architectural divergence:** Phase 1's WebFlux JWT auth in the gateway is implemented via Spring Security's `AuthenticationWebFilter` (see RESEARCH.md Pattern 2), NOT a custom `WebFilter`. This file in `libs/jwt-common/reactive/` is the wrapper bean that integrates with Spring Security via the gateway's `WebFluxSecurityConfig` — analog is reactive in shape only. The actual security-component (manager + converter) classes have no Phase 0 analog and use research code verbatim.

### `libs/jwt-common/.../JwtAutoConfiguration.java` (NEW)

**Analog:** `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java` (45 lines — exact pattern, with Pitfall I correction).

**Existing analog** (lines 21-45):
```java
@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Configuration
    @ConditionalOnClass(name = "jakarta.servlet.Filter")    // ← WR-02: should be @ConditionalOnWebApplication
    static class ServletConfig {
        @Bean
        public FilterRegistrationBean<MdcEnrichmentFilter> mdcEnrichmentFilter(Tracer tracer) {
            FilterRegistrationBean<MdcEnrichmentFilter> bean =
                    new FilterRegistrationBean<>(new MdcEnrichmentFilter(tracer));
            bean.setOrder(Integer.MIN_VALUE + 100);
            return bean;
        }
    }

    @Configuration
    @ConditionalOnClass(name = "org.springframework.web.server.WebFilter")  // ← WR-02
    static class ReactiveConfig {
        @Bean
        public ReactiveMdcEnrichmentFilter reactiveMdcEnrichmentFilter(Tracer tracer) {
            return new ReactiveMdcEnrichmentFilter(tracer);
        }
    }
}
```

**Pattern to write (Pitfall I corrected — `@ConditionalOnWebApplication` per RESEARCH.md):**
```java
// Source: 01-RESEARCH.md "Recommended Project Structure" + Pitfall I correction (WR-02 carry-over).
// Discriminates reactive vs servlet using @ConditionalOnWebApplication, NOT @ConditionalOnClass —
// see 00-REVIEW.md WR-02 rationale.
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAutoConfiguration {

    @Bean
    public JwtVerifier jwtVerifier(JwtProperties props) {
        return new JwtVerifier(props.getSecret());
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class ServletConfig {
        @Bean
        public FilterRegistrationBean<ServletJwtCommonFilter> servletJwtFilter(JwtVerifier verifier) {
            FilterRegistrationBean<ServletJwtCommonFilter> bean =
                    new FilterRegistrationBean<>(new ServletJwtCommonFilter(verifier));
            // After observability MDC filter (Integer.MIN_VALUE + 100), before any controller filter.
            bean.setOrder(Integer.MIN_VALUE + 200);
            return bean;
        }
    }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveConfig {
        // Beans for ReactiveJwtAuthenticationManager + ServerBearerTokenConverter live in
        // services/api-gateway/.../security/WebFluxSecurityConfig (where the SecurityWebFilterChain
        // is constructed). The lib only exposes JwtVerifier + the exception type for the reactive side.
    }
}
```

> **Phase-1 opportunistic fix (per Open Question 1):** While editing `libs/observability`, also flip its `@ConditionalOnClass` to `@ConditionalOnWebApplication` to clear WR-02 from the carryover backlog.

### `libs/jwt-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (NEW)

**Analog:** `libs/observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (1 line).

**Existing analog** (verbatim file content):
```
com.tripplanner.observability.ObservabilityAutoConfiguration
```

**Pattern to write:**
```
com.tripplanner.jwt.JwtAutoConfiguration
```

### `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java` (NEW)

**Analog:** none in Phase 0 (first `testFixtures` source set in repo).

**Pattern source:** RESEARCH.md lines 1021-1076 (paste verbatim, then add header citation):
```java
// Source: 01-RESEARCH.md "Code Examples" lines 1021-1076.
// Used by Phase 1 integration tests in api-gateway, trip-service, destination-service
// via testImplementation(testFixtures(project(":libs:jwt-common"))).
```

> **Cross-reference:** Wave 0 must add `java-test-fixtures` plugin to `libs/jwt-common/build.gradle.kts` AND `testImplementation(testFixtures(project(":libs:jwt-common")))` to each consumer's build file (RESEARCH.md "Wave 0 Gaps" lines 1300-1305).

---

## Bucket C — API Gateway Wiring (Wave 2)

### `services/api-gateway/build.gradle.kts` (modify)

**Analog:** itself (existing 36-line file).

**Existing dependencies block** (lines 18-29):
```kotlin
dependencies {
    implementation(project(":libs:observability"))
    implementation(project(":libs:error-handling"))
    implementation(project(":libs:api-contracts"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.cloud.starter.gateway)            // reactive gateway (NOT the WebMVC variant)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.bundles.observability)

    testImplementation(libs.spring.boot.starter.test)
}
```

**Pattern to extend (Phase 1 additions):**
```kotlin
dependencies {
    implementation(project(":libs:observability"))
    implementation(project(":libs:error-handling"))
    implementation(project(":libs:api-contracts"))
    implementation(project(":libs:jwt-common"))                          // NEW Phase 1

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)                    // NEW
    implementation(libs.spring.boot.starter.data.redis.reactive)         // NEW (rate-limiter store)
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.bundles.observability)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)                        // NEW
    testImplementation(libs.spring.boot.testcontainers)                  // NEW (Redis container)
    testImplementation(libs.testcontainers.junit.jupiter)                // NEW
    testImplementation(testFixtures(project(":libs:jwt-common")))        // NEW (JwtFixtures)
    testImplementation(libs.wiremock.spring.boot)                        // NEW (downstream stubs in RoutingIT)
}
```

> **Header-comment update:** preserve the existing 4-line preamble explaining "no Spring MVC starter — would crash WebFlux" (lines 5-9); append a Phase-1 note about Spring Security WebFlux being intentional.

### `services/api-gateway/.../security/WebFluxSecurityConfig.java` (NEW)

**Analog:** none in Phase 0 (first Spring Security configuration in the repo).

**Closest existing comment-discipline analog:** `services/api-gateway/src/main/java/com/tripplanner/gateway/health/GatewayHealthController.java` (lines 1-15 — header citing source + describing routing context).

**Header-citation pattern from analog:**
```java
// Phase 0 scaffold-only health placeholder for the gateway itself (D-01 / Convention C10).
//
// Source: adapted from 00-RESEARCH.md lines 1011-1033 (the per-service health controller template),
// substituting "api-gateway" for the service name.
//
// Reactive controller in a WebFlux app: returning Map<String, Object> is fine — Spring Cloud Gateway
// auto-wires the reactive Jackson encoder. ...
```

**Pattern to write:** apply analog's header-citation discipline; paste body from RESEARCH.md Pattern 2 (lines 350-466). Required header text:
```java
// Source: 01-RESEARCH.md Pattern 2 (lines 346-469); 01-CONTEXT.md D-03 (Spring Security
// WebFlux owns the public-route allowlist).
//
// SecurityWebFilterChain composition:
//   - permitAll: /__health/**, /actuator/health|info, /api/auth/{login,signup,verify,refresh},
//                /api/search/**, /api/destinations/**
//   - authenticated: anyExchange (catches /api/trips/**)
//   - AuthenticationWebFilter wired with JwtVerifier-backed ReactiveAuthenticationManager
//   - NoOpServerSecurityContextRepository — stateless gateway, no session
//
// CORS: localhost:5173 only, allowCredentials=true (D-03), Access-Control-Expose-Headers
//       includes X-Request-Id (D-18 propagation).
```

### `services/api-gateway/.../security/XUserIdInjectionGlobalFilter.java` (NEW)

**Analog:** `libs/observability/.../ReactiveMdcEnrichmentFilter.java` (50 lines — WebFlux mutation pattern).

**Mutation pattern from analog** (lines 32-48):
```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    if (requestId == null || requestId.isBlank()) {
        requestId = UUID.randomUUID().toString();
    }
    final String reqId = requestId;
    return chain.filter(exchange)
            .doOnEach(signal -> { /* ... */ })
            .doFinally(signalType -> MDC.clear());
}
```

**Pattern to write:** the analog provides the `exchange.getRequest().getHeaders().getFirst()` + `final String reqId` idiom Phase 1 reuses. Body comes from RESEARCH.md Pattern 3 (lines 471-545); the analog reinforces the practice of NOT mutating `MDC` here (Pitfall F sidestep). Required header text:
```java
// Source: 01-RESEARCH.md Pattern 3 (lines 471-545); 01-CONTEXT.md D-02 (Pitfall 1 keystone).
//
// Runs AFTER Spring Security AuthenticationWebFilter so SecurityContext is populated.
// STRIPS any client-supplied X-User-Id / X-User-Email and replaces them with values
// derived from the validated JWT principal. This is the gateway's contribution to
// the Pitfall 1 mitigation; downstream services re-validate via ServletJwtCommonFilter
// (defense-in-depth, D-02).
```

### `services/api-gateway/src/main/resources/application.yml` (modify — append routes)

**Analog:** itself (existing 75-line file).

**Existing route-table pattern + Phase 1 marker** (lines 22-53):
```yaml
spring:
  application:
    name: api-gateway          # D-25 (Pitfall 7) — Zipkin service name
  profiles:
    default: dev
  cloud:
    gateway:
      routes:
        # P0 health-routing convention — STATIC URIs per D-02 (no Eureka load-balanced scheme).
        - id: health-gateway
          uri: http://localhost:8080
          predicates:
            - Path=/__health/gateway
          filters:
            - SetPath=/__health
        - id: health-auth
          uri: http://auth-service:8081
          # ... (4 health routes; uniform shape)
        # Phase 1 will append /api/auth/**, /api/search/**, /api/destinations*/**, /api/trips/** below this comment.
```

**Pattern to extend:** the existing file has an explicit `# Phase 1 will append ... below this comment.` marker at line 53. Append YAML from RESEARCH.md Pattern 5 (lines 670-763). Critical convention — preserve EVERY existing health route entry above; the smoke script's SC#3-route check depends on `/__health/<svc>` continuing to work.

**Existing application-level config to interleave** (lines 54-75 — Phase 1 must add `spring.data.redis.*` + `spring.cloud.gateway.httpclient.*` BEFORE the routes block, while keeping `server.port`, `management.*`, `eureka.client.*` intact):
```yaml
server:
  port: 8080
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus    # T-00-20: env/configprops/beans/mappings INTENTIONALLY NOT exposed
  # ...
  tracing:
    sampling:
      probability: 1.0   # D-04 — full sampling in dev
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
```

> **Convention C9 reinforced:** every Phase 1 route URI is `http://service-name:port` — NEVER `lb://`.
> **Pitfall E mitigation:** do NOT add `spring.cloud.gateway.actuator.*` or any actuator endpoint expansion — keep the `include: health,info,prometheus` allowlist.

### `services/api-gateway/src/main/resources/application-docker.yml` (modify — add Redis host override)

**Analog:** itself (existing 26-line file).

**Existing override pattern** (lines 1-25):
```yaml
# Source: 00-PATTERNS.md Bucket D (lines 480-498); 00-CONTEXT.md D-21 (Eureka warm-up tuning, Pitfall 10 / C13)
spring:
  config:
    activate:
      on-profile: docker
eureka:
  # ... (5s/5s/10s tuning)
management:
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_BASE_URL:http://zipkin:9411}/api/v2/spans
```

**Pattern to extend (add Redis docker hostname override under `spring:`):**
```yaml
spring:
  config:
    activate:
      on-profile: docker
  data:
    redis:
      host: redis              # NEW Phase 1 — compose-internal DNS for the rate-limiter
      port: 6379
eureka:
  # ... (unchanged)
```

---

## Bucket D — Downstream Servlet Wiring (Wave 3)

### `services/<service>-service/build.gradle.kts` (modify; <service> ∈ {trip, destination})

**Analog:** itself (existing 39-line file — identical between trip and destination per Phase 0 PATTERNS.md Bucket E).

**Existing dependencies block** (lines 17-33):
```kotlin
dependencies {
    implementation(project(":libs:observability"))
    implementation(project(":libs:error-handling"))
    implementation(project(":libs:api-contracts"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)        // Pitfall A — MANDATORY for PG 16 support
    runtimeOnly(libs.postgresql.jdbc)
    implementation(libs.bundles.observability)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
}
```

**Pattern to extend:**
```kotlin
dependencies {
    implementation(project(":libs:observability"))
    implementation(project(":libs:error-handling"))
    implementation(project(":libs:api-contracts"))
    implementation(project(":libs:jwt-common"))                  // NEW Phase 1

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.security)            // NEW (servlet)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql.jdbc)
    implementation(libs.bundles.observability)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.test)                // NEW
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(testFixtures(project(":libs:jwt-common"))) // NEW
}
```

### `services/<service>-service/.../<service>/security/ServletSecurityConfig.java` (NEW)

**Analog:** none in Phase 0 (first Spring Security servlet config). Closest comment-discipline analog: `services/trip-service/src/main/java/com/tripplanner/trip/health/HealthPlaceholderController.java` (header citation pattern).

**Header-citation pattern from analog** (lines 1-9 of `HealthPlaceholderController.java`):
```java
// Phase 0 scaffold-only health placeholder for trip-service (D-01 / Convention C10).
//
// Source: 00-RESEARCH.md lines 1011-1033, with the per-service substitution table from
// 00-PATTERNS.md Bucket E applied (service="trip"). The api-gateway forwards /__health/trip
// to http://trip-service:8082/__health (D-02 static-URI routing); the gateway strips the
// /<svc> suffix via SetPath=/__health, so this controller responds at /__health here.
```

**Pattern to write:** apply analog's header-citation; paste body from RESEARCH.md lines 647-662. Required header:
```java
// Source: 01-RESEARCH.md Pattern 4 (lines 647-662); 01-CONTEXT.md D-04 (downstream
// SecurityContextHolder population; @AuthenticationPrincipal UserContext consumption).
//
// SecurityFilterChain composition:
//   - permitAll: /__health, /actuator/health|info  (preserves Phase 0 SC#3-route)
//   - authenticated: anyRequest
//   - ServletJwtCommonFilter wired before UsernamePasswordAuthenticationFilter
//
// Pitfall 1 keystone (Pitfall C): this filter MUST run on every authenticated path,
// regardless of whether the call came through the gateway. Phase 1's
// DirectServiceAccessIT integration test asserts a direct localhost:8082 call with
// crafted X-User-Id and no Authorization returns 401.
```

> **Per-service substitution:** identical config text in trip-service and destination-service; only the package (`com.tripplanner.trip.security` vs `com.tripplanner.destination.security`) differs.

### `services/<service>-service/.../<service>/health/PingController.java` (NEW)

**Analog:** `services/trip-service/src/main/java/com/tripplanner/trip/health/HealthPlaceholderController.java` (29 lines — exact pattern match).

**Existing analog (Phase 0 scaffold pattern):**
```java
// Phase 0 scaffold-only health placeholder for trip-service (D-01 / Convention C10).
//
// Source: 00-RESEARCH.md lines 1011-1033, with the per-service substitution table from
// 00-PATTERNS.md Bucket E applied (service="trip"). The api-gateway forwards /__health/trip
// to http://trip-service:8082/__health (D-02 static-URI routing); ...
//
// Phase 1 ships /api/trips/_ping (single underscore) as the application-level probe. The
// double-underscore /__health stays as the Phase 0 scaffold probe forever (Convention C10).
package com.tripplanner.trip.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthPlaceholderController {

    @GetMapping("/__health")
    public Map<String, Object> health() {
        return Map.of(
            "service", "trip-service",
            "status", "UP",
            "phase", 0
        );
    }
}
```

**Pattern to write (extend exact analog with auth-aware payload per RESEARCH.md lines 985-1019):**
```java
// Source: 01-RESEARCH.md "Code Examples / _ping controller" (lines 983-1019);
// 01-CONTEXT.md D-12 (permanent debug endpoint).
//
// Companion to HealthPlaceholderController (/__health) — this is the Phase 1
// application-level probe at /api/trips/_ping. It exercises the JWT filter +
// SecurityContextHolder + Tracer integration:
//   - @AuthenticationPrincipal resolves to the UserContext populated by
//     ServletJwtCommonFilter
//   - Tracer.currentSpan() returns the auto-instrumented gateway-propagated trace id
//
// Phase 5+ keeps this endpoint alongside real /api/trips/* routes — it's a permanent
// runtime sanity check ("is the gateway routing here with the right principal?").
package com.tripplanner.trip.health;

import com.tripplanner.contracts.UserContext;
import io.micrometer.tracing.Tracer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class PingController {

    private final Tracer tracer;
    public PingController(Tracer tracer) { this.tracer = tracer; }

    @GetMapping("/api/trips/_ping")
    public Map<String, Object> ping(@AuthenticationPrincipal UserContext user,
                                    @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
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

> **Per-service substitution:**
> - trip-service → `package com.tripplanner.trip.health`, `@GetMapping("/api/trips/_ping")`, `"service": "trip-service"`
> - destination-service → `package com.tripplanner.destination.health`, `@GetMapping("/api/destinations/_ping")`, `"service": "destination-service"`

---

## Bucket E — Compose & Smoke Script (Wave 5)

### `infra/docker-compose.yml` (modify)

**Analog:** itself (existing 172-line file with all 10 services).

**Existing api-gateway block** (lines 76-89):
```yaml
api-gateway:
  build:
    context: ..
    dockerfile: services/api-gateway/Dockerfile
  ports:
    - "0.0.0.0:8080:8080"                    # public surface
  depends_on:
    eureka-server:
      condition: service_healthy
  environment:
    SPRING_PROFILES_ACTIVE: docker
    EUREKA_URL: http://eureka-server:8761/eureka
    ZIPKIN_BASE_URL: http://zipkin:9411
```

**Pattern to extend (add Redis dep per Pitfall H + downstream `service_healthy` gating per Pitfall J):**
```yaml
api-gateway:
  build:
    context: ..
    dockerfile: services/api-gateway/Dockerfile
  ports:
    - "0.0.0.0:8080:8080"
  depends_on:
    eureka-server:
      condition: service_healthy
    redis:                          # NEW Pitfall H — gateway needs Redis for rate-limiter
      condition: service_healthy
    auth-service:                   # NEW Pitfall J — avoid first-request 502
      condition: service_healthy
    trip-service:
      condition: service_healthy
    destination-service:
      condition: service_healthy
  environment:
    SPRING_PROFILES_ACTIVE: docker
    EUREKA_URL: http://eureka-server:8761/eureka
    ZIPKIN_BASE_URL: http://zipkin:9411
    AUTH_JWT_SECRET: ${AUTH_JWT_SECRET}     # NEW Phase 1 — pass JWT secret to gateway
```

> **Pattern reuse:** the existing `redis:` block (lines 41-49) already has a healthcheck (`redis-cli ping | grep -q PONG`); no change there. Just add the depends_on edge.
> **Per-service env addition:** trip-service and destination-service env blocks (lines 113-133, 135-155) MUST also gain `AUTH_JWT_SECRET: ${AUTH_JWT_SECRET}` so their `JwtVerifier` resolves at startup.

### `scripts/smoke.sh` (modify — extend with Phase 1 cases)

**Analog:** itself (existing 423-line script with `--criterion <N>` dispatch).

**Existing dispatch pattern** (lines 372-384):
```bash
run_criterion() {
  local CRITERION="$1"
  case "$CRITERION" in
    1)        check_sc1 ;;
    2)        check_sc2 ;;
    3)        check_sc3 ;;
    3-route)  check_sc3_route ;;
    4)        check_sc4 ;;
    5)        check_sc5 ;;
    nfr-04)   check_nfr_04 ;;
    *)        fail "Unknown criterion '$CRITERION'. Use --list to see available criteria." ;;
  esac
}
```

**Existing check function pattern** (lines 209-232 — `check_sc3_route`):
```bash
check_sc3_route() {
  for svc in auth trip destination; do
    local url="http://localhost:8080/__health/$svc"
    local body
    if ! body=$(curl -sf "$url" 2>/dev/null); then
      fail "SC#3-route: $url unreachable"
    fi
    # ... jq + grep fallback for status check ...
  done
  pass "SC#3-route /__health/<svc> UP for auth, trip, destination"
}
```

**Pattern to extend (per RESEARCH.md "Wave 0 Gaps" lines 1316-1318):**
```bash
# NEW Phase 1 — anonymous /api/trips/_ping returns 401
check_sc1_p1_anon() {
  local code
  code=$(curl -fs -o /dev/null -w '%{http_code}' http://localhost:8080/api/trips/_ping || true)
  [ "$code" = "401" ] || fail "SC#P1-anon: expected 401 from /api/trips/_ping without Auth, got $code"
  pass "SC#P1-anon /api/trips/_ping returns 401 without Authorization"
}

# NEW Phase 1 — CORS preflight allowlist
check_sc1_p1_cors() {
  local headers
  headers=$(curl -fs -D - -o /dev/null -X OPTIONS \
              -H 'Origin: http://localhost:5173' \
              -H 'Access-Control-Request-Method: GET' \
              http://localhost:8080/api/trips/_ping || true)
  echo "$headers" | grep -qi 'access-control-allow-origin: http://localhost:5173' \
    || fail "SC#P1-cors: preflight did not return allowlisted origin"
  pass "SC#P1-cors preflight ACAO header for localhost:5173"
}
```

> **Dispatch additions:** add `p1-anon` and `p1-cors` to the case dispatch (line 374-383); add to `list_criteria()` and `usage()` (lines 56-65, 76-85); add to `run_full_gate()` (line 361-370).

---

## Shared Patterns

### Header-Citation Discipline

**Source:** every Phase 0 source file (e.g., `libs/observability/.../MdcEnrichmentFilter.java` lines 1-7, `libs/error-handling/.../ProblemDetailFactory.java` lines 1-3, `services/trip-service/.../health/HealthPlaceholderController.java` lines 1-9).

**Apply to:** every NEW source file in Phase 1.

**Pattern:**
```java
// <Brief one-line description of what this file does>
//
// Source: <CONTEXT.md decision IDs>; <RESEARCH.md section names + line ranges>;
//         <PATTERNS.md bucket reference if applicable>.
//
// <Optional: 2-4 lines of why-context, especially calling out Pitfall/Convention IDs>.
package <package>;
```

This discipline is a Phase 0 convention not formally listed in C1-C25 but uniformly enforced across every shipped file. Phase 1 must continue it; planner MUST require explicit Source citations on every new file's first comment block.

### Auto-Configuration Pattern

**Source:** `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java` (45 lines).

**Apply to:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java`.

**Pattern (with Pitfall I correction):**
```java
@AutoConfiguration
public class XxxAutoConfiguration {
    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)  // ← Pitfall I correction
    static class ServletConfig { @Bean ... }

    @Configuration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ReactiveConfig { @Bean ... }
}
```

Plus `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` registering the FQN.

### Servlet Filter Wiring Pattern

**Source:** `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java` lines 27-33.

**Apply to:** `JwtAutoConfiguration.ServletConfig.servletJwtFilter(...)`.

**Pattern:**
```java
@Bean
public FilterRegistrationBean<ServletJwtCommonFilter> servletJwtFilter(JwtVerifier verifier) {
    FilterRegistrationBean<ServletJwtCommonFilter> bean =
            new FilterRegistrationBean<>(new ServletJwtCommonFilter(verifier));
    bean.setOrder(Integer.MIN_VALUE + 200);  // After observability MDC filter (+100)
    return bean;
}
```

> **Order discipline:** observability MDC filter is at `Integer.MIN_VALUE + 100`. JWT filter MUST run after MDC enrichment so that traceId/requestId are already in MDC when the JWT filter writes userId. Pick `Integer.MIN_VALUE + 200`.

### MDC Hygiene Pattern (Servlet only)

**Source:** `libs/observability/.../MdcEnrichmentFilter.java` lines 43-47:
```java
try {
    chain.doFilter(req, resp);
} finally {
    MDC.clear();
}
```

**Apply to:** `ServletJwtCommonFilter.doFilterInternal` (set `userId`, then clear in `finally`). MDC.clear() in the observability filter handles the global cleanup; the JWT filter's `finally` only needs to remove `userId` and clear `SecurityContextHolder`.

### MDC Avoidance at Gateway (Reactive)

**Source:** `libs/observability/.../ReactiveMdcEnrichmentFilter.java` header comment (lines 1-7) explicitly notes the WebFlux MDC pattern is acknowledged-imperfect.

**Apply to:** Phase 1's gateway filters MUST NOT write `userId` to MDC. RESEARCH.md Pitfall F mandates this sidestep. The gateway logs will show empty `userId`; downstream service logs are the source of truth for userId attribution.

### Convention C9 — Static-URI Routing

**Source:** `services/api-gateway/src/main/resources/application.yml` lines 6-12 (header explaining hard rule).

**Apply to:** every new route in Phase 1. NEVER use `lb://` URIs. Static `http://service-name:port` only.

### Convention C7 — No Manual ObservationFilter

**Source:** `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java` lines 9-11.

**Apply to:** every Phase 1 file. Do NOT register `ServerHttpObservationFilter` manually anywhere. Spring Boot 3.2+ auto-configures it.

### Convention C2 — Three Profiles Only (dev / docker / test)

**Source:** every existing `application*.yml` and Phase 0 PATTERNS.md C2.

**Apply to:** Phase 1's application.yml extensions and any new test config. NO `prod` profile.

### Convention C16 — Catalog as Sole Version Source

**Source:** `gradle/libs.versions.toml` header (lines 1-9) — "No version literals outside this file."

**Apply to:** every Phase 1 dep. New JWT-common deps go in `libs.versions.toml` first; service `build.gradle.kts` files reference accessors only.

### Convention C25 — Locked Port Assignments

**Source:** Phase 0 PATTERNS.md C25 + `infra/docker-compose.yml` port mappings.

**Apply to:** Phase 1 Redis port (6379), gateway (8080), trip (8082), destination (8083). Don't change. Phase 1 does NOT introduce any new external port.

---

## No Analog Found (per file)

These Phase 1 files have NO Phase 0 analog and rely on RESEARCH.md excerpts only. Planner should:
1. Cite RESEARCH.md line ranges in plan actions.
2. Apply the header-citation discipline (Shared Patterns above) regardless.
3. Mark these files with extra verify-block scrutiny because no project-internal precedent exists.

| File | Role | Reason | Code Source |
|------|------|--------|-------------|
| `libs/jwt-common/.../JwtVerifier.java` | utility | first pure-Java jjwt usage in repo | RESEARCH.md Pattern 1 (lines 282-340) |
| `libs/jwt-common/.../JwtAuthenticationException.java` | exception | first custom exception class in repo | RESEARCH.md "Recommended Project Structure" line 228 |
| `libs/jwt-common/.../JwtProperties.java` | config-properties | first `@ConfigurationProperties` in repo | RESEARCH.md "Recommended Project Structure" line 227, D-16 |
| `libs/jwt-common/.../reactive/ReactiveJwtAuthenticationManager.java` | security-component | first reactive auth manager | RESEARCH.md Pattern 2 lines 422-435 |
| `libs/jwt-common/.../reactive/ServerBearerTokenConverter.java` | security-component | first reactive auth converter | RESEARCH.md Pattern 2 lines 412-420 |
| `libs/jwt-common/src/testFixtures/.../JwtFixtures.java` | test-fixture | first testFixtures source set | RESEARCH.md "Code Examples" lines 1021-1076 |
| `libs/jwt-common/src/test/.../JwtVerifierTest.java` | unit-test | first lib unit test | research-driven (no analog) |
| `services/api-gateway/.../security/WebFluxSecurityConfig.java` | security-config | first Spring Security in repo | RESEARCH.md Pattern 2 lines 350-466 |
| `services/api-gateway/.../security/ProblemDetailAuthEntryPoint.java` | security-component | first ServerAuthenticationEntryPoint | RESEARCH.md Pattern 6 lines 805-827 |
| `services/api-gateway/.../security/RateLimitProblemDetailFilter.java` | webflux-filter | first response-decorating WebFilter | RESEARCH.md Pattern 6 lines 832-862 |
| `services/api-gateway/.../routing/KeyResolverConfig.java` | config | first @Bean factory in api-gateway | RESEARCH.md Pattern 5 lines 772-800 |
| `services/<service>-service/.../security/ServletSecurityConfig.java` | security-config | first servlet Spring Security config | RESEARCH.md Pattern 4 lines 647-662 |
| All Wave 4 IT files | integration-test | first IT in repo (Phase 0 ships only frontend smoke + bash smoke) | RESEARCH.md "Validation Architecture / Wave 0 Gaps" lines 1296-1318 |

---

## Conventions to Enforce (cross-cutting checklist for the planner)

The planner MUST verify every plan applies these conventions consistently. Each is rooted in 01-CONTEXT.md decisions, RESEARCH.md pitfalls, or carries forward from Phase 0 PATTERNS.md C1-C25.

| # | Convention | Source | Applies to |
|---|-----------|--------|-----------|
| C1-P1 | Java package `com.tripplanner.jwt` for the new shared lib (parallel to `com.tripplanner.observability`, `com.tripplanner.errors`, `com.tripplanner.contracts`) | C1 (Phase 0); 01-CONTEXT.md D-28 | `libs/jwt-common/src/main/java/com/tripplanner/jwt/**` |
| C2-P1 | Phase 1 inherits Phase 0 C2 — only `dev`, `docker`, `test` profiles. NO `prod`. | 01-CONTEXT.md "Specifics" line 169 | All `application*.yml` edits |
| C3-P1 | Existing C3 (spring.application.name) preserved on every service — Phase 1 must not remove it | Phase 0 C3 | All `application.yml` |
| C6-P1 | Phase 1 inherits C6 (no per-service version literals); micrometer-tracing-bom still pinned ONCE in libs/observability/build.gradle.kts | Phase 0 C6 | All `build.gradle.kts` edits |
| C7-P1 | Phase 1 inherits C7 — NO manual `@Bean ServerHttpObservationFilter` ANYWHERE | Phase 0 C7; RESEARCH.md "Anti-Patterns" line 871 | All Phase 1 files |
| C9-P1 | Phase 1 inherits C9 — gateway routes use `http://service-name:port`, NEVER `lb://` | Phase 0 C9; 01-CONTEXT.md D-08 | `services/api-gateway/src/main/resources/application.yml` |
| C10-P1 | `/__health/**` is Phase 0; `/api/<svc>/_ping` is Phase 1 — they coexist forever per D-12 | Phase 0 C10; 01-CONTEXT.md D-12 | trip-service + destination-service |
| C11-P1 | Phase 1 extends C11 — api-gateway's depends_on now includes redis + auth-service + trip-service + destination-service with `service_healthy` (Pitfall H + Pitfall J) | RESEARCH.md Pitfalls H, J | `infra/docker-compose.yml` |
| C13-P1 | Phase 1 inherits C13 — no new Eureka client tuning; existing 5s/5s/10s stays | Phase 0 C13 | api-gateway, trip, destination application-docker.yml |
| C14-P1 | Phase 1 inherits C14 — `AUTH_JWT_SECRET` from env; `.env.example` already has placeholder | Phase 0 C14; 01-CONTEXT.md D-16 | All service application.yml + docker-compose.yml |
| C16-P1 | Phase 1 inherits C16 — every NEW dep version in `gradle/libs.versions.toml` first | Phase 0 C16 | All `build.gradle.kts` edits |
| C17-P1 | Phase 1 inherits C17 — Spring Cloud train is `2025.0.x` (Northfields) | Phase 0 C17; gradle/libs.versions.toml line 20 | `gradle/libs.versions.toml` |
| C26-P1 | NEW: defense-in-depth — every authenticated endpoint downstream MUST have `ServletJwtCommonFilter` even before real endpoints exist (Pitfall 1 / Pitfall C) | 01-CONTEXT.md D-02; RESEARCH.md Pitfall C | trip-service, destination-service |
| C27-P1 | NEW: jjwt 0.13.0 modern API only — no `parserBuilder().setSigningKey(...)` (Pitfall A) | RESEARCH.md Pitfall A | All `JwtVerifier`-touching code |
| C28-P1 | NEW: `JwtVerifier` constructor validates secret length ≥ 32 bytes; throws `IllegalStateException` (Pitfall B) | RESEARCH.md Pitfall B | `libs/jwt-common/.../JwtVerifier.java` |
| C29-P1 | NEW: gateway MUST NOT write `userId` to MDC (Pitfall F sidestep — WebFlux MDC leak); servlet MAY | RESEARCH.md Pitfall F | All gateway WebFilter classes |
| C30-P1 | NEW: every `KeyResolver` returns non-empty `Mono` via `switchIfEmpty(Mono.just("anonymous"))` (Pitfall G) | RESEARCH.md Pitfall G | `KeyResolverConfig.java` |
| C31-P1 | NEW: WebFluxSecurityConfig uses `@ConditionalOnWebApplication(REACTIVE\|SERVLET)` not `@ConditionalOnClass(name=...)` (Pitfall I / WR-02 fix) | RESEARCH.md Pitfall I; Open Question 1 | `JwtAutoConfiguration.java` + opportunistic `ObservabilityAutoConfiguration.java` retrofit |
| C32-P1 | NEW: `UserContext` is a `record` implementing `java.security.Principal` with `getName() = userId` (Open Question 2) | RESEARCH.md Open Question 2 | `libs/api-contracts/.../UserContext.java` |
| C33-P1 | NEW: Phase 1 does NOT enable `spring.cloud.gateway.server.webflux.trusted-proxies` — `X-Forwarded-*` trust stays OFF (CVE-2025-41235) | RESEARCH.md Pitfall E + Anti-Patterns | `application.yml` |
| C34-P1 | NEW: Phase 1 does NOT expose any `gateway.*` actuator endpoint — `include: health,info,prometheus` stays (CVE-2025-41253) | RESEARCH.md Pitfall E | gateway `application.yml` |
| C35-P1 | NEW: every Phase 1 source file carries the header-citation pattern (file purpose + Source: + Pitfall/Convention IDs) | shared-patterns above | All `*.java` Phase 1 files |
| C36-P1 | NEW: header for `KeyResolver` ipKeyResolver explicitly notes the compose-bridge-IP caveat (Assumption A5) — single dev hits localhost so IP appears as one bridge IP, acceptable for portfolio | RESEARCH.md Assumption A5 | `KeyResolverConfig.java` header comment |

---

## Per-Plan Pattern Hand-off Map

This is the planner's quick-reference. For each plan that gsd-planner produces, the recommended primary analog + bucket reference:

| Plan candidate | Primary analog | Bucket | Key pitfalls |
|----------------|---------------|--------|--------------|
| Plan: Wave 0 — testFixtures + catalog | `gradle/libs.versions.toml` self-edit; `libs/observability/build.gradle.kts` | A | C16-P1, C27-P1 |
| Plan: Wave 1 — libs/jwt-common (Verifier, exception, properties, autoconfig, MetaInf imports, UserContext, ErrorCode extension) | `libs/observability/.../ObservabilityAutoConfiguration.java`, `libs/error-handling/.../ProblemDetailFactory.java`, `libs/observability/.../MdcEnrichmentFilter.java`, `libs/observability/.../ReactiveMdcEnrichmentFilter.java` | B | C27-P1, C28-P1, C31-P1, C32-P1 |
| Plan: Wave 2 — gateway security config, GlobalFilter, EntryPoint, KeyResolverConfig, application.yml extension | `services/api-gateway/src/main/resources/application.yml`, `libs/observability/.../ReactiveMdcEnrichmentFilter.java` | C | C9-P1, C29-P1, C30-P1, C33-P1, C34-P1 |
| Plan: Wave 3 — downstream ServletSecurityConfig + PingController (× 2 services) | `services/trip-service/.../health/HealthPlaceholderController.java`, `services/trip-service/build.gradle.kts` | D | C1-P1, C10-P1, C26-P1 |
| Plan: Wave 4 — 9 IT files | (research-only — no Phase 0 IT) | — | C26-P1 (DirectServiceAccessIT is the keystone test) |
| Plan: Wave 5 — compose + smoke extension | `infra/docker-compose.yml`, `scripts/smoke.sh` | E | C11-P1 |

---

## Metadata

**Analog search scope:** repository-wide; emphasis on `libs/observability`, `libs/error-handling`, `libs/api-contracts`, `services/api-gateway`, `services/trip-service`, `services/destination-service`, `gradle/libs.versions.toml`, `infra/docker-compose.yml`, `scripts/smoke.sh`.
**Files scanned:** 28 source/config files (all in-repo Phase 0 deliverables).
**Primary external pattern source:** `01-RESEARCH.md` Patterns 1-6 + Code Examples (lines 276-1153).
**Phase 0 PATTERNS.md alignment:** Bucket lettering A-E preserved (no Bucket F-I needed in Phase 1; Phase 0's Bucket F-I covered infra/frontend/CI which Phase 1 doesn't touch except for compose).
**Pattern extraction date:** 2026-05-08

**Confidence breakdown:**
- File classification: HIGH — extracted directly from CONTEXT.md `<domain>` In-scope and RESEARCH.md "Recommended Project Structure" + "Wave 0 Gaps".
- Analog selection: HIGH for files with Phase 0 siblings (build.gradle.kts, application.yml, autoconfig, filter, controller, smoke script); MEDIUM for files with no analog (security configs, security components, IT tests) — these rely on RESEARCH.md excerpts which the planner must cite by line range.
- Cross-cutting conventions: HIGH — Phase 0 enforced C1-C25 uniformly; Phase 1 inherits + extends to C26-P1..C36-P1 with concrete RESEARCH.md citations.
