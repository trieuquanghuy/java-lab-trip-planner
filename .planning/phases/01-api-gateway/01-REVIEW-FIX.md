---
phase: 01-api-gateway
fixed_at: 2026-05-09T13:42:00Z
review_path: .planning/phases/01-api-gateway/01-REVIEW.md
iteration: 1
findings_in_scope: 13
fixed: 13
skipped: 0
status: all_fixed
---

# Phase 01: Code Review Fix Report

**Fixed at:** 2026-05-09T13:42:00Z
**Source review:** .planning/phases/01-api-gateway/01-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 13
- Fixed: 13
- Skipped: 0

## Fixed Issues

### BL-01: RFC 7807 `code` field inconsistency

**Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java`, `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java`, `services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayForgedJwtIT.java`, `services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayMissingAuthHeaderIT.java`, `services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayProblemDetailRenderingIT.java`, `services/api-gateway/src/test/java/com/tripplanner/gateway/it/LoginRateLimiterIT.java`
**Commit:** ce0a78b
**Applied fix:** Injected Spring Boot's auto-configured ObjectMapper (with ProblemDetailJacksonMixin) via constructor into both ProblemDetailAuthEntryPoint and RateLimitProblemDetailFilter, replacing `new ObjectMapper()`. Updated all gateway integration test assertions from `$.properties.code` to `$.code` to match the now-consistent contract.

### BL-02: JWT error classification by string-matching

**Files modified:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAuthenticationException.java`, `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java`, `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java`, `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ServerBearerTokenConverter.java`, `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java`, `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java`
**Commit:** 76218bb
**Applied fix:** Added `Reason` enum (EXPIRED, INVALID, MISSING_SUB) to JwtAuthenticationException with reason() accessor. Updated JwtVerifier to pass Reason on throw. Updated ServletJwtCommonFilter and ProblemDetailAuthEntryPoint to dispatch on `ex.reason()` instead of `ex.getMessage().contains("expired")`. Updated JwtVerifierTest to verify reason on expired token test.

### BL-03: Spring Cloud version pin documentation

**Files modified:** `gradle/libs.versions.toml`
**Commit:** eee1079
**Applied fix:** Added ADR-PENDING comment acknowledging the deviation from CLAUDE.md's locked stack and noting a stack-amendment ADR should be raised.

### BL-04: mint-test-token.sh leaks JWT in test reports

**Files modified:** `libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/MintTokenMain.java`, `libs/jwt-common/build.gradle.kts`, `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtFixturesSmokeMintTask.java`, `scripts/mint-test-token.sh`
**Commit:** 408a977
**Applied fix:** Created MintTokenMain in testFixtures with standalone main(). Added `mintTestToken` JavaExec task to build.gradle.kts. Updated mint-test-token.sh to use `./gradlew :libs:jwt-common:mintTestToken -q` with length sanity check (`awk 'length > 60'`). Disabled JwtFixturesSmokeMintTask with @Disabled annotation.

### WR-01: RateLimitProblemDetailFilter double-write

**Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java`
**Commit:** ce0a78b (combined with BL-01)
**Applied fix:** Added AtomicBoolean `written` guard against re-invocation in the decorator. Set Content-Length header explicitly before writing response body.

### WR-02: X-Request-Id missing on 429 path

**Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java`
**Commit:** ce0a78b (combined with BL-01)
**Applied fix:** Added X-Request-Id echo from request to response in `writeProblemDetail()` method.

### WR-03: ServletJwtCommonFilter finally clears SecurityContext unconditionally

**Files modified:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java`
**Commit:** 76218bb (combined with BL-02)
**Applied fix:** Added `boolean weSet` flag to track whether filter set the SecurityContext. Only clear SecurityContext/MDC in finally if flag was set. Added `shouldNotFilterAsyncDispatch()` override returning true.

### WR-04: JwtAuthenticationException message can leak into RFC 7807 detail

**Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java`, `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java`
**Commit:** 76218bb (combined with BL-02)
**Applied fix:** Replaced `ex.getMessage()` with constant detail messages per error code: "Token has expired", "Token is invalid", "Authentication required".

### WR-05: ServerBearerTokenConverter case-sensitive "Bearer " prefix

**Files modified:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/reactive/ServerBearerTokenConverter.java`, `libs/jwt-common/src/main/java/com/tripplanner/jwt/servlet/ServletJwtCommonFilter.java`
**Commit:** 76218bb (combined with BL-02)
**Applied fix:** Used `regionMatches(true, 0, "Bearer ", 0, 7)` for case-insensitive matching in both ServerBearerTokenConverter and ServletJwtCommonFilter.

### WR-06: KeyResolverConfig userIdKeyResolver fallback to "anonymous"

**Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/routing/KeyResolverConfig.java`
**Commit:** 1f913e6
**Applied fix:** Replaced `switchIfEmpty(Mono.just("anonymous"))` with `switchIfEmpty(Mono.error(new IllegalStateException(...)))` to fail loud if resolver is accidentally attached to a public route.

### WR-07: CORS missing exposed headers

**Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/WebFluxSecurityConfig.java`
**Commit:** d133414
**Applied fix:** Added "Retry-After" and "WWW-Authenticate" to `cfg.setExposedHeaders(...)`.

### WR-08: JwtVerifier uses raw SecretKeySpec instead of Keys.hmacShaKeyFor

**Files modified:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java`
**Commit:** 76218bb (combined with BL-02)
**Applied fix:** Replaced `new SecretKeySpec(bytes, "HmacSHA256")` with `Keys.hmacShaKeyFor(bytes)`. Removed manual length check; jjwt's WeakKeyException is caught and re-thrown as IllegalStateException with descriptive message to preserve backward-compatible test assertions.

### WR-09: JwtVerifierTest dead code

**Files modified:** `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java`
**Commit:** 76218bb (combined with BL-02)
**Applied fix:** Deleted the first (discarded) token build in `tokenMissingSubThrowsWithMissingSubMessage`. Only the second build (assigned to `noSubToken`) is retained.

---

_Fixed: 2026-05-09T13:42:00Z_
_Fixer: the agent (gsd-code-fixer)_
_Iteration: 1_
