---
phase: 01-api-gateway
reviewed: 2026-05-09T00:00:00Z
depth: standard
files_reviewed: 49
files_reviewed_list:
  - gradle/libs.versions.toml
  - infra/docker-compose.yml
  - libs/api-contracts/build.gradle.kts
  - libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java
  - libs/api-contracts/src/test/java/com/tripplanner/contracts/UserContextTest.java
  - libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java
  - libs/jwt-common/build.gradle.kts
  - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAuthenticationException.java
  - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java
  - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtProperties.java
  - libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java
  - libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ReactiveJwtAuthenticationManager.java
  - libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ServerBearerTokenConverter.java
  - libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java
  - libs/jwt-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtFixturesSmokeMintTask.java
  - libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java
  - libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java
  - libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java
  - scripts/README.md
  - scripts/mint-test-token.sh
  - scripts/smoke.sh
  - services/api-gateway/build.gradle.kts
  - services/api-gateway/src/main/java/com/tripplanner/gateway/observability/GatewayTracingObservationConfig.java
  - services/api-gateway/src/main/java/com/tripplanner/gateway/routing/KeyResolverConfig.java
  - services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java
  - services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java
  - services/api-gateway/src/main/java/com/tripplanner/gateway/security/WebFluxSecurityConfig.java
  - services/api-gateway/src/main/java/com/tripplanner/gateway/security/XUserIdInjectionGlobalFilter.java
  - services/api-gateway/src/main/resources/application-docker.yml
  - services/api-gateway/src/main/resources/application.yml
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayCorsIT.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayForgedJwtIT.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayMissingAuthHeaderIT.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayProblemDetailRenderingIT.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayRoutingIT.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayTraceContinuityIT.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/LoginRateLimiterIT.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/XUserIdInjectionIT.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/support/GatewayItProperties.java
  - services/api-gateway/src/test/java/com/tripplanner/gateway/it/support/GatewayTracingTestConfig.java
  - services/api-gateway/src/test/resources/application-gateway-it-ratelimit.yml
  - services/api-gateway/src/test/resources/application-gateway-it.yml
  - services/destination-service/build.gradle.kts
  - services/destination-service/src/main/java/com/tripplanner/destination/security/RestAuthenticationEntryPoint.java
  - services/destination-service/src/main/java/com/tripplanner/destination/security/ServletSecurityConfig.java
  - services/destination-service/src/test/java/com/tripplanner/destination/security/DirectServiceAccessWithoutGatewayReturns401IT.java
  - services/trip-service/build.gradle.kts
  - services/trip-service/src/main/java/com/tripplanner/trip/security/RestAuthenticationEntryPoint.java
  - services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java
  - services/trip-service/src/test/java/com/tripplanner/trip/security/DirectServiceAccessWithoutGatewayReturns401IT.java
  - settings.gradle.kts
findings:
  blocker: 4
  warning: 9
  info: 5
  total: 18
status: issues_found
---

# Phase 1: Code Review Report — api-gateway

**Reviewed:** 2026-05-09
**Depth:** standard
**Files Reviewed:** 49
**Status:** issues_found

## Summary

Phase 1 ships the Pitfall-1 keystone (gateway X-User-Id strip+inject + downstream defense-in-depth ServletJwtCommonFilter), JWT verification, RFC 7807 problem-detail rendering, IP-only Redis rate limiting, and W3C trace propagation. Most of the design is sound, but adversarial review surfaced several real defects of differing severity:

1. **Inconsistent RFC 7807 JSON shape across services.** The gateway emits `code` nested under `$.properties.code` (raw `new ObjectMapper()` without the Spring Boot mixin), while downstream servlet services emit `code` at `$.code` (auto-configured ObjectMapper with `ProblemDetailJacksonMixin`). The phase prompt explicitly mandates `code, correlationId at root not under properties`. Clients receive different shapes depending on which service answered — a public API contract bug.
2. **JWT identity-classification by string-matching.** `ServletJwtCommonFilter.doFilterInternal` and `ProblemDetailAuthEntryPoint.commence` decide between `AUTH_TOKEN_EXPIRED` and `AUTH_INVALID_TOKEN` by calling `getMessage().contains("expired")`. The exception-type signal (`ExpiredJwtException`) is already available in `JwtVerifier.verify`; throwing it away and pattern-matching the human-readable string is brittle and changes behavior for any future message containing the substring "expired".
3. **MDC leak on async / stale auth on error path.** `ServletJwtCommonFilter`'s `finally` block always clears the SecurityContext and MDC even on success. When the request handler dispatches to async (Spring MVC `DeferredResult` / `WebAsyncManager`) the request thread returns through this filter while async work is still running on a different thread; both contexts are wiped before downstream code can use them, AND the cleared context returns to the thread pool clean — but downstream async code that captured the original context will see it cleared mid-run. Even without async, on the `JwtAuthenticationException` branch the filter still clears the *outer* MDC keys it never set on this code path, masking values written by `MdcEnrichmentFilter` upstream.
4. **`JwtFixturesSmokeMintTask` builds a test JWT inside `mint-test-token.sh` and exposes it on stdout.** The script uses Gradle test runner output to extract a token. Any future failure or warning whose lines coincidentally match the regex pattern `^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$` would be returned as a token (unlikely but possible — JaCoCo class names use that shape); more importantly, running `./gradlew :libs:jwt-common:test --tests JwtFixturesSmokeMintTask` runs the test class which uses `System.out.println(token)` — the token is captured to Gradle's test report XML in `build/test-results`, where it persists in CI artifacts. Token value is short-lived (15 min) but audits often surface this kind of test artifact.

