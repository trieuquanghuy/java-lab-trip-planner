---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
current_plan: 3
status: executing
stopped_at: Completed 02-02-PLAN.md (JwtIssuer + auto-config)
last_updated: "2026-05-09T15:41:46.377Z"
progress:
  total_phases: 11
  completed_phases: 2
  total_plans: 23
  completed_plans: 18
  percent: 78
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

**Phase:** 2
**Status:** Executing Phase 02
**Current plan:** 3

```
Progress: [████████░░] 78%
Phase: 02 (auth-service) — EXECUTING
Plan: 3 of 7
```

**Next action:** Phase 2 planning — Auth Service (signup → verify email → login → refresh → logout + 8 mandatory security tests).

---

## Performance Metrics

| Metric | Target | Current |
|--------|--------|---------|
| Phases complete | 11 | 1 |
| Requirements delivered | 31 | 1 |
| Backend line coverage | ≥ 70% | N/A |
| Auth + ownership coverage | 100% | N/A |
| Search p95 latency | < 500 ms | N/A |
| Phase 01 P02 | 8min | 5 tasks | 17 files |
| Phase 01 P03 | 6min | 3 tasks | 7 files |
| Phase 01 P04 | 15 | 3 tasks | 13 files |
| Phase 01-api-gateway P05 | 4h | 3 tasks | 9 files |
| Phase 01-api-gateway P06 | 20min | 3 tasks (2 + checkpoint) | 4 files |
| Phase 02-auth-service P01 | 13min | 3 tasks | 12 files |
| Phase 02-auth-service P02 | 6min | 2 tasks | 3 files |

