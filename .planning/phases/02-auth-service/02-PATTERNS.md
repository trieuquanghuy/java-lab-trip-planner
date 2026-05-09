# Phase 2: Auth Service — Pattern Map

**Mapped:** 2026-05-09
**Files analyzed:** 50 (43 NEW + 7 MODIFIED)
**Analogs found:** 47 / 50 (94% — strong Phase 0/1 base)
**Phase kind:** backend (per UI-SPEC frontmatter — zero React components in Phase 2; SPA owned by Phase 7)

> **Greenfield-on-skeleton note.** Phase 2 builds production code on top of the Phase 0/1 scaffold. The `services/auth-service/` skeleton already has `AuthServiceApplication.java`, `application.yml`, `application-docker.yml`, `Dockerfile`, `build.gradle.kts`, `db/migration/V1__init.sql` (empty `SELECT 1;` baseline), and `health/HealthPlaceholderController.java`. Phase 1 already shipped the gateway-side WebFlux config + `libs/jwt-common` (`JwtVerifier`, `JwtAutoConfiguration`, `JwtFixtures`, `ServletJwtCommonFilter`), `libs/error-handling` (`ProblemDetailFactory`, `ErrorCode`), `libs/api-contracts` (`UserContext`), `libs/observability` (`MdcEnrichmentFilter`). The **closest existing analogs for every Phase-2 file live in this repo**, not in RESEARCH excerpts — this PATTERNS.md cites them by file path + line number throughout.
>
> **No React/frontend files in this phase.** UI-SPEC.md frontmatter `phase_kind: backend`; sections 2-5 (Visuals/Color/Typography/Spacing) are explicitly marked N/A. The "verification email" + "RFC 7807 detail strings" + "redirect query-param vocabulary" surfaces are server-side string literals. SPA `/verify`, login screen, signup form belong to Phase 7.

---

## File Classification

### NEW files (40)