The Phase 0 / 1 Spring Cloud version pin (`2025.0.2`) deliberately deviates from the `CLAUDE.md` instruction "Spring Cloud 2024.0.x" with a comment explaining the locked stack is wrong. The deviation is itself defensible (2024.0 is for SB 3.4, 3.5 needs 2025.0) but per the project's own policy of "no negotiation on locked stack" this should have been raised as a stack-amendment ADR rather than absorbed silently inside `libs.versions.toml`. Flagged BLOCKER because CLAUDE.md is the stated source of truth.

A handful of warnings cover defensive issues (CORS allowCredentials with explicit origin is fine, but the wildcard fallback elsewhere isn't — Authorization not exposed; dnd-related and react-related entries in the project context are out of scope here), unhandled `Mono.error(jpe)` in `ProblemDetailAuthEntryPoint` with no fallback, the rate-limit response header strip preserved across decoration, and a duplicate dead-code block in `JwtVerifierTest.tokenMissingSubThrowsWithMissingSubMessage`.

## Blockers

### BL-01: RFC 7807 `code` field is at `$.properties.code` on the gateway, but at `$.code` on downstream services — public API contract is inconsistent

**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java:29`
**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java:46`
**File:** `services/destination-service/src/main/java/com/tripplanner/destination/security/RestAuthenticationEntryPoint.java:35-39`
**File:** `services/trip-service/src/main/java/com/tripplanner/trip/security/RestAuthenticationEntryPoint.java:35-39`

**Issue:** The phase prompt explicitly states "RFC 7807 problem-detail JSON shape (`code`, `correlationId` at root not under `properties`)." Both gateway emitters (`ProblemDetailAuthEntryPoint` and `RateLimitProblemDetailFilter`) instantiate `new ObjectMapper()` directly. The Spring Boot `ProblemDetailJacksonMixin` is registered ONLY on the auto-configured `ObjectMapper` bean; bypassing it means `setProperty("code", ...)` serializes under `$.properties.code` — which is exactly what the gateway IT files at `GatewayMissingAuthHeaderIT:96`, `GatewayForgedJwtIT:102`, `LoginRateLimiterIT:139`, etc. assert (`jsonPath("$.properties.code")`).

In contrast, downstream `RestAuthenticationEntryPoint` (both copies) and `ServletJwtCommonFilter` use the auto-configured `ObjectMapper` (constructor-injected), so they emit `$.code` — and the downstream IT `DirectServiceAccessWithoutGatewayReturns401IT` asserts on `$.code`, NOT `$.properties.code`.

A real client cannot read the same field in both places; you've shipped two different error envelopes through one product surface. The `docs/04-api-spec.md §6` reference (cited in `ProblemDetailAuthEntryPoint`) defines a single contract, not two.

**Fix:** Inject Spring Boot's auto-configured `ObjectMapper` into `ProblemDetailAuthEntryPoint` and `RateLimitProblemDetailFilter`, mirroring `ServletJwtCommonFilter`'s constructor pattern. Then update `GatewayMissingAuthHeaderIT`, `GatewayForgedJwtIT`, `GatewayProblemDetailRenderingIT`, and `LoginRateLimiterIT` to assert `$.code` (so both stacks expose the same contract).
```java
// ProblemDetailAuthEntryPoint
@Component
public class ProblemDetailAuthEntryPoint implements ServerAuthenticationEntryPoint {
    private final ObjectMapper mapper;          // injected, not new'd
    public ProblemDetailAuthEntryPoint(ObjectMapper mapper) { this.mapper = mapper; }
    // ...
}
// RateLimitProblemDetailFilter
@Component @Order(-2)
public class RateLimitProblemDetailFilter implements WebFilter {
    private final ObjectMapper mapper;          // injected
    public RateLimitProblemDetailFilter(ObjectMapper mapper) { this.mapper = mapper; }
    // ...
}
```

### BL-02: JWT error classification by message-string-matching swallows the typed exception signal

**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java:54-58`
**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java:81-83`
**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java:39-45`

**Issue:** `JwtVerifier.verify` catches `ExpiredJwtException` separately, then collapses the type information into a `JwtAuthenticationException("token expired", ex)`. Both filters then re-classify the failure by calling `getMessage().contains("expired")`. This is fragile: any change to the message text (translation, log enrichment, jjwt internal-message format change in a future patch) silently flips a 401 from `auth.token_expired` to `auth.invalid_token` — the latter is the wrong code per `docs/04-api-spec.md §6` and downstream client logic that distinguishes "force re-login" from "show invalid-token toast" breaks.

Also: `ProblemDetailAuthEntryPoint` ignores the case where `ex.getMessage()` is `null` (it tests `jae.getMessage() != null`, good) but it then falls through to `BadCredentialsException` → `AUTH_INVALID_TOKEN` even when the cause is a `JwtAuthenticationException` whose original cause was `ExpiredJwtException` if the message somehow doesn't contain "expired". Type-based dispatch is the only correct way to do this.

**Fix:** Introduce typed subclasses or enum field on `JwtAuthenticationException` so the classification is type-driven, not message-driven.
```java
public class JwtAuthenticationException extends AuthenticationException {
    public enum Reason { EXPIRED, INVALID, MISSING_SUB }
    private final Reason reason;
    public JwtAuthenticationException(Reason reason, String message, Throwable cause) {
        super(message, cause); this.reason = reason;
    }
    public Reason reason() { return reason; }
}
// JwtVerifier.verify
} catch (ExpiredJwtException ex) {
    throw new JwtAuthenticationException(Reason.EXPIRED, "token expired", ex);
} catch (JwtException ex) {
    throw new JwtAuthenticationException(Reason.INVALID, "token invalid", ex);
}
// Filter / EntryPoint dispatch on .reason() instead of .getMessage().contains(...)
```

### BL-03: Spring Cloud version pin (`2025.0.2`) silently overrides the locked stack documented in CLAUDE.md (`2024.0.x`)

**File:** `gradle/libs.versions.toml:17-20`

**Issue:** CLAUDE.md (project-instruction, OVERRIDE) explicitly states: "Spring Cloud Gateway | Spring Cloud 2023.x (matches SB 3.3) | **Spring Cloud 2024.0.x** (matches SB 3.5)" as the recommended pin. The libs catalog comment claims this is wrong and pins `2025.0.2`. The comment IS technically right per Spring's matrix (Spring Cloud 2024.0 train shipped in May 2024 with `springCloudVersion=2024.0.x` for Boot 3.4; the 2025.0 "Northfields" train released in May 2025 targets Boot 3.5+). However:

- The project policy says "Tech stack — locked... no negotiation on language/framework." A unilateral upgrade to `2025.0.x` inside a phase implementation is a stack amendment that should be raised as an ADR or a CLAUDE.md update PR, not absorbed silently in a `libs.versions.toml` comment.
- Spring Cloud `2025.0` introduces a **new property prefix** (`spring.cloud.gateway.server.webflux.*`) replacing the deprecated `spring.cloud.gateway.*`. The application.yml still uses the old prefix (`spring.cloud.gateway.routes:`) — and the test-fixture comment in `GatewayItProperties.java:10-15` admits this directly: "GatewayProperties uses spring.cloud.gateway.server.webflux as its prefix (Spring Cloud 2025.0 migration) while application.yml still uses the deprecated spring.cloud.gateway prefix (pending application.yml migration to new prefix)." This is half-migrated config — a deprecation warning today, a startup failure tomorrow when the deprecated prefix is removed.

**Fix:** Pick one path:
1. Revert the catalog to `springCloud = "2024.0.x"` (latest patch matching SB 3.5 — verify on Maven Central; SB 3.5.14 actually pairs with **2024.0.x** OR **2025.0.x** — Spring Boot's release notes tabulate both as supported), AND keep current YAML; OR
2. Keep `2025.0.2` AND finish the migration: rename `spring.cloud.gateway.routes` → `spring.cloud.gateway.server.webflux.routes` in both `application.yml` files (production and test profiles), update CLAUDE.md, and amend the locked-stack ADR.

Do not ship the half-migrated state. The half-migration has already required the test profile (`application-gateway-it.yml`) to redefine all routes because `@DynamicPropertySource` partial-list overrides break — that's a real bug already paid for.

### BL-04: `mint-test-token.sh` runs a Gradle test that prints a JWT to stdout, persisting it in test-result XML in CI artifacts

**File:** `scripts/mint-test-token.sh:23-25`
**File:** `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtFixturesSmokeMintTask.java:13-19`

**Issue:** The script invokes `./gradlew :libs:jwt-common:test --tests JwtFixturesSmokeMintTask`. The Gradle test runner captures `System.out.println(token)` into JUnit XML in `build/test-results/test/TEST-com.tripplanner.jwt.JwtFixturesSmokeMintTask.xml` (and HTML reports in `build/reports/tests/test/`). When CI runs the dev test suite (which it will: the class lives under `src/test/java`, not gated), every CI run produces an artifact containing a freshly-minted, valid JWT signed by the project's TEST_SECRET. CI artifact retention is typically 90 days. Anyone with read access to the artifacts (often "anyone with the repo URL" for public portfolio repos) can extract a token signed by `phase-1-jwt-fixture-secret-32bytes!!`.

That secret is also the same value bound to `auth.jwt.secret` for the integration tests via `GatewayItProperties.JWT_SECRET_PROPERTY`. If a developer accidentally exports `AUTH_JWT_SECRET=phase-1-jwt-fixture-secret-32bytes!!` in any non-test environment (copy-paste from `.env.example`?), tokens minted by anyone with the test secret authenticate against that environment. The 32-byte minimum is satisfied; nothing else stops it.

Secondarily, the regex filter in the script (`^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$`) matches the JWT shape but ALSO matches dotted Java class names like `com.tripplanner.jwt.JwtVerifier` if such a string is ever printed on its own line by Gradle's plain console — unlikely, but the script has no defense (no length check, no base64 sanity). A spurious match would be returned as if it were a token.

**Fix:** Replace the gradle-test approach with a Gradle JavaExec task that doesn't ship a `*Test` artifact and writes the token only to stdout (not to any file or test report).
```kotlin
// libs/jwt-common/build.gradle.kts
tasks.register<JavaExec>("mintTestToken") {
    classpath = sourceSets["testFixtures"].runtimeClasspath
    mainClass.set("com.tripplanner.jwt.MintTokenMain")
    standardOutput = System.out
}
// new src/testFixtures/java/com/tripplanner/jwt/MintTokenMain.java with public static void main()
```
Then `mint-test-token.sh` becomes `./gradlew :libs:jwt-common:mintTestToken -q` with no test report side-effect. Add a length sanity check (`awk 'length > 60'`) before `head -n1`. Also rename `JwtFixturesSmokeMintTask` to make clear it is NOT a test (or delete it once the JavaExec replaces it) — JUnit will keep running it on every `./gradlew test` until you do.

## Warnings

### WR-01: `RateLimitProblemDetailFilter` emits ProblemDetail body but never sets Content-Length, and `setComplete()` override may double-write on lifecycle reentry

**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java:64-83`

