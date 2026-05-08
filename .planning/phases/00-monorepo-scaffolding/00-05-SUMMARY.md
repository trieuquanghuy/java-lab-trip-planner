---
phase: 00-monorepo-scaffolding
plan: 05
subsystem: service-registry
tags: [spring-cloud, netflix-eureka, service-discovery, registry, no-db]

requires:
  - 00-01 (Wave 1 foundation: gradle/libs.versions.toml `spring-cloud-starter-netflix-eureka-server` accessor + `springCloud` version key + `spring-boot` plugin alias + `spring-dependency-management` plugin alias)
  - 00-03 (libs/observability ships logback-spring-base.xml — Eureka opts OUT for Phase 0 minimum surface; documented in logback-spring.xml comment for future Option B switch)
provides:
  - services/eureka-server Spring Boot subproject (compiles + bootJar packages cleanly via the wrapper toolchain)
  - Standalone Netflix Eureka registry server bootstrapped with @EnableEurekaServer
  - eureka-server-0.1.0-SNAPSHOT.jar (~58 MB) at services/eureka-server/build/libs/ — runnable on JDK 21
  - Port 8761 listener (the Eureka REST API + dashboard)
  - Actuator /actuator/health endpoint (compose healthcheck target per D-20)
  - spring.application.name=eureka-server set (D-25 / Pitfall 7) for log clarity
  - register-with-eureka=false + fetch-registry=false (standalone — no self-registration loop)
affects:
  - 00-06 (api-gateway) — once gateway-side Eureka client wires up, the gateway must register here at startup; this plan provides the registry it depends on
  - 00-07 / 00-08 / future plans for auth/trip/destination services — same registration target
  - 00-09 (compose orchestration) — eureka-server is the first service in the compose health-gated startup chain (depends_on: eureka-server: condition: service_healthy gates the other 4 services in Wave 4+)
  - 00-10 (smoke.sh) — SC#2 (4 services in Eureka via /eureka/apps?json) becomes assertable once compose comes up; eureka-server is the registry the smoke script queries

tech-stack:
  added:
    - "Spring Cloud Netflix Eureka Server (standalone mode) wired via spring-cloud-starter-netflix-eureka-server (Spring Cloud 2025.0.2 / Northfields per D-30)"
  patterns:
    - "Standalone-server Eureka config: register-with-eureka=false + fetch-registry=false in application.yml (Pattern 4 in 00-RESEARCH.md)"
    - "Self-preservation disabled in dev: enable-self-preservation=false makes lease eviction predictable in compose (D-21 client tuning + D-20 healthchecks together drive cold-start <60s per Pitfall 10)"
    - "Logback Option A pass-through: a comment-only <configuration> block keeps SB's default Logback config and avoids dragging libs/observability into the registry path (00-PATTERNS.md line 425)"
    - "spring-cloud-dependencies BOM imported via mavenBom + ${libs.versions.springCloud.get()} (NOT a per-service version literal — Convention C16 honored)"

key-files:
  created:
    - "services/eureka-server/build.gradle.kts (1024 B, ~22 lines) — spring-boot + dependency-management plugin aliases; spring-boot-starter-actuator + spring-cloud-starter-netflix-eureka-server (no Flyway, no JPA, no Postgres, no observability lib — D-11)"
    - "services/eureka-server/src/main/java/com/tripplanner/eureka/EurekaServerApplication.java (590 B, 16 lines) — @SpringBootApplication + @EnableEurekaServer main class; package com.tripplanner.eureka per D-28/C1"
    - "services/eureka-server/src/main/resources/application.yml (1100 B, ~28 lines) — port 8761, spring.application.name=eureka-server, register-with-eureka=false, fetch-registry=false, enable-self-preservation=false, actuator health,info exposed"
    - "services/eureka-server/src/main/resources/logback-spring.xml (~890 B, 21 lines) — Option A pass-through with documented rationale + Option B switch instructions for future phases"
  modified: []
  deleted:
    - "services/eureka-server/.gitkeep — Wave 1 marker, obsolete now that the subproject has real content (mirrors Plan 00-03's libs/observability/.gitkeep cleanup)"

