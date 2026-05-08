---
phase: 00-monorepo-scaffolding
plan: 03
subsystem: observability
tags: [auto-configuration, micrometer-tracing, mdc, logback-json, webflux, servlet, pitfall-7]

requires:
  - 00-01 (Wave 1 foundation: gradle/libs.versions.toml `[bundles].observability`, `micrometerTracing` version key, `spring-dependency-management` plugin alias)
  - 00-02 (smoke.sh `--criterion nfr-04` for incremental free-tier audit during this plan)
provides:
  - libs/observability java-library subproject (compiles + jars cleanly via the wrapper toolchain)
  - Spring Boot 3.x AutoConfiguration registered via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - Servlet MdcEnrichmentFilter (gates on `jakarta.servlet.Filter`) for auth/trip/destination services
  - Reactive ReactiveMdcEnrichmentFilter (gates on `org.springframework.web.server.WebFilter`) for the api-gateway
  - Shared logback-spring-base.xml emitting JSON to stdout with timestamp/level/service/traceId/spanId/userId/requestId/message/stackTrace
  - micrometer-tracing-bom + spring-boot-dependencies imported here ONCE (Pitfall 7 / Convention C6) — every Wave 3+ service consumer gets the BOM transitively
  - Pitfall 7 hard rule (Convention C7) encoded in source comments: NO manual HTTP observation filter registration anywhere
affects:
  - 00-04 (libs/error-handling) — sibling lib will be a smaller version of the same shape (java-library + dep-management plugin alias only)
  - 00-05 (libs/api-contracts) — sibling lib will be the empty-module shape
  - 00-06 (eureka-server) — optional consumer for log-format consistency
  - 00-07 (api-gateway) — consumer of both this lib (`implementation(project(":libs:observability"))`) AND its reactive filter
  - 00-08 (auth/trip/destination services) — consumers of the lib AND its servlet filter
  - All Wave 3+ services need only ONE line in build.gradle.kts + ONE include in logback-spring.xml to get correct behavior
  - Phase 1 (gateway JWT + downstream filter) — its JwtCommonFilter populates MDC `userId` (left empty here)
  - Every later phase emits JSON logs through this same encoder; trace-context propagation across reactive↔servlet boundary depends on the auto-config wiring landed here

tech-stack:
  added:
    - "Spring Boot 3.x @AutoConfiguration discovery convention (META-INF/spring/...AutoConfiguration.imports replacing META-INF/spring.factories)"
    - "Micrometer Tracing 1.4.5 BOM imported via dependencyManagement (Pitfall 7 single-pin)"
    - "logstash-logback-encoder 7.4 LoggingEventCompositeJsonEncoder (Logback JSON appender)"
    - "Reactive WebFilter doOnEach + doFinally MDC pattern (Phase 0 minimal; Phase 10 may switch to contextWrite + automatic context propagation)"
  patterns:
    - "Bundle-driven dep wiring: `api(libs.bundles.observability)` pulls all three observability deps via the catalog's [bundles] accessor — no per-service overrides possible (Convention C6)"
    - "Single auto-config class with two nested @Configuration classes gating filter registration on classpath presence (servlet vs webflux) — supports both stack types from one shared lib"
    - "compileOnly for jakarta.servlet-api AND spring-webflux so consumers don't pull either stack they don't use"
    - "Shared logback `<included>` fragment consumed by every service via single-line `<include resource=\"logback-spring-base.xml\"/>` — log shape can never drift across services"

key-files:
  created:
    - "libs/observability/build.gradle.kts (1816 B, 39 lines) — java-library plugin + dependency-management; api(actuator) + api(bundles.observability); compileOnly servlet/webflux; mavenBom imports for SB-deps + micrometer-tracing-bom (Pitfall 7 single-pin)"
    - "libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java (2021 B, 45 lines) — @AutoConfiguration with two nested @Configuration classes (ServletConfig + ReactiveConfig) each gated by @ConditionalOnClass"
    - "libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java (1843 B, 49 lines) — servlet OncePerRequestFilter; copies traceId/spanId from current Tracer span + generates X-Request-Id; clears MDC in finally"
    - "libs/observability/src/main/java/com/tripplanner/observability/ReactiveMdcEnrichmentFilter.java (2049 B, 49 lines) — WebFilter implementation; doOnEach to populate MDC + doFinally to clear it"
    - "libs/observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports (61 B, 1 line) — single line referencing com.tripplanner.observability.ObservabilityAutoConfiguration"
    - "libs/observability/src/main/resources/logback-spring-base.xml (1725 B, 37 lines) — `<included>` fragment with LoggingEventCompositeJsonEncoder emitting timestamp/level/service/traceId/spanId/userId/requestId/message/stackTrace"
  modified: []