**Issue:** The decorator overrides both `writeWith` and `setComplete`. `setComplete()` is called by RequestRateLimiterGatewayFilterFactory on the empty-body path; that path calls `writeProblemDetail()` which calls `super.writeWith(...)` — but Spring's reactive HttpResponse contract only allows ONE of (writeWith, setComplete) to be invoked per response. If `setComplete` is invoked first (the rate-limiter path) and it returns a publisher whose subscription emits a write, then a different filter elsewhere calls `setComplete()` again, the underlying Reactor-Netty machinery may emit `IllegalStateException: Only one connection receive subscriber allowed` — your fallback path is actually swapping a `setComplete` call for a `writeWith`+`setComplete` pair, which is technically a violation of the contract (although in practice tolerated by the current Netty integration).

Also: `getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON)` is called in `writeProblemDetail`, but if Spring Cloud Gateway has already committed headers before reaching the filter (rare, but possible if an exception is thrown after a partial write), `setContentType` becomes a silent no-op and the response goes out without `application/problem+json`. There's no `if (getHeaders().isReadOnly())` guard.

**Fix:** Set `Content-Length` explicitly, and guard against re-invocation:
```java
private final java.util.concurrent.atomic.AtomicBoolean written = new AtomicBoolean(false);

private Mono<Void> writeProblemDetail() {
    if (!written.compareAndSet(false, true)) return Mono.empty();
    if (getHeaders().containsKey("Content-Type")) return super.setComplete(); // already committed
    getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    ProblemDetail pd = ProblemDetailFactory.of(...);
    try {
        byte[] bytes = mapper.writeValueAsBytes(pd);
        getHeaders().setContentLength(bytes.length);
        return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
    } catch (JsonProcessingException ex) { return Mono.error(ex); }
}
```

