---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_plan: 5
status: executing
last_updated: "2026-05-08T04:37:54.922Z"
progress:
  total_phases: 11
  completed_phases: 0
  total_plans: 10
  completed_plans: 4
  percent: 40
---

# Project State: Trip Planner

**Initialized:** 2026-05-08
**Last updated:** 2026-05-08

---

## Project Reference

**Core value:** A signed-in user can search a city, discover attractions, and assemble them into a multi-day itinerary that persists across sessions — with drag-drop reorder, optional time slots, and a map view.

**Project type:** Practice/portfolio. Single developer. Demonstrates Spring Boot microservices + React at senior-reviewer quality.

**Canonical docs:** `docs/` is the source of truth for design; `.planning/` operationalizes it. If conflict arises, `docs/` wins unless explicitly evolved.

---

## Current Position

**Phase:** Phase 0 — Monorepo Scaffolding
**Status:** Executing Phase 00
**Current plan:** 5

```
Progress: [████░░░░░░] 40%
Phase: 00 (monorepo-scaffolding) — EXECUTING
Plan: 5 of 10
           ^
           HERE
```

**Next action:** Execute Plan 00-05 (`.planning/phases/00-monorepo-scaffolding/00-05-PLAN.md`).

---

## Performance Metrics

| Metric | Target | Current |
|--------|--------|---------|
| Phases complete | 11 | 0 |
| Requirements delivered | 31 | 1 |
| Backend line coverage | ≥ 70% | N/A |
| Auth + ownership coverage | 100% | N/A |
| Search p95 latency | < 500 ms | N/A |

### Plan Execution Log

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| 00-monorepo-scaffolding P01 | 63min | 3 | 14 |
| 00-monorepo-scaffolding P02 | 3min | 2 | 2 |
| 00-monorepo-scaffolding P03 | 8min | 3 | 6 |
| 00-monorepo-scaffolding P04 | 3min | 2 | 5 |

---

## Accumulated Context

### Key Decisions Made

- Stack locked: Java 21, Spring Boot 3.5.x (corrected from 3.3.x), Spring Cloud **2025.0.x** (D-30 — corrects earlier 2024.0.x; 2025.0 / Northfields matches SB 3.5), React 18.3.x, Tailwind 3.4.x
- Architecture locked: 4 services + eureka-server + frontend monorepo, no service-to-service calls in v1
- react-leaflet pinned to 4.2.x (v5 requires React 19, incompatible with locked React 18)
- Axios pinned to >= 1.15.0 (CVE-2025-62718, CVE-2026-40175 in earlier versions)
- jjwt pinned to 0.13.0 (latest stable, decompression leak fix)
- **00-01:** `gradle/libs.versions.toml` is the single source of truth for backend versions (Convention C16); no version literals in any service `build.gradle.kts`
- **00-01:** `micrometer-tracing-bom` and observability bundle pinned ONCE in catalog (Pitfall 7 / Convention C6) — never overridden per-service
- **00-01:** Subproject directories created with `.gitkeep` so newer Gradle launchers do not error on missing project paths during settings evaluation; build files arrive in Wave 2+
- **00-01:** Gradle 8.14.2 wrapper distribution side-loaded after network flake; SHA256 verified against services.gradle.org (`7197a12f…0a6999`)
- **00-02:** `scripts/smoke.sh` lands in Wave 1 per D-33 (NOT a final wave) so each subsequent wave's containers can be smoke-tested incrementally as they come online via `--criterion <N>` per-criterion gating
- **00-02:** NFR-04 free-tier audit uses an enumerated grep deny-list (31 tokens: 11 Java + 10 npm + 10 compose) — concrete verification of `requirements: [NFR-04]`, NOT a vague "no paid deps" heuristic (BLOCKER 4 fix)
- **00-02:** Smoke script has jq detection with grep fallback for SC#2/#3/#3-route/#4 — usable in minimal CI environments without an extra package install
- **00-03:** `libs/observability` is FULLY WIRED — `@AutoConfiguration` registered via SB 3.x `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; two nested `@Configuration` classes with `@ConditionalOnClass` gate filter registration (servlet `MdcEnrichmentFilter` for auth/trip/destination; reactive `ReactiveMdcEnrichmentFilter` for api-gateway). Wave-3+ services need only `implementation(project(":libs:observability"))` + `<include resource="logback-spring-base.xml"/>`.
- **00-03:** Pitfall 7 single-pin enforced — `mavenBom("io.micrometer:micrometer-tracing-bom:...")` appears EXACTLY ONCE in the entire monorepo (in `libs/observability/build.gradle.kts`); verified by `find libs services -name '*.kts' -exec grep -h ... {} +`. Consumers get the BOM transitively via `dependencyManagement`.
- **00-03:** `ServerHttpObservationFilter` is NEVER registered manually anywhere (Convention C7 hard rule) — auto-configured by Spring Boot 3.2+ via `WebHttpHandlerBuilder`. Encoded in build-file Pitfall-7 header comment + Java source comments + plan acceptance grep.
- **00-03:** `ReactiveMdcEnrichmentFilter` uses `doOnEach` + `doFinally` Phase-0 minimal pattern; Phase 10 may upgrade to `contextWrite` + `Hooks.enableAutomaticContextPropagation()` if async trace-context loss surfaces in real usage.
- **00-04:** `libs/error-handling` ships ONLY `ProblemDetailFactory` static helper + 2-code `ErrorCode` enum (`AUTH_UNAUTHORIZED`, `AUTH_RATE_LIMITED`) per D-05. No `@ControllerAdvice` / `GlobalExceptionHandler` in Phase 0 — those are added per-service in Phase 1+ when real endpoints exist.
- **00-04:** `libs/api-contracts` is the empty-module shape per D-06 — `java-library` plugin only + `src/main/java/.gitkeep`. Phase 1 lands `com.tripplanner.contracts.UserContext` here without modifying `settings.gradle.kts` or any service `build.gradle.kts` (the dependency graph is already wired in Wave 1).
- **00-04:** ProblemDetail envelope shape locked — `type` URI = `https://tripplanner.example.com/errors/<code>`; `code` extension property = `code.code()` (machine-readable string identifier per `docs/04-api-spec.md §6`). Phase 1+ MUST go through `ProblemDetailFactory.of(...)` — never construct `ProblemDetail` directly. Convention C22 preserved: `libs/jwt-common` does NOT exist (Phase 1 owns its creation).

