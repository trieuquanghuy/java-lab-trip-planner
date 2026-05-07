# Phase 0: Monorepo Scaffolding - Context

**Gathered:** 2026-05-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 0 stands up the **entire repository skeleton** so every downstream phase can execute without re-doing infrastructure work. By end of phase, `docker compose up` reaches a healthy state in under 60 seconds with all 5 services (api-gateway, auth-service, trip-service, destination-service, eureka-server) booted, registered with Eureka, and reachable through the gateway via static-URI routing.

Phase 0 ships **infrastructure and shared scaffolding only** — no domain logic. That includes:

- Gradle Kotlin DSL multi-module build with version catalog (`gradle/libs.versions.toml`)
- 5 Spring Boot 3.5.x service skeletons that boot empty (each: `@SpringBootApplication` + Spring Boot Actuator + Eureka client + a single `/__health` placeholder controller)
- 3 shared Java libs: `libs/observability` fully wired (Pitfall 7 mandate), `libs/error-handling` with minimal stubs, `libs/api-contracts` as an empty Gradle module
- PostgreSQL 16 schema-per-service setup (auth, trip, destination) with per-service DB users (Pitfall 3) and empty Flyway V1 baseline migrations per DB-backed service
- Docker Compose stack at `infra/docker-compose.yml` with a root-level alias so `docker compose up` works from repo root
- React 18 + Vite + TypeScript + Tailwind v3.4 + shadcn/ui frontend with the full provider stack wired (Router, QueryClient, axios, Zustand) but no real pages
- Skeleton CI workflows (`.github/workflows/backend.yml` + `frontend.yml`) that build and lint the empty repo
- All 6 ROADMAP success criteria pass: compose healthy <60s, services in Eureka, gateway `/actuator/health` UP, frontend renders without console errors, all three Flyway migrations run, per-service `*_flyway_schema_history` tables present.

**In scope:**
- `services/{api-gateway,auth-service,trip-service,destination-service,eureka-server}/` skeletons that boot empty + ship `/__health` placeholder
- `libs/{observability,error-handling,api-contracts}/` per the per-lib content matrix below (`libs/jwt-common` is **owned by Phase 1**, not Phase 0)
- `infra/docker-compose.yml` + root `docker-compose.yml` alias (extends/include) + `infra/postgres/init.sql` (creates `auth`, `trip`, `destination` schemas + per-service users)
- `infra/seeds/` directory created (empty in Phase 0; Phase 3 lands `cities-15000.tsv`)
- `frontend/` Vite project with provider stack wired, single landing element, no real pages or routes beyond `/`
- `.env.example` covering every variable in `docs/08-deployment.md §1.4`
- `.github/workflows/backend.yml` (matrix-by-service `./gradlew check`) + `frontend.yml` (lint + test + build)
- `gradle/libs.versions.toml` as the **single source of truth** for every dep version (Pitfall 7 — pinned `micrometer-tracing-bom`)
- Logback JSON encoder + MDC enrichment configured globally via `libs/observability` auto-config