### WR-02: `XUserIdInjectionGlobalFilter` mutates the response in `beforeCommit` after exchange has been mutated — the original response, not the mutated one, may be the one written

**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/XUserIdInjectionGlobalFilter.java:67-73, 90-95`

**Issue:** The code does `ServerWebExchange mutatedExchange = exchange.mutate().request(mutated).build();` (mutates only the request), then registers `mutatedExchange.getResponse().beforeCommit(...)` to set X-Request-Id. `mutatedExchange.getResponse()` returns the SAME ServerHttpResponse instance as `exchange.getResponse()` (the response is not mutated). That works — the same instance is observed by the gateway's `NettyWriteResponseFilter` — BUT if any UPSTREAM filter (e.g. `RateLimitProblemDetailFilter` at `@Order(-2)`) decorates the response after this filter has registered `beforeCommit`, the decorator chains through and the registration still fires. Order is:
1. `RateLimitProblemDetailFilter` (Order -2) wraps response with decorator A.
2. `XUserIdInjectionGlobalFilter` (Order -100) operates on the wrapped response (decorator A) and registers `beforeCommit` on it.
3. NettyRoutingFilter writes to decorator A; decorator A intercepts, fires `beforeCommit`, sets `X-Request-Id` on decorator A's headers.

This works for the normal path. But `RateLimitProblemDetailFilter` is `@Order(-2)`, while `GlobalFilter` ordering uses Spring Cloud Gateway's `Ordered` interface where `XUserIdInjectionGlobalFilter` is at `-100`. The Reactor pipeline orders **GlobalFilter** beans separately from **WebFilter** beans; Spring Security's WebFilter runs first, then all WebFilters in order, then the WebHandler invokes the GatewayFilterChain (where GlobalFilter Ordering applies). Net effect: `RateLimitProblemDetailFilter` runs BEFORE `XUserIdInjectionGlobalFilter` in the WebFilter pipeline (order -2 vs implicit), so by the time XUserIdInjection runs, the response IS already wrapped, and `beforeCommit` is registered on the wrapper. Fine.

The actual subtle bug: `beforeCommit` on the underlying ServerHttpResponse is called when headers are about to flush. If the rate limiter SHORT-CIRCUITS the chain (returns 429 BEFORE NettyRoutingFilter runs), the `chain.filter(mutatedExchange)` line in `XUserIdInjectionGlobalFilter` is never reached — meaning `beforeCommit` was never registered for that 429 response — meaning the rate-limited response loses `X-Request-Id`. The integration test `XUserIdInjectionIT.xRequestId_propagated_when_client_supplies_one` does NOT cover the rate-limited 429 path. So a rate-limited client can't correlate the rejection with their request — degrades T-01-12 / D-18.

**Fix:** Move X-Request-Id propagation upstream of the rate limiter (e.g. `@Order(-3)` WebFilter), OR have `RateLimitProblemDetailFilter.writeProblemDetail` echo the header from the request:
```java
private Mono<Void> writeProblemDetail() {
    String reqId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    if (reqId != null) getHeaders().set("X-Request-Id", reqId);
    // ... rest unchanged
}
```

### WR-03: `ServletJwtCommonFilter.finally` always clears SecurityContext + MDC even when the filter wrote a 401 and returned early

**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java:71-88`