### Plan Execution Log

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| 00-monorepo-scaffolding P01 | 63min | 3 | 14 |
| 00-monorepo-scaffolding P02 | 3min | 2 | 2 |
| 00-monorepo-scaffolding P03 | 8min | 3 | 6 |
| 00-monorepo-scaffolding P04 | 3min | 2 | 5 |
| 00-monorepo-scaffolding P05 | 6min | 2 | 4 |
| 00-monorepo-scaffolding P06 | 5min | 2 | 6 |
| 00-monorepo-scaffolding P07 | 8min | 3 | 21 |
| 00-monorepo-scaffolding P08 | 11min | 2 | 10 |
| 00-monorepo-scaffolding P09 | 28min | 4 | 25 |
| 00-monorepo-scaffolding P10 | 12min | 3 | 2 |
| 01-api-gateway P03 | 6min | 3 | 7 |
| 02-auth-service P01 | 13min | 3 | 12 |
| 02-auth-service P02 | 6min | 2 | 3 |

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
- **00-05:** `services/eureka-server` is the standalone Netflix Eureka registry — `register-with-eureka=false` + `fetch-registry=false` + `enable-self-preservation=false`; D-21 client tuning (`registry-fetch-interval-seconds`/`lease-renewal-interval-in-seconds`) deliberately NOT applied here (Convention C13: tuning is for clients, eureka is the SERVER); D-11 honored (no Flyway, no datasource, no JPA, no Postgres deps); `spring.application.name=eureka-server` set per D-25/Pitfall 7.
- **00-05:** Logback Option A chosen — eureka-server uses Spring Boot's default Logback config (pass-through `logback-spring.xml` with documented Option B switch instructions). NO `libs/observability` dependency in `services/eureka-server/build.gradle.kts`; registry traffic is not in the application trace path in Phase 0 (00-PATTERNS.md line 425). Phase 10 may switch to Option B if cross-service log correlation needs Eureka to match.
- **00-05:** Local `java -jar` smoke deferred to Wave 4 compose. bootJar artifact is class-file 65 (JDK 21) and developer host has no JDK 21 installed (homebrew openjdk@17, openjdk 25, Corretto 11). Plan acceptance was met via successful `bootJar` — full runtime smoke (port 8761 + dashboard + `/actuator/health` + `/eureka/apps`) runs in compose with `eclipse-temurin:21-jre` image; CI uses `actions/setup-java@v4 java-version: 21`. Same ergonomic pattern Plan 00-03 SUMMARY documented.
- **00-06:** `services/api-gateway` is the only public-facing backend in Phase 0 — Spring Cloud Gateway 4.x (reactive WebFlux variant) via `spring-cloud-starter-gateway` from the Spring Cloud 2025.0.x train (D-30); `spring-boot-starter-web` (servlet) deliberately ABSENT — including it would crash startup with "Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway". All three libs (`observability`, `error-handling`, `api-contracts`) wired as `project(...)` deps now so Phase 1 lands without modifying `build.gradle.kts`.
- **00-06:** Gateway route table contains 4 STATIC-URI entries (D-02 / Convention C9 hard rule — zero `lb://` schemes anywhere). Routes `/__health/{gateway,auth,trip,destination}` forward via `http://service-name:port` URIs with `SetPath=/__health` filter. Header comment locks the Phase 1 append-below convention so `/api/<svc>/**` entries land later without reordering. Optional `/__health/gateway` route INCLUDED at TOP of route list per plan recommendation for naming uniformity (loops back to `http://localhost:8080`).
- **00-06:** Reactive trace context bridge (Pitfall 7 mitigation) registered TRANSITIVELY via `libs/observability` auto-config — `ObservabilityAutoConfiguration`'s `@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")` activates `ReactiveMdcEnrichmentFilter` on the gateway. NO manual `@Bean ServerHttpObservationFilter` (Convention C7 hard rule). Cross-trace assertion (single trace ID across gateway → downstream) deferred to Phase 1 per Pitfall 7 step 4.
- **00-06:** Local `compileJava` workaround documented — developer `JAVA_HOME` defaults to homebrew openjdk JDK 25, which Gradle 8.14.2's bundled Kotlin compiler chokes on at `JavaVersion.parse('25.0.2')`. Fix: invoke gradle with `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/...`; `JavaLanguageVersion.of(21)` toolchain still drives the Java compiler. CI is unaffected (`actions/setup-java@v4 java-version: 21`). Same friction Plan 00-01 SUMMARY's open Todo tracks.
- **01-02:** jjwt 0.13.0 modern API only — `Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(jws)`. `parserBuilder()`/`setSigningKey()` absent from all source. C27-P1.
- **01-02:** UserContext is a `record` implementing `java.security.Principal` — `getName()` returns `userId` for audit logging. C32-P1.
- **01-02:** WR-02 closed — `@ConditionalOnWebApplication` replaces `@ConditionalOnClass` in `ObservabilityAutoConfiguration` + `JwtAutoConfiguration`. Convention C31-P1 now uniform across all autoconfigs.
- **01-02:** jwt-common filter chain ordering: `MdcEnrichmentFilter @ Integer.MIN_VALUE + 100` → `ServletJwtCommonFilter @ Integer.MIN_VALUE + 200`. traceId/spanId populated before userId written to MDC.
- **01-02:** `JwtVerifier` constructor throws `IllegalStateException` on null or <32-byte secret — fail-fast at startup (C28-P1).
- **01-02:** `junit-platform-launcher` must be `testRuntimeOnly` on all modules using `useJUnitPlatform()` with JUnit Platform 1.12.x (engine/launcher version alignment).
- **00-06:** Actuator surface limited to `health,info,prometheus` ONLY (T-00-20 mitigation — `env`, `configprops`, `beans`, `mappings` NOT exposed). `management.tracing.sampling.probability=1.0` set in dev/docker for Pitfall 7 step-4 trace-continuity verification in Phase 1.
- **00-07:** All 3 DB-backed services (`auth-service`/`trip-service`/`destination-service`) share **byte-for-byte-identical `build.gradle.kts`** files (modulo a small comment). Catalog accessors are not service-specific — service identity lives in `application.yml` + package names + V1 comment block. Convention C5 (Pitfall A 3x) and C16 (single-source-of-truth catalog) audits become a single-grep verification per file.
- **00-07:** Per-service Flyway history table convention enforced **in artifact form** — three distinct table names locked in 3 application.yml files: `auth_flyway_schema_history` / `trip_flyway_schema_history` / `destination_flyway_schema_history`. Pitfall 3 / D-09 / Convention C4 mitigation now testable by SC#5 once Wave 6 compose lands. Combined with `spring.flyway.schemas` + `default-schema` + `hibernate.default_schema` per-service, no Flyway run can ever collide across the three services.
- **00-07:** Servlet stack (NOT WebFlux) on auth/trip/destination — `spring-boot-starter-web` only. Verified: 0 webflux references in `services/{auth,trip,destination}-service/build.gradle.kts`. **Convention locked: this project has exactly one reactive service** (api-gateway, Plan 00-06). All other Spring Boot services are servlet-stack.
- **00-07:** `spring.jpa.hibernate.ddl-auto: validate` (NEVER `update`) on all 3 services per C15. Anti-pattern absent — `grep -r 'ddl-auto: update' services/` returns 0 matches. Phase 0 has no entities so it's effectively a no-op now; from Phase 2/3/5 onward Hibernate will reject any entity↔schema drift on boot, forcing all schema changes through Flyway.
- **00-07:** JDBC `?currentSchema=<svc>` URL parameter is the **primary lock** for the per-service-DB-user model (D-08). Combined with `spring.flyway.schemas` + `flyway.default-schema` + `hibernate.default_schema`, every persistence-stack layer agrees on the schema each service operates on. Wave 6's `infra/postgres/init.sql` will set each user's `search_path` to its own schema as defense-in-depth, but the JDBC URL is the application-side guarantee.
- **00-07:** Initial git-add ordering caused Task 7.1's first commit (`2625bf7`) to include only the `.gitkeep` deletion. Caught immediately via `git status` and amended into `2508472` to merge the source-file additions. Tasks 7.2/7.3 used the same git-add pattern and committed cleanly on the first attempt — only `2508472`/`74bf90c`/`91a709f` appear in the final commit history for plan 00-07.
- **00-08:** Multi-stage backend Dockerfiles use `build.context: ..` (repo root from `infra/`) + `dockerfile: services/<svc>/Dockerfile` (BLOCKER 2 fix). The builder stage runs `./gradlew :services:<svc>:bootJar` inside an `eclipse-temurin:21-jdk-alpine` container; the runtime stage uses `eclipse-temurin:21-jre-alpine`. Eliminates the "must run `./gradlew bootJar` before `docker compose up`" trap — satisfies ROADMAP SC#1's "no manual intervention" requirement. No parent-relative `COPY ../...` anywhere (WARNING 3 elimination).
- **00-08:** `frontend/Dockerfile` is owned by Plan 00-09 (BLOCKER 3 fix). Plan 00-08's compose `frontend` block only references it via `build.context: ../frontend` + `dockerfile: Dockerfile` + `args.VITE_API_URL`. Verified: `test -e frontend/Dockerfile` returns false; `test -e infra/frontend.Dockerfile` returns false.
- **00-08:** D-22 binding audit clean — 7 loopback bindings (postgres, redis, mailhog SMTP, auth, trip, destination, eureka) + 4 public bindings (api-gateway, frontend, mailhog UI, zipkin UI). 8 `condition: service_healthy` assertions across dependents (api-gateway → eureka; auth/trip/destination → postgres + eureka).
- **00-08:** Postgres `init.sql` is idempotent via `CREATE SCHEMA IF NOT EXISTS` + `DO $$ ... pg_roles WHERE rolname='X' THEN CREATE ROLE X` guards. Creates 3 schemas (auth/trip/destination) + 3 service users (auth_svc/trip_svc/destination_svc) + schema-scoped `USAGE,CREATE` grants + `ALTER ROLE search_path` defaults. Mounted ro at `/docker-entrypoint-initdb.d/init.sql:ro`. Re-runnable after `docker compose down -v`.
- **00-08:** Redis healthcheck uses `["CMD-SHELL", "redis-cli ping | grep -q PONG"]` (not array form `["CMD", "redis-cli", "ping"]`). Two reasons: the plan's verify grep needs the literal phrase `redis-cli ping` to appear in compose; the explicit PONG match makes the healthcheck strictly fail-fast on non-PONG responses. Backend Dockerfiles use `wget -qO- http://localhost:<port>/actuator/health | grep -q '"status":"UP"'` for the runtime HEALTHCHECK (more correct than `curl -f` which only checks HTTP status, not body content — Spring Boot Actuator returns HTTP 200 even when subsystems are DOWN).
- **00-08:** Root `docker-compose.yml` is a 14-line `include:` alias for `infra/docker-compose.yml` (D-19 / Compose 2.20+). Both files declare `name: tripplanner` so containers get the same project prefix regardless of which directory `docker compose` is invoked from. README documents the `include:` portability fallback (`docker compose -f infra/docker-compose.yml up`) for older Compose installs.
- **00-08:** Eureka server's HEALTHCHECK uses `start-period=25s` (vs 20s for the other 4 backends) to absorb its self-bootstrap timing. All other healthcheck params (interval=10s, timeout=5s, retries=6) stay identical. Worst-case fail window: 85s for eureka, 80s for others — both fit within `--wait-timeout 60` once warm-cache hits.
- **00-09:** shadcn CLI deviation — used `pnpm dlx shadcn@2.x init --base-color slate --css-variables` non-interactively (arrow-down + Enter for Style=Default) instead of plan-mandated `shadcn@latest`. The latest shadcn CLI (as of 2026-05) eliminated the Tailwind v3 prompts in favor of preset-based config defaulting to Tailwind v4 + React 19 — incompatible with our locked v3 + React 18 stack (CLAUDE.md SHADCN gotcha; UI-SPEC line 34 anticipated this). shadcn@2.x still respects the v3 path AND retains the Style prompt. Outcomes match D-32 verbatim — `components.json` has `tailwind.config: tailwind.config.ts` (NOT `.js`), `cssVariables: true`, `baseColor: slate`, all aliases right. Documented in `frontend/README.md` so Phase 7 reproduces the same path. Both `shadcn@2.x` and `shadcn@latest` work for subsequent `add` commands because committed `components.json` is the source of truth.
- **00-09:** Console-silent contract automated via Vitest mocks (UI-SPEC §Copywriting Contract automated for the React render path — ROADMAP SC#4). `App.test.tsx` spies on `console.error` + `console.warn` via `vi.spyOn`, renders `<App />` inside `<StrictMode>` (which double-invokes to surface React warnings — missing keys, deprecated APIs, dangerous lifecycles), and asserts both spies have zero calls. Catches the entire React render-path silent-console contract automatically. Browser-only console errors (asset 404s, runtime CORS, fetch failures) still need DevTools manual inspection per UI-SPEC §Phase 0 Verification Checklist.
- **00-09:** Provider stack pre-wired in `src/main.tsx` (`StrictMode > QueryClientProvider > BrowserRouter > App`). Phase 7+ feature pages just add `<Routes>` inside `<App>`; no `main.tsx` changes needed. `apiClient` singleton ships with `withCredentials: true` from day one (Phase 2 cookie flow doesn't retrofit) + `X-Request-Id` stamper request-interceptor placeholder (Phase 1 trace correlation + Phase 7 Authorization header attaches in same interceptor). Zustand v5 `useAppStore` typed for `theme: 'light' | 'dark'` so Phase 9 dark-mode toggle drops in without store rewrite.
- **00-09:** Multi-stage frontend Dockerfile — `node:20-alpine` builder (`pnpm install --frozen-lockfile` + `pnpm build` with `ARG VITE_API_URL` baked into the JS bundle at build time, since Vite inlines `import.meta.env.VITE_API_URL` at compile time) → `nginx:alpine` runtime (serves `/usr/share/nginx/html` on `:5173` with `try_files` SPA fallback for React Router). HEALTHCHECK greps `<title>Trip Planner</title>` from the served bundle — catches `index.html` template regressions. Self-contained per ROADMAP SC#1 ('no manual intervention'): `docker compose up --wait` works on a fresh checkout without prior local `pnpm install` or `pnpm build`. Mirrors Plan 00-08's backend-Dockerfile pattern (which does the same thing for `./gradlew bootJar`).
- **00-09:** Rule 3 auto-fixes for `tsc -b` to compile `vite.config.ts`: added `@types/node` devDep (so `import path from 'node:path'` resolves), added `/// <reference types="vitest" />` to `vite.config.ts` (so the `test:` block typechecks against `UserConfigExport`), added `types: ['node', 'vitest']` to `tsconfig.node.json`. Plan's PATTERNS.md template referenced `'node:path'` verbatim but didn't list `@types/node` in the dep table — without it `pnpm build` fails with `Cannot find module 'node:path'`. Also gitignored `*.tsbuildinfo` + `vite.config.{d.ts,js}` (TS project-references emits) so future builds don't pollute `git status`.
- **00-09:** `tailwind.config.ts` `darkMode` normalized from shadcn@2.x init's quirky `['class', 'class']` (it appends instead of replacing when the file already has `darkMode: 'class'`) back to canonical single-string `'class'`. Both forms are functionally equivalent in Tailwind (treated as class-based dark mode); single-string form matches verify grep + UI-SPEC §Design System spec.
- **00-09:** `frontend/Dockerfile` uses `nginx:alpine` runtime serving the production bundle (NOT `node:alpine` running `pnpm dev`) — the production-bundle path supports a real HEALTHCHECK + faster boot + faithful 'production-like dev'. A future phase can swap to a Vite-dev-server runtime stage for hot-reload-in-container if needed; Phase 0 doesn't need that. Manual `docker build` runtime smoke deferred to Plan 00-10 / first user `docker compose up --wait` (docker daemon was not running on dev host) — same convention as Plan 00-08.
- **00-10:** CI scope intentionally minimal per D-15 — `backend.yml` runs only `./gradlew :services:<svc>:check` (per-service matrix); `frontend.yml` runs only `pnpm install --frozen-lockfile && pnpm lint && pnpm test --run && pnpm build`. NO OWASP Dependency-Check, NO security-tagged JUnit suite, NO integration tests with Testcontainers, NO Playwright E2E, NO Lighthouse — all deferred to Phase 10 with the header comment of each workflow file documenting what's missing. Branch protection NOT configured (manual GitHub UI follow-up; deferred to Phase 10).
- **00-10:** Skeleton CI uses `ubuntu-24.04` + `actions/setup-java@v4` (temurin 21) + `actions/setup-node@v4` (node 20) per D-16; Corepack auto-bootstraps `pnpm 9.15.0` from frontend/package.json's `packageManager` field — no manual `npm i -g pnpm`, no `pnpm/action-setup` action. Triggers per D-17: `push:` (any branch — no `branches:` filter; runs on every commit) + `pull_request:` to main. T-00-48 (unbounded CI runs) accepted in Phase 0; Phase 10 may add `concurrency: cancel-in-progress`.
- **00-10:** `dorny/paths-filter@v3` explicit YAML-block syntax with 7 named filters (5 services + libs + gradle root). Lib/Gradle changes fan out to all services via per-matrix-entry `if:` clauses combining the service's own filter output with `libs` and `gradle`. `gradle/wrapper-validation-action@v3` runs unconditionally before any matrix step (T-00-46 mitigation — verifies committed gradle-wrapper.jar sha256 against official Gradle distribution).
- **00-10:** Final Phase 0 phase-gate cleared via Task 10.3 `checkpoint:human-verify` — user signal "approved — smoke passed, all 5 SCs + NFR-04 green" after running `docker compose down -v && docker compose up -d --wait && bash scripts/smoke.sh` on a fresh checkout. Per-criterion wall-clock not captured at agent level (acceptable — `scripts/smoke.sh` asserts SC#1's «<60s» internally on warm cache, so user's aggregate "approved" signal implicitly carries the timing assertion). With this plan complete, Phase 0 declared complete (10/10 plans done); ready for Phase 1.
- **01-03:** XUserIdInjectionGlobalFilter strips X-User-Id/X-User-Email on BOTH authenticated and public branches (Pitfall 1 keystone T-01-04); injection only on authenticated branch from validated JWT UserContext.
- **01-03:** ProblemDetailAuthEntryPoint distinguishes AUTH_TOKEN_EXPIRED / AUTH_INVALID_TOKEN / AUTH_UNAUTHORIZED by inspecting ex.getCause() instanceof JwtAuthenticationException with .contains("expired").
- **01-03:** D-05 sub-1-rps token bucket formula for /api/auth/login: replenishRate=30, requestedTokens=900, burstCapacity=30 = 30 req/15 min IP-only; Phase 2 auth-service adds IP+email leg.
- **01-03:** KeyResolver bean names are exactly ipKeyResolver and userIdKeyResolver to match #{@ipKeyResolver}/#{@userIdKeyResolver} SpEL refs in application.yml (default Spring bean-name-from-method-name).
- **01-03:** NoOpServerSecurityContextRepository in WebFluxSecurityConfig keeps gateway stateless between requests (T-01-04 defense-in-depth). Gateway validates JWT once; downstream services re-validate via ServletJwtCommonFilter (T-01-01).
- **01-04:** `FilterRegistrationBean<ServletJwtCommonFilter>.getFilter()` used in `addFilterBefore` — `JwtAutoConfiguration.ServletConfig` only exposes `FilterRegistrationBean`, not a bare `ServletJwtCommonFilter` bean; T-01-11 explicit chain-ordering mitigation.
- **01-04:** Spring Boot auto-configured `ObjectMapper` injected into `ServletJwtCommonFilter` + `RestAuthenticationEntryPoint` — `ProblemDetailJacksonMixin` flattens `ProblemDetail` extension properties to JSON root (enables `$.code` jsonPath assertion in ITs). `new ObjectMapper()` nests them under `"properties"`.
- **01-04:** H2 in-memory DB added to `libs.versions.toml` as `testRuntimeOnly` in trip-service + destination-service — security ITs disable Flyway (`spring.flyway.enabled=false`) but Spring context still needs a DataSource; H2 satisfies without docker-compose.
- **01-04:** SC#4 / Pitfall 1 closed: `DirectServiceAccessWithoutGatewayReturns401IT` green in BOTH trip-service and destination-service. Direct hit on `localhost:<port>/api/trips/_ping` with forged `X-User-Id` + no Authorization returns 401 `application/problem+json` code=auth.unauthorized.
- **01-05:** `GatewayTracingObservationConfig` uses `ContextRefreshedEvent` (not `SmartInitializingSingleton`) to register `GatewayPropagatingSenderTracingObservationHandler` with `ObservationRegistry` after all beans initialize — avoids circular OTel bean-ordering issue where handler depends on Tracer which depends on ObservationRegistry during startup.
- **01-05:** `GatewayTracingTestConfig` provides explicit `ContextPropagators(W3CTraceContextPropagator)` in test context — fixes `otelContextPropagators(ObjectProvider<TextMapPropagator>)` building with an empty list and producing `NoopTextMapPropagator` due to bean ordering in the test application context.
- **01-05:** `RateLimitProblemDetailFilter` overrides both `writeWith()` and `setComplete()` in `ServerHttpResponseDecorator` — `RequestRateLimiterGatewayFilterFactory` uses the empty-body `setComplete()` path for rate-limited requests, bypassing a `writeWith()`-only override and leaving `Content-Type: null`.
- **01-05:** `spring.reactor.context-propagation: auto` required in gateway-it test profile to activate `Hooks.enableAutomaticContextPropagation()` via `ReactorAutoConfiguration` — without it, `Slf4JEventListener` cannot propagate OTel span context (traceId/spanId) across reactive thread switches, so MDC is empty in SCG log events captured by `ListAppender`.
- **01-06:** Phase 1 runtime gate cleared 2026-05-09 — clean `docker compose down -v && up -d --wait` (10 services healthy), `bash scripts/smoke.sh` exit 0 (Phase 0 SC#1-5 + NFR-04 + Phase 1 bypass/routing/rate-limit), Eureka dashboard shows 4 services, Zipkin SC#6 single trace ID spanning api-gateway + trip-service confirmed. Pitfall H (Redis depends_on) and Pitfall J (downstream service depends_on) closed in compose.
- **02-01:** Pinned greenmail 2.1.4 explicitly in `gradle/libs.versions.toml` (not Spring-managed) — latest stable 2.x as of 2026-05; consumed test-only via `greenmail-junit5` alias for Plans 02-04/02-06.
- **02-01:** `@Column(columnDefinition = "char(64)")` on token PKs (`EmailVerificationToken.token`, `RefreshToken.tokenHash`) forces Hibernate to round-trip CHAR(64) — NOT VARCHAR(64) — so `ddl-auto: validate` matches Flyway DDL byte-for-byte. Length 64 alone is insufficient (Hibernate defaults to VARCHAR for `String` fields).
- **02-01:** Hibernate `ddl-auto: validate` boot test against the live PostgreSQL container deferred to Plan 02-04/02-05 (no service-layer beans yet to wire up the application context). `./gradlew :services:auth-service:assemble` (clean) is the strongest static guarantee Plan 02-01 can ship; the runtime entity↔schema validation gate fires when `AuthServiceApplication` first boots with the full bean graph.
- **02-01:** ErrorCode catalog grew 5 → 13 entries (5 baseline from Phase 0/1 + 8 new auth.*/validation.* codes per docs/04 §6 + D-15) — append-only enum extension idiom established (`BAD_GATEWAY` semicolon → comma; new entries appended; terminal entry carries the `;` enum-list terminator). Downstream phases (3, 4, 5, 6) can grow the catalog the same way.
- **02-01:** Repository pattern locked for the auth domain — `@Repository` annotation + `JpaRepository<Entity, IdType>` + `@Lock(LockModeType.PESSIMISTIC_WRITE)` with explicit JPQL `@Query` for the row-lock primitive (`RefreshTokenRepository.findByTokenHashForUpdate`); `@Modifying` for UPDATE primitives (`EmailVerificationTokenRepository.markAllUnconsumedAsConsumedFor` — D-23 re-signup). This is the template for the trip-service / destination-service repositories in Phase 5+.
- **02-02:** JwtIssuer location RESOLVED — sibling-twin to JwtVerifier in `libs/jwt-common` (Open Q3 closed). Constructor body byte-for-byte identical to JwtVerifier lines 26-38. Same `JwtProperties`, same `Keys.hmacShaKeyFor` key construction, same fail-at-startup contract on null or `<32-byte` secrets. The maintenance unit is the pair, not the individual class.
- **02-02:** Phase 1 IN-01 closed — `@ConditionalOnMissingBean` now on BOTH `jwtVerifier` AND `jwtIssuer` beans in `JwtAutoConfiguration`. A downstream service that supplies its own `@Bean JwtVerifier` (e.g., a `@TestConfiguration`) will no longer crash with `NoUniqueBeanDefinitionException`; the auto-config backs off cooperatively.
- **02-02:** jjwt 0.13.0 modern issuance API locked — `Jwts.builder().issuer().subject().issuedAt().expiration().id().claim().signWith(SecretKey).compact()`. `signWith(SecretKey)` auto-selects HS256 from the key (no explicit `SignatureAlgorithm.HS256` parameter that could be downgraded — T-2-02-01 algorithm-confusion mitigation at the issuance boundary).
- **02-02:** Access-token TTL = `Duration.ofMinutes(15)` per docs/05 §1; claim shape: `iss=tripplanner-auth`, `sub=userId`, `iat=now`, `exp=now+15min`, `jti=UUID.randomUUID()`, `email`, `ver`. Round-trip through `JwtVerifier` confirmed in `JwtIssuerTest` (7/7 GREEN); `JwtVerifierTest` still 7/7 GREEN — no regression from the `@ConditionalOnMissingBean` addition.

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

**Last session:** 2026-05-09T15:40:43.277Z
**Stopped at:** Phase 2 UI-SPEC approved
**Resume file:** None