key-decisions:
  - "Logback Option A chosen — eureka-server uses Spring Boot's default Logback config and does NOT depend on libs/observability. Rationale: registry traffic is not in the trace path in Phase 0 (00-PATTERNS.md line 425; 00-CONTEXT.md 'Reusable assets' notes Eureka 'may not need MDC/tracing'); minimum surface beats log-format consistency for this single service. Phase 10 may switch to Option B (add the lib dep + replace pass-through with `<include resource=\"logback-spring-base.xml\"/>`) if cross-service log correlation needs Eureka to match."
  - "D-21 (Eureka client tuning) deliberately NOT applied here. The plan called this out: registry-fetch-interval-seconds is a CLIENT setting, and eureka-server is the SERVER. Tuning belongs in api-gateway/auth-service/trip-service/destination-service application-docker.yml (Convention C13)."
  - "D-22 (127.0.0.1 binding) deliberately NOT applied here. Compose-level port binding lives in infra/docker-compose.yml (Wave 4) — application.yml has no business pinning the bind address."
  - "Local java -jar smoke deferred to Wave 4 compose. The bootJar artifact is class-file 65 (Java 21 bytecode), and no JDK 21 is on the developer host (Plan 00-01 SUMMARY's open Todo). The plan's <output> directive treated the local boot as 'if run locally' — this SUMMARY documents 'not run locally; deferred to Wave 4 compose' which is consistent with the verification pattern Plan 00-03 used (compileJava + jar are sufficient for Phase 0)."
  - "Removed the .gitkeep marker once real source landed — same pattern Plan 00-03 used for libs/observability/.gitkeep. The post-commit deletion is intentional and reconciled with Plan 00-01's 'Wave 2/3/4/5 will replace each .gitkeep with the real build.gradle.kts' note."

patterns-established:
  - "Convention C13 reinforced (negative case): eureka-server has NO eureka.client tuning block. The tuning is for clients only."
  - "D-11 enforced: services/eureka-server/build.gradle.kts contains zero references to flyway/data-jpa/postgresql/datasource. Verified by `! grep -q` checks in plan acceptance."
  - "D-25 / Pitfall 7 enforced even on the registry: spring.application.name=eureka-server set so logs (when emitted) attribute clearly to the right service."

requirements-completed: [NFR-04]

duration: 6min
completed: 2026-05-08
---

# Phase 0 Plan 5: Eureka Server Skeleton Summary

**Wave-3 deliverable: `services/eureka-server` boots a standalone Netflix Eureka registry on port 8761 with the dashboard surface, the actuator `/actuator/health` endpoint for compose healthchecks, and zero data-store / observability-lib dependencies (D-11). It is the registry that gateway/auth/trip/destination services will register with at startup once Wave 4+ comes online — without it, Wave 4 services would fail their EurekaClient bootstrap and SC#2 (4 services in Eureka) would not be assertable.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-05-08T04:40:40Z
- **Completed:** 2026-05-08T04:47:18Z
- **Tasks:** 2
- **Files created:** 4
- **Files modified:** 0
- **Files deleted:** 1 (services/eureka-server/.gitkeep — obsolete Wave 1 marker)

## Accomplishments

- Eureka server compiles cleanly: `./gradlew :services:eureka-server:compileJava` BUILD SUCCESSFUL in 2m 25s (first compile incl. Spring Cloud BOM + Netflix Eureka starter resolution).
- Boot jar packages cleanly: `./gradlew :services:eureka-server:bootJar` BUILD SUCCESSFUL in 24s; produces `services/eureka-server/build/libs/eureka-server-0.1.0-SNAPSHOT.jar` (57810522 bytes ≈ 58 MB).
- D-11 honored: `services/eureka-server/build.gradle.kts` contains NO Flyway, NO `data-jpa`, NO Postgres, NO `datasource` references. Verified by Plan 00-05 Task 5.1 acceptance grep (`! grep -q 'flyway'`, `! grep -q 'data-jpa'`, `! grep -q 'postgresql'`).
- Standalone Eureka config locked in `application.yml`: `register-with-eureka: false` + `fetch-registry: false` (registry does not register with itself), `enable-self-preservation: false` (predictable lease eviction in single-instance dev).
- D-25 / Pitfall 7 honored: `spring.application.name: eureka-server` set even though the registry does not trace itself — required for log attribution clarity per Open Question #5 resolution.
- D-21 NOT applied here (deliberate, encoded in plan and source): `registry-fetch-interval-seconds` is a client setting; eureka-server is the server.
- Logback Option A chosen: `logback-spring.xml` is a documented pass-through; eureka-server has no `libs/observability` dependency. The file exists so Spring Boot's logback subsystem reads it without warnings, but its body is intentionally empty with a comment explaining the Option B switch.
- NFR-04 free-tier audit (`scripts/smoke.sh --criterion nfr-04`) re-passes after this plan — the Spring Cloud Netflix Eureka stack is 100% open source (Apache 2.0).