**Issue:** The `try { ... verify ... } catch (...) { write401 } finally { clearContext; MDC.remove }` covers ONLY the path that reached `try`. The early-return paths (whitelist hit, missing/invalid Authorization header) bypass the try-finally entirely AND the finally clears nothing (good there). But within the try, on the catch-branch (`JwtAuthenticationException`), the filter writes a 401 ProblemDetail then falls through to `finally` and clears the SecurityContext / MDC. Good.

The bug: on the SUCCESS branch, after `chain.doFilter(req, resp)` returns, the `finally` runs and clears SecurityContext and MDC. That is the right cleanup for synchronous filters but is WRONG for async dispatching. If the controller returns a `DeferredResult` / `CompletableFuture`, the original request thread exits this filter (running `finally`) while the async task still holds a reference to the SecurityContext. With `OncePerRequestFilter` on a Spring MVC dispatch, async re-entry runs the filter chain again with `isAsyncDispatch()==true`; this filter does NOT call `super.shouldNotFilterAsyncDispatch()` — meaning it re-runs `doFilterInternal`, calls `verify` AGAIN, sets a NEW SecurityContext, then clears it AGAIN. Performance hit and a double-MDC-write/clear. More importantly, code that uses `SecurityContextHolder` in a `@Async` method invoked during the request dispatch will see `null` after the original thread's `finally` runs.