key-decisions:
  - "Two nested @Configuration classes inside one @AutoConfiguration — chose this over two separate top-level auto-configs because (a) both share the same Tracer dependency, (b) both filters logically belong to the same observability story, (c) one entry in AutoConfiguration.imports is simpler than two. Each nested class is independently classpath-gated so only the right filter loads in each service."
  - "ReactiveMdcEnrichmentFilter uses doOnEach + doFinally rather than contextWrite + automatic-context-propagation — this is the Phase 0 minimal pattern that gives correct log correlation for synchronous-style WebFlux handling (which is what the gateway does in Phase 0). Phase 10 hardening may upgrade if async trace-context loss becomes a real issue."
  - "Removed libs/observability/.gitkeep (intentional deletion in Task 3.2 commit) — the marker existed only to keep the empty subproject directory tracked under Wave 1; once real source files arrived in this plan, the marker was redundant. STATE.md's Plan 00-01 decision called this transition out explicitly."
  - "Reworded the Pitfall 7 hard-rule comments in the three Java files to NOT use the literal symbol name `ServerHttpObservationFilter` — the plan acceptance criterion was scoped 'does NOT appear anywhere in any of the three files', and a literal grep is the simplest enforcement. The build.gradle.kts retains the literal symbol per Task 3.1's explicit verbatim requirement."

patterns-established:
  - "Convention C7 (Pitfall 7 hard rule): NO `@Bean ServerHttpObservationFilter` registration anywhere in the lib OR any service. Auto-configured by Spring Boot 3.2+ via WebHttpHandlerBuilder. Enforced by a literal `grep` in plan acceptance + scoped comments in source."
  - "Convention C8: every service depends on libs/observability for MDC + JSON logback. The two-line consumer integration is locked: `implementation(project(\":libs:observability\"))` + `<include resource=\"logback-spring-base.xml\"/>`."
  - "Spring Boot 3.x AutoConfiguration discovery convention: META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports (single FQCN per line; replaces META-INF/spring.factories)."

requirements-completed: [NFR-04]

duration: 8min
completed: 2026-05-08
---

# Phase 0 Plan 3: libs/observability Auto-Config + MDC Filters + Logback JSON Summary

**Wave-2 deliverable: `libs/observability` is FULLY WIRED — Spring Boot 3.x @AutoConfiguration discovers two classpath-gated MDC filters (servlet for auth/trip/destination, reactive WebFilter for the gateway), `micrometer-tracing-bom` is pinned ONCE here per Pitfall 7, and a shared `logback-spring-base.xml` emits JSON to stdout with `timestamp`/`level`/`service`/`traceId`/`spanId`/`userId`/`requestId`/`message`/`stackTrace`. Wave-3+ services need exactly two lines of integration (one `implementation(project(...))` + one `<include resource=...>`)**.

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-08T04:16:03Z
- **Completed:** 2026-05-08T04:24:24Z
- **Tasks:** 3
- **Files created:** 6
- **Files modified:** 0
- **Files deleted:** 1 (libs/observability/.gitkeep — obsolete marker)

## Accomplishments

- D-04 fully realized: `libs/observability` ships @AutoConfiguration class registered via the SB 3.x AutoConfiguration.imports convention, MDC filters for both servlet AND reactive stacks, the shared logback-spring-base.xml include, and the dep stack (micrometer-tracing-bridge-otel + opentelemetry-exporter-zipkin + logstash-logback-encoder) wired via the catalog's `[bundles].observability` accessor.
- Pitfall 7 single-pin enforced: `mavenBom("io.micrometer:micrometer-tracing-bom:...")` appears in EXACTLY one Gradle build file across the entire repo. Verified by `find libs services -name '*.kts' -exec grep -h 'mavenBom("io.micrometer:micrometer-tracing-bom' {} +` returning a single hit.
- Convention C7 (Pitfall 7 hard rule) encoded in source: `ServerHttpObservationFilter` is referenced ONLY in the `build.gradle.kts` Pitfall 7 comment (Task 3.1 explicitly required the literal symbol there). All three Java source files use reworded comments that pass the plan's strict `! grep -q 'ServerHttpObservationFilter'` check.
- Build verified: `./gradlew :libs:observability:compileJava` exits 0; `./gradlew :libs:observability:jar` produces `libs/observability/build/libs/observability-0.1.0-SNAPSHOT.jar` (7013 bytes); `unzip -l` shows the AutoConfiguration.imports file packaged at the canonical META-INF path.
- The lib auto-discovers in any consuming service: a service that adds `implementation(project(":libs:observability"))` gets the right MDC filter for its stack with zero extra config. The two nested `@ConditionalOnClass` gates (`jakarta.servlet.Filter` for servlet, `org.springframework.web.server.WebFilter` for reactive) ensure each service loads exactly one filter — never both, never zero.
- NFR-04 free-tier audit (`scripts/smoke.sh --criterion nfr-04`) re-passes after Plan 00-03 — the observability stack uses 100% open-source components (micrometer / OpenTelemetry / logstash-logback-encoder / Zipkin) with no paid SaaS dependencies.