| File | Role | Data Flow | Closest Analog | Match Quality |
|------|------|-----------|----------------|---------------|
| `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java` | controller | request-response | `services/api-gateway/.../health/GatewayHealthController.java` (thin route handler) + RESEARCH §Pattern 2/3 | role-match (no other servlet REST controller in repo yet) |
| `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java` | exception-mapper | request-response | RESEARCH §Code Examples lines 903-1008 (only example) + `libs/error-handling/ProblemDetailFactory.java` | RESEARCH-driven — no `@ControllerAdvice` exists yet in repo |
| `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/SignupRequest.java` | DTO | request-response | RESEARCH §Code Examples lines 887-901 (only example) | RESEARCH-driven |
| `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginRequest.java` | DTO | request-response | sibling `SignupRequest.java` | sibling-twin |
| `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/LoginResponse.java` | DTO | request-response | `libs/api-contracts/.../UserContext.java` (record idiom) | role-match |
| `services/auth-service/src/main/java/com/tripplanner/auth/api/dto/RefreshResponse.java` | DTO | request-response | sibling `LoginResponse.java` | sibling-twin |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java` | service | CRUD | RESEARCH §Pattern 1, 3 | RESEARCH-driven (greenfield service layer) |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/EmailVerificationService.java` | service | CRUD | RESEARCH §Pattern 2 (consume + idempotency) | RESEARCH-driven |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java` | service | CRUD | RESEARCH §Pattern 4 (rotate + revokeChain) | RESEARCH-driven |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/LoginRateLimiter.java` | service | event-driven (Redis pub-sub-ish) | RESEARCH §Pattern 6 (Lua INCR+EXPIRE) | RESEARCH-driven (no Redis client usage in any servlet service yet) |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/EmailAlreadyRegisteredException.java` | exception | n/a | `libs/jwt-common/.../JwtAuthenticationException.java` | exact (custom checked/unchecked exception) |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/InvalidCredentialsException.java` | exception | n/a | `libs/jwt-common/.../JwtAuthenticationException.java` | exact |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/EmailNotVerifiedException.java` | exception | n/a | `libs/jwt-common/.../JwtAuthenticationException.java` | exact |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/TokenInvalidException.java` | exception | n/a | `libs/jwt-common/.../JwtAuthenticationException.java` | exact |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/TokenExpiredException.java` | exception | n/a | `libs/jwt-common/.../JwtAuthenticationException.java` | exact |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/RefreshInvalidException.java` | exception | n/a | `libs/jwt-common/.../JwtAuthenticationException.java` | exact |
| `services/auth-service/src/main/java/com/tripplanner/auth/service/exception/LoginRateLimitedException.java` | exception | n/a | `libs/jwt-common/.../JwtAuthenticationException.java` | exact |
| `services/auth-service/src/main/java/com/tripplanner/auth/domain/User.java` | entity | persistence | RESEARCH §V2 migration (lines 765-779) | role-match (first JPA entity in repo) |
| `services/auth-service/src/main/java/com/tripplanner/auth/domain/EmailVerificationToken.java` | entity | persistence | sibling `User.java` + RESEARCH §V3 | sibling-twin |
| `services/auth-service/src/main/java/com/tripplanner/auth/domain/RefreshToken.java` | entity | persistence | sibling `User.java` + RESEARCH §V4 | sibling-twin |
| `services/auth-service/src/main/java/com/tripplanner/auth/repository/UserRepository.java` | repository | CRUD | (none — first `JpaRepository` in repo) | RESEARCH-driven |
| `services/auth-service/src/main/java/com/tripplanner/auth/repository/EmailVerificationTokenRepository.java` | repository | CRUD | sibling `UserRepository.java` | sibling-twin |
| `services/auth-service/src/main/java/com/tripplanner/auth/repository/RefreshTokenRepository.java` | repository | CRUD + pessimistic-lock | RESEARCH §Pattern 4 lines 477-481 (`@Lock(PESSIMISTIC_WRITE)`) | RESEARCH-driven |
| `services/auth-service/src/main/java/com/tripplanner/auth/email/EmailVerificationSender.java` | service | event-driven (async) | RESEARCH §Pattern 5 (`@Async` `@EventListener`) | RESEARCH-driven (no `JavaMailSender` user yet) |
| `services/auth-service/src/main/java/com/tripplanner/auth/email/VerificationEmailRequestedEvent.java` | event | event-driven | RESEARCH §Pattern 1 lines 376-385 | RESEARCH-driven |
| `services/auth-service/src/main/java/com/tripplanner/auth/scheduling/TokenCleanupJob.java` | scheduler | batch | RESEARCH §Pattern 5 wiring + CONTEXT D-17 | RESEARCH-driven (no `@Scheduled` users yet) |
| `services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java` | config | request-response (filter-chain) | `services/trip-service/.../security/ServletSecurityConfig.java` | EXACT — copy-and-adapt the permitAll list |
| `services/auth-service/src/main/java/com/tripplanner/auth/security/RestAuthenticationEntryPoint.java` | config | request-response | `services/trip-service/.../security/RestAuthenticationEntryPoint.java` | EXACT — verbatim copy with package rename |
| `services/auth-service/src/main/java/com/tripplanner/auth/config/AsyncConfig.java` | config | event-driven | RESEARCH §Pattern 5 lines 567-581 | RESEARCH-driven |
| `services/auth-service/src/main/java/com/tripplanner/auth/config/AuthProperties.java` | config | n/a | `libs/jwt-common/.../JwtProperties.java` | EXACT — `@ConfigurationProperties` idiom |
| `services/auth-service/src/main/resources/application-test.yml` | config | n/a | `services/api-gateway/src/test/resources/application-gateway-it.yml` | role-match |
| `services/auth-service/src/main/resources/db/migration/V2__create_users.sql` | migration | persistence | RESEARCH §V2 migration (lines 765-779) | EXACT — schema spec verbatim |
| `services/auth-service/src/main/resources/db/migration/V3__create_email_verification_tokens.sql` | migration | persistence | RESEARCH §V3 migration (lines 781-797) | EXACT — schema spec verbatim |
| `services/auth-service/src/main/resources/db/migration/V4__create_refresh_tokens.sql` | migration | persistence | RESEARCH §V4 migration (lines 799-819) | EXACT — schema spec verbatim |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java` | service (in lib) | request-response | `libs/jwt-common/.../JwtVerifier.java` | EXACT — sibling-twin (Open Question 3 → libs placement) |
| `services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerIT.java` | test | request-response | `services/trip-service/.../security/DirectServiceAccessWithoutGatewayReturns401IT.java` | role-match (servlet IT pattern) |
| `services/auth-service/src/test/java/com/tripplanner/auth/api/AuthControllerAdviceIT.java` | test | request-response | `services/api-gateway/.../it/GatewayProblemDetailRenderingIT.java` (RFC 7807 contract) | role-match (BL-01 contract) |
| `services/auth-service/src/test/java/com/tripplanner/auth/security/EmailNotVerifiedCannotLoginIT.java` | test (`@Tag("security")`) | request-response | `services/api-gateway/.../it/LoginRateLimiterIT.java` (Testcontainers + `@Tag`) | role-match |
| `services/auth-service/src/test/java/com/tripplanner/auth/security/DeletedRefreshTokenCannotBeUsedIT.java` | test (`@Tag("security")`) | CRUD | sibling above + RESEARCH §IT skeleton | sibling-twin |
| `services/auth-service/src/test/java/com/tripplanner/auth/security/RotatedRefreshTokenCannotBeReusedIT.java` | test (`@Tag("security")`) | CRUD + chain-walk | RESEARCH §Code Examples lines 1076-1150 (verbatim sketch) | RESEARCH-driven |
| `services/auth-service/src/test/java/com/tripplanner/auth/security/LoginRateLimitFailedAttemptsTriggersIT.java` | test (`@Tag("security")`) | event-driven | `services/api-gateway/.../it/LoginRateLimiterIT.java` (Redis Testcontainer + WireMock) | EXACT — same `@Tag("security")`, same `@ServiceConnection` Redis pattern |
| `services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java` | test-support | n/a | `services/api-gateway/.../it/support/GatewayItProperties.java` (constants) + RESEARCH §Validation Architecture | role-match |
| `services/auth-service/src/test/java/com/tripplanner/auth/support/TestSecurityConfig.java` | test-config | n/a | RESEARCH §Pattern 5 + CONTEXT D-19 (BCrypt cost 4) | RESEARCH-driven (first `@TestConfiguration` in repo) |
| `services/auth-service/src/test/java/com/tripplanner/auth/service/RefreshTokenServiceTest.java` | unit-test | n/a | `libs/jwt-common/.../JwtVerifierTest.java` (unit-test idiom) | role-match |
| `services/auth-service/src/test/java/com/tripplanner/auth/service/LoginRateLimiterTest.java` | unit-test | n/a | sibling above | sibling-twin |
| `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtIssuerTest.java` | unit-test | n/a | `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java` | EXACT (round-trip pattern) |

### MODIFIED files (10)

| File | Change | Closest Analog | Match Quality |
|------|--------|----------------|---------------|
| `services/auth-service/build.gradle.kts` | ADD: 5 deps (security, mail, validation, data-redis, jwt-common); 4 testDeps (security-test, testcontainers-postgresql, greenmail, junit-platform-launcher) | `services/trip-service/build.gradle.kts` (Phase-1 added the same security + jwt-common pattern) | EXACT — copy add-dep diff |
| `services/auth-service/src/main/resources/application.yml` | ADD: `spring.mail.*`, `spring.data.redis.*`, `auth.jwt.secret=${AUTH_JWT_SECRET}`, `app.auth.cookie.secure`, `app.frontend.base-url`, `app.mail.from`, `app.verification.link-base`, `spring.task.execution.*` | `services/auth-service/src/main/resources/application.yml` (current Phase 0 base) | self-extension |
| `services/auth-service/src/main/resources/application-docker.yml` | ADD: `spring.mail.host=mailhog`, `spring.data.redis.host=redis` | `services/auth-service/src/main/resources/application-docker.yml` (current) | self-extension |
| `services/auth-service/src/main/java/com/tripplanner/auth/AuthServiceApplication.java` | ADD: `@EnableAsync`, `@EnableScheduling` annotations | current file (1-line annotation diff per RESEARCH §Recommended Project Structure) | self-extension |
| `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` | ADD: 8 enum entries (`AUTH_EMAIL_ALREADY_REGISTERED`, `AUTH_INVALID_CREDENTIALS`, `AUTH_EMAIL_NOT_VERIFIED`, `AUTH_TOKEN_INVALID`, `AUTH_REFRESH_INVALID`, `AUTH_WEAK_PASSWORD`, `AUTH_INVALID_EMAIL`, `VALIDATION_FAILED`) | current `ErrorCode.java` (5 existing entries) | self-extension (RESEARCH lines 1010-1025) |
| `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java` | ADD: `@Bean @ConditionalOnMissingBean JwtIssuer jwtIssuer(JwtProperties)` (RESEARCH lines 873-883) | current file lines 26-29 (analog `JwtVerifier` bean) | self-extension |
| `gradle/libs.versions.toml` | ADD: `spring-boot-starter-mail`, `spring-boot-starter-validation`, `spring-boot-starter-data-redis` aliases; `testcontainers-postgresql`, `greenmail-junit5` aliases; `[versions] greenmail = "2.x"` | current `libs.versions.toml` lines 84-93 (existing alias style) | self-extension |
| `infra/docker-compose.yml` | EXTEND: `auth-service.depends_on` adds `mailhog: started` and `redis: service_healthy`; `environment:` adds `MAIL_FROM`, `FRONTEND_BASE_URL`, `AUTH_COOKIE_SECURE`, `AUTH_JWT_SECRET` | current `auth-service` block (lines 108-128) | self-extension |
| `.env.example` | ADD: `MAIL_FROM`, `FRONTEND_BASE_URL`, `AUTH_COOKIE_SECURE` keys (`AUTH_JWT_SECRET` already present) | current `.env.example` | self-extension |
| `services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayMissingAuthHeaderIT.java` (+ 3 sibling ITs `GatewayForgedJwtIT`, `GatewayProblemDetailRenderingIT`, `LoginRateLimiterIT`) | BL-01 fix (RESEARCH §BL-01 lines 1153-1164): IT files **already** assert `$.code` at root — production code in `ProblemDetailAuthEntryPoint.java` and `RateLimitProblemDetailFilter.java` ALREADY constructor-injects `ObjectMapper`. The remaining work is verification + adding negative `$.properties.code.doesNotExist()` assertions. | current IT files (`$.code` already in place lines 95, 102, 109, 128, 139) | self-extension (READ FIRST before diffing) |

### NO-ANALOG files

| File | Role | Why no analog |
|------|------|---------------|
| `services/auth-service/src/main/java/com/tripplanner/auth/service/LoginRateLimiter.java` | Redis Lua-script client | No prior Redis-client usage in any servlet service. Gateway uses `RedisRateLimiter` (Spring Cloud Gateway abstraction) — different API. RESEARCH §Pattern 6 is the canonical reference. |
| `services/auth-service/src/main/java/com/tripplanner/auth/email/EmailVerificationSender.java` | `@Async @EventListener` | First `JavaMailSender` consumer + first `@Async` event listener. RESEARCH §Pattern 5 is canonical. |
| `services/auth-service/src/main/java/com/tripplanner/auth/scheduling/TokenCleanupJob.java` | `@Scheduled` cleanup | First `@Scheduled` job in repo. CONTEXT D-17 + RESEARCH §Pattern 5 are canonical. |

---

## Pattern Assignments

### `services/auth-service/src/main/java/com/tripplanner/auth/security/SecurityConfig.java` (config, request-response)

**Analog:** `services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java`

> EXACT match. Phase 1 already shipped this exact filter-chain twice (trip-service, destination-service). Phase 2 is the **third** instance — copy lines 19-54 verbatim, change package + permitAll list.

**Imports pattern** (lines 19-30):
```java
package com.tripplanner.auth.security;   // CHANGE: was com.tripplanner.trip.security

import com.tripplanner.jwt.servlet.ServletJwtCommonFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
```

**Core pattern — filter chain composition** (lines 32-54):
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   FilterRegistrationBean<ServletJwtCommonFilter> jwtFilterReg,
                                                   RestAuthenticationEntryPoint entryPoint) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(
                                "/__health", "/__health/**",
                                "/actuator/health", "/actuator/health/**",
                                "/actuator/info",
                                // Phase 2 ADDITIONS — public auth endpoints (CONTEXT D-12)
                                "/api/auth/signup", "/api/auth/verify",
                                "/api/auth/login", "/api/auth/refresh"
                        ).permitAll()
                        // /api/auth/logout (and /me if shipped — Open Q1 RESOLVED: SKIP) → authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
```

**Bean dependencies** (also copy from trip-service):
- `PasswordEncoder bcryptPasswordEncoder()` returning `new BCryptPasswordEncoder(12)` — D-19; production cost 12. (Live in same `SecurityConfig` per RESEARCH lines 1051-1053.)

**Why this analog:** Same servlet stack, same `ServletJwtCommonFilter` from `libs/jwt-common`, same `STATELESS` session policy, same `addFilterBefore(...)` ordering. The **only** Phase 2 deltas are (a) the four `/api/auth/*` permitAll entries and (b) the `BCryptPasswordEncoder` bean. The `UsernamePasswordAuthenticationFilter` placeholder ordering (line 51) is required by `@AuthenticationPrincipal UserContext` resolution downstream.