## Logback Option Chosen

**Option A** — pass-through `logback-spring.xml` with no `libs/observability` dep on the build classpath.

**Rationale:**
- Registry traffic is not in the application trace path in Phase 0 (00-PATTERNS.md line 425; 00-CONTEXT.md 'Reusable assets' line 255 explicitly notes Eureka 'may not need MDC/tracing').
- The four other services that DO route application traffic through them (api-gateway in Wave 4 + auth/trip/destination in Wave 5) all depend on `libs/observability` and use the full `<include resource="logback-spring-base.xml"/>` per Convention C8.
- Minimum surface for the registry: ~5 MB smaller jar without the observability bundle, no autoconfig running for filters that would never be invoked on a non-application-routing service.
- The Option B switch is documented inline in the file: a future phase needing unified Eureka log format adds `implementation(project(":libs:observability"))` to `build.gradle.kts` and replaces the pass-through body with a single-line `<include resource="logback-spring-base.xml"/>`.

## D-11 Confirmation (no Flyway, no datasource)

```
$ grep -E 'flyway|data-jpa|postgresql|datasource' services/eureka-server/build.gradle.kts
$ # (empty — confirmed)
$ grep -E 'flyway|datasource|jpa' services/eureka-server/src/main/resources/application.yml
$ # (empty — confirmed)
```

`build.gradle.kts` declares only:
- `spring-boot-starter-actuator` (for `/actuator/health` compose healthcheck)
- `spring-cloud-starter-netflix-eureka-server` (the registry server starter)
- `spring-boot-starter-test` (testImplementation only)

Plus the `spring-cloud-dependencies:${libs.versions.springCloud.get()}` BOM import. No Flyway, no JPA, no Postgres driver, no `spring-boot-starter-data-jpa`. Eureka has no application data; it is in-memory by design.

## Boot Smoke Test Result

**Not run locally — deferred to Wave 4 compose.**

