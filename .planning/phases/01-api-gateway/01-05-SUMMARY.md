---
phase: 01-api-gateway
plan: 05
subsystem: api-gateway
tags: [integration-tests, security, tracing, rate-limiting, cors, jwt, wiremock, testcontainers]
dependency-graph:
  requires: [01-01, 01-02, 01-03, 01-04]
  provides: [SC#1, SC#2, SC#3, SC#5-ip-leg, SC#6, T-01-02, T-01-04, T-01-10, D-07-contract]
  affects: [services/api-gateway/src/test]
tech-stack:
  added: []
  patterns:
    - WireMock-Spring-Boot 3.9.0 @EnableWireMock / @InjectWireMock for stub injection
    - Testcontainers GenericContainer redis:7-alpine with @ServiceConnection for rate-limit isolation
    - Logback ListAppender<ILoggingEvent> attached to SCG logger hierarchy for MDC assertion
    - GatewayTracingObservationConfig via ContextRefreshedEvent to fix circular-dependency handler registration
    - GatewayTracingTestConfig explicit ContextPropagators bean to fix NoopTextMapPropagator in test context
    - ServerHttpResponseDecorator overriding both writeWith() and setComplete() for 429 body injection
key-files:
  created:
    - services/api-gateway/src/main/java/com/tripplanner/gateway/observability/GatewayTracingObservationConfig.java
    - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayCorsIT.java
    - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayProblemDetailRenderingIT.java
    - services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayTraceContinuityIT.java
    - services/api-gateway/src/test/java/com/tripplanner/gateway/it/LoginRateLimiterIT.java
    - services/api-gateway/src/test/java/com/tripplanner/gateway/it/support/GatewayTracingTestConfig.java
  modified:
    - services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java
    - services/api-gateway/src/test/resources/application-gateway-it.yml
    - services/api-gateway/src/test/resources/application-gateway-it-ratelimit.yml
decisions:
  - "GatewayTracingObservationConfig uses ContextRefreshedEvent (not SmartInitializingSingleton) to register GatewayPropagatingSenderTracingObservationHandler with ObservationRegistry after all beans are initialized, avoiding circular OTel dependency"
  - "GatewayTracingTestConfig provides explicit ContextPropagators(W3CTraceContextPropagator) in test context to prevent otelContextPropagators(ObjectProvider) from building with empty list and producing NoopTextMapPropagator"
  - "LoginRateLimiterIT uses FLUSHALL in @BeforeEach to reset Redis rate-limiter bucket state between tests; without this the second test finds an empty bucket and fails on its first request"
  - "RateLimitProblemDetailFilter overrides both writeWith() and setComplete() because RequestRateLimiterGatewayFilterFactory uses the empty-body setComplete() path for 429 rejection"
  - "application-gateway-it.yml adds spring.reactor.context-propagation=auto to activate Hooks.enableAutomaticContextPropagation() via ReactorAutoConfiguration, enabling Slf4JEventListener to propagate MDC traceId/spanId across reactive thread switches"
metrics:
  duration: "~4 hours (spread across two context windows)"
  completed: "2026-05-09"
  tasks-completed: 3
  files-changed: 9
---

# Phase 01 Plan 05: Gateway Integration Test Suite Summary

**One-liner:** 8 Spring Cloud Gateway ITs covering all Phase 1 success criteria (SC#1–#6 including rate limiter, trace continuity, CORS, and JWT auth) with Micrometer OTel W3C propagation fix and rate-limiter 429 body injection fix.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 5.1 | Shared IT infrastructure | 951c415 | GatewayItProperties.java, test YAMLs |
| 5.2 | SC#1 routing / SC#2 missing-auth / SC#3 forged-JWT / T-01-04 injection ITs | a0e2d0e | GatewayRoutingIT, GatewayMissingAuthHeaderIT, GatewayForgedJwtIT, XUserIdInjectionIT |
| 5.2 fix | Gateway security correctness fixes | fec7137 | Production security filter fixes |
| 5.3 | SC#3 CORS / SC#5 rate-limit / SC#6 trace continuity / D-07 problem-detail ITs | 774b78c | GatewayCorsIT, GatewayProblemDetailRenderingIT, GatewayTraceContinuityIT, LoginRateLimiterIT, GatewayTracingObservationConfig, GatewayTracingTestConfig |

## Test Results

All 27 tests pass across 8 IT classes:

| IT Class | Tests | Success Criteria |
|----------|-------|-----------------|
| GatewayRoutingIT | 3 | SC#1 — path predicates route to correct downstream service |
| GatewayMissingAuthHeaderIT | 4 | SC#2 — missing JWT returns 401; downstream gets 0 requests |
| GatewayForgedJwtIT | 4 | SC#3 + T-01-02 — wrong-sig/expired/malformed/alg=none all return 401 |
| XUserIdInjectionIT | 6 | T-01-04 — XUserIdInjectionGlobalFilter strips spoofed header; injects JWT sub |
| GatewayCorsIT | 3 | T-01-10 — CORS preflight from localhost:5173 allowed; evil.example.com denied |
| GatewayProblemDetailRenderingIT | 3 | D-07 — every gateway error is application/problem+json with code extension |
| GatewayTraceContinuityIT | 1 | SC#6 — MDC traceId matches W3C traceparent sent downstream |
| LoginRateLimiterIT | 3 | SC#5 IP-leg — 31st login returns 429 problem+json; separate buckets |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] GatewayTracingObservationConfig: trace handler not registered with ObservationRegistry**
- **Found during:** Task 5.3 (GatewayTraceContinuityIT authoring)
- **Issue:** `GatewayPropagatingSenderTracingObservationHandler` is created by `GatewayMetricsAutoConfiguration.ObservabilityConfiguration.GatewayTracingConfiguration` as a Spring bean but NOT registered with `ObservationRegistry` due to a circular bean dependency: the handler depends on `Tracer` → `ObservationRegistry`, which is initialized before `ObservationRegistryConfigurer.configure()` can collect the handler via `observationHandlers.orderedStream()`. Result: `ObservedRequestHttpHeadersFilter` starts observations but the W3C `traceparent` header is never injected into outgoing Reactor Netty requests.
- **Fix:** Created `GatewayTracingObservationConfig` which implements `ApplicationListener<ContextRefreshedEvent>` and explicitly calls `observationRegistry.observationConfig().observationHandler(gatewayTracingHandler)` after all beans are fully initialized. A `volatile boolean registered` guard prevents double-registration on child context refreshes.
- **Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/observability/GatewayTracingObservationConfig.java` (new)
- **Commit:** 774b78c

**2. [Rule 1 - Bug] GatewayTracingTestConfig: OtelPropagator built with NoopTextMapPropagator in test context**
- **Found during:** Task 5.3 (GatewayTraceContinuityIT debugging)
- **Issue:** `OpenTelemetryTracingAutoConfiguration.otelContextPropagators(ObjectProvider<TextMapPropagator>)` collects `TextMapPropagator` beans via `ObjectProvider.orderedStream()`. In the test context, the `TextMapPropagator` beans from `PropagationWithBaggage` / `PropagationWithoutBaggage` are not available at instantiation time — the `ObjectProvider` returns an empty stream, `TextMapPropagator.composite([])` returns `NoopTextMapPropagator.getInstance()`, and no `traceparent` header is ever written.
- **Fix:** Created `GatewayTracingTestConfig` `@TestConfiguration` with an explicit `ContextPropagators` bean using `W3CTraceContextPropagator`. Because Spring Boot's `otelContextPropagators` has `@ConditionalOnMissingBean`, this test bean takes precedence and supplies the correct propagator to `OtelPropagator`.
- **Files modified:** `services/api-gateway/src/test/java/com/tripplanner/gateway/it/support/GatewayTracingTestConfig.java` (new), `GatewayTraceContinuityIT.java` (added `@Import`)
- **Commit:** 774b78c

**3. [Rule 1 - Bug] RateLimitProblemDetailFilter: 429 body not written when RequestRateLimiterGatewayFilterFactory uses setComplete()**
- **Found during:** Task 5.3 (LoginRateLimiterIT.thirty_logins_pass_then_thirty_first_returns_429_with_problem_detail)
- **Issue:** `RequestRateLimiterGatewayFilterFactory` calls `response.setComplete()` (empty-body path) when rejecting a rate-limited request. The original `ServerHttpResponseDecorator` only overrode `writeWith()`, which was never invoked. The 31st request received HTTP 429 but with `Content-Type: null` and no body.
- **Fix:** Added `setComplete()` override to the `ServerHttpResponseDecorator` in `RateLimitProblemDetailFilter`. When `getStatusCode()` is 429, both paths now call the shared `writeProblemDetail()` private method that sets `Content-Type: application/problem+json` and writes the RFC 7807 body via `super.writeWith()`.
- **Files modified:** `services/api-gateway/src/main/java/com/tripplanner/gateway/security/RateLimitProblemDetailFilter.java`
- **Commit:** 774b78c

**4. [Rule 2 - Missing] application-gateway-it.yml: missing reactor context propagation for MDC traceId in reactive logs**
- **Found during:** Task 5.3 (GatewayTraceContinuityIT MDC assertion)
- **Issue:** Without `spring.reactor.context-propagation: auto`, `ReactorAutoConfiguration` does not call `Hooks.enableAutomaticContextPropagation()`. `Slf4JEventListener` (OTel's MDC bridge) never receives OTel span context on reactive worker threads, so `traceId`/`spanId` are absent from SCG log events.
- **Fix:** Added `spring.reactor.context-propagation: auto` to `application-gateway-it.yml`. This activates `Hooks.enableAutomaticContextPropagation()` which propagates OTel context via Project Reactor's `ContextView`, enabling `Slf4JEventListener` to populate MDC on every reactive thread switch.
- **Files modified:** `services/api-gateway/src/test/resources/application-gateway-it.yml`
- **Commit:** 774b78c

**5. [Rule 1 - Bug] LoginRateLimiterIT: Redis bucket state leaking between tests**
- **Found during:** Task 5.3 (LoginRateLimiterIT.signup_route_has_separate_bucket_from_login — second test found login bucket already empty)
- **Issue:** The rate-limiter Redis bucket keys (`request_rate_limiter.{key}.tokens` / `.timestamp`) persisted between tests. The `@BeforeEach` only reset WireMock stubs. The second test that exhausted the login bucket found it already empty on its first request.
- **Fix:** Added `redis.execInContainer("redis-cli", "FLUSHALL")` to `@BeforeEach` in `LoginRateLimiterIT`. Also added `throws Exception` to the method signature.
- **Files modified:** `services/api-gateway/src/test/java/com/tripplanner/gateway/it/LoginRateLimiterIT.java`
- **Commit:** 774b78c

**6. [Rule 1 - Bug] GatewayTraceContinuityIT: SCG logger at INFO suppressed DEBUG-level trace events**
- **Found during:** Task 5.3 (GatewayTraceContinuityIT — ListAppender captured zero events)
- **Issue:** SCG filter/handler classes (`NettyRoutingFilter`, `FilteringWebHandler`) emit trace context at DEBUG level. The `logAppender` was attached to `org.springframework.cloud.gateway` but the logger was at its inherited INFO level, so zero events flowed to the appender.
- **Fix:** Added `scgLogger.setLevel(Level.DEBUG)` in `@BeforeEach` and `scgLogger.setLevel(null)` in `@AfterEach` to restore the inherited level.
- **Files modified:** `services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayTraceContinuityIT.java`
- **Commit:** 774b78c

## Known Stubs

None — all ITs use WireMock stubs for downstream services. WireMock stubs are test infrastructure, not production data stubs. All assertions verify real gateway behavior.

## Threat Flags

None — this plan adds test code only (plus `GatewayTracingObservationConfig` which is production code in the observability package). No new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED

Created files verified:
- `services/api-gateway/src/main/java/com/tripplanner/gateway/observability/GatewayTracingObservationConfig.java` — FOUND
- `services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayCorsIT.java` — FOUND
- `services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayProblemDetailRenderingIT.java` — FOUND
- `services/api-gateway/src/test/java/com/tripplanner/gateway/it/GatewayTraceContinuityIT.java` — FOUND
- `services/api-gateway/src/test/java/com/tripplanner/gateway/it/LoginRateLimiterIT.java` — FOUND
- `services/api-gateway/src/test/java/com/tripplanner/gateway/it/support/GatewayTracingTestConfig.java` — FOUND

All commits verified in git log:
- 774b78c — feat(01-05): add gateway integration test suite
- a0e2d0e — feat(01-05): task 5.2 ITs
- fec7137 — fix(01-05): gateway security correctness fixes
- 951c415 — feat(01-05): GatewayItProperties
- 101b2b7 — docs(phase-01): plan 01-05