---

### `services/auth-service/src/main/java/com/tripplanner/auth/security/RestAuthenticationEntryPoint.java` (config, request-response)

**Analog:** `services/trip-service/src/main/java/com/tripplanner/trip/security/RestAuthenticationEntryPoint.java`

> EXACT match. Verbatim copy with package rename. Already mirrored on destination-service. The Phase 2 third instance.

**Full file pattern** (lines 13-51):
```java
package com.tripplanner.auth.security;   // CHANGE: was com.tripplanner.trip.security

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Spring Boot's auto-configured ObjectMapper has ProblemDetailJacksonMixin registered,
    // which flattens ProblemDetail extension properties (code, etc.) to JSON root level.
    // Using new ObjectMapper() would nest under "properties" — wrong for $.code assertions.
    private final ObjectMapper mapper;

    public RestAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetail pd = ProblemDetailFactory.of(
                HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, "Authentication required");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
```

**Why this analog:** Constructor-injects auto-configured `ObjectMapper` (BL-01 lesson burned in). Identical RFC 7807 envelope shape. Lifts directly into auth-service.

---

### `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtIssuer.java` (service in lib, request-response)

**Analog:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtVerifier.java`

> EXACT sibling-twin pair. Same lib, same package, same `JwtProperties` consumer, same `Keys.hmacShaKeyFor(...)` key construction, same fail-at-startup error contract. CONTEXT discretion item (Open Q3 in RESEARCH) → place in `libs/jwt-common`.

**Imports + key construction pattern** (mirror `JwtVerifier.java` lines 8-38):
```java
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
            throw new IllegalStateException(
                "AUTH_JWT_SECRET must be set; got null. See .env.example for the dev placeholder.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        try {
            this.signingKey = Keys.hmacShaKeyFor(bytes);
        } catch (WeakKeyException ex) {
            throw new IllegalStateException(
                "AUTH_JWT_SECRET must be at least 32 bytes (256 bits) for HS256; got " + bytes.length, ex);
        }
    }
```

> The constructor body is **verbatim** from `JwtVerifier.java` lines 26-38. Copy-and-keep — don't redesign.

**Core issue pattern** (RESEARCH lines 857-869, modern jjwt 0.13.0 builder):
```java
    public String issueAccess(String userId, String email, boolean emailVerified) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(ISSUER)                                  // iss
            .subject(userId)                                 // sub  (jjwt 0.13.0 modern API — Convention C27-P1)
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

**Auto-config wiring extension** — modify `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java`:

After line 29 (the existing `jwtVerifier` bean), add:
```java
    @Bean
    @ConditionalOnMissingBean(JwtIssuer.class)
    public JwtIssuer jwtIssuer(JwtProperties props) {
        return new JwtIssuer(props.getSecret());
    }
```

> The `@ConditionalOnMissingBean` is recommended both because RESEARCH lines 873-885 mandate it AND because Phase 1's open IN-01 review item flagged the same defect on `JwtVerifier`. Apply to BOTH beans in this same plan.

**Round-trip contract:** A token from `JwtIssuer.issueAccess(...)` MUST verify cleanly through `JwtVerifier.verify(...)`. The `JwtIssuerTest` (Wave 0) asserts this round-trip via `JwtFixtures.TEST_SECRET` (`libs/jwt-common/src/testFixtures/java/com/tripplanner/jwt/JwtFixtures.java:21`).

---

### `services/auth-service/src/main/java/com/tripplanner/auth/config/AuthProperties.java` (config)

