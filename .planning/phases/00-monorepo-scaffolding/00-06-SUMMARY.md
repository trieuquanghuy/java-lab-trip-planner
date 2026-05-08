---
phase: 00-monorepo-scaffolding
plan: 06
subsystem: api-gateway
tags: [spring-cloud-gateway, webflux, reactive, eureka-client, static-uri-routing, observability]

requires:
  - 00-01 (Wave 1 foundation: gradle/libs.versions.toml `spring-cloud-starter-gateway` + `spring-cloud-starter-netflix-eureka-client` accessors + `springCloud=2025.0.2` version key + `spring-boot` and `spring-dependency-management` plugin aliases + `bundles.observability`)
  - 00-03 (libs/observability fully wired — auto-config + ReactiveMdcEnrichmentFilter + logback-spring-base.xml; api-gateway depends on it transitively for MDC + JSON encoder + reactive trace bridge)
  - 00-04 (libs/error-handling + libs/api-contracts — wired as project dependencies even though Phase 0 gateway has no real endpoints to throw ProblemDetail from yet; the dep graph is pre-wired so Phase 1 lands without modifying build.gradle.kts)
provides:
  - services/api-gateway Spring Boot reactive subproject (compileJava + bootJar both BUILD SUCCESSFUL)
  - api-gateway-0.1.0-SNAPSHOT.jar (~51 MB) at services/api-gateway/build/libs/ — runnable on JDK 21
  - Spring Cloud Gateway 4.x (from Spring Cloud 2025.0.x train) on port 8080
  - 4 STATIC-URI routes for `/__health/{gateway,auth,trip,destination}` (D-02 / Convention C9 — zero `lb://`)
  - Gateway's own `/__health` controller (`GatewayHealthController`) returning {service: "api-gateway", status: "UP", phase: 0}
  - Eureka client registration to ${EUREKA_URL:http://localhost:8761/eureka} (dev) / http://eureka-server:8761/eureka (docker profile)
  - D-21 client tuning in application-docker.yml (registry-fetch=5s, lease-renewal=5s, lease-expiration=10s) per Pitfall 10 / Convention C13
  - Actuator surface limited to health,info,prometheus (T-00-20 mitigation — env/configprops/beans/mappings NOT exposed)
  - tracing.sampling.probability=1.0 in dev (D-04, Pitfall 7 step-4 prep for Phase 1)
  - Logback config: single-line include of logback-spring-base.xml from libs/observability (Convention C8) — JSON encoder + MDC fields + reactive trace bridge auto-active via libs/observability auto-config
affects:
  - 00-07 (auth-service skeleton) — auth-service exposes /__health on :8081 which the gateway's `health-auth` route forwards to via SetPath=/__health; Phase 1 layers /api/auth/** routes below the existing /__health entries (header comment in application.yml documents the convention)
  - 00-08 (trip-service + destination-service skeletons) — same /__health/<svc> routing pattern; the route table top is already locked
  - 00-09 (compose orchestration) — gateway is the only public-facing service (binds 0.0.0.0:8080 per D-22; downstream services bind 127.0.0.1); compose `depends_on: { eureka-server: condition: service_healthy }` gates gateway startup
  - 00-10 (smoke.sh) — SC#3 (curl localhost:8080/actuator/health → UP) + SC#3-route (curl localhost:8080/__health/auth → forwarded payload) become assertable once Wave 4 compose comes online; the route entries are already in place
  - 01 (api-gateway Phase 1) — Phase 1 appends /api/auth/**, /api/search/**, /api/destinations*/**, /api/trips/** below the existing `/__health/<svc>` entries (no reordering needed — header comment documents this); Phase 1 also adds Spring Security WebFlux config + JwtCommonFilter + Redis rate limiter; libs/jwt-common (deferred per D-07 / C22) is consumed at that point

tech-stack:
  added:
    - "Spring Cloud Gateway (reactive WebFlux variant) wired via spring-cloud-starter-gateway from the Spring Cloud 2025.0.2 (Northfields) train per D-30"
    - "Spring Cloud Netflix Eureka Client wired via spring-cloud-starter-netflix-eureka-client (matches eureka-server registry from Plan 00-05)"
  patterns:
    - "Static-URI gateway routes (D-02 / Convention C9): every downstream URI is `http://service-name:port`, never the load-balanced Eureka scheme. Phase 0 routing path bypasses Eureka discovery deliberately to avoid Pitfall 10 cold-start 503s. Eureka is in the observability path (dashboard) only (D-03)."
    - "Phase 1 append-below convention: header comment in application.yml's `routes:` block locks `/__health/<svc>` entries at the TOP so Phase 1 can simply append `/api/<svc>/**` below without reordering. /__health entries stay forever as ops debug endpoints (00-CONTEXT.md cross-phase handoff line 274)."
    - "SetPath=/__health filter strips the /<svc> suffix before forwarding so each downstream service receives a request for its own /__health (Plan 00-07's auth-service Health controller; same shape for trip and destination)."
    - "Reactive controller uses `Map<String, Object>` return type (not `Mono<Map<...>>`) — Spring Cloud Gateway auto-wires WebFlux Jackson encoder for plain controllers on the gateway's own routes."
    - "spring.application.name=api-gateway set per D-25 / Pitfall 7 (Zipkin attributes spans correctly)."
    - "Optional /__health/gateway route: a 4th entry pointed at http://localhost:8080 with SetPath=/__health, placed FIRST in the route list, so /__health/gateway works through the route table for naming consistency with the other /__health/<svc> entries (smoke.sh can iterate uniformly if SC#3-route ever extends to verify the gateway placeholder via the route table)."

key-files:
  created:
    - "services/api-gateway/build.gradle.kts (~1.4 KB, 35 lines) — spring-boot + dependency-management plugin aliases; spring-cloud-starter-gateway (reactive) + spring-cloud-starter-netflix-eureka-client + spring-boot-starter-actuator + libs/observability + libs/error-handling + libs/api-contracts + bundles.observability. NO spring-boot-starter-web, NO Flyway, NO datasource (D-11). spring-cloud-dependencies BOM imported via `${libs.versions.springCloud.get()}` (Convention C16 / C17 — 2025.0.2)."
    - "services/api-gateway/src/main/java/com/tripplanner/gateway/ApiGatewayApplication.java (~440 B, 14 lines) — @SpringBootApplication main class in package com.tripplanner.gateway per D-28 / Convention C1."
    - "services/api-gateway/src/main/java/com/tripplanner/gateway/health/GatewayHealthController.java (~1.4 KB, 34 lines) — @RestController @GetMapping(\"/__health\") returning {service: \"api-gateway\", status: \"UP\", phase: 0} (D-01 / Convention C10). Header comment documents that /__health/gateway is rewritten by the route table's SetPath=/__health filter rather than mapped here directly."
    - "services/api-gateway/src/main/resources/application.yml (~3.0 KB, 74 lines) — port 8080, spring.application.name=api-gateway (D-25), 4 STATIC-URI routes for /__health/{gateway,auth,trip,destination} each with SetPath=/__health filter, actuator surface limited to health,info,prometheus (T-00-20 mitigation), tracing.sampling.probability=1.0 (D-04), Eureka client registration to ${EUREKA_URL:http://localhost:8761/eureka}. Header comment documents the Phase 1 append-below convention."
    - "services/api-gateway/src/main/resources/application-docker.yml (~750 B, 25 lines) — on-profile=docker; Eureka URL switches to compose DNS eureka-server:8761/eureka; D-21 client tuning (registry-fetch=5s, lease-renewal=5s, lease-expiration=10s) per Pitfall 10 / Convention C13; Zipkin endpoint via compose DNS."
    - "services/api-gateway/src/main/resources/logback-spring.xml (~700 B, 15 lines) — single-line include of logback-spring-base.xml from libs/observability (Convention C8 — gateway is on the trace path so Option B is mandatory, unlike eureka-server's Option A pass-through)."
  modified: []
  deleted:
    - "services/api-gateway/.gitkeep — Wave 1 marker, obsolete now that the subproject has real content (mirrors Plan 00-03's libs/observability/.gitkeep and Plan 00-05's services/eureka-server/.gitkeep cleanup)"

key-decisions:
  - "Optional /__health/gateway route INCLUDED. The plan's <action> section for Task 6.2 explicitly recommended this 4th route entry for naming consistency, placing it FIRST in the route list. Doing so means smoke.sh's `for svc in auth trip destination` loop can be extended to include `gateway` if SC#3-route ever needs to verify the gateway placeholder is reachable through the route table. The route loops back to http://localhost:8080 with SetPath=/__health, which works since the gateway's own GatewayHealthController is mapped at /__health on port 8080."
  - "Reactive controller uses plain Map<String, Object> return type rather than Mono<Map<...>>. Per the plan's <action> note, Spring Cloud Gateway tolerates plain controllers for the gateway's own routes (the Reactive Jackson encoder auto-wires). Mono wrapping would add ceremony with no benefit for a static payload."
  - "JDK toolchain workaround for local compileJava: developer host JAVA_HOME defaults to JDK 25 (homebrew openjdk) which Gradle 8.14.2's bundled Kotlin (Kotlin 2 / KotlinCompilerKt) cannot parse — `IllegalArgumentException: 25.0.2` at JavaVersion.parse during build script compilation. Fix: invoke gradle with `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home`. Gradle's `JavaLanguageVersion.of(21)` toolchain still drives the Java compiler — the JAVA_HOME override only affects the Gradle launcher / Kotlin DSL host JVM. Same workaround documented in Plan 00-01 SUMMARY's open Todo. CI uses `actions/setup-java@v4 java-version: 21` so this is local-only friction."
  - "Local java -jar smoke deferred to Wave 4 compose (same pattern as Plans 00-03 / 00-05). The bootJar artifact is JDK 21 bytecode (class-file major 65); developer host has no JDK 21 installed locally. Plan acceptance was met via `./gradlew :services:api-gateway:bootJar` BUILD SUCCESSFUL; full runtime smoke (port 8080 listener, /actuator/health UP, /__health controller responding, route table forwarding /__health/<svc> downstream, Eureka registration via the compose-network eureka-server) runs in Wave 4 compose with `eclipse-temurin:21-jre`."
  - "All three lib dependencies wired even though Phase 0 gateway has no thrown errors / no UserContext consumption. Per the plan's must_haves#truths, libs/observability + libs/error-handling + libs/api-contracts are on the gateway's classpath now (D-04 mandate; Bucket D pattern) so Phase 1 can land without modifying build.gradle.kts. libs/api-contracts is NO-SOURCE in Phase 0 (empty Gradle module per D-06 / C23) — Gradle reports `Task :libs:api-contracts:compileJava NO-SOURCE` during compile, expected behavior."

patterns-established:
  - "Convention C9 (no lb://) enforced for the api-gateway. Verified: `grep -c 'lb://' services/api-gateway/src/main/resources/*.yml` returns 0 in BOTH application.yml and application-docker.yml."
  - "Convention C13 (D-21 Eureka tuning) applied — gateway is a CLIENT (it registers with eureka-server), so the 5s/5s/10s tuning belongs here. Mirrors what Plans 00-07 / 00-08 will repeat for the auth/trip/destination services."
  - "Convention C8 (libs/observability dependency for trace bridge + JSON logback) — gateway uses the FULL Option B include (`<include resource=\"logback-spring-base.xml\"/>`) since it's on the trace path. Plan 00-05's eureka-server uses Option A (pass-through default config) since registry traffic is not in the trace path."
  - "Reactive trace context bridge (Pitfall 7 form) is registered TRANSITIVELY via libs/observability auto-config — no manual @Bean ServerHttpObservationFilter anywhere (Convention C7). Verified: ObservabilityAutoConfiguration's @ConditionalOnClass on org.springframework.web.server.WebFilter activates the ReactiveMdcEnrichmentFilter on the gateway. Cross-trace assertion (single trace ID across gateway → downstream) deferred to Phase 1 per Pitfall 7 step 4."
  - "Header-comment convention for the gateway route table: a multi-line `# PHASE 0 ROUTE TABLE: /__health/<svc> entries sit at the TOP. Phase 1 will append /api/<svc>/** entries below.` block at the top of `spring.cloud.gateway.routes:`. Phase 1 should preserve and extend this comment."

requirements-completed: [NFR-04]

duration: 5min
completed: 2026-05-08
---

# Phase 0 Plan 6: API Gateway Skeleton Summary

**Wave-3 deliverable: `services/api-gateway` is a buildable Spring Boot 3.5.x reactive Spring Cloud Gateway service that boots on port 8080, registers with Eureka, hosts `/__health` for itself, and forwards `/__health/{auth,trip,destination}` to the matching compose-network DNS hostnames via STATIC URIs (D-02 / Convention C9 — zero `lb://`). It is the only public-facing backend in Phase 0; SC#3 (`curl localhost:8080/actuator/health` → UP) and SC#3-route (`curl localhost:8080/__health/auth` → auth's payload) become end-to-end assertable once Wave 4 compose lands.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-05-08T04:55:01Z
- **Completed:** 2026-05-08T05:00:30Z
- **Tasks:** 2
- **Files created:** 6
- **Files modified:** 0
- **Files deleted:** 1 (services/api-gateway/.gitkeep — obsolete Wave 1 marker)

## Accomplishments

- API gateway compiles cleanly: `./gradlew :services:api-gateway:compileJava` BUILD SUCCESSFUL in 39s (first compile incl. Spring Cloud BOM + Spring Cloud Gateway starter + Eureka client resolution).
- Boot jar packages cleanly: `./gradlew :services:api-gateway:bootJar` BUILD SUCCESSFUL in 15s; produces `services/api-gateway/build/libs/api-gateway-0.1.0-SNAPSHOT.jar` (~51 MB — larger than eureka-server because of WebFlux + Reactor + observability bundle + logstash encoder).
- D-11 honored: `services/api-gateway/build.gradle.kts` contains NO `spring-boot-starter-web` (servlet — would crash startup), NO `flyway`, NO `datasource`, NO `data-jpa`, NO `postgresql`. Verified by Plan 00-06 Task 6.1 acceptance grep.
- D-02 / C9 hard rule honored: `grep -c 'lb://' services/api-gateway/src/main/resources/*.yml` returns 0 across BOTH `application.yml` and `application-docker.yml`. Static URIs only.
- All three lib deps wired: `project(":libs:observability")`, `project(":libs:error-handling")`, `project(":libs:api-contracts")` declared in `build.gradle.kts`. The graph is pre-wired so Phase 1 lands without modifying it.
- Route table contains 4 STATIC-URI entries (`health-gateway`, `health-auth`, `health-trip`, `health-destination`) — 3 downstream + 1 self-routed gateway placeholder for naming uniformity. Each route applies `SetPath=/__health` so the downstream service receives a request for its own `/__health` endpoint.
- Header comment in `application.yml` locks the Phase 1 append-below convention: "/__health/<svc> entries sit at the TOP. Phase 1 will append /api/<svc>/** entries below."
- D-21 / Convention C13 client tuning applied in `application-docker.yml`: `registry-fetch-interval-seconds: 5`, `lease-renewal-interval-in-seconds: 5`, `lease-expiration-duration-in-seconds: 10`. This drives Eureka cold-start <60s (Pitfall 10) when compose comes up.
- D-04 / Pitfall 7 prep: `management.tracing.sampling.probability: 1.0` in dev profile + `spring.application.name: api-gateway` for correct Zipkin/log attribution.
- T-00-20 (Information Disclosure) mitigated: `management.endpoints.web.exposure.include: health,info,prometheus` ONLY. `env`, `configprops`, `beans`, `mappings` are NOT exposed.
- Convention C8 (libs/observability JSON logback) applied via Option B: `services/api-gateway/src/main/resources/logback-spring.xml` is a one-liner that `<include>`s `logback-spring-base.xml` from libs/observability.

## Route Table Inventory

`application.yml` declares 4 routes (must_haves#artifacts pinned the format; verified by `grep -c '^        - id: health-' services/api-gateway/src/main/resources/application.yml` returning 4):

| id | predicate | uri | filter |
|----|-----------|-----|--------|
| `health-gateway` | `Path=/__health/gateway` | `http://localhost:8080` | `SetPath=/__health` (loops back to GatewayHealthController for naming consistency) |
| `health-auth` | `Path=/__health/auth` | `http://auth-service:8081` | `SetPath=/__health` (downstream — auth-service /__health placeholder, Plan 00-07) |
| `health-trip` | `Path=/__health/trip` | `http://trip-service:8082` | `SetPath=/__health` (downstream — trip-service /__health placeholder, Plan 00-08) |
| `health-destination` | `Path=/__health/destination` | `http://destination-service:8083` | `SetPath=/__health` (downstream — destination-service /__health placeholder, Plan 00-08) |

Per the must_haves#artifacts target (`provides: "Base config: route table for /__health/<svc> static URIs"`), this exceeds the Phase 0 minimum (3 downstream routes — gateway is optional). The plan's <action> step explicitly documented the recommendation to include the 4th route at the top, which we did.

## D-11 / D-02 Confirmation (no Flyway, no datasource, no `lb://`)

```
$ grep -E 'flyway|data-jpa|postgresql|datasource' services/api-gateway/build.gradle.kts
$ # (empty — confirmed)
$ ! grep -q 'spring-boot-starter-web' services/api-gateway/build.gradle.kts && echo OK
OK
$ grep -c 'lb://' services/api-gateway/src/main/resources/*.yml
services/api-gateway/src/main/resources/application-docker.yml:0
services/api-gateway/src/main/resources/application.yml:0
```

`build.gradle.kts` declares only:
- `spring-boot-starter-actuator` (for `/actuator/health` + `/actuator/info` + `/actuator/prometheus`)
- `spring-cloud-starter-gateway` (reactive Spring Cloud Gateway 4.x — pulls in WebFlux transitively)
- `spring-cloud-starter-netflix-eureka-client` (for service registration)
- `bundles.observability` (micrometer-tracing-bridge-otel + opentelemetry-exporter-zipkin + logstash-logback-encoder)
- `project(":libs:observability")` + `project(":libs:error-handling")` + `project(":libs:api-contracts")` (all three pre-wired)
- `spring-boot-starter-test` (testImplementation only)

Plus the `spring-cloud-dependencies:${libs.versions.springCloud.get()}` BOM import (D-30 / 2025.0.2). No Flyway, no JPA, no Postgres driver, no `datasource` (D-11). No `spring-boot-starter-web` (servlet — would crash Spring Cloud Gateway startup with "Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway").

## Boot Smoke Test Result

**Not run locally — deferred to Wave 4 compose.**

Same pattern Plans 00-03 / 00-05 documented: the bootJar artifact is JDK 21 bytecode (class-file major version 65). The developer host has only JDK 17 (homebrew openjdk@17), JDK 25 (homebrew openjdk), and Corretto 11 — all of which fail `java -jar` with `UnsupportedClassVersionError: class file version 65.0`. Plan 00-01 SUMMARY's open Todo: "Once JDK 21 is on JAVA_HOME locally" still applies.

The full runtime smoke (port 8080 listener + `/actuator/health` UP + `/__health` controller responding + Eureka registration via compose-network `eureka-server:8761/eureka` + `/__health/<svc>` route forwarding) will run automatically in Wave 4 once `infra/docker-compose.yml` brings up `api-gateway` on a JDK 21 docker base image (`eclipse-temurin:21-jre`). `scripts/smoke.sh --criterion gateway` (or SC#3 / SC#3-route) will gate this end-to-end.

For Phase 0 acceptance criteria (`bootJar` produces a runnable jar), this is sufficient — the plan's `<acceptance_criteria>` for both Task 6.1 and Task 6.2 explicitly require `compileJava` / `bootJar` to succeed but do NOT require local `java -jar` invocation.

## Local JDK Workaround Note (cross-cutting friction)

`./gradlew :services:api-gateway:compileJava` failed with `IllegalArgumentException: 25.0.2` at `org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse` when invoked with the default developer `JAVA_HOME=/opt/homebrew/Cellar/openjdk/25.0.2/libexec/openjdk.jdk/Contents/Home`. Gradle 8.14.2 ships a Kotlin compiler (Kotlin 2 / Kotlin DSL host) that does not recognize JDK 25's version string format.

Fix applied for this plan: re-invoke gradle with `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home`. Gradle's `JavaLanguageVersion.of(21)` toolchain still drives the Java compiler (per `subprojects { extensions.configure<JavaPluginExtension> { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } } }` in the root `build.gradle.kts`) — the JAVA_HOME override only affects the Gradle launcher / Kotlin DSL host JVM. With the override, both `compileJava` and `bootJar` BUILD SUCCESSFUL.

CI is unaffected: `actions/setup-java@v4` with `java-version: '21'` (D-16) sets JAVA_HOME=21 on the runner. This is purely local-developer ergonomic friction. No code change required; STATE.md's existing Todo (`Once JDK 21 is on JAVA_HOME locally, run ./gradlew help to validate`) covers it.

## Task Commits

Each task was committed atomically (sequential executor on `master`):

1. **Task 6.1: build.gradle.kts + ApiGatewayApplication.java + GatewayHealthController.java** — `2f3c6b4` (feat)
2. **Cleanup: remove obsolete services/api-gateway/.gitkeep marker** — `36a0de6` (chore)
3. **Task 6.2: application.yml + application-docker.yml + logback-spring.xml** — `f93b108` (feat)

**Plan metadata commit:** _to be added by final commit step (SUMMARY.md + STATE.md + ROADMAP.md)_

## Files Created/Modified

### Created

| Path | Bytes | Lines | Purpose |
|------|-------|-------|---------|
| `services/api-gateway/build.gradle.kts` | ~1.4 KB | 35 | Spring Boot + dependency-management plugin aliases; `spring-cloud-starter-gateway` (reactive) + `spring-cloud-starter-netflix-eureka-client` + actuator + libs/observability + libs/error-handling + libs/api-contracts + bundles.observability. spring-cloud-dependencies BOM imported with `${libs.versions.springCloud.get()}`. NO `spring-boot-starter-web`, NO Flyway, NO datasource. Header comment encodes D-11 / D-30 rationale. |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/ApiGatewayApplication.java` | ~440 B | 14 | `@SpringBootApplication` main class. Package `com.tripplanner.gateway` per D-28 / Convention C1. |
| `services/api-gateway/src/main/java/com/tripplanner/gateway/health/GatewayHealthController.java` | ~1.4 KB | 34 | `@RestController` `@GetMapping("/__health")` returning `{service: "api-gateway", status: "UP", phase: 0}` (D-01 / Convention C10). Plain `Map<String, Object>` return type — Spring Cloud Gateway tolerates plain controllers for gateway-self routes. Header comment cross-links to the route table's `SetPath=/__health` rewrite for `/__health/gateway`. |
| `services/api-gateway/src/main/resources/application.yml` | ~3.0 KB | 74 | port 8080; spring.application.name=api-gateway (D-25); 4 STATIC-URI routes (health-gateway, health-auth, health-trip, health-destination) each with `SetPath=/__health` filter; actuator surface `health,info,prometheus` (T-00-20 mitigation); tracing sampling probability 1.0 (D-04); Eureka client registration. Header comment documents Phase 1 append-below convention (D-02 / C9 / "Cross-phase handoff"). |
| `services/api-gateway/src/main/resources/application-docker.yml` | ~750 B | 25 | `on-profile: docker`; Eureka URL `http://eureka-server:8761/eureka`; D-21 client tuning (5s/5s/10s) per Pitfall 10 / Convention C13; Zipkin endpoint `${ZIPKIN_BASE_URL}/api/v2/spans`. |
| `services/api-gateway/src/main/resources/logback-spring.xml` | ~700 B | 15 | One-liner `<include resource="logback-spring-base.xml" />` from libs/observability (Convention C8 — Option B because gateway is on the trace path). Header comment cross-references Plan 00-05's Option A choice for eureka-server. |

### Modified
None.

### Deleted

| Path | Reason |
|------|--------|
| `services/api-gateway/.gitkeep` | Wave 1 marker, obsolete now that `services/api-gateway/` has real subproject content. Mirrors Plan 00-03's `libs/observability/.gitkeep` and Plan 00-05's `services/eureka-server/.gitkeep` cleanup pattern. |

## Acceptance Criteria Status

### Task 6.1 — build + main + GatewayHealthController

- [x] `spring-cloud-starter-gateway` (reactive) declared; `spring-boot-starter-web` NOT declared — `! grep -q 'spring-boot-starter-web'` returns success.
- [x] All three libs (`observability`, `error-handling`, `api-contracts`) wired as `project(...)` deps.
- [x] No Flyway / no Postgres / no JPA deps (D-11).
- [x] Main class in `com.tripplanner.gateway` (D-28 / C1).
- [x] `GatewayHealthController` returns `{service: "api-gateway", status: "UP", phase: 0}` shape and is mapped at `/__health` (NOT `/__health/gateway` — the route table rewrites).
- [x] `./gradlew :services:api-gateway:compileJava` BUILD SUCCESSFUL.

### Task 6.2 — application.yml + application-docker.yml + logback-spring.xml

- [x] 3 static-URI routes for `/__health/{auth,trip,destination}` exist (plus optional 4th `health-gateway`); ZERO `lb://` URIs.
- [x] Each route has `SetPath=/__health` filter to strip the `/<svc>` suffix before forwarding.
- [x] `spring.application.name=api-gateway` (D-25).
- [x] `management.tracing.sampling.probability=1.0` in base config (D-04).
- [x] Actuator `health,info,prometheus` exposed (T-00-20 mitigation).
- [x] `application-docker.yml` overrides Eureka URL to `eureka-server:8761` and applies the D-21 5s/5s/10s tuning (Pitfall 10 / C13).
- [x] Header comment in route table documents Phase 1's append-below convention.
- [x] `logback-spring.xml` includes `logback-spring-base.xml` (shared from libs/observability, Convention C8).
- [x] `./gradlew :services:api-gateway:bootJar` BUILD SUCCESSFUL.

## Plan-Level Success Criteria Status

- [x] Gateway compiles, packages, boots on 8080 (compile + bootJar verified locally; runtime boot deferred to Wave 4 compose).
- [x] Route table contains 3 static-URI entries for downstream `/__health/<svc>` routes (4 with the optional gateway entry — chose to include).
- [x] No `lb://` URIs (Pitfall 10 cold-start avoidance / Convention C9).
- [x] `spring.application.name=api-gateway` set.
- [x] Actuator surface limited to `health,info,prometheus` (T-00-20 mitigation).
- [x] Eureka tuning per D-21 in `application-docker.yml`.
- [x] libs/observability dep wires the reactive MDC filter — no manual `ServerHttpObservationFilter` (Pitfall 7 / Convention C7); the auto-config in `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java` registers `ReactiveMdcEnrichmentFilter` via `@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")` — that condition is met on the api-gateway because `spring-cloud-starter-gateway` pulls in WebFlux transitively.

## Deviations from Plan

None. Plan executed exactly as written. All deviation rules (1-4) inactive — no bugs, no missing critical functionality, no blocking issues, no architectural decisions surfaced. The optional 4th `/__health/gateway` route was explicitly recommended in the plan's <action> for Task 6.2 (with the "Decision" callout); including it follows the plan exactly.

## Threat Flags

None. The plan's `<threat_model>` enumerated T-00-19 through T-00-23. T-00-20 (actuator information disclosure) is mitigated by limiting `management.endpoints.web.exposure.include` to `health,info,prometheus` only. T-00-19 / T-00-21 / T-00-22 / T-00-23 are accepted-risk in Phase 0 and Phase 1+ owns the follow-on mitigations (JWT validation, rate limiting, source-control review). No NEW threat surface introduced beyond what the threat model registered.

## Self-Check: PASSED

Verification summary:
- All 6 created files exist (build.gradle.kts, ApiGatewayApplication.java, GatewayHealthController.java, application.yml, application-docker.yml, logback-spring.xml).
- All 3 task commits exist on master: `2f3c6b4` (Task 6.1), `36a0de6` (.gitkeep cleanup), `f93b108` (Task 6.2).
- `./gradlew :services:api-gateway:bootJar` produces `services/api-gateway/build/libs/api-gateway-0.1.0-SNAPSHOT.jar` (~51 MB).
- `! grep -q 'spring-boot-starter-web'` and `grep -c 'lb://' …yml = 0` confirm Convention C9 / D-11 hard rules.