**Out of scope (deferred to specific phases):**
- `libs/jwt-common` (`JwtVerifier`, `ReactiveJwtAuthFilter`, `ServletJwtCommonFilter`) — owned by **Phase 1**
- Actual `/api/*` routing on the gateway — owned by **Phase 1**
- Real V2+ Flyway migrations (auth's `users`, trip's `trips`/`itinerary_*`, destination's `cities`/`destinations`) — owned by **Phase 2 / Phase 5 / Phase 3**
- Spring Security configuration on any service — owned by **Phase 1**
- Real domain controllers, services, repositories — owned by their respective phases
- Email-aware rate limiting, CORS allowlists, JWT-bearing routes — **Phase 1 / Phase 2**
- OpenTripMap / Foursquare client wiring — **Phase 4**
- Playwright E2E tests, OWASP dependency-check, security-tagged JUnit suites in CI — **Phase 10**
- shadcn component generation beyond CLI initialization — **Phase 7+**
- React Router placeholder route stubs (`/login`, `/search`, `/trips`) — **Phase 7**
- k6 load tests, distributed-trace assertions in CI — **Phase 10**

</domain>

<decisions>
## Implementation Decisions

### Boot-time surface per service
- **D-01: Each service ships a `/__health` placeholder controller in addition to Spring Boot Actuator.** The double-underscore prefix marks it as scaffold-only / internal so it cannot collide with Phase 1's `/api/<svc>/_ping` (single underscore) or any real `/api/*` route. Returns `{service: "auth-service", status: "UP", phase: 0}`. Lives in each service's main package; ~20 lines of code.
- **D-02: The api-gateway routes `/__health/<svc>` to each downstream service in Phase 0** using **static URIs** (`http://service-name:port`) — not `lb://` Eureka-routed URIs. Matches Phase 1 D-08 (static URIs). Concretely:
  - `/__health/gateway` → handled directly by gateway (its own controller)
  - `/__health/auth` → `http://auth-service:8081/__health`
  - `/__health/trip` → `http://trip-service:8082/__health`
  - `/__health/destination` → `http://destination-service:8083/__health`
  Validates end-to-end gateway routing path is alive before Phase 1 ships the real route table.
- **D-03: Each downstream service still registers with Eureka in Phase 0** even though gateway routing is static — so Eureka dashboard at `:8761` shows all 4 services per ROADMAP success criterion #2. Eureka is therefore in the *health-check / observability* path, not in the routing path. Mirrors Phase 1 D-09.

### Shared library content matrix
- **D-04: `libs/observability` is FULLY WIRED in Phase 0.** Ships:
  - `@AutoConfiguration` class registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Spring Boot 3.x convention)
  - Micrometer Tracing + OpenTelemetry → Zipkin exporter wiring (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin`)
  - Logback JSON encoder configuration (logstash-logback-encoder per `docs/02-architecture.md §6.3`) emitting `timestamp`, `level`, `service`, `traceId`, `spanId`, `userId`, `message`
  - MDC filter that copies `traceId`, `spanId`, `requestId` into MDC after request begins (`userId` is empty in Phase 0 — Phase 1's `JwtCommonFilter` populates it)
  - Default `management.tracing.sampling.probability=1.0` for `dev` and `docker` profiles
  - **Pitfall 7 hard rule:** do NOT register `ServerHttpObservationFilter` manually anywhere in Phase 0 — Spring Boot 3.2+ auto-configures it via `WebHttpHandlerBuilder`. Lint check / code review enforces this.
- **D-05: `libs/error-handling` ships minimal stubs in Phase 0.** Ships:
  - `ProblemDetailFactory` static helpers that return `org.springframework.http.ProblemDetail` instances with stable `type` URIs and `code` extension members per `docs/04-api-spec.md §6`
  - `ErrorCode` constants enum with the **2 baseline codes only** (`auth.unauthorized`, `auth.rate_limited`). Phase 1+ extends this enum as new codes arise.
  - No `GlobalExceptionHandler` or `@ControllerAdvice` in Phase 0 — those are added per service when they grow real endpoints (Phase 1+).
- **D-06: `libs/api-contracts` is an empty Gradle module in Phase 0.** Module folder + `build.gradle.kts` only; no source files. Phase 1 defines `UserContext` here (per Phase 1 D-04). This keeps the dependency-graph wired so Phase 1 can add a class without modifying `settings.gradle.kts` or any other service's `build.gradle.kts`.
- **D-07: `libs/jwt-common` is NOT created in Phase 0.** The folder does not exist after Phase 0 ships. Phase 1 creates the module, source files, and registrations. Avoids ownership ambiguity.

### Database scaffolding (Pitfall 3 enforcement)
- **D-08: Single PostgreSQL 16 instance, schema-per-service.** `infra/postgres/init.sql` runs at first container init and:
  - Creates 3 schemas: `auth`, `trip`, `destination`
  - Creates 3 service users with passwords from env: `auth_svc`, `trip_svc`, `destination_svc`
  - Grants each user `USAGE, CREATE` on their own schema only — cross-schema access is denied at the DB level
  - Sets each user's `search_path` to their own schema by default
- **D-09: Per-service Flyway history table names** (Pitfall 3 mandatory). In each DB-backed service's `application.yml`:
  ```yaml
  spring:
    flyway:
      schemas: <service>
      default-schema: <service>
      table: <service>_flyway_schema_history
  ```
  Verified by ROADMAP success criterion #5 (per-service tables present after migration).
- **D-10: V1 migrations are intentional empty placeholders per service.** Each DB-backed service ships `src/main/resources/db/migration/V1__init.sql` containing only:
  ```sql
  -- Intentional empty baseline. Real schema lands in:
  --   auth-service:        Phase 2 (V2__users.sql, V3__email_verifications.sql, ...)
  --   trip-service:        Phase 5 (V2__trips.sql, V3__itinerary_days.sql, ...)
  --   destination-service: Phase 3 (V2__cities.sql, V3__destinations.sql, ...)
  SELECT 1;
  ```
  Validates Pitfall 3's per-service-history-table mechanism with no schema commitment. Each domain phase owns its real V2+.
- **D-11: api-gateway and eureka-server have NO Flyway dependency, NO datasource.** Gateway is stateless (Redis comes online in Phase 1 for rate limiting). Eureka is a registry, not a data store.

### Frontend scaffolding
- **D-12: Frontend Phase 0 ships the full provider stack with no real pages.** `frontend/` is a standalone pnpm project (NOT a workspace member of the Gradle multi-module build). Concretely:
  - Vite 5 or 6 + React 18.3.x + TypeScript 5.8.x
  - Tailwind v3.4.x (NOT v4 — locked by `CLAUDE.md`) + `tailwind.config.js` + base `index.css`
  - shadcn/ui CLI initialized (creates `components.json` + `lib/utils.ts`); **no components generated yet** — Phase 7 adds them
  - `BrowserRouter` from `react-router-dom` v6.30.x wrapping the app (NOT v7 — keep locked spec)
  - `QueryClientProvider` (TanStack Query v5.100.x) with default `QueryClient` config
  - `axios` v1.16.0 instance (CVE-fixed) constructed once, base URL from `import.meta.env.VITE_API_URL`
  - Zustand v5.0.x store skeleton (single `useAppStore` with placeholder shape for theme + auth state — empty for Phase 0)
  - `App.tsx` renders a single landing element (heading "Trip Planner" + subtitle) — no `/login`, `/search`, `/trips` routes
- **D-13: Frontend uses pnpm 9.x** with `pnpm-lock.yaml` committed. `frontend/package.json` has `"packageManager": "pnpm@9.x.x"` to pin via Corepack. Node 20 LTS in CI.
- **D-14: Vitest 3.x + React Testing Library 16.x are installed in Phase 0** with one trivial smoke test (`App.test.tsx` rendering "Trip Planner") so `pnpm test` passes in CI. Playwright is NOT installed in Phase 0 — Phase 10 adds it.

### CI / GitHub Actions
- **D-15: Phase 0 ships skeleton CI only.** Two workflow files:
  - `.github/workflows/backend.yml` — `dorny/paths-filter` change-detection, matrix-per-service running `./gradlew :services:<svc>:check`. Skip OWASP dependency-check, security-tag tests, integration-test job — those need real source.
  - `.github/workflows/frontend.yml` — `pnpm install` + `pnpm lint` + `pnpm test --run` + `pnpm build`. Skip Playwright E2E job — no real flows yet.
- **D-16: CI uses ubuntu-24.04 + actions/setup-java@v4 (java 21 temurin) + actions/setup-node@v4 (node 20)** per `CLAUDE.md` advisory.
- **D-17: All workflow files trigger on `push` to any branch and `pull_request` to `main`.** No path-based gating beyond the change-detection matrix; greenfield repo doesn't need it.

### Docker Compose layout
- **D-18: `infra/docker-compose.yml` is the canonical compose file** per `docs/02-architecture.md §5` and `docs/08-deployment.md §2`. It contains every service: postgres:16, redis:7-alpine, mailhog:latest, openzipkin/zipkin:3, eureka-server, api-gateway, auth-service, trip-service, destination-service, frontend.
- **D-19: A root-level `docker-compose.yml` exists as a thin alias** that uses Compose's `include:` directive (Compose 2.20+ feature) to pull in `./infra/docker-compose.yml`. This lets `docker compose up` work from the repo root without flags. README documents both forms.
- **D-20: Healthchecks + service_healthy ordering (Pitfall 10).** Every service has a `healthcheck:` block, and every dependent uses `depends_on: { <dep>: { condition: service_healthy } }`:
  - postgres: `pg_isready -U postgres`
  - redis: `redis-cli ping`
  - eureka-server: `curl -f http://localhost:8761/actuator/health`
  - 4 backend services: `curl -f http://localhost:<port>/actuator/health` (Eureka gates them; gateway also gates on backends being up isn't required since gateway uses static URIs and routing tolerates downstream warm-up)
- **D-21: Eureka client tuning for dev fast-warmup (Pitfall 10).** All 4 services include in `application-docker.yml`:
  ```yaml
  eureka:
    client:
      registry-fetch-interval-seconds: 5
    instance:
      lease-renewal-interval-in-seconds: 5
      lease-expiration-duration-in-seconds: 10
  ```
- **D-22: Compose port exposure binds downstream services to `127.0.0.1` only** (e.g. `127.0.0.1:8081:8081`) to neuter Pitfall 1 partially in dev (gateway is the only public surface). Gateway, frontend, mailhog UI, zipkin UI bind to `0.0.0.0`. Postgres binds `127.0.0.1:5432`. Eureka dashboard binds `127.0.0.1:8761`.

### Profiles + configuration
- **D-23: Three Spring profiles per service: `dev`, `docker`, `test`.** No `prod` in v1.
  - `dev` — local IDE; targets `localhost:5432` Postgres, `localhost:6379` Redis, `localhost:8761` Eureka, `localhost:9411` Zipkin.
  - `docker` — compose; targets `postgres:5432`, `redis:6379`, `eureka-server:8761/eureka`, `zipkin:9411`.
  - `test` — for `@SpringBootTest`; uses Testcontainers via `@ServiceConnection` (Spring Boot 3.1+ zero-config wiring) — Phase 1+ exercises this.
- **D-24: All sensitive values come from environment variables.** `.env.example` is committed; `.env` is gitignored. No secrets in `application-*.yml`. Variables match `docs/08-deployment.md §1.4` exactly. `AUTH_JWT_SECRET` ships as `dev-only-32-byte-secret-replace-in-prod` placeholder.
- **D-25: `spring.application.name` is set in every service's base `application.yml`** (Pitfall 7 — Zipkin trace service name derives from this; missing it groups all spans under default name).

### Gradle build conventions
- **D-26: `gradle/libs.versions.toml` is the SINGLE source of truth for every dependency version.** Individual service `build.gradle.kts` files reference via type-safe accessors (`libs.spring.boot.starter.web`, etc.). No version literals outside the catalog. **Pitfall 7 hard rule:** `micrometer-tracing-bom` is pinned in this catalog and not overridable per-service.
- **D-27: Gradle 8.14.x (NOT 9.x).** Spring Boot 3.5 toolchain is officially Gradle 8.x. Wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/`).
- **D-28: Subproject naming follows `:services:<service-name>` and `:libs:<lib-name>`** in `settings.gradle.kts`. Each service's package root is `com.tripplanner.<service>` (e.g. `com.tripplanner.gateway`, `com.tripplanner.auth`, `com.tripplanner.trip`, `com.tripplanner.destination`, `com.tripplanner.eureka`). Shared libs use `com.tripplanner.observability`, `com.tripplanner.errors`, `com.tripplanner.contracts`.
- **D-29: Each service has its own `build.gradle.kts`** declaring its dependencies. The root `build.gradle.kts` defines plugin versions (Spring Boot, Spring Dependency Management, Kotlin DSL conventions) and a `subprojects { ... }` block for common Java toolchain (21), JUnit Platform, and Jacoco config (Phase 10 finalizes coverage thresholds; Phase 0 just wires Jacoco).

### Cross-cutting (Pitfall enforcement summary)
- **Pitfall 3** (Flyway shared history collision): D-08, D-09, D-10 prevent it. Verified by ROADMAP success criterion #5.
- **Pitfall 7** (trace context lost across gateway boundary + ServerHttpObservationFilter):
  - Single `micrometer-tracing-bom` pin (D-26)
  - `libs/observability` shared auto-config (D-04)
  - `spring.application.name` set per service (D-25)
  - `management.tracing.sampling.probability=1.0` in dev/docker profiles (D-04)
  - No manual `ServerHttpObservationFilter` (D-04 hard rule)
  - **Validation in Phase 1** (per Pitfall 7 step 4): one request through gateway produces a single trace ID across both spans in Zipkin.
- **Pitfall 10** (Eureka registration lag → gateway 503 cold-start):
  - Healthchecks + `depends_on: condition: service_healthy` (D-20)
  - Eureka tuning `registry-fetch-interval-seconds: 5`, `lease-renewal-interval-in-seconds: 5` (D-21)
  - Pitfall 10 step 4: README documents the 30–60s warm-up — but D-20 + D-21 should make this <60s per success criterion #1.

### Claude's Discretion
- Exact filename for the gateway's `/__health/<svc>` route configuration (`application.yml` route table vs `@Configuration` Java DSL — Spring Cloud Gateway supports both; pick whichever reads cleanest given Phase 1 will extend it).
- Whether `frontend/.eslintrc` uses `eslint-config-prettier` + `eslint-plugin-react-hooks` or a single shared config like `@vercel/style-guide`. Default to the standard `eslint-plugin-react` + `eslint-plugin-react-hooks` + `@typescript-eslint` triad unless researcher finds a better 2026 baseline.
- Whether `infra/postgres/init.sql` uses `CREATE USER ... WITH PASSWORD` literals (sourced from env at compose-up time via templating) or relies entirely on `POSTGRES_INITDB_ARGS` and a startup script that reads env. Either works — pick the one that survives `docker compose down -v` cleanly.
- Vite version pin: 5.x or 6.x. Both are valid per `CLAUDE.md`. If picking 6.x, Vitest must be 3.x or 4.x. Match Vite major to Vitest major.
- React Router v6.30.x vs v7.x. `CLAUDE.md` says v6 is the locked spec but advises v7 for greenfield. Default to **v6.30.x** to honor the locked spec; flag v7 as a Phase 7-time consideration.
- shadcn/ui CLI registry — the v3-compatible legacy path (since Tailwind v3 is locked). Researcher confirms which `npx shadcn` invocation generates v3-class output (`@latest` defaults to v4; may need explicit `@2.x` or registry flag).
- Logback JSON encoder choice: `logstash-logback-encoder` per `docs/02-architecture.md §6.3` is recommended; if a simpler 2026-current option exists (e.g., `slf4j-event-logger`), researcher may swap it as long as MDC fields propagate.
- Whether the root-level `docker-compose.yml` uses Compose's `include:` directive or `extends:`. `include:` (Compose 2.20+) is cleaner. Verify Docker Compose v2 default install on macOS Docker Desktop 4.x supports `include:` — if not, fall back to `extends:`.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Stack and version pins (read FIRST)
- `CLAUDE.md` — locked tech stack with 2026 pin overrides (Spring Boot 3.5.x, Spring Cloud 2024.0.x, Flyway 10 + flyway-database-postgresql, jjwt 0.13.0, React 18.3.x, Tailwind v3.4.x, react-leaflet v4.2.x, Axios 1.16.0, dnd-kit/core 6.x). Every version in `gradle/libs.versions.toml` and `frontend/package.json` MUST match this file.

### Architecture and layout
- `docs/02-architecture.md §3` — service decomposition (gateway :8080, services :8081/8082/8083, eureka :8761)
- `docs/02-architecture.md §3.1` — service responsibilities (which service owns what)
- `docs/02-architecture.md §5` — repo layout (definitive folder structure for `services/`, `libs/`, `infra/`, `frontend/`)
- `docs/02-architecture.md §6.1` — configuration approach (per-service YAML, env vars for secrets, no Spring Cloud Config in v1)
- `docs/02-architecture.md §6.3` — observability stack (Logback JSON, Micrometer + OpenTelemetry → Zipkin)
- `docs/02-architecture.md §7` — deployment topology (v1 = local docker compose only)

### Deployment and infrastructure
- `docs/08-deployment.md §1.3` — service ports (locked: 5173, 8080, 8081, 8082, 8083, 8761, 5432, 6379, 1025, 8025, 9411)
- `docs/08-deployment.md §1.4` — `.env.example` variables (Phase 0 ships this exact list)
- `docs/08-deployment.md §2` — Docker Compose layout (services, healthchecks, depends_on, env injection)
- `docs/08-deployment.md §4.1` — backend GitHub Actions workflow (skeleton in Phase 0; Phase 10 fills security + dependency-check)
- `docs/08-deployment.md §4.2` — frontend GitHub Actions workflow (skeleton in Phase 0; Phase 10 adds Playwright E2E)
- `docs/08-deployment.md §5` — observability stack (Logback config, Prometheus endpoint, Zipkin URL, health endpoints)

### Pitfall enforcement (Phase 0 owners)
- `.planning/research/PITFALLS.md Pitfall 3` — Flyway per-service `flyway_schema_history` table names + per-service DB users (D-09 + D-10)
- `.planning/research/PITFALLS.md Pitfall 7` — single `micrometer-tracing-bom` pin, `spring.application.name` set per service, no manual `ServerHttpObservationFilter`, sampling probability 1.0 in dev (D-04 + D-25 + D-26)
- `.planning/research/PITFALLS.md Pitfall 10` — Eureka registration lag mitigation via healthchecks + `service_healthy` + 5s fetch/renew intervals (D-20 + D-21)

### Roadmap and requirements
- `.planning/ROADMAP.md` Phase 0 — full success criteria #1–#5, "Notes" section (Spring Boot 3.5.x, Flyway PostgreSQL dep, per-service history tables, Micrometer BOM, no ServerHttpObservationFilter, Eureka tuning, depends_on health)
- `.planning/REQUIREMENTS.md` NFR-04 — free-tier-only external services (Phase 0 must NOT introduce paid deps)

### Stack research (advisory; CLAUDE.md is canonical)
- `.planning/research/STACK.md` — version pin rationale, gotchas (Flyway 10 modularization, Axios CVE, react-leaflet v5 React-19 requirement, Tailwind v4 incompatibility)
- `.planning/research/ARCHITECTURE.md` — supplementary architecture notes; defer to `docs/02-architecture.md` if any conflict
- `.planning/research/SUMMARY.md` — research synthesis

### Cross-phase handoff
- `.planning/phases/01-api-gateway/01-CONTEXT.md` — Phase 1 immediately consumes Phase 0 outputs:
  - `libs/observability` (D-04 here, D-17/D-19 there) for MDC + trace continuity
  - `libs/error-handling` (D-05 here, D-07 there) for ProblemDetail factory
  - `libs/api-contracts` (D-06 here, D-04 there) for `UserContext` record (Phase 1 defines it)
  - Scaffold-level decisions on profiles, env, ports, compose layout (D-23, D-18, D-22) for routing config
  - Static-URI routing pattern (D-02 here, D-08 there)

### Forward-reference (informational; Phase 0 does NOT implement)
- `docs/03-data-model.md` — read by Phase 2 (auth schema), Phase 3 (destination schema), Phase 5 (trip schema). Phase 0's empty V1 migrations leave room for these.
- `docs/04-api-spec.md §6` — error code catalog. Phase 0's `libs/error-handling` ships only the 2 baseline codes; Phase 1+ extends.
- `docs/05-auth-security.md §10` — 8 mandatory security tests (Phase 1 owns 4, Phase 2 owns the rest). Phase 0 does NOT implement these.

</canonical_refs>

<code_context>
## Existing Code Insights

**This is a greenfield repository.** No source files exist yet. Phase 0 produces every reusable asset for the rest of the project.

### Existing artifacts (not source code)
- `docs/` — 11 SDLC documents (PRD, architecture, data model, API spec, auth/security, frontend design, test strategy, deployment, roadmap, risks)
- `.planning/` — GSD planning artifacts (PROJECT.md, REQUIREMENTS.md, ROADMAP.md, STATE.md, research/)
- `CLAUDE.md` — locked tech stack and project conventions
- `.gitignore` — minimal (existing)
- `.obsidian/` — IDE workspace folder, ignore for planning purposes
- `Trip Planner Feature List.pdf` — original feature list, superseded by `docs/01-prd.md`

### Reusable assets created BY Phase 0 (and consumed by every later phase)
- `gradle/libs.versions.toml` — version catalog. Every service / lib `build.gradle.kts` references this.
- `gradle/wrapper/gradle-wrapper.properties` — pinned Gradle 8.14.x
- `libs/observability` — Spring Boot auto-config consumed by all 5 services (gateway, auth, trip, destination, eureka — though eureka may not need MDC/tracing).
- `libs/error-handling` — `ProblemDetailFactory` + `ErrorCode` enum consumed by Phase 1 (gateway error WebFilter) + every later phase that returns RFC 7807 problem responses.
- `libs/api-contracts` — empty module that Phase 1's `UserContext` record will live in.
- `infra/postgres/init.sql` — DDL for schemas + per-service users. Re-runs on `docker compose down -v` + fresh up.
- `infra/docker-compose.yml` — canonical compose file. Every later phase adds environment variables to it as needed.
- Gateway route table (`/__health/<svc>` static-URI entries) — Phase 1 extends with the real `/api/<svc>/**` route table.

### Patterns this phase establishes (every later phase MUST follow)
- **Per-service Spring profiles**: `dev` (local), `docker` (compose), `test` (Testcontainers). No `prod`.
- **Per-service Flyway history table**: `<service>_flyway_schema_history`.
- **Per-service DB user with schema-scoped grants.**
- **Logback JSON encoder + MDC enrichment** via `libs/observability`.
- **Single source of truth for versions** in `gradle/libs.versions.toml`.
- **Static-URI gateway routing** (Phase 1 extends with auth-bearing routes).
- **Healthcheck-gated `depends_on`** on every dependent service.
- **`/__health` for Phase 0 scaffolding probes** vs `/api/<svc>/_ping` for Phase 1+ application probes — never collide.

### Integration points (Phase 1 immediately uses)
- Phase 1 adds `libs/jwt-common` as a sibling of `libs/observability` and references its types from `services/api-gateway`, `services/trip-service`, `services/destination-service`.
- Phase 1 extends the gateway's `application.yml` route table by adding `/api/auth/**`, `/api/search/**`, `/api/destinations*/**`, `/api/trips/**` entries (alongside the `/__health/<svc>` entries already shipped by Phase 0). Phase 1 does NOT remove the `/__health/<svc>` routes — they stay as ops debug endpoints.
- Phase 1 wires Spring Security WebFlux config into the gateway and Servlet config into trip + destination services. Phase 0 ships these services with NO Spring Security config (they accept all requests on their own port — bound to `127.0.0.1` only per D-22).

</code_context>

<specifics>
## Specific Ideas

- The gateway's Phase 0 `/__health/<svc>` route entries should sit at the TOP of the route table so Phase 1 can simply append `/api/*` entries after them without reordering. Document the convention in a header comment in `application.yml`.
- `infra/postgres/init.sql` should be idempotent: use `CREATE SCHEMA IF NOT EXISTS` and `CREATE USER ... IF NOT EXISTS`-style guards so re-running compose without a volume nuke doesn't fail.
- `frontend/src/lib/queryClient.ts` should construct the `QueryClient` with sane defaults (`staleTime: 30s`, `refetchOnWindowFocus: false`) — Phase 7 may tune these but locking the file location now means Phase 7 only edits one file.
- `frontend/src/lib/axios.ts` should expose a singleton `apiClient` instance with `baseURL: import.meta.env.VITE_API_URL` and a placeholder request interceptor (no-op in Phase 0). Phase 2's auth flow attaches the JWT bearer token here.
- The root `docker-compose.yml` alias should include a comment explaining why two compose files exist (one `include:` line + comment) so future contributors don't delete the alias thinking it's redundant.
- `.env.example` includes a "DO NOT COMMIT .env" comment header.
- The `gradle/libs.versions.toml` file should group dependencies under sections (`[versions]` then `[libraries]` then `[plugins]`) per Gradle version-catalog convention. Individual service `build.gradle.kts` files use only the catalog accessors, never raw coordinates.

</specifics>

<deferred>
## Deferred Ideas

- **`libs/jwt-common`** — Phase 1 owns creation, source files, and registration. Phase 0 leaves the directory absent.
- **Real V2+ Flyway migrations** — Phase 2 (`auth.users`, `auth.email_verifications`, `auth.refresh_tokens`), Phase 3 (`destination.cities`, `destination.destinations`), Phase 5 (`trip.trips`, `trip.itinerary_days`, `trip.itinerary_items`, `trip.favorites`).
- **OWASP dependency-check job + security-tagged JUnit suite + Playwright E2E in CI** — Phase 10 hardening adds these.
- **Image build + push to GHCR + Fly.io deploy workflow (`release.yml`)** — v2 backlog per `docs/08-deployment.md §4.3`.
- **shadcn/ui component generation (`Button`, `Card`, `Input`, etc.)** — Phase 7 generates them when first used in real pages.
- **React Router placeholder routes for `/login`, `/search`, `/trips`, `/favorites`** — Phase 7 introduces them with real handlers.
- **AppLayout (header/nav/footer) + theme toggle UI** — Phase 7 / Phase 9.
- **JWT secret rotation, CORS allowlist, rate limiter Redis keys** — Phase 1.
- **`lb://` Eureka-routed traffic at the gateway** — parked for Phase 10 hardening per Phase 1's deferred ideas.
- **Connection pool tuning beyond Spring Boot defaults** — Phase 10 perf tuning.
- **`prod` Spring profile** — out of scope for v1 (local-only deployment per project constraints).
- **Postgres extensions (`pg_trgm`, `unaccent`)** — Phase 3 adds them via repeatable Flyway migrations when destination search needs them.

</deferred>

---

*Phase: 0-Monorepo Scaffolding*
*Context gathered: 2026-05-08*
