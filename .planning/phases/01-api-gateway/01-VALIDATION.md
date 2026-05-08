---
phase: 01
slug: api-gateway
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-08
---

# Phase 01 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Source: 01-RESEARCH.md §Validation Architecture (Wave 0 = 14 test files + 1 smoke extension).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5.12.x + Mockito 5 + WireMock-Spring-Boot 3.9.0 (managed by SB 3.5.14 BOM); Spring Boot Test slices (`@WebFluxTest`, `@SpringBootTest(webEnvironment=RANDOM_PORT)`) |
| **Config file** | Each module's `build.gradle.kts` test block (Junit Platform, Jacoco) |
| **Quick run command** | `./gradlew :services:api-gateway:test` (per-task verification on api-gateway changes); `./gradlew :services:trip-service:test` for JwtCommonFilter changes |
| **Full suite command** | `./gradlew check` (runs every subproject's test + Jacoco) |
| **Estimated runtime** | ~120s full suite (cold), ~30s per-service (warm) |

---

## Sampling Rate

- **After every task commit:** Run the affected service's `test` task (e.g. `./gradlew :services:api-gateway:test`)
- **After every plan wave:** Run `./gradlew check` (all subprojects)
- **Before `/gsd-verify-work`:** Full suite must be green; `bash scripts/smoke.sh` must exit 0; manual SC checks done
- **Max feedback latency:** 30 seconds (per-service warm test); 120 seconds (full check)

---

## Per-Task Verification Map

> Populated by gsd-planner during plan generation. Each row maps a task to its automated verify command.
> Format placeholder — planner fills in once 01-XX-PLAN.md files exist.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-XX-YY | XX | N | NFR-02/NFR-06 | T-01-NN | {to be filled by planner} | unit/integration | `./gradlew ...` | ⬜ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

> Per 01-RESEARCH.md §Validation Architecture, Wave 0 must land 14 test files + 1 smoke extension before Wave 1 (filter wiring) starts.

Wave 0 test scaffolding (RED gates — failing tests assert intended behavior before implementation lands):

- [ ] `services/api-gateway/src/test/java/.../JwtAuthenticationFilterTest.java` — gateway JWT validation unit test (NFR-02 / SC#3)
- [ ] `services/api-gateway/src/test/java/.../XUserIdInjectionGlobalFilterTest.java` — Pitfall 1: strip incoming X-User-Id (NFR-06 / SC#4)
- [ ] `services/api-gateway/src/test/java/.../GatewayRoutingIT.java` — `/api/{auth,trips,search}/*` routing (SC#1)
- [ ] `services/api-gateway/src/test/java/.../GatewayMissingAuthHeaderIT.java` — 401 when Authorization absent (SC#2)
- [ ] `services/api-gateway/src/test/java/.../GatewayForgedJwtIT.java` — 401 on forged/expired JWT (SC#3)
- [ ] `services/api-gateway/src/test/java/.../LoginRateLimiterIT.java` — 30/15min IP-only login gate per D-05 (SC#5 base; strict 5/15min IP+email lands in Phase 2)
- [ ] `services/api-gateway/src/test/java/.../GatewayTraceContinuityIT.java` — single traceId end-to-end (SC#6)
- [ ] `services/trip-service/src/test/java/.../ServletJwtCommonFilterTest.java` — servlet filter unit test
- [ ] `services/trip-service/src/test/java/.../DirectServiceAccessWithoutGatewayReturns401IT.java` — Pitfall 1 keystone (SC#4)
- [ ] `services/destination-service/src/test/java/.../ServletJwtCommonFilterTest.java` — mirror of trip's
- [ ] `services/destination-service/src/test/java/.../DirectServiceAccessWithoutGatewayReturns401IT.java` — mirror of trip's
- [ ] `libs/jwt-common/src/test/java/.../JwtVerifierTest.java` (or libs/api-contracts if that's the chosen home) — jjwt 0.13.0 modern API (`Jwts.parser().verifyWith(...).build().parseSignedClaims(...).getPayload()`)
- [ ] `libs/api-contracts/src/test/java/.../UserContextTest.java` — UserContext record + Principal contract
- [ ] `services/api-gateway/src/test/java/.../GatewayProblemDetailRenderingTest.java` — RFC 7807 envelope on 401/403/429 from gateway
- [ ] `scripts/smoke.sh` extension — add `--criterion phase-01-bypass` that asserts `curl localhost:8082/api/trips -H "X-User-Id: xxx"` returns 401 (the runtime-side Pitfall 1 check)

*Wave 0 placement note:* The RESEARCH.md proposed test fixtures live alongside production code in their respective service modules. The `libs/jwt-common` location is conditional — see Open Question 1 in research; if Phase 1 chooses libs/api-contracts as the home (per D-07/C22 forbidding libs/jwt-common), tests move there.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Single trace ID visible in Zipkin UI for one routed request | SC#6 | Zipkin UI is human-eyeballed; automated assertion checks MDC values in service logs only | `docker compose up -d --wait`; `curl localhost:8080/api/trips/anything -H "Authorization: Bearer <valid>"`; open Zipkin UI at `http://localhost:9411/` → search for the trace; confirm one trace ID spans gateway + trip-service |
| Eureka registration confirms gateway, auth, trip, destination still appear after Phase 1 changes | SC carryover from Phase 0 | Browser dashboard | Open `http://localhost:8761/` and confirm 4 services |
| 429 response shape on rate limit breach matches RFC 7807 | SC#5 | Curl + manual JSON inspection (also asserted in `LoginRateLimiterIT`) | `for i in {1..6}; do curl -i -X POST localhost:8080/api/auth/login -d '{"email":"x@y","password":"z"}' -H "Content-Type: application/json"; done` — request #6 should return 429 with `application/problem+json` body |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies (planner fills in per-task table above)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (14 tests + smoke extension)
- [ ] No watch-mode flags (`./gradlew test --continuous` MUST NOT be used in CI)
- [ ] Feedback latency < 30s per-service / 120s full check
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending — set to approved by gsd-plan-checker on Wave 0 sign-off