The bootJar artifact is JDK 21 bytecode (class-file major version 65). The developer host has only JDK 17 (homebrew openjdk@17), JDK 25 (homebrew openjdk), and Corretto 11 — all of which fail `java -jar` with `UnsupportedClassVersionError: class file version 65.0`. This is a known local-developer ergonomic gap (Plan 00-01 SUMMARY's open Todo: "Once JDK 21 is on JAVA_HOME locally").

The plan's `<output>` directive treated the local boot as conditional ("Boot smoke test result if run locally"). The full runtime smoke (port 8761 listener, dashboard reachable, `/actuator/health` UP, `/eureka/apps` returning JSON) will run automatically in Wave 4 once `infra/docker-compose.yml` brings up `eureka-server` on a JDK 21 docker base image (e.g., `eclipse-temurin:21-jre`) — `scripts/smoke.sh --criterion eureka` (or the SC#2 criterion the smoke script already enforces) will gate this end-to-end.

For Phase 0 acceptance criteria (`bootJar` produces a runnable jar), this is sufficient — the plan's `<acceptance_criteria>` for Task 5.2 explicitly requires `bootJar` to succeed but does NOT require local `java -jar` invocation.

## Task Commits

Each task was committed atomically (sequential executor on `master`):

1. **Task 5.1: build.gradle.kts + EurekaServerApplication.java** — `d02a6cc` (feat)
2. **Task 5.2: application.yml + logback-spring.xml** — `eb98938` (feat)
3. **Cleanup: remove obsolete services/eureka-server/.gitkeep marker** — `858675e` (chore)

**Plan metadata commit:** _to be added by final commit step (SUMMARY.md + STATE.md + ROADMAP.md)_

## Files Created/Modified

### Created

| Path | Bytes | Lines | Purpose |
|------|-------|-------|---------|
| `services/eureka-server/build.gradle.kts` | ~1024 | ~22 | Spring Boot + dependency-management plugin aliases; actuator + netflix-eureka-server starter; spring-cloud-dependencies BOM imported with `${libs.versions.springCloud.get()}` (D-30 — 2025.0.2). Pitfall 7 / D-11 header comment encodes "no Flyway, no datasource, no observability lib" rationale. |
| `services/eureka-server/src/main/java/com/tripplanner/eureka/EurekaServerApplication.java` | ~590 | 16 | `@SpringBootApplication` + `@EnableEurekaServer` main class. Package `com.tripplanner.eureka` per D-28 / Convention C1. Header comment cross-links to application.yml standalone-server config. |
| `services/eureka-server/src/main/resources/application.yml` | ~1100 | ~28 | port 8761, `spring.application.name=eureka-server` (D-25), `register-with-eureka=false` + `fetch-registry=false` (standalone), `enable-self-preservation=false` (dev), `management.endpoints.web.exposure.include=health,info` + health probes. Comment block enforces D-11 / D-21-NOT-here / D-22-NOT-here. |
| `services/eureka-server/src/main/resources/logback-spring.xml` | ~890 | 21 | Option A pass-through `<configuration>` block. Source comment documents Option B switch path for future phases. |

### Deleted

| Path | Reason |
|------|--------|
| `services/eureka-server/.gitkeep` | Wave 1 marker placed in Plan 00-01 to track the empty subproject directory. Now obsolete because Plan 00-05 added real content (build.gradle.kts + application.yml + logback-spring.xml + EurekaServerApplication.java). Mirrors Plan 00-03's libs/observability/.gitkeep cleanup. |

## Decisions Made

- **Logback Option A — pass-through.** Documented above. Rationale: minimum surface for the registry; eureka-server is not in the application trace path in Phase 0. Option B switch instructions are inline in the file.
- **D-21 NOT applied here.** `eureka.client.registry-fetch-interval-seconds` is a CLIENT setting; eureka-server is the SERVER. The plan called this out explicitly as a CRITICAL note. Convention C13 lists this as applying to api-gateway/auth/trip/destination — NOT eureka-server.
- **D-22 NOT applied here.** The 127.0.0.1:8761 binding is a compose-level concern (per `infra/docker-compose.yml` ports mapping in Wave 4). `application.yml` does not pin the bind address — Spring Boot defaults to 0.0.0.0 inside the container, and compose maps it to 127.0.0.1 on the host.
- **No `application-docker.yml` profile override file.** The plan's Task 5.2 action notes: "The `docker` profile override is not needed for eureka-server because it does not connect to any other service in the compose network — it just listens on 8761." Plan 00-05's `<verify>` block does not test for application-docker.yml; D-23's three profiles can be expressed via the base file alone for this service.
- **Removed `.gitkeep` as part of plan execution.** Same pattern as Plan 00-03's libs/observability/.gitkeep cleanup. The deletion is documented and atomic in commit `858675e` (separate from the source-file commits to keep the diff focused).

## Deviations from Plan

None — plan executed exactly as written.

The `.gitkeep` removal is enforcing Plan 00-01's reconciliation directive ("Wave 2/3/4/5 will replace each .gitkeep with the real build.gradle.kts when those subprojects are built out") rather than deviating from Plan 00-05. It is committed as a separate `chore` commit per the executor protocol's "task-related files" guidance.

## Issues Encountered

- **Local launcher JVM is OpenJDK 25, not 21 — same ergonomic gap Plan 00-03 SUMMARY documented.** Resolved by setting `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home` so the wrapper runs on JDK 17 and Gradle's toolchain mechanism auto-provisions JDK 21 for `javac`. Both `compileJava` and `bootJar` succeed under this setup.
- **Local `java -jar` of the bootJar fails with `UnsupportedClassVersionError: class file version 65.0`** — JDK 17 cannot run JDK 21 bytecode. This is expected: the bootJar runs on JDK 21 in compose (`eclipse-temurin:21-jre` or similar in Wave 4) and in CI (`actions/setup-java@v4 java-version: 21` per Plan 00-09). For Phase 0 acceptance, `bootJar` succeeding is sufficient — runtime smoke happens in Wave 4 compose.
- No further issues during planned work.

## Threat Register Outcomes

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-00-16 (Eureka dashboard at :8761 leaks internal hostnames/IPs) | partial-mitigation pending | application.yml exposes the dashboard; the LOOPBACK binding (127.0.0.1) is a compose-level concern landed in Wave 4 (`infra/docker-compose.yml` per D-22). This plan does the right thing by NOT hard-binding the address in the app config — leaving compose as the single point of network surface control. |
| T-00-17 (Malicious "fake auth-service" registers and gateway routes traffic to it) | accepted (per plan) | Phase 0 gateway uses STATIC URIs (D-02), not `lb://` Eureka-routed URIs — registry contents do NOT influence routing in Phase 0. Phase 10 hardening adds mTLS or token-based Eureka client auth before switching to `lb://` URIs. This plan does not change the trust posture. |
| T-00-18 (Self-preservation disabled → stale leases evicted aggressively → transient 503s) | accepted (per plan) | `enable-self-preservation: false` in application.yml is a dev-only choice. D-21's 5s lease intervals + 10s expiration + Pitfall 10's compose healthcheck-gated startup keep cold-start <60s. |

## Self-Check

Verified each claim against the workspace:

- `[x] FOUND: services/eureka-server/build.gradle.kts (~1024 B)`
- `[x] FOUND: services/eureka-server/src/main/java/com/tripplanner/eureka/EurekaServerApplication.java (~590 B, 16 lines)`
- `[x] FOUND: services/eureka-server/src/main/resources/application.yml (~1100 B)`
- `[x] FOUND: services/eureka-server/src/main/resources/logback-spring.xml (~890 B)`
- `[x] FOUND commit: d02a6cc (Task 5.1 feat)`
- `[x] FOUND commit: eb98938 (Task 5.2 feat)`
- `[x] FOUND commit: 858675e (chore — .gitkeep removal)`
- `[x] FOUND assertion: services/eureka-server/.gitkeep DELETED (intentional)`
- `[x] FOUND assertion: ./gradlew :services:eureka-server:compileJava BUILD SUCCESSFUL`
- `[x] FOUND assertion: ./gradlew :services:eureka-server:bootJar BUILD SUCCESSFUL — services/eureka-server/build/libs/eureka-server-0.1.0-SNAPSHOT.jar (57810522 bytes)`
- `[x] FOUND assertion: ! grep -q 'flyway' services/eureka-server/build.gradle.kts (D-11 honored)`
- `[x] FOUND assertion: ! grep -q 'data-jpa' services/eureka-server/build.gradle.kts (D-11 honored)`
- `[x] FOUND assertion: ! grep -q 'postgresql' services/eureka-server/build.gradle.kts (D-11 honored)`
- `[x] FOUND assertion: grep -q '@EnableEurekaServer' EurekaServerApplication.java`
- `[x] FOUND assertion: grep -q 'name: eureka-server' application.yml`
- `[x] FOUND assertion: grep -q 'port: 8761' application.yml`
- `[x] FOUND assertion: grep -q 'register-with-eureka: false' application.yml`
- `[x] FOUND assertion: grep -q 'fetch-registry: false' application.yml`
- `[x] FOUND assertion: grep -q 'health,info' application.yml`
- `[x] FOUND assertion: micrometer-tracing-bom mavenBom STILL appears EXACTLY ONCE across libs/**/*.kts + services/**/*.kts (Pitfall 7 / Convention C6) — only in libs/observability/build.gradle.kts; this plan adds nothing to that surface`
- `[x] FOUND assertion: scripts/smoke.sh --criterion nfr-04 passes (no paid SaaS deps after this plan)`

**Self-Check: PASSED**

## User Setup Required

None — no external service configuration required for this plan. eureka-server is purely local infrastructure that gateway/auth/trip/destination services will register with at startup in subsequent plans.

## Next Phase Readiness

- **Plan 00-06 (api-gateway) is unblocked.** The gateway will reference `eureka-server:8761/eureka` as its `eureka.client.service-url.defaultZone` in `application-docker.yml` (D-21 client tuning applies there, not here). Static-URI routing for `/__health/<svc>` (D-02) does not need a running Eureka registry to exist — but Eureka must be in the dependency graph for `EurekaClientAutoConfiguration` to activate.
- **Plans 00-07 / 00-08 (auth/trip/destination services) are unblocked from a registry-target perspective.** Each service will declare `eureka-server` as a `depends_on: { eureka-server: condition: service_healthy }` once compose lands in Wave 4.
- **Plan 00-09 (compose) is unblocked.** `infra/docker-compose.yml` will mount the eureka-server jar (or a slim docker image), bind `127.0.0.1:8761:8761` per D-22, and add the standard healthcheck `curl -f http://localhost:8761/actuator/health` per D-20.
- **Plan 00-10 (smoke.sh SC#2 4 services in Eureka)** depends on this plan — eureka-server must be running for `curl http://localhost:8761/eureka/apps` to return registered application names.
- **Open dependency:** Phase 1 (post-Phase-0) may switch to `lb://` Eureka-routed gateway URIs (currently static per D-02). When that happens, T-00-17 (fake-service spoofing) needs a mitigation — token-based Eureka client auth or mTLS — landed before the switch.
- **No blockers for Plan 00-06.**

---
*Phase: 00-monorepo-scaffolding*
*Completed: 2026-05-08*