**Analog:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtProperties.java`

> EXACT match. `@ConfigurationProperties` idiom — getter/setter or record-style.

**Pattern adapted from `JwtProperties.java` lines 8-20:**

> NOTE: prefix is `"app"` (NOT `"app.auth"`). The four binding keys live under `app.auth.cookie.*`, `app.frontend.base-url`, `app.mail.from`, `app.verification.link-base` — only the cookie keys are nested under `app.auth`. Using prefix `"app"` with nested wrapper classes (`Auth` containing `Cookie`, plus sibling `Frontend`/`Mail`/`Verification`) is the only shape that binds all four keys correctly.

```java
package com.tripplanner.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public class AuthProperties {

    private final Auth auth = new Auth();
    private final Frontend frontend = new Frontend();
    private final Mail mail = new Mail();
    private final Verification verification = new Verification();

    public Auth getAuth() { return auth; }
    public Frontend getFrontend() { return frontend; }
    public Mail getMail() { return mail; }
    public Verification getVerification() { return verification; }

    public static class Auth {
        private final Cookie cookie = new Cookie();
        public Cookie getCookie() { return cookie; }
        public static class Cookie { private boolean secure; /* getters/setters */ }
    }
    public static class Frontend { private String baseUrl; /* getters/setters */ }
    public static class Mail { private String from; /* getters/setters */ }
    public static class Verification { private String linkBase; /* getters/setters */ }
}
```

**Wiring** — register via `@EnableConfigurationProperties(AuthProperties.class)` on `AuthServiceApplication` (mirrors `JwtAutoConfiguration.java:23` pattern).

**YAML keys** (in `application.yml`):
- `app.auth.cookie.secure: false` (CONTEXT D-12; profile-toggle)
- `app.frontend.base-url: ${FRONTEND_BASE_URL:http://localhost:5173}` (CONTEXT D-02)
- `app.mail.from: ${MAIL_FROM:no-reply@tripplanner.local}` (CONTEXT D-03)
- `app.verification.link-base: ${VERIFICATION_LINK_BASE:http://localhost:8080/api/auth/verify?token=}` (UI-SPEC Email Copy Contract)

---

### `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthController.java` (controller, request-response)

**Analog:** RESEARCH §Pattern 2/3 + `services/api-gateway/src/main/java/com/tripplanner/gateway/health/GatewayHealthController.java` (existing thin route handler — limited shape match because gateway is reactive WebFlux; auth-service is servlet)

> No prior servlet REST controller in the repo. The closest is the placeholder `HealthPlaceholderController.java` (servlet, returns `Map<String,Object>`) but that's too thin. RESEARCH §Pattern 2 (verify endpoint) and §Pattern 3 (login + cookie) are the canonical references.

**Verify endpoint pattern** (RESEARCH lines 405-411):
```java
@GetMapping("/api/auth/verify")
public ResponseEntity<Void> verify(@RequestParam("token") String token) {
    String status = verificationService.consume(token);  // returns "success" | "invalid" | "expired"
    URI redirect = URI.create(authProps.getFrontend().getBaseUrl() + "/verify?status=" + status);
    return ResponseEntity.status(HttpStatus.FOUND).location(redirect).build();
}
```

> **Spelling lock per UI-SPEC §Redirect Query-Param Contract:** lowercase, three values exactly: `success`, `invalid`, `expired`. No `ok`, `bad`, `timeout`. The string is emitted as a literal in `EmailVerificationService.consume(...)`.

**Login endpoint with cookie pattern** (RESEARCH lines 459-466 — `ResponseCookie`):
```java
@PostMapping("/api/auth/login")
public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                           HttpServletRequest http) {
    String ip = resolveClientIp(http);                            // X-Forwarded-For first, fallback getRemoteAddr (D-05)
    LoginResult r = authService.login(req, ip);

    ResponseCookie cookie = ResponseCookie.from("refresh_token", r.rawTokenForCookie())
        .httpOnly(true)
        .secure(authProps.getCookie().isSecure())                  // false in dev/test (D-12)
        .sameSite("Strict")
        .path("/api/auth")                                         // narrow scope: /refresh + /logout (D-12)
        .maxAge(Duration.ofDays(7))
        .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, cookie.toString())
        .body(new LoginResponse(r.accessToken(), 900, r.user()));
}
```

**Bean Validation pattern** (`@Valid @RequestBody`) — RESEARCH lines 887-901 + CONTEXT D-18. The advice handler picks up `MethodArgumentNotValidException`.

**Why this is RESEARCH-driven:** No precedent servlet controller exists. The gateway's `GatewayHealthController` is reactive (`WebFlux`) — wrong stack. Stick to the RESEARCH excerpts above + Spring Boot 3.5 reference docs cited in RESEARCH §Sources.

---

### `services/auth-service/src/main/java/com/tripplanner/auth/api/AuthControllerAdvice.java` (exception-mapper, request-response)

**Analog:** RESEARCH §Code Examples lines 903-1008 (verbatim) + `libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java`

> No `@ControllerAdvice` exists in the repo yet. RESEARCH ships the canonical example.

**Imports + class signature** (RESEARCH lines 909-930):
```java
package com.tripplanner.auth.api;

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
    // ObjectMapper auto-injected via Spring's RequestMappingHandlerAdapter; NEVER `new ObjectMapper()`
    // (Pitfall 7 / BL-01 — verified by AuthControllerAdviceIT asserting $.code at root)
```

**Bean Validation discrimination** (RESEARCH lines 938-959 — `MethodArgumentNotValidException` → field-name check):
```java
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
            detail = "Password does not meet minimum requirements.";   // UI-SPEC §Server-Driven Copy Contract
        } else if (failedFields.contains("email")) {
            code = ErrorCode.AUTH_INVALID_EMAIL;
            detail = "Invalid email format.";                          // UI-SPEC §Server-Driven Copy Contract
        } else {
            code = ErrorCode.VALIDATION_FAILED;
            detail = "Request validation failed.";
        }
        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.BAD_REQUEST, code, detail);
        return new ResponseEntity<>(pd, headers, HttpStatus.BAD_REQUEST);
    }
```

**Custom exception handlers** — one per code (RESEARCH lines 962-999). UI-SPEC §Server-Driven Copy Contract is the **single source of truth** for `detail` strings:

| Exception class | HTTP | ErrorCode | `detail` (verbatim from UI-SPEC) |
|-----------------|------|-----------|-----------------------------------|
| `EmailAlreadyRegisteredException` | (NOT EMITTED on `/signup`) | `AUTH_EMAIL_ALREADY_REGISTERED` | n/a — Open Q5 resolved Option A; D-23 returns opaque 201 |
| `InvalidCredentialsException` | 400 | `AUTH_INVALID_CREDENTIALS` | `Email or password is incorrect.` |
| `EmailNotVerifiedException` | 403 | `AUTH_EMAIL_NOT_VERIFIED` | `Please verify your email before logging in.` |
| `TokenInvalidException` | 400 | `AUTH_TOKEN_INVALID` | `This verification link is invalid.` |
| `TokenExpiredException` | 400 | `AUTH_TOKEN_EXPIRED` | `This verification link has expired.` |
| `RefreshInvalidException` | 401 | `AUTH_REFRESH_INVALID` | `Session expired. Please log in again.` |
| `LoginRateLimitedException` | 429 | `AUTH_RATE_LIMITED` | `Too many attempts. Please try again later.` |

**Helper pattern** (RESEARCH lines 1001-1006):
```java
    private ResponseEntity<ProblemDetail> body(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetailFactory.of(status, code, detail);
        return ResponseEntity.status(status)
            .header(HttpHeaders.CONTENT_TYPE, "application/problem+json")
            .body(pd);
    }
}
```

> Every `body(...)` call routes through `ProblemDetailFactory.of(...)` (defined in `libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java:17-22`) — **never** construct `ProblemDetail.forStatusAndDetail(...)` directly here, the factory sets the stable `type` URI and `code` extension property.

---

### `services/auth-service/src/main/java/com/tripplanner/auth/service/AuthService.java` (service, CRUD)

**Analog:** RESEARCH §Pattern 1 (signup, lines 350-388) + §Pattern 3 (login, lines 428-453)

> Greenfield service layer — no precedent. Follow RESEARCH excerpts.

**Class shape** (RESEARCH §Pattern 1):
```java
package com.tripplanner.auth.service;

import com.tripplanner.auth.api.dto.SignupRequest;
import com.tripplanner.auth.api.dto.LoginRequest;
import com.tripplanner.auth.domain.User;
import com.tripplanner.auth.email.VerificationEmailRequestedEvent;
import com.tripplanner.auth.repository.UserRepository;
import com.tripplanner.auth.repository.EmailVerificationTokenRepository;
import com.tripplanner.auth.service.exception.*;
import com.tripplanner.jwt.JwtIssuer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {
    // Dependencies (constructor-inject all):
    //   UserRepository userRepo
    //   EmailVerificationTokenRepository verifTokenRepo
    //   EmailVerificationService verificationService
    //   RefreshTokenService refreshTokenService
    //   LoginRateLimiter rateLimiter
    //   PasswordEncoder passwordEncoder
    //   JwtIssuer jwtIssuer
    //   ApplicationEventPublisher events
```

**Signup with re-signup-of-unverified branch** (D-23, RESEARCH lines 357-388):
```java
    @Transactional  // D-21 (REQUIRED isolation default)
    public SignupResult signup(SignupRequest req) {
        String emailLower = req.email().toLowerCase().trim();      // D-09 normalize ONCE
        Optional<User> existing = userRepo.findByEmail(emailLower);
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.isEmailVerified()) {
                // Open Q5 / D-23 — return opaque 201 even for verified-existing (Option A)
                return new SignupResult(u.getId(), false /* no email sent */);
            }
            verifTokenRepo.markAllUnconsumedAsConsumedFor(u.getId());   // invalidate prior tokens
            String tok = verificationService.mintFor(u);
            events.publishEvent(new VerificationEmailRequestedEvent(u.getEmail(), u.getId(), tok));
            return new SignupResult(u.getId(), true);
        }
        // True new account
        String hash = passwordEncoder.encode(req.password());      // bcrypt 12 (D-19)
        User u = new User(UUID.randomUUID(), emailLower, hash, false);
        userRepo.save(u);
        String tok = verificationService.mintFor(u);
        events.publishEvent(new VerificationEmailRequestedEvent(u.getEmail(), u.getId(), tok));
        return new SignupResult(u.getId(), true);
    }
```

**Login with rate-limit-before-bcrypt** (D-05, RESEARCH lines 430-453):
```java
    @Transactional
    public LoginResult login(LoginRequest req, String clientIp) {
        String emailLower = req.email().toLowerCase().trim();
        if (rateLimiter.exceeded(clientIp, emailLower)) {            // BEFORE bcrypt verify (D-05)
            throw new LoginRateLimitedException();
        }
        User u = userRepo.findByEmail(emailLower)
            .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            rateLimiter.recordFailure(clientIp, emailLower);         // D-07: failed-only
            throw new InvalidCredentialsException();
        }
        if (!u.isEmailVerified()) {
            // Verified-check AFTER bcrypt — same timing for verified vs unverified (timing-attack defense)
            throw new EmailNotVerifiedException();
        }
        rateLimiter.clear(clientIp, emailLower);                     // D-07: success clears
        String access = jwtIssuer.issueAccess(u.getId().toString(), u.getEmail(), true);
        RefreshTokenIssued rt = refreshTokenService.create(u.getId());
        return new LoginResult(access, 900, rt.rawValue(), u);
    }
```

**Logout pattern** (D-11):
```java
    @Transactional
    public void logout(String rawCookieValue) {
        if (rawCookieValue == null || rawCookieValue.isBlank()) {
            return;   // idempotent — bearer JWT valid but no cookie → 204 (D-11)
        }
        refreshTokenService.revokeChainHead(rawCookieValue);
    }
```

**Why ApplicationEventPublisher (not direct call):** Pitfall 1 — `@Async this.send()` bypasses the AOP proxy. The event publisher forces a fresh proxy hop. The listener bean `EmailVerificationSender` is a separate Spring bean, guaranteeing the proxy.

---

### `services/auth-service/src/main/java/com/tripplanner/auth/service/RefreshTokenService.java` (service, CRUD + pessimistic-lock)

**Analog:** RESEARCH §Pattern 4 (lines 469-535)

> Greenfield. RESEARCH §Pattern 4 is the only canonical reference. Cited from official Spring Data JPA + Hibernate sources.

**Key concerns** (apply ALL):
1. **`@Transactional(isolation = Isolation.REPEATABLE_READ)`** — required because `@Lock` only works inside a transaction (Pitfall 2; D-13).
2. **`@Lock(LockModeType.PESSIMISTIC_WRITE)`** on `findByTokenHashForUpdate(...)` — emits `SELECT FOR UPDATE`.
3. **`revokeChain(...)` walks BOTH directions** — first walk back to root via `findByRotatedTo(...)` reverse-lookup, then walk forward via `findById(rotated_to)` (Pitfall 4; D-10).
4. **SHA-256-store the token** — column `token_hash CHAR(64)`. Cookie holds raw 64-hex; DB never does.
5. **`Instant`, never `LocalDateTime`** — Pitfall 9.

**Repository pattern** (RESEARCH lines 477-481):
```java
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    Optional<RefreshToken> findByRotatedTo(String tokenHash);   // for chain-revocation walk
}
```

**Rotate pattern** (RESEARCH lines 485-513):
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)            // D-13
public RotatedTokens rotate(String rawCookieValue) {
    String hash = sha256Hex(rawCookieValue);
    RefreshToken current = refreshRepo.findByTokenHashForUpdate(hash)
        .orElseThrow(RefreshInvalidException::new);

    if (current.getRevokedAt() != null) throw new RefreshInvalidException();
    if (current.getExpiresAt().isBefore(Instant.now())) throw new RefreshInvalidException();
    if (current.getRotatedTo() != null) {
        revokeChain(current);                                    // D-10
        log.warn("Refresh-token replay detected userId={} chainHead={}",
            current.getUserId(), current.getTokenHash().substring(0, 8));
        throw new RefreshInvalidException();
    }
    // Successful rotation: mint new, link old → new
    RefreshToken next = mintFor(current.getUserId(), current.getCreatedAt().plus(Duration.ofDays(7)));
    current.setRotatedTo(next.getTokenHash());
    return new RotatedTokens(jwtIssuer.issueAccess(...), next.rawValue());
}
```

**`revokeChain` BOTH-DIRECTIONS pattern** (RESEARCH lines 515-534):
```java
private void revokeChain(RefreshToken pivot) {
    // 1) Walk BACKWARD to root via reverse-lookup
    RefreshToken cursor = pivot;
    while (true) {
        Optional<RefreshToken> prior = refreshRepo.findByRotatedTo(cursor.getTokenHash());
        if (prior.isEmpty()) break;
        cursor = prior.get();
    }
    RefreshToken root = cursor;

    // 2) Walk FORWARD from root, revoking each row
    cursor = root;
    Instant now = Instant.now();
    while (cursor != null) {
        cursor.setRevokedAt(now);
        cursor = cursor.getRotatedTo() == null
            ? null
            : refreshRepo.findById(cursor.getRotatedTo()).orElse(null);
    }
}
```

> The `RotatedRefreshTokenCannotBeReusedIT` (security IT #6) MUST verify both head AND root are revoked after replay, per RESEARCH lines 1144-1148. This is the regression gate.

---

### `services/auth-service/src/main/java/com/tripplanner/auth/service/LoginRateLimiter.java` (service, event-driven)

**Analog:** RESEARCH §Pattern 6 (lines 615-650). NO repo precedent for `StringRedisTemplate` use in servlet services.

**Lua-script-as-constant pattern** (RESEARCH lines 618-622) — single atomic INCR+EXPIRE:
```java
package com.tripplanner.auth.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoginRateLimiter {

    // KEYS[1]=full key; ARGV[1]=ttl seconds; ARGV[2]=limit (unused — checker is read-side)
    private static final String LUA_INCR_WITH_TTL = """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return count
        """;

    private final StringRedisTemplate redis;
    private final RedisScript<Long> script = RedisScript.of(LUA_INCR_WITH_TTL, Long.class);

    public LoginRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public boolean exceeded(String ip, String emailLower) {
        // Read-only: GET + compare; do NOT increment on read (D-05/D-07)
        String v = redis.opsForValue().get(key(ip, emailLower));
        return v != null && Long.parseLong(v) >= 5;
    }

    public void recordFailure(String ip, String emailLower) {
        redis.execute(script, List.of(key(ip, emailLower)),
            "900",   // 15-min TTL (D-06 / docs/04 §7)
            "5");
    }

    public void clear(String ip, String emailLower) {
        redis.delete(key(ip, emailLower));   // D-07: success clears
    }

    private String key(String ip, String emailLower) {
        return "rl:login:fail:" + ip + ":" + emailLower;   // D-09 lower-only; D-06 prefix `rl:` matches gateway
    }
}
```

> **Why Lua, not separate INCR + EXPIRE:** the non-atomic form has a documented race (client INCRs, then crashes before EXPIRE → immortal counter). RESEARCH §Don't Hand-Roll lines 673-674 + §Anti-Patterns are the citations.

> **Why `>= 5` not `> 5`:** D-06 wording — "5 failed-attempts / 15min". The 6th attempt trips the threshold. After `recordFailure` the counter is N; the NEXT attempt's `exceeded` checks the CURRENT stored value before its own INCR. So values 1, 2, 3, 4, 5 are "ok"; on attempt 6, `exceeded` reads 5 and trips → 429.

> **Wave 0 unit-test (`LoginRateLimiterTest`):** assert that the Lua script's atomicity guarantee holds — set up an embedded Redis (or Testcontainers) and verify EXPIRE always fires after the first INCR, even under concurrent load.

---

### `services/auth-service/src/main/java/com/tripplanner/auth/email/EmailVerificationSender.java` (service, event-driven async)

**Analog:** RESEARCH §Pattern 5 (lines 583-602)

> Greenfield. No `JavaMailSender` consumer exists. The pattern is the **canonical** Spring `@Async @EventListener` combo with `TaskDecorator` MDC propagation.

**Sender pattern** (RESEARCH lines 583-602):
```java
package com.tripplanner.auth.email;