### Critical Pitfalls to Watch

- Flyway per-service history tables must be configured in Phase 0 or services fail to start (Pitfall 3)
- X-User-Id stripping + JwtCommonFilter wiring in downstream services must happen in Phase 1, not Phase 2 (Pitfall 1)
- dnd-kit optimistic update: do NOT call `invalidateQueries` on success, only on error — implement in Phase 8 before writing any dnd-kit code (Pitfall 2)
- Foursquare free-tier photos/hours are Premium fields silently absent — WireMock stubs must reflect free-tier reality (Pitfall 6)
- Axios 401 infinite refresh loop: implement `isRefreshing` + `failedQueue` pattern in Phase 7 before adding Phase 8 parallel queries (Pitfall 9)

### Todos

- [x] Begin Phase 0 planning (`/gsd-plan-phase 0`)
- [ ] Before Phase 0 ships: update `docs/02-architecture.md` tech stack table with corrected version pins (Spring Boot 3.5.x, Spring Cloud **2025.0.x** per D-30, react-leaflet 4.2.x, Axios >= 1.15.0, jjwt 0.13.0). CLAUDE.md likewise needs the 2024.0.x → 2025.0.x correction.
- [ ] Once JDK 21 is on `JAVA_HOME` locally, run `./gradlew help` to validate `build.gradle.kts` Kotlin DSL parses cleanly (Plan 00-02+ depends on this)

### Blockers

None.

---

## Session Continuity

**To resume this project in a new session:**

1. Read `/Users/huyqtrieu/Desktop/Practice/java-lab/.planning/PROJECT.md` for core value and constraints
2. Read this file (STATE.md) for current position
3. Read `/Users/huyqtrieu/Desktop/Practice/java-lab/.planning/ROADMAP.md` for the full phase structure
4. Check which phase is current and whether a plan already exists under `.planning/plans/`

**Architecture overview:** docs/02-architecture.md
**Full roadmap with acceptance criteria:** docs/09-roadmap.md (canonical) + .planning/ROADMAP.md (operationalized)
**Research findings:** .planning/research/SUMMARY.md

---

*State initialized: 2026-05-08 after roadmap creation*

**Last session:** 2026-05-08T04:33Z — Stopped at: Completed 00-04-PLAN.md — Resume from: 00-05-PLAN.md