For Phase 1 there are no controllers or async dispatching, but `@AuthenticationPrincipal` is intended to be used by Phase 2+ controllers, and at that point this becomes a live bug.

**Fix:** Override `shouldNotFilterAsyncDispatch()` to return `true` (default already, but defensive), and only clear the SecurityContext/MDC the filter SET. Also verify that `MDC.remove("userId")` is harmless if "userId" was never put — it is, MDC.remove is a no-op when the key is absent. The real fix:
```java
@Override
protected boolean shouldNotFilterAsyncDispatch() { return true; } // default; assert it
@Override
protected boolean shouldNotFilterErrorDispatch() { return false; } // ensure error dispatch still authenticates
// only clear if we set:
boolean weSet = false;
try {
    UserContext user = verifier.verify(...);
    SecurityContextHolder.getContext().setAuthentication(auth);
    MDC.put("userId", user.userId());
    weSet = true;
    chain.doFilter(req, resp);
} catch (JwtAuthenticationException ex) { ... }
finally {
    if (weSet) {
        SecurityContextHolder.clearContext();
        MDC.remove("userId");
    }
}
```

### WR-04: `JwtAuthenticationException` message can leak into RFC 7807 `detail` — exposes internal exception messages to clients

**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java:54-58`
**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java:84`
**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java:47`

**Issue:** `writeProblem(resp, HttpStatus.UNAUTHORIZED, code, ex.getMessage())` — `ex.getMessage()` is the exception's message. For `JwtAuthenticationException("token invalid", JwtException ex)`, that's "token invalid". But `BadCredentialsException` inside `ProblemDetailAuthEntryPoint` originates from `ReactiveJwtAuthenticationManager`'s `Mono.error(new BadCredentialsException(ex.getMessage(), ex))`. `ex.getMessage()` is the original `JwtAuthenticationException` message (so "token expired" or "token invalid") — also fine. But if jjwt internals throw a `JwtException` whose message contains user-controllable input (e.g. malformed-payload diagnostics: `"JWT payload is malformed: garbage"` where "garbage" is part of the user's submitted token), that message reaches `ProblemDetail.detail` and is rendered as JSON to the client. Most jjwt messages are safe, but the dependency on "jjwt sanitizes its own diagnostics" is implicit and fragile.

`GatewayProblemDetailRenderingIT.error_body_does_NOT_leak_stack_trace_or_internal_details` checks for "Exception" / "at com.tripplanner" / "Caused by" but does NOT check that the user's token bytes don't appear in the body.

**Fix:** Don't pass `ex.getMessage()` to `ProblemDetail.detail`. Use a constant per-error-code message:
```java
String detail = switch (code) {
    case AUTH_TOKEN_EXPIRED -> "Token has expired";
    case AUTH_INVALID_TOKEN -> "Token is invalid";
    default -> "Authentication required";
};
ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.UNAUTHORIZED, code, detail);
```

### WR-05: `ServerBearerTokenConverter` accepts case-sensitive "Bearer " prefix only — RFC 6750 §2.1 says the scheme is case-insensitive

**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ServerBearerTokenConverter.java:24-26`
**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java:65, 72`

**Issue:** RFC 6750 §2.1 mandates: "The syntax for Bearer credentials is as follows: ... credentials = 'Bearer' 1*SP b64token. The Bearer authentication scheme is case-insensitive" (per RFC 7235 §2.1). The current `startsWith("Bearer ")` check rejects `bearer`, `BEARER`, `Bearer\t` (tab as separator), and any other RFC-conformant variant. Real-world clients (especially mobile SDKs that have case-normalization bugs) sometimes send lowercase `bearer`, and you'll reject them with a 401 looking exactly like an auth failure — but the failure mode is that the converter returned `Mono.empty()` so the SecurityWebFilterChain treats the request as unauthenticated and delegates to ProblemDetailAuthEntryPoint with `AUTH_UNAUTHORIZED` (not `AUTH_INVALID_TOKEN`), making the bug hard to diagnose.

`ServletJwtCommonFilter` has the same issue.

**Fix:** Use case-insensitive prefix matching:
```java
if (h == null || h.length() < 7 || !h.regionMatches(true, 0, "Bearer ", 0, 7)) return Mono.empty();
String token = h.substring(7).trim();
```

### WR-06: `KeyResolverConfig.userIdKeyResolver` falls back to "anonymous" and burns into the same Redis bucket for every unauthenticated user

**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/routing/KeyResolverConfig.java:58-65`