import com.tripplanner.auth.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationSender.class);

    private final JavaMailSender mailSender;
    private final AuthProperties authProps;

    public EmailVerificationSender(JavaMailSender mailSender, AuthProperties authProps) {
        this.mailSender = mailSender;
        this.authProps = authProps;
    }

    @Async("authAsyncExecutor")           // pin to the named executor (AsyncConfig)
    @EventListener
    public void on(VerificationEmailRequestedEvent ev) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("Trip Planner <" + authProps.getMail().getFrom() + ">");   // D-03 display name
        msg.setTo(ev.email());
        msg.setSubject("Verify your Trip Planner account");                    // UI-SPEC LOCKED
        msg.setText(buildBody(ev.token()));                                    // plain text per D-03
        try {
            mailSender.send(msg);
            log.info("Verification email sent userId={}", ev.userId());       // traceId via MDC
        } catch (MailException ex) {
            log.warn("Mail send failed userId={} class={}", ev.userId(), ex.getClass().getSimpleName());
            // D-04: NEVER log body or token; no retry; recovery via re-signup (D-23)
        }
    }

    private String buildBody(String token) {
        // UI-SPEC §Email Copy Contract — body verbatim
        return "Welcome to Trip Planner!\n\n"
             + "Click the link below to verify your email and activate your account:\n\n"
             + authProps.getVerification().getLinkBase() + token + "\n\n"
             + "This link expires in 24 hours.\n\n"
             + "If you didn't create an account, you can safely ignore this email.\n\n"
             + "— The Trip Planner team";
    }
}
```

> **Body lock per UI-SPEC §Email Copy Contract:** the body is verbatim. The em-dash is U+2014 `—`. `\n` separators only — no `\r\n` Windows endings. UTF-8 encoded.

---

### `services/auth-service/src/main/java/com/tripplanner/auth/config/AsyncConfig.java` (config, event-driven)

**Analog:** RESEARCH §Pattern 5 lines 567-581 + `libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java` (the MDC-propagation reference; the `@Async` analog of MdcEnrichmentFilter's request-scoped propagation)

**Config pattern** (RESEARCH lines 567-581):
```java
package com.tripplanner.auth.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean(name = "authAsyncExecutor")
    public ThreadPoolTaskExecutor authAsyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);                                  // D-01: core 2
        ex.setMaxPoolSize(4);                                   // D-01: max 4
        ex.setQueueCapacity(50);                                // D-01: queue 50
        ex.setThreadNamePrefix("auth-async-");
        ex.setTaskDecorator(new MdcCopyingTaskDecorator());     // D-22 MDC propagation
        ex.initialize();
        return ex;
    }

    static class MdcCopyingTaskDecorator implements TaskDecorator {
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
}
```

> **Why inline the TaskDecorator (not extract to libs/observability):** Phase 2 deliberately keeps it local. RESEARCH §Component Responsibilities flags `MdcCopyingTaskDecorator` as a libs candidate "if Phase 3+ also needs `@Async`." For Phase 2, the inline static class avoids cross-lib churn. Phase 10 polish can extract.

---

### `services/auth-service/src/main/java/com/tripplanner/auth/scheduling/TokenCleanupJob.java` (scheduler, batch)

**Analog:** RESEARCH §Component Responsibilities + CONTEXT D-17. Greenfield.

**Pattern:**
```java
package com.tripplanner.auth.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);
    private final EntityManager em;

    @Scheduled(cron = "0 0 2 * * *")    // D-17: daily 02:00 UTC
    public void cleanup() {
        // CONTEXT Claude's Discretion: TWO transactions for cleaner logs (Open Q4 RESOLVED)
        int evDeleted = cleanupEmailTokens();
        int rtDeleted = cleanupRefreshTokens();
        log.info("TokenCleanupJob: deleted {} email_verification_tokens, {} refresh_tokens (>7d past expiry)",
            evDeleted, rtDeleted);
    }

    @Transactional
    int cleanupEmailTokens() {
        return em.createNativeQuery(
            "DELETE FROM auth.email_verification_tokens WHERE expires_at < NOW() - INTERVAL '7 days'")
            .executeUpdate();
    }

    @Transactional
    int cleanupRefreshTokens() {
        return em.createNativeQuery(
            "DELETE FROM auth.refresh_tokens WHERE expires_at < NOW() - INTERVAL '7 days'")
            .executeUpdate();
    }
}
```

> Enable via `@EnableScheduling` on `AuthServiceApplication` (or fold the annotation onto the main class — single-line equivalent per RESEARCH §Component Responsibilities).

---

### `services/auth-service/src/main/resources/db/migration/V2__create_users.sql` (migration, persistence)

**Analog:** RESEARCH §V2 migration (lines 765-779) — verbatim from `docs/03-data-model.md §3.1`.

```sql
-- File: services/auth-service/src/main/resources/db/migration/V2__create_users.sql
CREATE TABLE auth.users (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(254) NOT NULL UNIQUE,
    password_hash   VARCHAR(72)  NOT NULL,                  -- bcrypt hash fits in 60-72
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

> `gen_random_uuid()` already enabled via `infra/postgres/init.sql` (pgcrypto extension is built-in to PG 16). Schema `auth` already created by Phase 0 init.sql. The `auth_svc` Postgres user has `USAGE, CREATE` on this schema (`infra/postgres/init.sql:31`).

---

### `services/auth-service/src/main/resources/db/migration/V3__create_email_verification_tokens.sql`

**Analog:** RESEARCH §V3 migration (lines 781-797) — verbatim from `docs/03-data-model.md §3.2`.

```sql
CREATE TABLE auth.email_verification_tokens (
    token        CHAR(64)    PRIMARY KEY,                  -- hex of 32 random bytes
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ NOT NULL,
    consumed_at  TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX evt_unconsumed_idx
    ON auth.email_verification_tokens (user_id)
    WHERE consumed_at IS NULL;
```

---

### `services/auth-service/src/main/resources/db/migration/V4__create_refresh_tokens.sql`

**Analog:** RESEARCH §V4 migration (lines 799-819) — verbatim from `docs/05-auth-security.md §2`.

```sql
CREATE TABLE auth.refresh_tokens (
    token_hash   CHAR(64)    PRIMARY KEY,                  -- SHA-256 hex of raw token
    user_id      UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ NOT NULL,
    rotated_to   CHAR(64)    NULL,                         -- successor's token_hash
    revoked_at   TIMESTAMPTZ NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX rt_rotated_to_idx
    ON auth.refresh_tokens (rotated_to)
    WHERE rotated_to IS NOT NULL;
CREATE INDEX rt_expires_at_idx
    ON auth.refresh_tokens (expires_at);
```

> The `rt_rotated_to_idx` enables the reverse-lookup walk in `RefreshTokenService.revokeChain(...)` (Pitfall 4 / D-10).

---

### `services/auth-service/build.gradle.kts` (MODIFIED)

**Analog:** `services/trip-service/build.gradle.kts` (Phase 1 added Spring Security + jwt-common in the same shape).

**Diff to apply** (after line 28 `implementation(libs.bundles.observability)`):
```kotlin
    // Phase 2 ADDS (existing 8 deps stay):
    implementation(project(":libs:jwt-common"))                   // JwtVerifier + JwtIssuer + JwtFixtures
    implementation(libs.spring.boot.starter.security)             // SecurityFilterChain, BCryptPasswordEncoder
    implementation(libs.spring.boot.starter.mail)                 // JavaMailSender                      [NEW alias]
    implementation(libs.spring.boot.starter.validation)           // @Email/@NotBlank/@Size              [NEW alias]
    implementation(libs.spring.boot.starter.data.redis)           // StringRedisTemplate                 [NEW alias — servlet]

    testImplementation(libs.spring.security.test)
    testImplementation(testFixtures(project(":libs:jwt-common"))) // JwtFixtures
    testImplementation(libs.testcontainers.postgresql)            // [NEW alias]
    testImplementation(libs.testcontainers.junit.jupiter)         // already in catalog (Phase 1)
    testImplementation(libs.greenmail.junit5)                     // [NEW alias + version pin]
    testRuntimeOnly("org.junit.platform:junit-platform-launcher") // Convention from Phase 1 01-02 SUMMARY
```

> **Source pattern (verbatim):** `services/trip-service/build.gradle.kts:21-37`. Identical accessor style. The `dependencyManagement { imports { mavenBom(...) } }` block is already present and unchanged.

---

### `services/auth-service/src/main/resources/application.yml` (MODIFIED)

**Analog:** existing `services/auth-service/src/main/resources/application.yml` (Phase 0 base) — extends in place.

**Pattern — add Phase 2 blocks under the existing `spring:` tree (after line 38 `properties.hibernate.default_schema: auth`):**
```yaml
  mail:                                     # Phase 2 NEW (D-03)
    host: ${SMTP_HOST:localhost}
    port: ${SMTP_PORT:1025}
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false
  data:                                     # Phase 2 NEW (D-06 — servlet variant)
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  task:                                     # Phase 2 NEW (D-22 backup pool config; the dedicated authAsyncExecutor wins)
    execution:
      thread-name-prefix: spring-async-
auth:                                       # Phase 2 NEW — JwtProperties binding (lib auto-config consumes)
  jwt:
    secret: ${AUTH_JWT_SECRET}              # D-16 single env var; lib's @ConfigurationProperties("auth.jwt")
app:                                        # Phase 2 NEW — AuthProperties binding
  auth:
    cookie:
      secure: ${AUTH_COOKIE_SECURE:false}   # D-12 profile-toggle
  frontend:
    base-url: ${FRONTEND_BASE_URL:http://localhost:5173}     # D-02; UI-SPEC redirect target
  mail:
    from: ${MAIL_FROM:no-reply@tripplanner.local}            # D-03 / UI-SPEC
  verification:
    link-base: ${VERIFICATION_LINK_BASE:http://localhost:8080/api/auth/verify?token=}   # UI-SPEC §Email Copy
```

> Existing `spring.application.name`, `spring.profiles`, `spring.datasource`, `spring.flyway`, `spring.jpa`, `server.port`, `management.*`, `eureka.*` blocks STAY UNCHANGED.

---

### `services/auth-service/src/main/resources/application-docker.yml` (MODIFIED)

**Analog:** existing file — extends in place.

**Pattern — add after line 15 `currentSchema=auth`:**
```yaml
  mail:
    host: mailhog                  # compose-network DNS (D-03)
  data:
    redis:
      host: redis                  # compose-network DNS (D-06)
```

---

### `gradle/libs.versions.toml` (MODIFIED)

**Analog:** existing `libs.versions.toml` — extends in-place at lines 84+ (Phase 1 already added `spring-boot-starter-security`, `spring-boot-starter-data-redis-reactive`, `spring-security-test`, `testcontainers-junit-jupiter`, `h2` here).

**Diff to apply (under `[libraries]` section — Phase 2 additions):**
```toml
# --- Spring Boot starters added in Phase 2 (versions managed by SB BOM) ---
spring-boot-starter-mail = { module = "org.springframework.boot:spring-boot-starter-mail" }
spring-boot-starter-validation = { module = "org.springframework.boot:spring-boot-starter-validation" }
spring-boot-starter-data-redis = { module = "org.springframework.boot:spring-boot-starter-data-redis" }   # servlet variant

# --- Test infrastructure added in Phase 2 ---
testcontainers-postgresql = { module = "org.testcontainers:postgresql" }
greenmail-junit5 = { module = "com.icegreen:greenmail-junit5", version.ref = "greenmail" }
```

**Add to `[versions]` block:**
```toml
greenmail = "2.x"   # NOT Spring-managed; verify latest 2.x via `gradle dependencyInsight --dependency com.icegreen:greenmail-junit5` per RESEARCH A1
```

> **Convention C16:** every Phase 2 build.gradle.kts MUST reference these via accessors (`libs.spring.boot.starter.mail`, etc.). Do NOT inline versions in service builds.

---

### `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` (MODIFIED)

**Analog:** existing `ErrorCode.java` — extends in-place (5 → 13 entries).

**Diff to apply** (after line 14 `BAD_GATEWAY("gateway.bad_gateway");`):
```java
    AUTH_EMAIL_ALREADY_REGISTERED("auth.email_already_registered"),   // 409 — catalog-only, NOT user-emitted (Open Q5 → Option A)
    AUTH_INVALID_CREDENTIALS("auth.invalid_credentials"),             // 400 — generic per docs/05 §9.1
    AUTH_EMAIL_NOT_VERIFIED("auth.email_not_verified"),               // 403
    AUTH_TOKEN_INVALID("auth.token_invalid"),                         // 400 — verify token unknown/consumed
    AUTH_REFRESH_INVALID("auth.refresh_invalid"),                     // 401 — refresh token bad/revoked/replayed
    AUTH_WEAK_PASSWORD("auth.weak_password"),                         // 400 — Bean Validation @Size on password
    AUTH_INVALID_EMAIL("auth.invalid_email"),                         // 400 — Bean Validation @Email
    VALIDATION_FAILED("validation.failed");                           // 400 — generic Bean Validation fallback
```

> Already-present codes (`AUTH_UNAUTHORIZED`, `AUTH_RATE_LIMITED`, `AUTH_INVALID_TOKEN`, `AUTH_TOKEN_EXPIRED`, `BAD_GATEWAY`) STAY UNCHANGED — the existing constructor + `code()` accessor handle these new entries by the same idiom.

---

### `services/auth-service/src/test/java/com/tripplanner/auth/security/LoginRateLimitFailedAttemptsTriggersIT.java` (test, `@Tag("security")`)

**Analog:** `services/api-gateway/src/test/java/com/tripplanner/gateway/it/LoginRateLimiterIT.java` — EXACT match for the Testcontainers Redis + `@Tag("security")` skeleton.

**Imports + class signature pattern** (LoginRateLimiterIT.java lines 22-78):
```java
package com.tripplanner.auth.security;

import com.tripplanner.auth.support.AuthIntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Tag("security")                         // mandatory — gates merge per CONTEXT D-15 / NFR-05
class LoginRateLimitFailedAttemptsTriggersIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("tripplanner");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
```

> **Why @ServiceConnection** (not `@DynamicPropertySource`): Spring Boot 3.5 auto-wires the connection. Same idiom Phase 1 already uses (verified: `LoginRateLimiterIT.java:80-84`).

**Test pattern — 6th attempt trips** (CONTEXT D-15, RESEARCH §Phase Requirements):
```java
    @Test
    void sixth_failed_login_from_same_ip_email_within_15min_returns_429() throws Exception {
        // Setup: signup + verify a real user (so credentials check passes the "user exists" branch)
        signupAndVerify("rl@example.com", "correctpassword");

        // Five failed attempts → 400 auth.invalid_credentials each
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"rl@example.com","password":"WRONG"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
        }

        // 6th attempt — rate-limit trips BEFORE bcrypt
        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"rl@example.com","password":"WRONG"}"""))
            .andExpect(status().isEqualTo(429))
            .andExpect(content().contentType("application/problem+json"))
            .andExpect(jsonPath("$.code").value("auth.rate_limited"))           // BL-01 contract
            .andExpect(jsonPath("$.properties.code").doesNotExist());           // BL-01 negative

        // Sanity: a different IP still works (key isolation — D-09)
        // ... (additional assertions)
    }
```

---

### `services/auth-service/src/test/java/com/tripplanner/auth/security/RotatedRefreshTokenCannotBeReusedIT.java` (test, `@Tag("security")`)

**Analog:** RESEARCH §Code Examples lines 1076-1150 (verbatim sketch) — copy and flesh out.

> Apply RESEARCH lines 1100-1149 directly. Key assertion (lines 1144-1148):
```java
// DB-level assertion — both rows have revoked_at != null after replay
String hashA = sha256Hex(cookieA);
String hashB = sha256Hex(cookieB);
assertThat(refreshRepo.findById(hashA).orElseThrow().getRevokedAt()).isNotNull();
assertThat(refreshRepo.findById(hashB).orElseThrow().getRevokedAt()).isNotNull();
```

This is **the** regression gate for `revokeChain` walking BOTH directions (Pitfall 4 / D-10).

---

### Other Security ITs

**`DeletedRefreshTokenCannotBeUsedIT`** — sibling of `RotatedRefreshTokenCannotBeReusedIT`. Pattern: signup → verify → login → logout → /refresh → 401 `auth.refresh_invalid`. (CONTEXT D-15 #5.)

**`EmailNotVerifiedCannotLoginIT`** — sibling pattern. Pattern: signup (no verify) → login → 403 `auth.email_not_verified`. (CONTEXT D-15 #7.)

Both reuse `AuthIntegrationTestBase` (Wave 0 file) for `@SpringBootTest` + Testcontainers PG + Redis + GreenMail wiring.

---

### `services/auth-service/src/test/java/com/tripplanner/auth/support/AuthIntegrationTestBase.java` (test-support)

**Analog:** `services/api-gateway/src/test/java/com/tripplanner/gateway/it/support/GatewayItProperties.java` (constants holder pattern) + `LoginRateLimiterIT.java:80-95` (Testcontainers + @ServiceConnection + WireMock setup).

**Pattern — abstract base class for all auth ITs** (RESEARCH §Validation Architecture + Wave 0 list):
```java
package com.tripplanner.auth.support;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(TestSecurityConfig.class)              // BCryptPasswordEncoder(4) override (D-19)
public abstract class AuthIntegrationTestBase {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("tripplanner");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
        .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication());
}
```

**Why use Postgres Testcontainer (not H2):** RESEARCH §State of the Art lines 1176 — H2 lacks `SELECT FOR UPDATE` semantics needed by `RefreshTokenService.rotate()` (Pitfall 2). H2 stays only for Phase 1's gateway-side ITs.

---

### `services/auth-service/src/test/java/com/tripplanner/auth/support/TestSecurityConfig.java`

**Analog:** RESEARCH §Pattern + CONTEXT D-19 (BCrypt cost 4 in tests). No precedent.

```java
package com.tripplanner.auth.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary                                    // overrides production cost-12 bean
    public PasswordEncoder testPasswordEncoder() {
        return new BCryptPasswordEncoder(4);    // D-19 — ~5ms per hash, vs ~250ms at cost 12
    }
}
```

---

### `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtIssuerTest.java`

**Analog:** `libs/jwt-common/src/test/java/com/tripplanner/jwt/JwtVerifierTest.java` — EXACT sibling-twin (round-trip pattern).

**Pattern — round-trip through `JwtFixtures.TEST_SECRET`:**
```java
package com.tripplanner.jwt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerTest {

    private final JwtIssuer issuer = new JwtIssuer(JwtFixtures.TEST_SECRET);
    private final JwtVerifier verifier = new JwtVerifier(JwtFixtures.TEST_SECRET);

    @Test
    void issued_token_round_trips_through_verifier() throws Exception {
        String tok = issuer.issueAccess("user-uuid", "u@e.com", true);

        UserContext ctx = verifier.verify(tok);

        assertThat(ctx.userId()).isEqualTo("user-uuid");
        assertThat(ctx.email()).isEqualTo("u@e.com");
        assertThat(ctx.verified()).isTrue();
    }

    @Test
    void issuer_throws_on_short_secret() {
        assertThatThrownBy(() -> new JwtIssuer("short"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void issuer_throws_on_null_secret() {
        assertThatThrownBy(() -> new JwtIssuer(null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be set");
    }
}
```

> Mirrors `JwtVerifierTest.java` test shape exactly. Both classes live in the same package, share `JwtFixtures.TEST_SECRET`, and assert the same defensive contract (null + short-secret rejection).

---

## Shared Patterns

### Authentication / Filter Chain

**Source:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java` (lines 22-46) + `services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java` (lines 32-54)

**Apply to:** `SecurityConfig.java`

**Filter ordering** (`JwtAutoConfiguration.java:43`):
```java
bean.setOrder(Integer.MIN_VALUE + 200);   // After MdcEnrichmentFilter @ +100, before any controller filter
```

**Filter-chain composition** (`ServletSecurityConfig.java:40-52`):
```java
return http
    .csrf(AbstractHttpConfigurer::disable)
    .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .authorizeHttpRequests(a -> a.requestMatchers(...).permitAll().anyRequest().authenticated())
    .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
    .addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)
    .build();
```

> The `MdcEnrichmentFilter @ Integer.MIN_VALUE + 100` (`libs/observability/.../MdcEnrichmentFilter.java`) is auto-registered. The `ServletJwtCommonFilter @ Integer.MIN_VALUE + 200` is auto-registered by `JwtAutoConfiguration`. Phase 2's SecurityConfig only adds `addFilterBefore(jwtFilterReg.getFilter(), UsernamePasswordAuthenticationFilter.class)` — explicit ordering relative to Spring Security's own chain.

### Error Handling — RFC 7807 ProblemDetail

**Source:** `libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java` (lines 11-23) + `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` (enum)

**Apply to:** All controllers, all `@ExceptionHandler` methods, both async + sync paths.

**Production rule** (verbatim from `ProblemDetailFactory.java:17-22`):
```java
public static ProblemDetail of(HttpStatus status, ErrorCode code, String detail) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
    pd.setType(URI.create(TYPE_BASE + code.code()));
    pd.setProperty("code", code.code());
    return pd;
}
```

**ObjectMapper rule** (verbatim from `ServletJwtCommonFilter.java:47-52`):
> "Accept Spring Boot's auto-configured ObjectMapper so ProblemDetailJacksonMixin is registered. The mixin flattens ProblemDetail extension properties (code, etc.) to the JSON root level, enabling `$.code` assertions in tests. Using `new ObjectMapper()` nests them under `properties`."

**Lint:** RESEARCH Pitfall 7 says `git grep "new ObjectMapper" services/auth-service/src/main/` MUST return ZERO matches.

### Validation — Bean Validation field-level discrimination

**Source:** RESEARCH §Code Examples lines 938-959

**Apply to:** `AuthControllerAdvice.java` only.

> The `@Override protected ResponseEntity<Object> handleMethodArgumentNotValid(...)` is the canonical Spring Web entry point. Field-name discrimination (`failedFields.contains("password")`) maps to `auth.weak_password`; `contains("email")` maps to `auth.invalid_email`; otherwise `validation.failed`. **No field-name leak in the `detail` string** — UI-SPEC §Server-Driven Copy Contract is the source of truth.

### MDC propagation — sync (filter) + async (TaskDecorator)

**Source:** `libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java` (sync; lines 21-49) + RESEARCH §Pattern 5 lines 550-565 (async TaskDecorator)

**Apply to:** All controllers (auto), `EmailVerificationSender.on(...)` (via `AsyncConfig.MdcCopyingTaskDecorator`).

**Sync pattern** (`MdcEnrichmentFilter.java:30-47`):
```java
var span = tracer.currentSpan();
if (span != null) {
    MDC.put("traceId", span.context().traceId());
    MDC.put("spanId", span.context().spanId());
}
// userId populated by ServletJwtCommonFilter (libs/jwt-common, line 82)
try {
    chain.doFilter(req, resp);
} finally {
    MDC.clear();
}
```

**Async pattern** (RESEARCH lines 550-565):
```java
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
```

> Phase 2 inherits the sync filter via `libs/observability` auto-config. The async TaskDecorator is local to `AsyncConfig.java` (RESEARCH commentary: extract to libs only when Phase 3+ also uses `@Async`).

### Configuration properties

**Source:** `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtProperties.java` (lines 1-20) + `libs/jwt-common/src/main/java/com/tripplanner/jwt/JwtAutoConfiguration.java` (`@EnableConfigurationProperties` line 23)

**Apply to:** `AuthProperties.java`

**Idiom:**

> Prefix MUST be `"app"`, not `"app.auth"`. Binding keys span four sibling subtrees: `app.auth.cookie.*`, `app.frontend.base-url`, `app.mail.from`, `app.verification.link-base`. The `Auth` wrapper nests `Cookie`; `Frontend`/`Mail`/`Verification` are siblings.

```java
@ConfigurationProperties("app")
public class AuthProperties {
    private final Auth auth = new Auth();
    private final Frontend frontend = new Frontend();
    private final Mail mail = new Mail();
    private final Verification verification = new Verification();
    // getters …
    public static class Auth {
        private final Cookie cookie = new Cookie();
        public Cookie getCookie() { return cookie; }
        public static class Cookie { private boolean secure; /* getters/setters */ }
    }
    public static class Frontend { private String baseUrl; /* getters/setters */ }
    public static class Mail { private String from; /* getters/setters */ }
    public static class Verification { private String linkBase; /* getters/setters */ }
}
```

Wired via `@EnableConfigurationProperties(AuthProperties.class)` on `AuthServiceApplication.java` (the same annotation pattern `JwtAutoConfiguration:23` uses for `JwtProperties`).

### Per-service Flyway history

**Source:** `services/auth-service/src/main/resources/application.yml:32` — already configured: `table: auth_flyway_schema_history`

**Apply to:** All Phase 2 V2/V3/V4 migrations land into this table — no config changes needed.

### Testing — `@Tag("security")` integration tests

**Source:** `services/api-gateway/src/test/java/com/tripplanner/gateway/it/LoginRateLimiterIT.java` line 78 (`@Tag("integration")` is the gateway's; auth-service Phase 2 uses `@Tag("security")` per CONTEXT D-15)

**Apply to:** `EmailNotVerifiedCannotLoginIT`, `DeletedRefreshTokenCannotBeUsedIT`, `RotatedRefreshTokenCannotBeReusedIT`, `LoginRateLimitFailedAttemptsTriggersIT`.

**Idiom:** `@Tag("security")` at class level. `useJUnitPlatform { includeTags("security") }` in `build.gradle.kts` test block (added Phase 2). The tag is the explicit merge-gate for NFR-05.

---

## No-Analog Files (planner uses RESEARCH directly)

The 3 files where no in-repo analog exists. Planner cites RESEARCH excerpts in plan actions:

| File | Reference |
|------|-----------|
| `services/auth-service/.../service/LoginRateLimiter.java` | RESEARCH §Pattern 6 lines 615-650 |
| `services/auth-service/.../email/EmailVerificationSender.java` | RESEARCH §Pattern 5 lines 583-602 |
| `services/auth-service/.../scheduling/TokenCleanupJob.java` | CONTEXT D-17 + RESEARCH §Component Responsibilities table |

---

## Critical Pre-Plan Verification

**The planner MUST verify these BEFORE writing diff tasks** (RESEARCH §BL-01 fix lines 1153-1164):

1. **Read `services/api-gateway/src/main/java/com/tripplanner/gateway/security/ProblemDetailAuthEntryPoint.java` lines 31-33** — confirm `ObjectMapper` is constructor-injected (not `new ObjectMapper()`). VERIFIED in this PATTERNS pass: lines 29-33 inject `ObjectMapper`. ✅
2. **Read `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java` lines 48-52** — confirm same. VERIFIED: lines 48-52 inject `ObjectMapper`. ✅
3. **Read the 4 IT files** (`GatewayMissingAuthHeaderIT`, `GatewayForgedJwtIT`, `GatewayProblemDetailRenderingIT`, `LoginRateLimiterIT`) — confirm assertions are at `$.code` (not `$.properties.code`).
   - `GatewayMissingAuthHeaderIT.java:95` → `.jsonPath("$.code").isEqualTo("auth.unauthorized")` ✅
   - `GatewayProblemDetailRenderingIT.java:90, 109, 128` → `.jsonPath("$.code")` ✅
   - `GatewayForgedJwtIT.java:102` → `.jsonPath("$.code").isEqualTo("auth.invalid_token")` ✅
   - `LoginRateLimiterIT.java:139` → `.jsonPath("$.code").isEqualTo("auth.rate_limited")` ✅

> **Verdict:** BL-01 production fix + IT assertions are ALREADY IN PLACE. The Phase 2 BL-01 task should be **defensive only** — add `.jsonPath("$.properties.code").doesNotExist()` negative-assertions to the 4 IT files (RESEARCH lines 1158-1162) to lock the contract from regressing. The plan should NOT diff `ProblemDetailAuthEntryPoint.java` or `RateLimitProblemDetailFilter.java` — they are already fixed.

---

## Metadata

**Analog search scope:**
- `services/auth-service/` (Phase 0 base — 3 files)
- `services/api-gateway/` (Phase 0/1 — 9 main + 8 test files)
- `services/trip-service/` (Phase 0/1 — 4 main + 1 test files)
- `services/destination-service/` (Phase 0/1 — 4 main + 1 test files)
- `libs/jwt-common/` (Phase 1 — 7 main + 4 test/fixture files)
- `libs/error-handling/` (Phase 0 — 2 files)
- `libs/api-contracts/` (Phase 1 — 1 main + 1 test file)
- `libs/observability/` (Phase 0/1 — 3 files)
- `infra/postgres/init.sql`, `infra/docker-compose.yml`, `.env.example`, `gradle/libs.versions.toml`

**Files scanned:** 47 source files + 4 yaml/toml configs + 5 planning docs (CONTEXT, RESEARCH, UI-SPEC, VALIDATION, REQUIREMENTS, ROADMAP, CLAUDE.md)

**Pattern extraction date:** 2026-05-09

**Frontend/React analog scan:** not performed — Phase 2 ships zero React components per UI-SPEC frontmatter (`phase_kind: backend`).