## ConditionalOnClass Gates → Service Wiring

| Gate | Service stack | Services that match (Wave 3+) | Filter that registers |
|------|---------------|--------------------------------|----------------------|
| `@ConditionalOnClass(name = "jakarta.servlet.Filter")` | Servlet (Spring MVC + Boot Web) | auth-service, trip-service, destination-service, eureka-server | `MdcEnrichmentFilter` (extends `OncePerRequestFilter`) wrapped in a `FilterRegistrationBean` with order `Integer.MIN_VALUE + 100` |
| `@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")` | Reactive (Spring Cloud Gateway / WebFlux) | api-gateway | `ReactiveMdcEnrichmentFilter` (implements `WebFilter`) registered as a plain `@Bean` |

Note: `eureka-server` runs Spring MVC (Eureka itself is a servlet app); whether it consumes `libs/observability` is per-service discretion (00-PATTERNS.md notes "default to including it for log-format consistency"). The auto-config does not error if the servlet filter classpath gate fires but the consumer doesn't actually serve HTTP traffic — the bean is created but never invoked.

## MDC Field Set (logback-spring-base.xml)

The shared logback include emits one JSON object per log event with these fields:

| Field | Source | Phase 0 status | Phase 1 status |
|-------|--------|---------------|----------------|
| `timestamp` | Logback `<timestamp>` provider | populated (every event) | populated |
| `level` | Logback `<logLevel>` provider | populated (every event) | populated |
| `service` | `${spring.application.name:-unknown}` Spring property substitution (D-25) | populated when `spring.application.name` is set in service's `application.yml` | populated |
| `traceId` | MDC, populated by `MdcEnrichmentFilter`/`ReactiveMdcEnrichmentFilter` from `Tracer.currentSpan().context().traceId()` | populated when an active trace span exists | populated (W3C trace context propagation cross-service per NFR-9) |
| `spanId` | MDC, populated by the same filters from `Tracer.currentSpan().context().spanId()` | populated when an active trace span exists | populated |
| `userId` | MDC | empty (Phase 0 placeholder per D-04) | populated by Phase 1's `JwtCommonFilter` after JWT validation |
| `requestId` | MDC, populated by the filters from `X-Request-Id` header (or generated UUID if absent) | populated | populated (Phase 1 propagates as a header to downstream services for cross-service correlation) |
| `message` | Logback `<message>` provider | populated (every event) | populated |
| `stackTrace` | Logback `<stackTrace>` provider | populated when an exception is logged | populated |

Encoder: `net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder` (logstash-logback-encoder 7.4 — pinned in `gradle/libs.versions.toml`).

## ServerHttpObservationFilter Confirmation

`grep -r 'ServerHttpObservationFilter' libs/observability/src/main/java/` returns no matches. The literal symbol appears ONLY in `libs/observability/build.gradle.kts`'s Pitfall 7 hard-rule header comment (where Task 3.1 explicitly required it as a verbatim header reminder for code review). The auto-config never registers Spring's HTTP observation filter manually — it is auto-configured by Spring Boot 3.2+ via `WebHttpHandlerBuilder`, exactly as Pitfall 7 / Convention C7 mandates.

```
$ grep -r 'ServerHttpObservationFilter' libs/observability
libs/observability/build.gradle.kts:// Do NOT register ServerHttpObservationFilter manually anywhere.
$ grep -r 'ServerHttpObservationFilter' libs/observability/src/
$ # (empty — confirmed)
```

## Task Commits

Each task was committed atomically (sequential executor on `master`):

1. **Task 3.1: libs/observability/build.gradle.kts (Pitfall 7 single-pin)** — `cfd2464` (chore)
2. **Task 3.2: ObservabilityAutoConfiguration + servlet/reactive MDC filters** — `e74af2c` (feat)
3. **Task 3.3: SB 3.x AutoConfiguration.imports + logback-spring-base.xml** — `622ea36` (feat)