**Issue:** The comment says "effectively unreachable on authenticated() routes because the SecurityWebFilterChain redirects unauthenticated requests to the ProblemDetailAuthEntryPoint before the RequestRateLimiter runs." That is TRUE for `/api/trips/**` (which is authenticated), but the bean is `@Bean public KeyResolver userIdKeyResolver()` — it can be referenced by ANY route filter via `key-resolver: "#{@userIdKeyResolver}"`. If a future engineer attaches it to a public route without thinking, every anonymous request shares the bucket "anonymous", which means a single attacker can DoS the rate limit for all anonymous users on that route.

The fallback "unknown" in `ipKeyResolver` has the same shape but is less risky because remote address is almost always present. The `userIdKeyResolver` fallback is more dangerous because the contract "this bean is only used on authenticated routes" is not enforced anywhere.

**Fix:** Have `userIdKeyResolver` return `Mono.empty()` on missing principal, and configure RequestRateLimiter to deny when key is empty (the `denyEmptyKey: true` option) — OR throw an `IllegalStateException` so misconfiguration is loud:
```java
@Bean
public KeyResolver userIdKeyResolver() {
    return exchange -> ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(a -> a != null && a.isAuthenticated() && a.getPrincipal() instanceof UserContext)
            .map(a -> ((UserContext) a.getPrincipal()).userId())
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "userIdKeyResolver invoked without authenticated principal — " +
                "do NOT attach this resolver to a public route")));
}
```

### WR-07: CORS configuration allows credentials AND a small set of origins, but does not list `Access-Control-Expose-Headers` for `WWW-Authenticate` — clients can't read the auth challenge

**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/WebFluxSecurityConfig.java:69-80`

**Issue:** When the gateway returns 401 with the RFC 7807 body, browser-side JS in the SPA can read the body (because Content-Type passes the simple-response filter), but it cannot read the `WWW-Authenticate` header (if Spring Security ever sets one) or `Retry-After` (set by the rate limiter on 429) because they aren't in `Access-Control-Expose-Headers`. The current expose list is only `X-Request-Id`. For 429 specifically, the SPA needs `Retry-After` to know how long to back off.

**Fix:** Add `Retry-After` (and optionally `WWW-Authenticate`) to the exposed headers list:
```java
cfg.setExposedHeaders(List.of("X-Request-Id", "Retry-After", "WWW-Authenticate"));
```

### WR-08: `JwtVerifier` constructor uses raw `SecretKeySpec(bytes, "HmacSHA256")` instead of jjwt's `Keys.hmacShaKeyFor` — silently truncates/pads

**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java:30-35`

**Issue:** `new SecretKeySpec(bytes, "HmacSHA256")` accepts a byte[] of any length and uses it as the HMAC key directly. JJwt's `Keys.hmacShaKeyFor(byte[])` validates that the key length matches the algorithm's minimum (256 bits for HS256, 384 for HS384, 512 for HS512) and rejects too-short keys with `WeakKeyException`. The constructor's manual `bytes.length < 32` check covers HS256 specifically, but if the verifier is later extended to support HS512 (which would require ≥64 bytes), the manual check would still pass at 32 bytes and SecretKeySpec would happily produce a too-weak key for HS512.

Also: `SecretKeySpec("HmacSHA256")` ties the verifier to HS256 even though `Jwts.parser().verifyWith(secretKey)` infers the algorithm from the JWS header — which IS what the test for `wrong_alg_rs256_token_returns_401_invalid_token` relies on. This works because `verifyWith(SecretKey)` for an HMAC key rejects RSA-signed tokens at parse time with a clear error. But the algorithm pinning is implicit, not explicit.