**Plan metadata commit:** _to be added by final commit step (SUMMARY.md + STATE.md + ROADMAP.md)_

## Files Created/Modified

### Created

| Path | Bytes | Lines | Purpose |
|------|-------|-------|---------|
| `libs/observability/build.gradle.kts` | 1816 | 39 | java-library + spring-dependency-management; bundle-driven deps; tracing-bom pinned ONCE here (Pitfall 7) |
| `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java` | 2021 | 45 | @AutoConfiguration with two nested @Configuration classes — one classpath-gated to servlet, one to webflux |
| `libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java` | 1843 | 49 | Servlet OncePerRequestFilter — copies traceId/spanId/requestId into MDC; clears MDC in finally |
| `libs/observability/src/main/java/com/tripplanner/observability/ReactiveMdcEnrichmentFilter.java` | 2049 | 49 | WebFlux WebFilter equivalent — doOnEach to populate MDC; doFinally to clear |
| `libs/observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 61 | 1 | SB 3.x auto-config discovery (single FQCN line; replaces spring.factories) |
| `libs/observability/src/main/resources/logback-spring-base.xml` | 1725 | 37 | `<included>` Logback fragment; LoggingEventCompositeJsonEncoder emitting timestamp/level/service/traceId/spanId/userId/requestId/message/stackTrace |

### Deleted

| Path | Reason |
|------|--------|
| `libs/observability/.gitkeep` | Marker placed in Plan 00-01 to keep the empty subproject directory tracked. Now obsolete because `build.gradle.kts` and `src/main/...` provide real content; deletion happened atomically with Task 3.2's source-file commit (`e74af2c`). |

## Decisions Made

- **Two nested `@Configuration` classes inside one `@AutoConfiguration` rather than two separate top-level auto-configs.** Both filters share the same `Tracer` dependency and belong to the same observability story; one entry in `AutoConfiguration.imports` is simpler than two. Each nested class is independently classpath-gated by `@ConditionalOnClass`, so only one of the two loads in any given service. This matches the plan's Task 3.2 verbatim code excerpt and is the SB-idiomatic pattern.
- **`ReactiveMdcEnrichmentFilter` uses `doOnEach + doFinally` rather than `contextWrite + automaticContextPropagation`.** WebFlux MDC propagation across thread-switching reactor boundaries is non-trivial. The plan's Task 3.2 action explicitly opts for the minimal Phase 0 pattern: "doOnEach + doFinally pattern above is the minimal Phase 0 wiring for log correlation; Phase 10 hardening may switch to contextWrite + Hooks.enableAutomaticContextPropagation() for full async-trace propagation. For Phase 0's /__health/<svc> route this is sufficient."
- **Reworded the Pitfall 7 hard-rule comments in the three Java files to NOT use the literal symbol `ServerHttpObservationFilter`.** Plan acceptance criterion for Task 3.2 was scoped: "`ServerHttpObservationFilter` does NOT appear anywhere in any of the three files (Pitfall 7 / C7 hard rule)." A literal `! grep -q ServerHttpObservationFilter` is the simplest enforcement. The `build.gradle.kts` Pitfall 7 header comment retains the literal symbol per Task 3.1's verbatim header-comment requirement, preserving the code-review reminder where it was explicitly mandated.
- **Removed `libs/observability/.gitkeep` as part of Task 3.2.** Plan 00-01's marker is no longer needed once real source files arrive; the deletion was atomic with the Task 3.2 source-file commit and is documented in Plan 00-01's SUMMARY ("Wave 2/3/4/5 will replace each `.gitkeep` with the real `build.gradle.kts` when those subprojects are built out"). This is intentional — the post-commit deletion check confirmed it.

## Deviations from Plan

None — plan executed exactly as written. The two adjustments above (rewording the Pitfall 7 comments in Java sources; deleting the now-obsolete `.gitkeep`) are both **enforcing acceptance criteria** rather than deviating from them, and are documented under Decisions Made.

## Issues Encountered

- **Local launcher JVM is OpenJDK 25, not 21.** Plan 00-01's SUMMARY documented this. `./gradlew :libs:observability:compileJava` initially failed with `25.0.2`. Resolved by setting `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home` so Gradle's wrapper runs on JDK 17 and Gradle's toolchain mechanism auto-provisions JDK 21 for `javac`. Both `compileJava` and `jar` then succeed (BUILD SUCCESSFUL in 2m 4s for first compile incl. dep resolution). CI uses `actions/setup-java@v4 java-version: 21` (Plan 00-09) so this is purely a local-developer ergonomics gap, not a CI gap.
- No further issues during planned work.

## Threat Register Outcomes

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-00-09 (Logback emitting Authorization / password values) | mitigated (partial — by design) | logback-spring-base.xml emits ONLY the declared MDC keys (traceId/spanId/userId/requestId) and the message body. Phase 0 has no service that logs raw HTTP headers; Phase 1 will add the redaction converter for Authorization / password / token / secret substrings. |
| T-00-10 (Trace context lost across reactive↔servlet boundary) | mitigated | Single `micrometer-tracing-bom` pin (verified single-occurrence across the repo) + `@AutoConfiguration` registering both filters via classpath-gated nested `@Configuration` classes + Pitfall 7 hard-rule comment in build file. Validation in Phase 1 will confirm one trace ID across gateway + downstream. |
| T-00-11 (Service overrides observability lib's filter to silently drop trace IDs) | accepted | Per plan: code-review enforcement only. The `BeanDefinitionOverrideException` Spring throws on duplicate filter beans is a runtime backstop. Convention C7 lists this as a hard rule for code review. |
| T-00-12 (Unbounded MDC accumulation if `MDC.clear()` not called → thread-local memory leak) | mitigated | Servlet filter calls `MDC.clear()` in `finally`; reactive filter calls `MDC.clear()` in `doFinally`. Verified by grep + manual code review. Thread pools recycle threads cleanly. |

## Self-Check

Verified each claim against the workspace:

- `[x] FOUND: libs/observability/build.gradle.kts (1816 B, 39 lines)`
- `[x] FOUND: libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java (2021 B, 45 lines)`
- `[x] FOUND: libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java (1843 B, 49 lines)`
- `[x] FOUND: libs/observability/src/main/java/com/tripplanner/observability/ReactiveMdcEnrichmentFilter.java (2049 B, 49 lines)`
- `[x] FOUND: libs/observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports (61 B, 1 line)`
- `[x] FOUND: libs/observability/src/main/resources/logback-spring-base.xml (1725 B, 37 lines)`
- `[x] FOUND commit: cfd2464 (Task 3.1 chore)`
- `[x] FOUND commit: e74af2c (Task 3.2 feat)`
- `[x] FOUND commit: 622ea36 (Task 3.3 feat)`
- `[x] FOUND assertion: micrometer-tracing-bom mavenBom appears EXACTLY ONCE across libs/**/*.kts + services/**/*.kts (Pitfall 7 / Convention C6)`
- `[x] FOUND assertion: ServerHttpObservationFilter NOT in any of the 3 Java source files (Convention C7)`
- `[x] FOUND assertion: ServerHttpObservationFilter PRESENT in build.gradle.kts Pitfall 7 header comment (Task 3.1 explicit requirement)`
- `[x] FOUND assertion: ./gradlew :libs:observability:compileJava exit 0 (with JDK 17 launcher + toolchain auto-provisioning JDK 21)`
- `[x] FOUND assertion: ./gradlew :libs:observability:jar produces observability-0.1.0-SNAPSHOT.jar`
- `[x] FOUND assertion: unzip -l shows META-INF/spring/...AutoConfiguration.imports + logback-spring-base.xml + 5 .class files at canonical paths`
- `[x] FOUND assertion: scripts/smoke.sh --criterion nfr-04 passes (no paid SaaS deps)`

**Self-Check: PASSED**

## User Setup Required

None — no external service configuration required for this plan.

## Next Phase Readiness

- **Wave 2 plans (00-04 libs/error-handling, 00-05 libs/api-contracts) are unblocked.** Both follow the same `java-library` + `spring-dependency-management` plugin shape established here; this plan is the prototype.
- **Wave 3+ services (00-06 eureka-server, 00-07 api-gateway, 00-08 auth/trip/destination services) can consume libs/observability immediately** via `implementation(project(":libs:observability"))` — the autoconfig will discover and wire the right filter for each service's stack with zero local config.
- **Wave 3+ services should add `<include resource="logback-spring-base.xml"/>`** to a one-line `logback-spring.xml` in their `src/main/resources/`. This is the Convention C8 integration pattern.
- **Open dependency:** Phase 1's `JwtCommonFilter` will populate MDC `userId`. The slot is ready in this lib's logback-spring-base.xml (declared via `<includeMdcKeyName>userId</includeMdcKeyName>`); Phase 1 just needs to call `MDC.put("userId", ...)`.
- **No blockers for Plan 00-04.**

---
*Phase: 00-monorepo-scaffolding*
*Completed: 2026-05-08*