**Fix:** Use jjwt's `Keys.hmacShaKeyFor`:
```java
import io.jsonwebtoken.security.Keys;
// ...
this.signingKey = Keys.hmacShaKeyFor(bytes);
```
And remove the manual length check (jjwt's `WeakKeyException` is more informative). Catch `WeakKeyException` in the constructor and re-throw as `IllegalStateException` with the descriptive message.

### WR-09: `JwtVerifierTest.tokenMissingSubThrowsWithMissingSubMessage` builds the same token twice — the first build is dead code

**File:** `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java:64-88`

**Issue:** Lines 67-74 build a no-sub token and discard the return value. Lines 76-83 build the same token again and assign to `noSubToken`. The first build is dead code — possibly a copy-paste from a refactor. Distracts the reader and bloats the test.

**Fix:** Delete lines 67-74. Use only the second build.

## Info

### IN-01: `JwtAutoConfiguration` exposes `JwtVerifier` as the only public verifier bean — no fallback for in-test secret rotation

**File:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java:26-29`

**Issue:** The `jwtVerifier` `@Bean` has no `@ConditionalOnMissingBean` guard. A test that wants to inject a mock `JwtVerifier` (e.g. to test a downstream service's behavior with a verifier that always succeeds, regardless of token bytes) cannot override the bean. This is fine for v1 but will hurt Phase 2+ where auth-service tests might want a stub.

**Fix:** Add `@ConditionalOnMissingBean(JwtVerifier.class)`:
```java
@Bean
@ConditionalOnMissingBean(JwtVerifier.class)
public JwtVerifier jwtVerifier(JwtProperties props) { return new JwtVerifier(props.getSecret()); }
```

### IN-02: `application.yml` exposes `prometheus` actuator publicly via the gateway

**File:** `services/api-gateway/src/main/resources/application.yml:144-148`

**Issue:** `management.endpoints.web.exposure.include: health,info,prometheus`. The gateway is the only service bound to `0.0.0.0` (per docker-compose.yml line 90). That means `localhost:8080/actuator/prometheus` is reachable from any network-adjacent host and dumps internal metrics (request counts, error rates, JVM stats) — useful for sizing an attack and identifying when monitoring is being scraped. CLAUDE.md doesn't mandate prometheus locally but does mandate "free-tier only" / minimal exposed surface.

**Fix:** Either route prometheus through a separate management port (`management.server.port: 9080`, bound to 127.0.0.1 only via compose port mapping), or drop `prometheus` from the gateway's exposure list and expose it only on downstream services (which are loopback-bound).

### IN-03: `JwtFixtures.mintExpired` issued-at is 7200s in the past, expiration is 60s in the past — a 7140-second-validity expired token

**File:** `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java:39-48`

**Issue:** `issuedAt(now-7200), expiration(now-60)`. The token was "valid" for 7140 seconds — far longer than the 15-minute production access token lifetime. It still expires correctly so the test passes, but the token shape doesn't match production. If a future test asserts on token age (e.g. "reject tokens older than 30 minutes regardless of exp"), this fixture would fail unexpectedly.

**Fix:**
```java
.issuedAt(Date.from(Instant.now().minusSeconds(900)))  // 15 min ago
.expiration(Date.from(Instant.now().minusSeconds(60))) // expired 1 min ago — 14-min validity
```

### IN-04: Several files use raw `new ObjectMapper()` — shadowed by Spring Boot's auto-configured bean which costs less and registers more modules

**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java:29`
**File:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java:46`

**Issue:** Documented in BL-01. Listed here to also flag the resource cost — every `new ObjectMapper()` instance carries ~3MB heap on first use and re-discovers Jackson modules from the classpath. The auto-configured bean is shared. (Constructor-injection of `ObjectMapper` fixes both this and BL-01.)

**Fix:** Already covered by BL-01 fix.

### IN-05: `application-docker.yml` does not set `auth.jwt.secret` — relies on `AUTH_JWT_SECRET` env propagation through compose

**File:** `services/api-gateway/src/main/resources/application-docker.yml`
**File:** `infra/docker-compose.yml:102-106` (api-gateway environment block)

**Issue:** `application-docker.yml` does not bind `auth.jwt.secret`. `JwtProperties.@ConfigurationProperties("auth.jwt")` reads from Spring's environment, and Spring Boot's relaxed binding maps `AUTH_JWT_SECRET` env var → `auth.jwt.secret`. BUT the api-gateway's compose `environment:` block (lines 102-106 of docker-compose.yml) does NOT export `AUTH_JWT_SECRET`. The other services (auth, trip, destination) also do not export it. If `AUTH_JWT_SECRET` is set in the host shell when `docker compose up` runs, it gets propagated only if the Compose service has `environment: { AUTH_JWT_SECRET: ${AUTH_JWT_SECRET} }` — which is missing.

This means `JwtProperties.secret` is null at gateway boot in the docker profile, `JwtVerifier` constructor throws `IllegalStateException("AUTH_JWT_SECRET must be set...")`, and the container fails health check. Pitfall B fail-fast behavior is correct, but every developer running `docker compose up` for the first time hits this and has to debug why all 4 services refuse to start.

**Fix:** Add `AUTH_JWT_SECRET: ${AUTH_JWT_SECRET}` to each backend service's `environment:` block in `infra/docker-compose.yml`, and document the requirement in `.env.example`. (This may already be in `.env.example` — outside this review's file scope — but the compose file definitely doesn't propagate it.)

---

_Reviewed: 2026-05-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
