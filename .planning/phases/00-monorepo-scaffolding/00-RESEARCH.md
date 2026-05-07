# Phase 0: Monorepo Scaffolding - Research

**Researched:** 2026-05-08
**Domain:** Multi-module Gradle Kotlin DSL + Spring Boot 3.5 microservices + Vite/React 18 monorepo bootstrap
**Confidence:** HIGH (every locked version cross-verified against official Spring/Flyway/Spring-Cloud release notes during this session)

## Summary

Phase 0 stands up the entire monorepo skeleton: 5 Spring Boot 3.5.x services (api-gateway, auth-service, trip-service, destination-service, eureka-server), 3 shared Java libs (observability fully wired, error-handling stubs, api-contracts empty), a single PostgreSQL 16 instance with `auth`/`trip`/`destination` schemas owned by per-service DB users, Redis 7, MailHog, Zipkin 3, and a React 18.3 + Vite + Tailwind v3.4 + shadcn/ui frontend with provider stack pre-wired. The success bar is `docker compose up` reaching healthy state in under 60 seconds, with all 4 backend services (excluding Eureka) registered in Eureka, gateway routing `/__health/<svc>` end-to-end via static URIs, and per-service `<service>_flyway_schema_history` tables present after V1 baseline migrations run.

This phase contains zero domain logic — its job is to lock conventions every later phase depends on (per-service Flyway history naming, single `micrometer-tracing-bom` pin, healthcheck-gated `depends_on`, static-URI gateway routing, Logback JSON + MDC, schema-per-service DB user grants). Done correctly, Phase 0 prevents the most expensive class of error: scaffolding bugs that surface as cross-cutting failures in later phases (Pitfall 3 Flyway collision, Pitfall 7 trace-context loss, Pitfall 10 Eureka cold-start 503).

**Primary recommendation:** Use the verified version pins in §"Standard Stack" verbatim. Treat Spring Boot 3.5 + Spring Cloud **2025.0.x** (NOT 2024.0.x — see ⚠ below) + Flyway 10 with explicit `flyway-database-postgresql` + Logback `logstash-logback-encoder` + Vite 5 + Vitest 3 + React 18.3 as a single matched set. Generate shadcn via `pnpm dlx shadcn@2.x init` to lock the Tailwind v3 path. Bind every downstream service port to `127.0.0.1` and use `depends_on: condition: service_healthy` everywhere.

⚠ **CRITICAL VERSION CORRECTION (verified 2026-05-08):** `CLAUDE.md` says "Spring Cloud 2024.0.x matches SB 3.5". This is wrong per the official Spring Cloud supported-versions wiki and the Spring blog: Spring Cloud **2024.0 (Moorgate)** matches Spring Boot **3.4.x**; Spring Cloud **2025.0 (Northfields)** matches Spring Boot **3.5.x**. Spring Cloud 2025.0.2 (April 2026) is built on Spring Boot 3.5.13. **Phase 0 must use Spring Cloud 2025.0.x, not 2024.0.x.** This is an [ASSUMED]→[VERIFIED] correction the planner must apply.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Boot-time surface per service**
- **D-01:** Each service ships a `/__health` placeholder controller in addition to Spring Boot Actuator. Double-underscore prefix (`/__health`) marks it as scaffold-only / internal so it cannot collide with Phase 1's `/api/<svc>/_ping` (single underscore) or any real `/api/*` route. Returns `{service: "auth-service", status: "UP", phase: 0}`. Lives in each service's main package; ~20 lines of code.
- **D-02:** The api-gateway routes `/__health/<svc>` to each downstream service in Phase 0 using **static URIs** (`http://service-name:port`) — not `lb://` Eureka-routed URIs. Matches Phase 1 D-08. Concretely:
  - `/__health/gateway` → handled directly by gateway (its own controller)
  - `/__health/auth` → `http://auth-service:8081/__health`
  - `/__health/trip` → `http://trip-service:8082/__health`
  - `/__health/destination` → `http://destination-service:8083/__health`
- **D-03:** Each downstream service still registers with Eureka in Phase 0 even though gateway routing is static — so Eureka dashboard at `:8761` shows all 4 services per ROADMAP success criterion #2.

**Shared library content matrix**
- **D-04:** `libs/observability` is FULLY WIRED in Phase 0. Ships `@AutoConfiguration` registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, Micrometer Tracing + OpenTelemetry → Zipkin exporter wiring, Logback JSON encoder (`logstash-logback-encoder`) emitting `timestamp/level/service/traceId/spanId/userId/message`, MDC filter copying `traceId/spanId/requestId` into MDC, `management.tracing.sampling.probability=1.0` for `dev`/`docker` profiles. **Pitfall 7 hard rule:** do NOT register `ServerHttpObservationFilter` manually anywhere.
- **D-05:** `libs/error-handling` ships minimal stubs: `ProblemDetailFactory` static helpers returning `org.springframework.http.ProblemDetail` instances, `ErrorCode` enum with **2 baseline codes only** (`auth.unauthorized`, `auth.rate_limited`). No `GlobalExceptionHandler`/`@ControllerAdvice` in Phase 0.
- **D-06:** `libs/api-contracts` is an empty Gradle module — folder + `build.gradle.kts` only, no source files.
- **D-07:** `libs/jwt-common` is NOT created in Phase 0; folder does not exist after Phase 0 ships.

**Database scaffolding**
- **D-08:** Single PostgreSQL 16 instance, schema-per-service. `infra/postgres/init.sql` creates 3 schemas (`auth/trip/destination`), 3 users (`auth_svc/trip_svc/destination_svc`) with passwords from env, grants each user `USAGE, CREATE` on their own schema only, sets each user's `search_path`.
- **D-09:** Per-service Flyway history tables (Pitfall 3 mandatory): `spring.flyway.schemas: <service>`, `default-schema: <service>`, `table: <service>_flyway_schema_history`.
- **D-10:** V1 migrations are intentional empty placeholders per service: `src/main/resources/db/migration/V1__init.sql` containing only a `SELECT 1;` stub.
- **D-11:** api-gateway and eureka-server have NO Flyway dependency, NO datasource.

**Frontend scaffolding**
- **D-12:** Frontend is a standalone pnpm project (NOT a Gradle workspace member). Vite 5 or 6 + React 18.3.x + TypeScript 5.8.x + Tailwind v3.4.x + shadcn CLI initialized (no components generated) + React Router 6.30.x + TanStack Query 5.100.x + axios 1.16.0 + Zustand 5.0.x + skeleton landing page only.
- **D-13:** pnpm 9.x with `pnpm-lock.yaml` committed; `"packageManager": "pnpm@9.x.x"` in package.json.
- **D-14:** Vitest 3.x + RTL 16.x in Phase 0 with one trivial smoke test. Playwright NOT installed.

**CI / GitHub Actions**
- **D-15:** Skeleton CI only: `backend.yml` (paths-filter + matrix `./gradlew :services:<svc>:check`) + `frontend.yml` (`pnpm install`/`lint`/`test`/`build`). No OWASP/security/E2E jobs.
- **D-16:** ubuntu-24.04 runner + actions/setup-java@v4 (java 21 temurin) + actions/setup-node@v4 (node 20).
- **D-17:** Trigger on push (any branch) + pull_request to main.

**Docker Compose layout**
- **D-18:** `infra/docker-compose.yml` is canonical with all services: postgres:16, redis:7-alpine, mailhog:latest, openzipkin/zipkin:3, eureka-server, api-gateway, auth-service, trip-service, destination-service, frontend.
- **D-19:** Root-level `docker-compose.yml` is a thin alias using Compose's `include:` directive (Compose 2.20+).
- **D-20:** Healthchecks on every service + `depends_on: condition: service_healthy`. postgres → `pg_isready -U postgres`; redis → `redis-cli ping`; eureka → `curl -f http://localhost:8761/actuator/health`; backend services → `curl -f http://localhost:<port>/actuator/health`.
- **D-21:** Eureka client tuning in `application-docker.yml`: `registry-fetch-interval-seconds: 5`, `lease-renewal-interval-in-seconds: 5`, `lease-expiration-duration-in-seconds: 10`.
- **D-22:** Compose port exposure binds downstream services to `127.0.0.1` only (e.g. `127.0.0.1:8081:8081`). Gateway, frontend, mailhog UI, zipkin UI bind to `0.0.0.0`. Postgres + Eureka dashboard bind `127.0.0.1`.

**Profiles + configuration**
- **D-23:** Three Spring profiles per service: `dev` (local IDE), `docker` (compose), `test` (Testcontainers via `@ServiceConnection`). No `prod` in v1.
- **D-24:** All sensitive values come from environment variables. `.env.example` committed; `.env` gitignored. `AUTH_JWT_SECRET=dev-only-32-byte-secret-replace-in-prod` placeholder.
- **D-25:** `spring.application.name` is set in every service's base `application.yml` (Pitfall 7).

**Gradle build conventions**
- **D-26:** `gradle/libs.versions.toml` is the SINGLE source of truth for every dep version. **Pitfall 7 hard rule:** `micrometer-tracing-bom` is pinned in this catalog and not overridable per-service.
- **D-27:** Gradle 8.14.x (NOT 9.x). Wrapper committed.
- **D-28:** Subproject naming `:services:<service-name>` and `:libs:<lib-name>`. Package roots: `com.tripplanner.{gateway,auth,trip,destination,eureka,observability,errors,contracts}`.
- **D-29:** Each service has its own `build.gradle.kts`. Root `build.gradle.kts` defines plugin versions + `subprojects { ... }` block for common Java toolchain (21), JUnit Platform, Jacoco.

### Claude's Discretion

- Exact filename for the gateway's `/__health/<svc>` route configuration (`application.yml` route table vs `@Configuration` Java DSL — pick whichever reads cleanest given Phase 1 will extend it).
- Whether `frontend/.eslintrc` uses `eslint-config-prettier` + `eslint-plugin-react-hooks` or a single shared config like `@vercel/style-guide`. Default to standard `eslint-plugin-react` + `eslint-plugin-react-hooks` + `@typescript-eslint` triad unless researcher finds a better 2026 baseline.
- Whether `infra/postgres/init.sql` uses `CREATE USER ... WITH PASSWORD` literals (sourced from env at compose-up time via templating) or relies entirely on `POSTGRES_INITDB_ARGS` and a startup script that reads env. Either works — pick the one that survives `docker compose down -v` cleanly.
- Vite version pin: 5.x or 6.x. Both valid per CLAUDE.md. If picking 6.x, Vitest must be 3.x or 4.x. Match Vite major to Vitest major.
- React Router v6.30.x vs v7.x. CLAUDE.md says v6 is the locked spec but advises v7 for greenfield. **Default to v6.30.x to honor locked spec; flag v7 as a Phase 7-time consideration.**
- shadcn/ui CLI registry — the v3-compatible legacy path. Researcher confirms which `npx shadcn` invocation generates v3-class output (`@latest` defaults to v4; may need explicit `@2.x` or registry flag).
- Logback JSON encoder choice: `logstash-logback-encoder` per `docs/02-architecture.md §6.3` is recommended; if a simpler 2026-current option exists (e.g., `slf4j-event-logger`), researcher may swap it as long as MDC fields propagate.
- Whether the root-level `docker-compose.yml` uses Compose's `include:` directive or `extends:`. `include:` (Compose 2.20+) is cleaner. Verify Docker Compose v2 default install on macOS Docker Desktop 4.x supports `include:` — if not, fall back to `extends:`.

### Deferred Ideas (OUT OF SCOPE)

- **`libs/jwt-common`** — Phase 1 owns creation, source files, and registration.
- **Real V2+ Flyway migrations** — Phase 2 (`auth.users`, `auth.email_verifications`, `auth.refresh_tokens`), Phase 3 (`destination.cities`, `destination.destinations`), Phase 5 (`trip.trips`, `trip.itinerary_days`, `trip.itinerary_items`, `trip.favorites`).
- **OWASP dependency-check job + security-tagged JUnit suite + Playwright E2E in CI** — Phase 10.
- **Image build + push to GHCR + Fly.io deploy workflow (`release.yml`)** — v2 backlog.
- **shadcn/ui component generation** (`Button`, `Card`, `Input`, etc.) — Phase 7.
- **React Router placeholder routes** for `/login`, `/search`, `/trips`, `/favorites` — Phase 7.
- **AppLayout (header/nav/footer) + theme toggle UI** — Phase 7 / Phase 9.
- **JWT secret rotation, CORS allowlist, rate limiter Redis keys** — Phase 1.
- **`lb://` Eureka-routed traffic at the gateway** — Phase 10.
- **Connection pool tuning beyond Spring Boot defaults** — Phase 10.
- **`prod` Spring profile** — out of scope for v1.
- **Postgres extensions (`pg_trgm`, `unaccent`)** — Phase 3.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| NFR-04 | All v1 external services operate on free tiers; no credit-card-required signups required to run the app locally. | Research confirms every dep in §Standard Stack ships under permissive open-source licenses (Apache 2.0 / MIT / EPL) with no paid signups: Spring Boot, Spring Cloud, Postgres 16, Redis 7 (RSALv2/SSPLv1/AGPLv3 — all OSI-eligible), MailHog, Zipkin OSS, all frontend libs. The compose stack uses MailHog (no SMTP signup) and skips OpenTripMap/Foursquare keys (Phase 0 doesn't call providers). NFR-04 is satisfied by **not introducing** any paid dependencies — research's job here is to flag any temptation to do so. None found. |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

The following directives in `./CLAUDE.md` MUST be honored by the plan. Treat as locked alongside CONTEXT.md decisions:

1. **Tech stack locked.** Java 21 + Spring Boot 3.3.x (corrected by CLAUDE.md to 3.5.x) + Gradle Kotlin DSL multi-module + PostgreSQL 16 + Redis 7 + React 18 + Vite + TypeScript + Tailwind + shadcn/ui. No language/framework substitutions.
2. **Architecture locked.** Monorepo with 5 services + 4 shared libs + frontend. (Note: Phase 0 ships only **3** of 4 libs per D-07; `libs/jwt-common` is Phase 1's.)
3. **Single Postgres, schema-per-service** with per-service DB users (matches D-08).
4. **Cost: free tier only.** No paid external APIs in v1 (matches NFR-04).
5. **Auth: JWT with email verification.** HS256 access (15 min) + refresh-token rotation (7 days httpOnly cookie); bcrypt cost 12. *(Phase 0 does not implement; Phase 2 owns.)*
6. **Local-only deployment in v1.** `docker compose up` is the ship target.
7. **Test discipline:** ≥70% backend service-layer line coverage; 100% on auth + ownership-check paths; 8 mandatory security integration tests gate every PR. *(Phase 0 wires Jacoco; thresholds finalized in Phase 10.)*
8. **GSD workflow enforcement.** All edits go through GSD commands.
9. **CLAUDE.md version pins are canonical** — `gradle/libs.versions.toml` and `frontend/package.json` MUST match the table in CLAUDE.md `## Technology Stack`. The one exception is the Spring Cloud train (see Critical Pitfalls §"CLAUDE.md Spring Cloud version drift").

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Static asset hosting (frontend) | Browser / Client | Vite dev server | React SPA built by Vite. Phase 0 runs `pnpm dev` locally; production-build path is documented for v2 cloud. |
| Provider stack wiring (Router/Query/Axios/Zustand) | Browser / Client | — | All client-side runtime; no SSR. |
| Gateway routing `/__health/<svc>` | API / Backend (gateway) | — | Spring Cloud Gateway WebFlux — first hop after the SPA. |
| Service discovery | Eureka server (registry tier) | — | Eureka standalone process; backend services register on startup. Not in routing path in Phase 0 (gateway uses static URIs per D-02). |
| Health probing (`/__health` placeholder + `/actuator/health`) | API / Backend (each service) | — | Each service hosts its own probe endpoints. |
| Per-service migrations (V1 baseline) | API / Backend (each DB-backed service) | Database / Storage | Flyway in each service runs against its own schema with per-service DB user. |
| Schema isolation (`auth/trip/destination`) | Database / Storage | — | Postgres-level: `init.sql` creates schemas + per-service users with `USAGE, CREATE` on their own schema. |
| Cache (Redis 7) | Database / Storage | — | Available in compose for Phase 1+; Phase 0 only verifies the container is healthy and reachable from gateway. |
| SMTP (MailHog) | Database / Storage (dev mailbox) | — | MailHog container; auth-service connects in Phase 2. Phase 0 only verifies container health. |
| Tracing (Zipkin) | Observability (sibling tier) | — | Standalone Zipkin 3 container; services export via `opentelemetry-exporter-zipkin`. |
| Trace context (`traceId/spanId`) propagation | Cross-cutting (libs/observability) | All API tiers | Auto-config in `libs/observability` binds Micrometer Tracing → OTel → Zipkin once for every service. |

**Why this matters:** Misassigning capabilities here would put domain logic in `libs/` (which is shared and not Phase 0's job) or push observability config into individual services (which causes Pitfall 7's BOM-drift trace-loss). The map locks **observability is shared lib, routing is gateway, schema is per-service**.

## Standard Stack

### Core (Backend)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Java | 21 LTS (Temurin) | JVM runtime | LTS through 2031; virtual threads GA; project-locked. [VERIFIED: CLAUDE.md] |
| Spring Boot | **3.5.14** (latest 3.5.x) | App framework, autoconfig, actuator | 3.3.x is OSS EOL since June 2025; 3.5.x is current. [VERIFIED: spring.io blog Spring Cloud 2025.0.2 announcement names SB 3.5.13; check `npm view`-equivalent `mvn central` for latest patch at scaffold time] |
| Spring Cloud BOM | **2025.0.x** (Northfields, latest 2025.0.2) | Spring Cloud release-train BOM (Gateway, Eureka client, etc.) | ⚠ **CLAUDE.md says 2024.0.x — that is wrong; 2024.0 (Moorgate) is for SB 3.4.x.** [VERIFIED: spring.io 2026-04-02 release post for 2025.0.2 — built on Spring Boot 3.5.13] |
| Spring Cloud Gateway (WebFlux) | from 2025.0.x BOM (Gateway 5.x line per Northfields) | API gateway, routing, predicates/filters | Reactive Spring-native; `spring-cloud-starter-gateway` is the entry artifact. **Note: Northfields renamed/deprecated some modules — for Phase 0 keep using the classic `spring-cloud-starter-gateway` (server-stack reactive); the new "proxy-exchange" variant is not what we want.** [CITED: spring.io blog Spring Cloud 2025.0.0 announcement] |
| Spring Cloud Netflix Eureka Server | from 2025.0.x BOM | Service registry | `spring-cloud-starter-netflix-eureka-server` for the eureka-server module; `spring-cloud-starter-netflix-eureka-client` for the 4 service modules. Maintenance-mode (no new features) but CVE-patched and fully compatible with 2025.0.x train. [VERIFIED: spring.io Northfields announcement still references it] |
| PostgreSQL | 16 (image: `postgres:16`) | Persistence | Supported through Nov 2028; locked. [VERIFIED: CLAUDE.md / endoflife.date] |
| Hibernate / Spring Data JPA | managed by SB 3.5 (Hibernate 6.6.x) | ORM | Auto-managed by Spring Boot — do NOT pin manually. [VERIFIED: SB 3.5 release notes] |
| Flyway core | managed by SB 3.5 (Flyway 10.x) | DB migrations | SB 3.5 manages Flyway 10.x; do not override. [CITED: SB 3.5 dependency management] |
| **Flyway PostgreSQL driver** | **`org.flywaydb:flyway-database-postgresql`** (managed by SB 3.5) | Flyway 10's modularized PG driver | **MANDATORY explicit dep — Pitfall 3.** Missing it → "Unsupported Database: PostgreSQL 16.x" at startup. [VERIFIED: github.com/flyway/flyway issue #3969 — modularized in Flyway 10] |
| jjwt | 0.13.0 (NOT in Phase 0 — Phase 1 adds it) | JWT issuance/verification | Phase 0 does not depend on jjwt; pin lives in `libs.versions.toml` for Phase 1 to consume. [CITED: jjwt GitHub releases] |
| Resilience4j | 2.4.0 (`resilience4j-spring-boot3`) | Circuit breaker / retry | Phase 0 does NOT use it (no external HTTP calls). Pin lives in catalog for Phase 4. [VERIFIED: Maven Central] |
| Micrometer Tracing BOM | `io.micrometer:micrometer-tracing-bom` (matches SB 3.5 = 1.4.x line; managed by SB 3.5) | Trace context bridge | **Pin once in `libs.versions.toml`. Pitfall 7 hard rule.** [CITED: Micrometer docs] |
| Micrometer-Tracing Bridge OTel | `io.micrometer:micrometer-tracing-bridge-otel` (managed by tracing-bom) | Bridge from Micrometer Tracing API → OpenTelemetry SDK | Phase 0 wires this in `libs/observability`. [CITED: docs.spring.io tracing reference] |
| OpenTelemetry Zipkin Exporter | `io.opentelemetry:opentelemetry-exporter-zipkin` (managed by OTel BOM transitively, or pinned in catalog) | Sends spans to Zipkin :9411 | Phase 0 wires this in `libs/observability`. [CITED: spring.io 2022/10/12 observability post] |
| Logback JSON encoder | `net.logstash.logback:logstash-logback-encoder` 7.4 | Structured JSON log output | Recommended by `docs/02-architecture.md §6.3`; emits `timestamp/level/service/traceId/spanId/userId/message`. [CITED: docs/02-architecture.md] |

### Core (Frontend)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Node | 20 LTS | Build/test runtime | LTS through April 2026 (then maintenance to April 2027). CI uses `actions/setup-node@v4` with `node-version: 20`. [CITED: CLAUDE.md D-16] |
| pnpm | 9.x (`packageManager: "pnpm@9.15.x"` recommended) | Package manager | Faster than npm, no global install needed (Corepack auto-bootstraps from `packageManager` field). [CITED: CLAUDE.md] |
| Vite | **6.x** (per D-31; v5.4 was the prior recommendation) | Dev server + build | D-31 (CONTEXT.md addendum 2026-05-08) locks Vite 6.x — paired with Vitest 3.x or 4.x and Node ≥20. Vite 6 breaking changes (Sass API default, build target name, Runtime API rename) are noise-level for a greenfield Phase 0 with no migration burden. [VERIFIED: vite.dev blog; LOCKED by D-31] |
| React | 18.3.x | UI framework | Locked by CLAUDE.md. React 19 incompatible with react-leaflet v4 (react-leaflet v5 needs React 19). [VERIFIED: react-leaflet GitHub releases] |
| TypeScript | 5.8.x | Static typing | TS 6.0 (March 2026) has breaking changes; 5.8 is stable. [VERIFIED: CLAUDE.md] |
| Tailwind CSS | **3.4.x** | Styling | Tailwind v4 is an engine rewrite with browser baseline bumps + CSS-first config — **not v4 in Phase 0**. [CITED: tailwindcss.com v4 announcement, CLAUDE.md] |
| shadcn/ui | CLI initialized via `pnpm dlx shadcn@2.x init` | Component generator (no components in Phase 0) | The `shadcn@latest` CLI defaults to Tailwind v4 + React 19. **Phase 0 must pin `shadcn@2.x`** (or use the explicit Tailwind-v3 init flag) to keep generated components compatible with our locked Tailwind v3 + React 18. [CITED: ui.shadcn.com tailwind-v4 docs; CLAUDE.md D-12] |
| `lucide-react` | latest from shadcn defaults | Icon library | shadcn default; no icons used in Phase 0 but install during init for Phase 7+. [CITED: UI-SPEC §Design System] |
| React Router | **6.30.x** (NOT 7.x) | Client-side routing | CONTEXT.md D-12 locks v6 to honor original spec; v7 advised for Phase 7+ consideration. Import from `react-router-dom`. [CITED: CONTEXT.md D-12] |
| TanStack Query | **5.100.x** | Server state cache | CLAUDE.md confirms v5 is stable & current. Phase 0 wires `QueryClient` + `QueryClientProvider`. [CITED: tanstack/query GitHub] |
| axios | **1.16.0** (must be ≥ 1.15.0) | HTTP client | CVE-2025-62718 (SSRF via NO_PROXY bypass) + CVE-2026-40175 in versions < 1.15.0. [VERIFIED: CLAUDE.md citing herodevs.com advisory] |
| Zustand | **5.0.x** | Client state | v5 drops React < 18; uses native `useSyncExternalStore`. CLAUDE.md confirms current. [VERIFIED: CLAUDE.md] |
| Vitest | **3.x** (latest in 3 line) | Unit test runner | Vitest 4 requires Vite ≥ 6 + Node ≥ 20. With Vite 5: **must use Vitest 3.x.** [VERIFIED: vitest.dev release notes] |
| React Testing Library | 16.x | Component test helpers | Stable, RTL 16 supports React 18. [VERIFIED: CLAUDE.md] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Spring Boot Actuator | managed by SB 3.5 | Health, info, metrics, prometheus endpoints | All services in Phase 0 — required for `depends_on: condition: service_healthy`. |
| Spring Boot Testcontainers (`spring-boot-testcontainers`) | managed by SB 3.5 | `@ServiceConnection` zero-config container wiring | Phase 0 declares the dep but writes no integration tests yet — Phase 1+ uses it. |
| Testcontainers | 1.21.x (managed by SB 3.5 BOM) | Container-based integration tests | Same as above. |
| WireMock Spring Boot | `org.wiremock.integrations:wiremock-spring-boot` 3.9.0 | HTTP stubbing in tests | Phase 0 catalogs it; Phase 4 actually uses it. |
| Jackson | managed by SB 3.5 | JSON serialization | Implicit; no Phase 0 config needed. |
| Hibernate Validator (Bean Validation) | managed by SB 3.5 | Validation API | Implicit; Phase 1+ uses it. |
| Docker Compose | v2 (bundled with Docker Desktop 4.x) | Local orchestration | `docker compose up` (plugin syntax, NOT `docker-compose`). |
| `dorny/paths-filter` | v3 | CI change-detection | Skeleton CI uses it for matrix-by-service test strategy. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Eureka | Consul, Kubernetes DNS | Consul adds config-management complexity; K8s DNS requires a cluster. Eureka is Spring-native and minimal-config. |
| Spring Cloud Gateway | Zuul (deprecated), Kong, Nginx | Spring Cloud Gateway is reactive Spring-native; Zuul 1 is dead, Zuul 2 isn't Spring-managed; Kong/Nginx pull us out of the JVM ecosystem. |
| Custom JWT (Phase 1+) | Spring Authorization Server | OAuth2 server is overkill for v1's email+password flow; ADR-5. |
| Logstash JSON encoder | `slf4j-event-logger`, native Logback `JsonLayout` | `logstash-logback-encoder` is the de-facto industry default for structured Logback JSON; native `JsonLayout` exists but is less ergonomic for MDC + custom fields. CONTEXT.md D-04 recommends logstash-logback-encoder. |
| Vite 5 | Vite 6 | Both valid. Vite 5 + Vitest 3 is a known-good pair; Vite 6 introduces Sass-API/build-target/Runtime-API breaks. **Recommend Vite 5.4.x for Phase 0; revisit at Phase 9 polish.** |
| Tailwind v3 | Tailwind v4 | v4 is a rewrite (Oxide engine, CSS-first config, browser baseline bump). Locked v3 by CLAUDE.md. |
| React Router 6 | React Router 7 | v7 is non-breaking from v6 with future flags enabled; CLAUDE.md says greenfield should prefer v7 but CONTEXT.md D-12 honors locked v6. |
| `flyway-database-postgresql` 10.x explicit | Older Flyway 9.x (driver was bundled) | Phase 0 must use Flyway 10.x because SB 3.5 manages it. **Adding `flyway-database-postgresql` as a `runtimeOnly` dep is mandatory; no alternative.** |

**Installation (Gradle catalog reference, not literal commands):**
- All backend deps consumed via `libs.versions.toml` accessors. No literal `npm install` / `gradle add` commands at this layer; the planner emits build files.
- Frontend bootstrap (the only literal command in Phase 0 acquisition):
  ```bash
  pnpm create vite@latest frontend -- --template react-ts
  cd frontend && pnpm add react-router-dom@~6.30 @tanstack/react-query@~5.100 axios@1.16.0 zustand@~5.0
  pnpm add -D tailwindcss@~3.4 postcss autoprefixer vitest@~3 @testing-library/react@~16 @testing-library/jest-dom @vitejs/plugin-react @types/node
  pnpm dlx tailwindcss init -p
  pnpm dlx shadcn@2.x init   # (or shadcn@latest with explicit Tailwind v3 prompt answer)
  ```

**Version verification (perform at scaffold time):**
- `npm view tailwindcss@3 version` → confirm latest 3.4.x patch
- `npm view vite version --tag latest` → confirm Vite 5 latest patch
- `npm view vitest@3 version` → confirm Vitest 3 latest patch
- `npm view react@18 version` → confirm latest 18.3.x patch
- For Spring: check `https://github.com/spring-projects/spring-boot/releases` for latest 3.5.x; check `https://github.com/spring-cloud/spring-cloud-release/releases` for latest 2025.0.x.

## Architecture Patterns

### System Architecture Diagram

```
                           Browser (Chrome/Safari/Firefox)
                                       │
                                       │ http://localhost:5173
                                       ▼
                       ┌───────────────────────────────┐
                       │     Vite dev server           │
                       │  (frontend/, React 18.3 SPA)  │
                       └───────────────┬───────────────┘
                                       │ axios → /api/** (Phase 1+)
                                       │ axios → /__health/* (Phase 0 smoke)
                                       │ baseURL = VITE_API_URL = http://localhost:8080
                                       ▼
                       ┌───────────────────────────────┐
                       │   api-gateway :8080           │
                       │   Spring Cloud Gateway        │
                       │   (WebFlux, reactive)         │
                       │                               │
                       │   Routes (Phase 0):           │
                       │   - /__health/gateway (local) │
                       │   - /__health/auth        ──┐ │
                       │   - /__health/trip        ──┤ │
                       │   - /__health/destination ──┤ │
                       │   /actuator/health (UP)     │ │
                       └───────────────┬─────────────┼─┘
              static URIs              │             │
              (NOT lb://; D-02)        │             │
                                       ▼             ▼
   ┌──────────────────┐     ┌──────────────────┐     ┌────────────────────────┐
   │ auth-service     │     │ trip-service     │     │ destination-service    │
   │ :8081 (127.0.0.1)│     │ :8082 (127.0.0.1)│     │ :8083 (127.0.0.1)      │
   │ /__health        │     │ /__health        │     │ /__health              │
   │ /actuator/health │     │ /actuator/health │     │ /actuator/health       │
   │ Flyway → auth    │     │ Flyway → trip    │     │ Flyway → destination   │
   │ Eureka client    │     │ Eureka client    │     │ Eureka client          │
   └────────┬─────────┘     └────────┬─────────┘     └────────────┬───────────┘
            │                        │                            │
            │ (Phase 0: register only; gateway routes statically)│
            │                        │                            │
            └─────────┐              │              ┌─────────────┘
                      │              │              │
                      ▼              ▼              ▼
              ┌───────────────────────────────────────────┐
              │  eureka-server :8761 (127.0.0.1)          │
              │  Spring Cloud Netflix Eureka              │
              │  Registry only (not in routing path P0)   │
              └───────────────────────────────────────────┘

   Cross-cutting:
   ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
   │ postgres :5432   │  │ redis :6379      │  │ mailhog          │  │ zipkin :9411     │
   │ (127.0.0.1)      │  │ (P0: idle)       │  │ :1025 / :8025    │  │ (0.0.0.0)        │
   │ schemas:         │  │                  │  │ (P0: idle)       │  │                  │
   │   auth, trip,    │  │                  │  │                  │  │ Spans from all   │
   │   destination    │  │                  │  │                  │  │ 4 services via   │
   │ users:           │  │                  │  │                  │  │ OTel-Zipkin exp. │
   │   auth_svc,      │  │                  │  │                  │  │                  │
   │   trip_svc,      │  │                  │  │                  │  │                  │
   │   destination_svc│  │                  │  │                  │  │                  │
   └──────────────────┘  └──────────────────┘  └──────────────────┘  └──────────────────┘

   Component map (file → responsibility):
   - libs/observability  → Spring Boot AutoConfiguration → registers MDC filter, tracing exporter, Logback JSON encoder for ALL services
   - libs/error-handling → ProblemDetailFactory + ErrorCode enum (consumed Phase 1+)
   - libs/api-contracts  → empty module (consumed Phase 1+ for UserContext record)
   - infra/postgres/init.sql → DDL run on first container init: CREATE SCHEMA + CREATE USER + GRANT
   - gradle/libs.versions.toml → single source of truth for every dep version
```

**Reading the diagram:** A request from the browser hits Vite (5173), which proxies (or just lets the SPA fetch directly to gateway) to the gateway (8080). Phase 0 wires `/__health/<svc>` static-URI routes; Phase 1 layers `/api/<svc>/**` on top. Each backend service registers with Eureka but Phase 0 routing does not consume registry — that's intentional, see Pitfall 10 and CONTEXT.md D-02.

### Recommended Project Structure

```
trip-planner/
├── settings.gradle.kts                    # multi-module includes
├── build.gradle.kts                       # root: plugins, subprojects {} block
├── gradle.properties                      # JVM args, parallel build
├── gradle/
│   ├── libs.versions.toml                 # version catalog (single source of truth)
│   └── wrapper/                           # Gradle 8.14.x wrapper
├── gradlew, gradlew.bat
│
├── services/
│   ├── eureka-server/
│   │   ├── build.gradle.kts
│   │   └── src/main/{java,resources}/...
│   ├── api-gateway/
│   │   ├── build.gradle.kts
│   │   └── src/main/{java,resources}/...
│   ├── auth-service/
│   │   ├── build.gradle.kts
│   │   └── src/main/{java,resources}/...
│   │       └── db/migration/V1__init.sql  # SELECT 1; placeholder
│   ├── trip-service/
│   │   └── (same layout, db/migration/V1__init.sql)
│   └── destination-service/
│       └── (same layout, db/migration/V1__init.sql)
│
├── libs/
│   ├── observability/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── java/com/tripplanner/observability/
│   │       │   ├── ObservabilityAutoConfiguration.java
│   │       │   ├── MdcEnrichmentFilter.java         # servlet
│   │       │   └── ReactiveMdcEnrichmentFilter.java # webflux (gateway)
│   │       └── resources/
│   │           ├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   │           └── logback-spring-base.xml          # JSON appender, MDC fields
│   ├── error-handling/
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/tripplanner/errors/
│   │       ├── ProblemDetailFactory.java
│   │       └── ErrorCode.java
│   └── api-contracts/
│       ├── build.gradle.kts                # empty module
│       └── src/main/java/.gitkeep          # ensures dir exists
│
├── frontend/
│   ├── package.json                        # packageManager: pnpm@9.15.x
│   ├── pnpm-lock.yaml
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── tsconfig.node.json
│   ├── tailwind.config.ts
│   ├── postcss.config.js
│   ├── components.json                     # shadcn config (Tailwind v3 path)
│   ├── index.html
│   ├── .eslintrc.cjs
│   ├── public/
│   ├── src/
│   │   ├── main.tsx                        # React mount + providers
│   │   ├── App.tsx                         # Phase 0 landing only (UI-SPEC)
│   │   ├── App.test.tsx                    # smoke test (Vitest)
│   │   ├── index.css                       # @tailwind + :root + .dark CSS vars
│   │   ├── lib/
│   │   │   ├── axios.ts                    # apiClient singleton, no-op interceptor
│   │   │   ├── queryClient.ts              # TanStack QueryClient w/ defaults
│   │   │   ├── utils.ts                    # shadcn cn()
│   │   │   └── store.ts                    # Zustand useAppStore skeleton
│   │   └── env.d.ts                        # Vite env types
│   └── tests/                              # (Phase 7+ adds; Phase 0 ships only App.test.tsx)
│
├── infra/
│   ├── docker-compose.yml                  # canonical compose
│   ├── postgres/
│   │   └── init.sql                        # CREATE SCHEMA + CREATE USER + GRANT
│   ├── seeds/                              # empty in P0; Phase 3 lands cities-15000.tsv
│   │   └── .gitkeep
│   └── README.md                           # describes compose + ports
│
├── docker-compose.yml                      # root alias: include: ./infra/docker-compose.yml
├── .env.example
├── .gitignore
├── .github/workflows/
│   ├── backend.yml
│   └── frontend.yml
└── README.md                               # quickstart, architecture summary
```

### Pattern 1: Gradle Version Catalog (single source of truth)

**What:** Centralize all dep versions in `gradle/libs.versions.toml` with type-safe accessors auto-generated by Gradle.
**When to use:** Every backend service's `build.gradle.kts`. **Pitfall 7 hard rule:** `micrometer-tracing-bom` lives here exactly once.
**Example (excerpt):**
```toml
# Source: docs.gradle.org/current/userguide/version_catalogs.html
[versions]
java = "21"
springBoot = "3.5.14"
springCloud = "2025.0.2"
flyway = "10.21.0"           # if not relying on SB-managed; otherwise omit
postgresqlJdbc = "42.7.4"
micrometerTracing = "1.4.x"  # let SB 3.5 manage; pin here only if overriding
logstashLogbackEncoder = "7.4"
junit = "5.12.0"
testcontainers = "1.21.0"

[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
spring-boot-starter-webflux = { module = "org.springframework.boot:spring-boot-starter-webflux" }
spring-boot-starter-actuator = { module = "org.springframework.boot:spring-boot-starter-actuator" }
spring-boot-starter-data-jpa = { module = "org.springframework.boot:spring-boot-starter-data-jpa" }
spring-cloud-starter-gateway = { module = "org.springframework.cloud:spring-cloud-starter-gateway" }
spring-cloud-starter-netflix-eureka-server = { module = "org.springframework.cloud:spring-cloud-starter-netflix-eureka-server" }
spring-cloud-starter-netflix-eureka-client = { module = "org.springframework.cloud:spring-cloud-starter-netflix-eureka-client" }
flyway-core = { module = "org.flywaydb:flyway-core" }
flyway-database-postgresql = { module = "org.flywaydb:flyway-database-postgresql" }   # <-- MANDATORY (Pitfall 3)
postgresql-jdbc = { module = "org.postgresql:postgresql" }
micrometer-tracing-bridge-otel = { module = "io.micrometer:micrometer-tracing-bridge-otel" }
opentelemetry-exporter-zipkin = { module = "io.opentelemetry:opentelemetry-exporter-zipkin" }
logstash-logback-encoder = { module = "net.logstash.logback:logstash-logback-encoder", version.ref = "logstashLogbackEncoder" }

[bundles]
observability = ["micrometer-tracing-bridge-otel", "opentelemetry-exporter-zipkin", "logstash-logback-encoder"]

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "springBoot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.6" }
```

Then in each service's `build.gradle.kts`:
```kotlin
// services/auth-service/build.gradle.kts
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":libs:observability"))
    implementation(project(":libs:error-handling"))
    implementation(project(":libs:api-contracts"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.cloud.starter.netflix.eureka.client)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)   // <-- Pitfall 3
    runtimeOnly(libs.postgresql.jdbc)
    implementation(libs.bundles.observability)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${libs.versions.springCloud.get()}")
    }
}
```

### Pattern 2: Per-Service Spring Profiles (`dev` / `docker` / `test`)

**What:** Each service ships base `application.yml` + profile overrides. CONTEXT.md D-23 locks the three profiles.
**When to use:** Every service (gateway, eureka, auth, trip, destination).
**Example (auth-service):**
```yaml
# services/auth-service/src/main/resources/application.yml
spring:
  application:
    name: auth-service          # <-- D-25, Pitfall 7
  profiles:
    default: dev
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:tripplanner}?currentSchema=auth
    username: ${AUTH_DB_USER:auth_svc}
    password: ${AUTH_DB_PASSWORD:auth_svc}
  flyway:
    enabled: true
    schemas: auth                       # <-- D-09, Pitfall 3
    default-schema: auth
    table: auth_flyway_schema_history   # <-- D-09, Pitfall 3
    locations: classpath:db/migration
  jpa:
    hibernate:
      ddl-auto: validate              # NEVER `update` (Pitfall: Tech-debt patterns)
    properties:
      hibernate.default_schema: auth
server:
  port: 8081
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: always
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
  tracing:
    sampling:
      probability: 1.0   # P0 dev/docker; P10 may tune for prod path
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
    fetch-registry: true
    register-with-eureka: true
---
spring:
  config:
    activate:
      on-profile: docker
  datasource:
    url: jdbc:postgresql://postgres:5432/${POSTGRES_DB}?currentSchema=auth
eureka:
  client:
    service-url:
      defaultZone: http://eureka-server:8761/eureka
    registry-fetch-interval-seconds: 5      # <-- D-21, Pitfall 10
  instance:
    lease-renewal-interval-in-seconds: 5    # <-- D-21
    lease-expiration-duration-in-seconds: 10
```

### Pattern 3: Spring Cloud Gateway Static-URI Routes

**What:** Gateway routes `/__health/<svc>` via static `http://service:port` URIs (D-02), NOT `lb://service-name`.
**When to use:** Phase 0 only — Phase 1 layers `/api/**` on top with the same static-URI strategy until Phase 10 hardening.
**Example (`application.yml` route table):**
```yaml
# services/api-gateway/src/main/resources/application.yml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        # P0 health-routing convention (top of table; Phase 1 appends /api/* below)
        - id: health-auth
          uri: http://auth-service:8081
          predicates:
            - Path=/__health/auth
          filters:
            - SetPath=/__health
        - id: health-trip
          uri: http://trip-service:8082
          predicates:
            - Path=/__health/trip
          filters:
            - SetPath=/__health
        - id: health-destination
          uri: http://destination-service:8083
          predicates:
            - Path=/__health/destination
          filters:
            - SetPath=/__health
server:
  port: 8080
```

The gateway's own `/__health/gateway` is a `@RestController` in the gateway's main package (D-01). Note that `dev` profile points URIs at `http://localhost:<port>`; `docker` profile (the canonical case for Phase 0 success criteria) points at the compose-network DNS names `auth-service`, `trip-service`, etc.

### Pattern 4: Eureka Server Bootstrap (minimal)

**What:** Standalone Eureka server that doesn't try to register with itself.
**Example:**
```java
// services/eureka-server/src/main/java/com/tripplanner/eureka/EurekaServerApplication.java
package com.tripplanner.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```
```yaml
# services/eureka-server/src/main/resources/application.yml
spring:
  application:
    name: eureka-server
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false    # don't self-register
    fetch-registry: false
  server:
    enable-self-preservation: false   # OK in single-instance dev
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

### Pattern 5: Per-Service Flyway Baseline (V1 placeholder)

**What:** Each DB-backed service ships an empty V1 migration to validate the per-service-history-table machinery (D-10).
**Example:**
```sql
-- services/auth-service/src/main/resources/db/migration/V1__init.sql
-- Intentional empty baseline. Real schema lands in:
--   auth-service:        Phase 2 (V2__users.sql, V3__email_verifications.sql, ...)
--   trip-service:        Phase 5 (V2__trips.sql, V3__itinerary_days.sql, ...)
--   destination-service: Phase 3 (V2__cities.sql, V3__destinations.sql, ...)
SELECT 1;
```

### Pattern 6: PostgreSQL `init.sql` (idempotent schemas + users)

**What:** Mounted at `/docker-entrypoint-initdb.d/init.sql`; runs only on first container init.
**Example:**
```sql
-- infra/postgres/init.sql
-- Phase 0 scaffold: 3 schemas + 3 service users with schema-scoped grants.
-- Idempotent: safe to re-run after `docker compose down -v`.

-- Schemas
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS trip;
CREATE SCHEMA IF NOT EXISTS destination;

-- Users (passwords come from env via psql -c at compose-up; OR use envsubst at the entrypoint)
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='auth_svc') THEN
    CREATE ROLE auth_svc LOGIN PASSWORD 'auth_svc';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='trip_svc') THEN
    CREATE ROLE trip_svc LOGIN PASSWORD 'trip_svc';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='destination_svc') THEN
    CREATE ROLE destination_svc LOGIN PASSWORD 'destination_svc';
  END IF;
END $$;

-- Schema-scoped grants
GRANT USAGE, CREATE ON SCHEMA auth TO auth_svc;
GRANT USAGE, CREATE ON SCHEMA trip TO trip_svc;
GRANT USAGE, CREATE ON SCHEMA destination TO destination_svc;

-- search_path defaults
ALTER ROLE auth_svc SET search_path = auth;
ALTER ROLE trip_svc SET search_path = trip;
ALTER ROLE destination_svc SET search_path = destination;
```

⚠ **Discretion (CONTEXT.md):** The `init.sql` literal-password approach above is the simplest. Alternative: use a small entrypoint shell script that runs `envsubst` against a template. Pick whichever is reliable across `docker compose down -v` cycles. Default = literal hard-coded dev passwords (matching `.env.example` defaults), since they're not real secrets.

### Pattern 7: Docker Compose Health-Gated Startup

**What:** Every service has `healthcheck:` + every dependent uses `depends_on: { <dep>: { condition: service_healthy } }`. (D-20)
**Example (excerpt):**
```yaml
# infra/docker-compose.yml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    ports: ["127.0.0.1:5432:5432"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER}"]
      interval: 5s
      timeout: 3s
      retries: 5
      start_period: 10s

  redis:
    image: redis:7-alpine
    ports: ["127.0.0.1:6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  mailhog:
    image: mailhog/mailhog:latest
    ports:
      - "127.0.0.1:1025:1025"
      - "0.0.0.0:8025:8025"   # web UI accessible from host

  zipkin:
    image: openzipkin/zipkin:3
    ports: ["0.0.0.0:9411:9411"]

  eureka-server:
    build: { context: ../services/eureka-server }
    ports: ["127.0.0.1:8761:8761"]
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8761/actuator/health || exit 1"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 20s

  api-gateway:
    build: { context: ../services/api-gateway }
    ports: ["0.0.0.0:8080:8080"]
    depends_on:
      eureka-server: { condition: service_healthy }
    environment:
      SPRING_PROFILES_ACTIVE: docker
      EUREKA_URL: http://eureka-server:8761/eureka
      ZIPKIN_BASE_URL: http://zipkin:9411
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 25s

  auth-service:
    build: { context: ../services/auth-service }
    ports: ["127.0.0.1:8081:8081"]
    depends_on:
      postgres: { condition: service_healthy }
      eureka-server: { condition: service_healthy }
    environment:
      SPRING_PROFILES_ACTIVE: docker
      POSTGRES_HOST: postgres
      POSTGRES_DB: ${POSTGRES_DB}
      AUTH_DB_USER: ${AUTH_DB_USER}
      AUTH_DB_PASSWORD: ${AUTH_DB_PASSWORD}
      EUREKA_URL: http://eureka-server:8761/eureka
      ZIPKIN_BASE_URL: http://zipkin:9411
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8081/actuator/health || exit 1"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 30s

  # trip-service, destination-service: same shape with their schemas / ports / users
  # frontend: build context ../frontend, ports 5173, no healthcheck (Vite dev server)

volumes:
  pgdata:
```

Root-level `docker-compose.yml` alias:
```yaml
# /docker-compose.yml — root alias so `docker compose up` works from repo root.
# This file uses Compose 2.20+ `include:`. If `include:` is not supported in the
# user's Docker Compose v2 install, replace with `extends:` per service.
include:
  - ./infra/docker-compose.yml
```

### Pattern 8: Frontend Provider Stack (D-12)

**What:** `main.tsx` mounts React with the full provider stack pre-wired but no real routes.
**Example:**
```tsx
// frontend/src/main.tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from './lib/queryClient';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>
);
```
```ts
// frontend/src/lib/queryClient.ts
import { QueryClient } from '@tanstack/react-query';
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, refetchOnWindowFocus: false, retry: 1 },
  },
});
```
```ts
// frontend/src/lib/axios.ts
import axios from 'axios';
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,   // http://localhost:8080
  withCredentials: true,
});
// Phase 0: no-op interceptor placeholder. Phase 2 attaches Authorization header here.
apiClient.interceptors.request.use((config) => {
  config.headers['X-Request-Id'] = crypto.randomUUID();
  return config;
});
```
```ts
// frontend/src/lib/store.ts
import { create } from 'zustand';
type AppState = {
  theme: 'light' | 'dark';
  setTheme: (t: 'light' | 'dark') => void;
};
export const useAppStore = create<AppState>((set) => ({
  theme: 'light',
  setTheme: (t) => set({ theme: t }),
}));
```

### Pattern 9: shadcn/ui Tailwind v3 Initialization

**What:** shadcn `@latest` defaults to Tailwind v4 + React 19 — Phase 0 must pin v3 path.
**Approach:**
```bash
pnpm dlx shadcn@2.x init
```
When prompted:
- Style: default
- Base color: slate (matches UI-SPEC §Color)
- Confirm `tailwind.config.ts` with `darkMode: 'class'` (UI-SPEC §Design System)
- CSS file: `src/index.css`
- CSS variables: yes
- Path alias: `@/*` → `src/*`

After init, `components.json` should have:
```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "default",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "tailwind.config.ts",
    "css": "src/index.css",
    "baseColor": "slate",
    "cssVariables": true
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils"
  }
}
```

No components are generated in Phase 0 (UI-SPEC explicitly: "no components generated yet").

### Anti-Patterns to Avoid

- **`spring.jpa.hibernate.ddl-auto=update`** — silently drops/recreates columns and breaks Flyway history. Use `validate`. (PITFALLS.md tech-debt patterns row 3.)
- **`@Transactional` on controllers** — transaction spans HTTP serialization; rollback is silent if Jackson throws. Always on service methods. (Phase 5+ concern, but lock the convention now.)
- **Hardcoding `localhost:8081` in service-to-service config** — breaks in compose because container names ≠ localhost. Phase 0 doesn't make service-to-service calls but the convention is locked.
- **Manual `ServerHttpObservationFilter` registration** — auto-configured since SB 3.2. Manual registration → duplicate spans or no-op tracing. Pitfall 7. Hard rule.
- **Per-service overrides of `micrometer-tracing-bom`** — breaks trace propagation across reactive↔servlet boundary. Pitfall 7.
- **Default Flyway `flyway_schema_history` table name** — Pitfall 3 — checksum collision on second-service startup.
- **Forgetting `flyway-database-postgresql`** — Flyway 10 modularization → "Unsupported Database: PostgreSQL 16.x" startup error.
- **`lb://service-name` URIs in Phase 0 gateway routes** — D-02 says static URIs only. `lb://` requires the gateway to have already fetched the registry, which causes the Pitfall 10 cold-start window.
- **Exposing downstream service ports on `0.0.0.0`** — D-22 binds them to `127.0.0.1`. Loopback-only mitigates Pitfall 1 (X-User-Id spoof) in dev.
- **Putting domain logic into `libs/`** — `libs/` is for cross-cutting infrastructure only. Domain code stays in services.
- **Mixing Tailwind v3 and v4** — they're not side-by-side compatible. Phase 0 = v3 only.
- **Using `shadcn@latest`** — defaults to v4 + React 19. Pin `shadcn@2.x` or use the explicit v3 init path.
- **Axios < 1.15.0** — CVE-2025-62718 + CVE-2026-40175. Pin 1.16.0.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| API gateway | Custom servlet that proxies requests | Spring Cloud Gateway | WebFlux reactive, built-in predicates/filters, Eureka integration, observability hooks. Hand-rolled gateways become technical debt instantly. |
| Service discovery | DNS hacks, hardcoded URLs | Spring Cloud Netflix Eureka | Mature, Spring-managed, handles registration/heartbeat/failure. (`lb://` parked for Phase 10 but the registry tier is Phase 0.) |
| DB migrations | SQL files run at startup by app code | Flyway 10 + per-service tables | Versioning, checksum validation, rollback strategy, schema-scoped history. |
| Connection pooling | Custom pool | HikariCP (SB default) | Battle-tested. Don't even configure unless tuning. |
| JSON log encoder | Custom Logback layout | `logstash-logback-encoder` | Handles MDC, exception stacktrace JSON, custom fields, timestamp formatting. |
| Trace context propagation | Manual `traceparent` header copying | Micrometer Tracing + OTel bridge | Reactive↔servlet propagation is non-trivial. Auto-configured in SB 3.2+. |
| Healthchecks | Custom `/health` endpoint with hand-rolled DB ping | Spring Boot Actuator `/actuator/health` | Handles DB, Redis, Eureka, custom indicators; integrates with K8s liveness/readiness; works with Docker Compose `condition: service_healthy`. |
| RFC 7807 Problem responses | Custom error JSON shape | `org.springframework.http.ProblemDetail` (Spring 6.0+) | Spring's first-class type. `libs/error-handling`'s `ProblemDetailFactory` wraps it with stable `code` extension. |
| Frontend HTTP client | `fetch()` everywhere | axios singleton + interceptors | Shared base URL, auth header injection, response error normalization. (Phase 7+ adds the 401 refresh-loop guard per Pitfall 9.) |
| Server state cache | Manual `useEffect` + state | TanStack Query | Stale-while-revalidate, optimistic updates, retry/dedup. Don't reinvent. |
| Drag-drop reorder (Phase 8 not Phase 0) | HTML5 drag-and-drop API | `@dnd-kit/core` 6.x + `@dnd-kit/sortable` | Accessibility (KeyboardSensor), mobile touch, virtual list support. |
| Maps (Phase 8) | Google Maps embed | Leaflet + OSM tiles + react-leaflet 4.2.x | No API key, no billing, no rate-limits. (`react-leaflet` v5 requires React 19 — locked at v4.2.x.) |
| CI change detection | Custom path-comparison shell | `dorny/paths-filter@v3` | Maintained, declarative filter syntax. |
| Schema isolation in Postgres | Multiple DB instances or separate containers | Single Postgres + `CREATE SCHEMA` + per-schema users | Memory budget (single container), ownership preserved by GRANT. (ADR-3.) |

**Key insight:** Spring Cloud's value is precisely that the routing/discovery/tracing/healthcheck primitives are already integrated. Phase 0 is mostly *configuration* of these primitives, not implementation of new mechanisms. Resist building any "lightweight" alternative — every primitive on this list has a 10-year-old failure mode you haven't thought of yet.

## Runtime State Inventory

> Greenfield phase — no rename/refactor/migration is happening. Section omitted intentionally.

## Common Pitfalls

### Pitfall A: Flyway 10 PostgreSQL driver missing → "Unsupported Database: PostgreSQL 16.x" at startup

**What goes wrong:** Each service's `build.gradle.kts` declares `org.flywaydb:flyway-core` (or relies on the Spring Boot starter pulling it transitively), but Flyway 10's PostgreSQL support was modularized into a separate artifact `org.flywaydb:flyway-database-postgresql`. Without it, `FlywayAutoConfiguration` constructs a `PostgreSQLDatabase` instance whose `ensureSupported()` method throws `org.flywaydb.core.api.FlywayException: Unsupported Database: PostgreSQL 16.x` at app startup.
**Why it happens:** SB 3.5's `spring-boot-starter-data-jpa` does NOT pull the modular driver transitively. Developers test with H2 locally, see no problem, and only discover this when the docker-compose stack tries to start.
**How to avoid:** In every DB-backed service's `build.gradle.kts`:
```kotlin
runtimeOnly(libs.flyway.database.postgresql)
```
Catalog already declares it (see Pattern 1 example). `eureka-server` and `api-gateway` skip it (no datasource).
**Warning signs:** Service exits at startup with `FlywayException: Unsupported Database`; `flyway-core` is on the classpath but `flyway-database-postgresql` is not.
**Reference:** [Flyway issue #3969](https://github.com/flyway/flyway/issues/3969) — 2024 modularization changelog.

### Pitfall B: Per-service Flyway history table not configured → checksum mismatch on second-service startup

**What goes wrong:** All three DB-backed services share one Postgres. If they all use the default `flyway_schema_history` table name in the `public` schema (or in their own schema with the same name), the second service's Flyway sees migration entries from another service with a different checksum and refuses to start: `FlywayException: Validate failed: Migration checksum mismatch`.
**Why it happens:** Spring Boot's default `spring.flyway.table=flyway_schema_history`. Devs scaffolding three services from the same template forget to override per-service.
**How to avoid:** In each service's `application.yml` (D-09):
```yaml
spring:
  flyway:
    schemas: <service>           # auth | trip | destination
    default-schema: <service>
    table: <service>_flyway_schema_history
```
Each service writes to `<schema>.<service>_flyway_schema_history`, isolated.
**Warning signs:** Second `docker compose up` after fresh `down -v` produces `checksum mismatch`; `psql -c "\dt auth.*"` returns `flyway_schema_history` instead of `auth_flyway_schema_history`.
**Reference:** PITFALLS.md Pitfall 3.

### Pitfall C: Trace context lost across gateway↔downstream boundary (Pitfall 7)

**What goes wrong:** Spring Cloud Gateway is reactive (WebFlux); auth/trip/destination services are servlet. The reactive↔servlet `ObservationRegistry` bridge depends on `micrometer-tracing-bom` versions matching across modules. If one service overrides the BOM, its `traceId` becomes a different value than the gateway's, breaking Zipkin trace continuity.
**A second mode:** `ServerHttpObservationFilter` was deprecated in Spring Framework 6.1 (SB 3.2). Code copied from pre-3.2 samples that manually `@Bean`-defines this filter ends up with duplicate spans or no-op tracing.
**How to avoid:**
1. Pin `micrometer-tracing-bom` once in `gradle/libs.versions.toml`. Never override per-service.
2. `libs/observability` ships the auto-config that wires the registry once. Every service depends on this lib; no service configures tracing locally.
3. Set `spring.application.name` in every service's base `application.yml` (D-25) — Zipkin's service name is derived from this.
4. Set `management.tracing.sampling.probability=1.0` in `dev` and `docker` profiles (D-04).
5. **Never** `@Bean ServerHttpObservationFilter`. Code review checks for this.
6. Validation lives in Phase 1 (per Pitfall 7 step 4): one request through gateway produces a single trace ID across both spans in Zipkin UI. Phase 0 leaves the wiring + dep alignment.
**Warning signs:** Different `traceId` between gateway and downstream logs; Zipkin shows isolated single-service traces; `ServerHttpObservationFilter` bean defined anywhere.

### Pitfall D: Eureka registration lag → gateway 503 cold-start window (Pitfall 10)

**What goes wrong:** `docker compose up` starts services concurrently. Each registers with Eureka after Spring context loads. Default Eureka `registryFetchIntervalSeconds=30` means gateway may not see a downstream registration for 60+s. **In Phase 0 gateway uses static URIs (D-02), so this is partially mitigated** — but `depends_on` ordering still matters: gateway should not start replying to `/__health/<svc>` until downstream services are healthy.
**How to avoid:**
1. Healthchecks + `depends_on: condition: service_healthy` on every service (D-20).
2. Reduce Eureka cache intervals in `application-docker.yml` for **every** service that registers (D-21):
   ```yaml
   eureka:
     client:
       registry-fetch-interval-seconds: 5
     instance:
       lease-renewal-interval-in-seconds: 5
       lease-expiration-duration-in-seconds: 10
   ```
3. README documents the 30–60s warm-up window and points readers at the healthcheck strategy.
**Warning signs:** `curl localhost:8080/__health/auth` returns 503 immediately after `docker compose up`; gateway log shows `No instances available for [service]`; integration tests are flaky in CI based on startup timing.

### Pitfall E: Spring Cloud version drift in CLAUDE.md (NEW — discovered during this research)

**What goes wrong:** CLAUDE.md says **Spring Cloud 2024.0.x matches SB 3.5**. Per the official Spring Cloud Supported-Versions wiki and the spring.io blog (2025-12-12 post for 2024.0.3 codenamed Moorgate), **Spring Cloud 2024.0 (Moorgate) is built on Spring Boot 3.4.x**. **Spring Cloud 2025.0 (Northfields)** is the train for SB 3.5.x; latest 2025.0.2 (April 2026) is built on SB 3.5.13.
**Why it happens:** CLAUDE.md was written with imperfect train-name memory; Northfields is the correct codename for the SB 3.5 train.
**How to avoid:** Phase 0 plans must use Spring Cloud **2025.0.x**, not 2024.0.x. Update `gradle/libs.versions.toml`:
```toml
springCloud = "2025.0.2"
```
And Spring Cloud module names: starting in Northfields, some module names changed (the "Web MVC" Gateway is deprecated in favor of Spring Cloud Function; the classic reactive Gateway is still `spring-cloud-starter-gateway`). Phase 0 uses the classic reactive `spring-cloud-starter-gateway` — that's still the default, no rename needed for our use case.
**Warning signs:** `spring-cloud-starter-gateway:4.x` (the SB 3.4 line) appears on the classpath; Spring Boot autoconfig fails with `dependency on Spring Boot 3.4.x` mismatch.
**Action for planner:** When writing the version catalog, use 2025.0.x. Note this correction explicitly in the plan so reviewers don't propagate the CLAUDE.md error.
**Reference:** [Spring Cloud 2025.0.0 release announcement](https://spring.io/blog/2025/05/29/spring-cloud-2025-0-0-is-abvailable/), [Spring Cloud 2025.0.2 (April 2026)](https://spring.io/blog/2026/04/02/spring-cloud-2025-0-2-aka-northfields-has-been-released/), [Supported Versions wiki](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions).

### Pitfall F: Compose `include:` vs `extends:` portability

**What goes wrong:** Root `docker-compose.yml` uses `include:` (Compose v2.20+). Some users on older Docker Desktop installs (<4.x) won't have it.
**How to avoid:** Default = `include:` per CONTEXT.md discretion. README documents minimum Docker Desktop 4.x. If a contributor reports failure, fall back to `extends:` per service or just use `docker compose -f infra/docker-compose.yml up`.
**Warning signs:** `docker compose up` from repo root errors with `unknown field include` — user is on a stale Compose.

### Pitfall G: shadcn CLI defaults to Tailwind v4 + React 19 → generated components incompatible with locked Tailwind v3

**What goes wrong:** `pnpm dlx shadcn@latest init` defaults to v4 since the CLI bifurcated. Generated components reference Tailwind v4 classes that don't exist in v3.
**How to avoid:** Use `pnpm dlx shadcn@2.x init` to pin the legacy v3 path. After init, verify `components.json` has `"tailwind.config": "tailwind.config.ts"` (v3 path) and `"tailwind.cssVariables": true` (UI-SPEC requirement). Phase 0 generates **zero** components, so the only invocation is `init`.
**Warning signs:** `components.json` references `tailwind.config.js` with v4-style `@import "tailwindcss"` directives; classes like `size-*` (v4-only) appear in generated files.

### Pitfall H: Vite + Vitest major-version mismatch

**What goes wrong:** Vitest 4 requires Vite ≥ 6 + Node ≥ 20. If Phase 0 picks Vite 5 + Vitest 4, Vitest fails to load Vite config.
**How to avoid:** Match Vite major to Vitest major. Phase 0 picks **Vite 5 + Vitest 3**. (CLAUDE.md option: Vite 6 + Vitest 3 also works; Vite 6 + Vitest 4 also works. Vite 5 + Vitest 4 ≠ supported.)
**Warning signs:** `vitest` errors at config-load with peer-dep warnings about Vite major.

### Pitfall I: react-leaflet v5 require React 19

**What goes wrong:** Phase 0 doesn't ship any map code (Phase 8 does), but the package is sometimes added eagerly during scaffolding. `react-leaflet@latest` is v5+ which requires React 19; React 18 + react-leaflet v5 produces peer-dep errors and runtime crashes.
**How to avoid:** Don't install react-leaflet in Phase 0 at all. When Phase 8 lands, pin `react-leaflet@~4.2`.
**Warning signs:** `package.json` includes `react-leaflet` as a Phase 0 dep (it shouldn't).

### Pitfall J: dnd-kit `@dnd-kit/react` is pre-1.0 / unstable

**What goes wrong:** Same as above — Phase 0 doesn't ship dnd code. But if added eagerly, picking `@dnd-kit/react` (v0.4.0 experimental) instead of `@dnd-kit/core@~6` produces runtime API breaks.
**How to avoid:** Don't install dnd-kit in Phase 0 at all. When Phase 8 lands, pin `@dnd-kit/core@~6` + `@dnd-kit/sortable`.
**Warning signs:** `@dnd-kit/react` in `package.json` (it shouldn't be in Phase 0).

## Code Examples

### Service health placeholder controller (D-01)

```java
// services/auth-service/src/main/java/com/tripplanner/auth/health/HealthPlaceholderController.java
// Source: CONTEXT.md D-01
package com.tripplanner.auth.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthPlaceholderController {

    @GetMapping("/__health")
    public Map<String, Object> health() {
        return Map.of(
            "service", "auth-service",
            "status", "UP",
            "phase", 0
        );
    }
}
```

(For api-gateway, the controller path is the same shape but the service name is `"api-gateway"`. Phase 0 chooses to put `/__health/gateway` as either a route table entry that points back to localhost or — cleaner — a direct `@RestController` on the gateway that the routes table forwards to via `Path=/__health/gateway`.)

### Servlet MDC enrichment filter (libs/observability)

```java
// libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java
// Source: docs/02-architecture.md §6.3 + Spring Boot 3.x Observation reference
package com.tripplanner.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class MdcEnrichmentFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public MdcEnrichmentFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        var span = tracer.currentSpan();
        if (span != null) {
            MDC.put("traceId", span.context().traceId());
            MDC.put("spanId", span.context().spanId());
        }
        var requestId = req.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        // userId populated by Phase 1's JwtCommonFilter; empty in Phase 0
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.clear();
        }
    }
}
```

### Spring Boot 3.x AutoConfiguration registration

```
# libs/observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
# Source: SB 3.x convention; replaces spring.factories entry
com.tripplanner.observability.ObservabilityAutoConfiguration
```

```java
// libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java
package com.tripplanner.observability;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.Filter")
    public FilterRegistrationBean<MdcEnrichmentFilter> mdcEnrichmentFilter(Tracer tracer) {
        var bean = new FilterRegistrationBean<>(new MdcEnrichmentFilter(tracer));
        bean.setOrder(Integer.MIN_VALUE + 100); // run early but after Spring's tracing filter
        return bean;
    }

    // (Reactive equivalent for the gateway lives in a separate @ConditionalOnClass(WebFilter)
    //  configuration class; both can co-exist in the same auto-config import file.)
}
```

### Logback JSON config baseline

```xml
<!-- libs/observability/src/main/resources/logback-spring-base.xml -->
<!-- Source: docs/02-architecture.md §6.3, logstash-logback-encoder docs -->
<included>
    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <timestamp><fieldName>timestamp</fieldName></timestamp>
                <logLevel><fieldName>level</fieldName></logLevel>
                <pattern>
                    <pattern>{"service":"${spring.application.name:-unknown}"}</pattern>
                </pattern>
                <mdc>
                    <includeMdcKeyName>traceId</includeMdcKeyName>
                    <includeMdcKeyName>spanId</includeMdcKeyName>
                    <includeMdcKeyName>userId</includeMdcKeyName>
                    <includeMdcKeyName>requestId</includeMdcKeyName>
                </mdc>
                <message><fieldName>message</fieldName></message>
                <stackTrace><fieldName>stackTrace</fieldName></stackTrace>
            </providers>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="JSON_STDOUT" />
    </root>
</included>
```

Each service's `logback-spring.xml` simply `<include resource="logback-spring-base.xml" />`s this from the lib's classpath.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring-cloud-sleuth` for tracing | Micrometer Tracing (built into SB 3.x) | SB 3.0 (Nov 2022) — Sleuth EOL | Use Micrometer; never add Sleuth. |
| Manual `ServerHttpObservationFilter` registration | Auto-configured | Spring Framework 6.1 / SB 3.2 | Don't register manually. |
| `META-INF/spring.factories` for autoconfig | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | SB 3.0 | New convention for shared lib autoconfig (used by `libs/observability`). |
| Flyway with bundled DB drivers | Flyway 10 modular drivers (`flyway-database-postgresql`, etc.) | Flyway 10 (2024) | Mandatory explicit dep — Pitfall 3. |
| `docker-compose` (Python, v1) | `docker compose` plugin (Go, v2) | Docker Desktop 4.x | Use `docker compose` syntax everywhere. |
| Spring Cloud `2024.0` (Moorgate) for SB 3.5 | Spring Cloud `2025.0` (Northfields) for SB 3.5 | Spring Cloud 2025.0.0 (May 2025) | CLAUDE.md is wrong; use 2025.0.x. |
| `react-router-dom` v6 with separate package | `react-router` v7 (combined imports) | RR 7.0 (Nov 2024) | CONTEXT.md D-12 honors v6.30.x; v7 is a Phase 7+ option. |
| `tailwind.config.js` | `tailwind.config.ts` (TypeScript) | community convention | Phase 0 uses `.ts` per UI-SPEC. |
| Tailwind v3 `tailwind.config.js`-driven | Tailwind v4 CSS-first config | Jan 2025 (v4.0) | NOT for Phase 0 — locked at v3. |
| `npm` / `yarn` | `pnpm` (with Corepack `packageManager` field) | community convention | D-13 locks pnpm 9. |
| `localStorage` for JWT | In-memory access token + httpOnly cookie for refresh | community + OWASP | Phase 2 implements this; Phase 0 prepares Zustand store skeleton. |

**Deprecated/outdated:**
- `spring-cloud-sleuth` — replaced by Micrometer Tracing.
- `com.github.tomakehurst:wiremock` artifact — replaced by `org.wiremock:wiremock` (or `org.wiremock.integrations:wiremock-spring-boot` for SB integration).
- Flyway `flyway-core` alone (without driver) — for any non-H2 DB you need the modular driver.
- Eureka 2.0 (Netflix abandoned) — Spring Cloud's fork is what we use, still maintained.
- Sass-API default in Vite < 6 — fine for Phase 0 (Vite 5).
- `npm install -g <anything>` patterns — replaced by Corepack + `dlx`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Boot 3.5.14 is the latest 3.5.x patch as of 2026-05-08. (Cited as 3.5.13 by spring.io 2026-04-02 announcement; 3.5.14 likely shipped after.) | Standard Stack | Negligible — pin to whatever `mvn central` shows for the latest 3.5.x at scaffold time. Plan should use `gsd-sdk` or `mvn versions` lookup. |
| A2 | Spring Cloud 2025.0.2 is current; latest 2025.0.x patch may have shifted. | Standard Stack, Pitfall E | Low — re-verify at scaffold time. |
| A3 | `flyway-database-postgresql` is automatically version-managed by Spring Boot 3.5's BOM. | Pattern 1, Pitfall A | Low — if not, pin explicitly. Empirically SB does manage Flyway 10.x family. |
| A4 | Logback `logstash-logback-encoder` 7.4 is compatible with SB 3.5 + Java 21. | Standard Stack, Code Examples | Low — 7.4 is widely used; alternative versions exist. Verify by running a service with the dep. |
| A5 | Compose `include:` directive is supported by Docker Desktop 4.x default install. | Pattern 7, Pitfall F | Low — most users on macOS/Win Docker Desktop 4.x have 2.20+. Fallback: drop the root alias and use `docker compose -f infra/docker-compose.yml up`. |
| A6 | `pnpm dlx shadcn@2.x init` correctly emits Tailwind v3-compatible `components.json`. | Pattern 9, Pitfall G | Medium — shadcn changelog at scaffold time should be re-checked. Alternative: run `shadcn@latest init` and answer prompts to choose v3 path explicitly. |
| A7 | Vite 5.4.x latest patch + Vitest 3 latest patch are mutually compatible with Node 20. | Standard Stack, Pitfall H | Low — known good combination. |
| A8 | The Spring Cloud Gateway "classic reactive" `spring-cloud-starter-gateway` artifact name is preserved through Northfields (the Web MVC variant deprecation does not affect us). | Standard Stack | Low — Phase 0 uses reactive Gateway. Verify by spring.io 2025.0 release post. |
| A9 | NFR-04 (free tier) is satisfied if Phase 0 introduces no paid SaaS dependencies and uses MailHog locally. | Phase Requirements | None — Phase 0 ships nothing but local containers + open source. |

## Open Questions (RESOLVED via CONTEXT.md addendum 2026-05-08)

> All six Open Questions raised in initial research have been resolved or deferred. Each is annotated with its resolution below. The Plan-Phase Addendum decisions (D-30..D-33) in `00-CONTEXT.md` are the authoritative source.

1. **Spring Cloud train (2024.0 vs 2025.0):** CLAUDE.md says 2024.0; verified sources say 2025.0 for SB 3.5.
   - What we know: Spring Cloud 2025.0 is built on SB 3.5.x; 2024.0 is built on SB 3.4.x.
   - What's unclear: Whether the user/team intentionally locked 2024.0 (Moorgate) for some reason despite the train mismatch.
   - Recommendation: **Plan uses Spring Cloud 2025.0.x.** If the user objects in plan-check, surface this Open Question; it's documented in Pitfall E. Update CLAUDE.md to reflect the verified train.
   - **RESOLVED by D-30 (CONTEXT.md addendum 2026-05-08):** `gradle/libs.versions.toml` pins `springCloud = "2025.0.2"` (or latest 2025.0.x patch). CLAUDE.md is being treated as out-of-date on this pin per D-30.

2. **shadcn CLI version path (2.x explicit vs. @latest with v3 prompts):**
   - What we know: `shadcn@latest` defaults to v4 + React 19; `shadcn@2.x` is the legacy v3 path.
   - What's unclear: Whether `shadcn@latest` exposes a CLI flag (`--tailwind v3`) to force the v3 path without needing the `@2.x` pin.
   - Recommendation: Default to `pnpm dlx shadcn@2.x init`. Discretion item.
   - **RESOLVED by D-32 (CONTEXT.md addendum 2026-05-08):** Use `pnpm dlx shadcn@latest init` with explicit prompt answers (Style=default, Base color=slate, CSS file=src/index.css, CSS variables=yes, Tailwind config=tailwind.config.ts, Path alias=@/*→src/*) — the LOCKING MECHANISM is the prompt answers, NOT the version pin. Prompt answers are documented in `frontend/README.md` (Plan 09 Task 9.4) so Phase 7 reproduces.

3. **Compose `include:` portability:**
   - What we know: `include:` is Compose 2.20+ (~Docker Desktop 4.30+).
   - What's unclear: Whether the user has tested this on their Docker Desktop install.
   - Recommendation: README documents minimum Docker Desktop 4.x; if `include:` fails, the planner emits a fallback step.
   - **DEFERRED:** README (root + `infra/README.md`) documents Docker Desktop 4.30+ requirement and the fallback `docker compose -f infra/docker-compose.yml up` invocation. No code-level mitigation in Phase 0; user-environment-specific.

4. **Vite 5 vs Vite 6 final decision:**
   - What we know: Both are valid per CLAUDE.md and CONTEXT.md discretion.
   - What's unclear: Whether the user prefers stability (Vite 5) or modernity (Vite 6).
   - Recommendation: **Vite 5.4.x** — the Vite 5 + Vitest 3 + Tailwind v3 combination is battle-tested. Vite 6 introduces breaks for negligible Phase 0 benefit.
   - **RESOLVED by D-31 (CONTEXT.md addendum 2026-05-08):** Vite 6.x + Vitest 3.x (or 4.x). Per D-31 the prior "Vite 5.4.x" recommendation is superseded; v5.4 → v6 migration burden is zero in greenfield. The §Standard Stack table line for Vite has been updated to reflect the D-31 pin.

5. **`spring.application.name` for eureka-server:**
   - What we know: D-25 says set in every service.
   - What's unclear: Whether eureka-server (which doesn't trace itself) needs this set for Zipkin grouping. (It does NOT register with itself per Pattern 4 config.)
   - Recommendation: Set `spring.application.name=eureka-server` for log clarity even if Zipkin grouping is irrelevant; harmless.
   - **RESOLVED by D-25 (already locked in original CONTEXT.md):** Set `spring.application.name=eureka-server` in eureka-server's `application.yml`. This was D-25's original mandate — apply uniformly to every service. No additional addendum decision needed.

6. **`libs/observability` reactive (gateway) vs servlet (services) MDC filter:**
   - What we know: Gateway is WebFlux; auth/trip/destination are Servlet. MDC enrichment filter must be implemented twice.
   - What's unclear: Whether `libs/observability` ships both filters and gates each via `@ConditionalOnClass(WebFilter.class)` / `@ConditionalOnClass(jakarta.servlet.Filter)`, or whether the gateway gets a separate sub-module.
   - Recommendation: Single lib, two filter classes, conditional registration. Cleaner. Code Examples §"AutoConfiguration registration" shows the pattern.
   - **DEFERRED to executor's discretion within D-04 hard rule:** D-04 states `libs/observability` ships both reactive + servlet MDC filter classes; the conditional registration pattern (Code Examples §"AutoConfiguration registration") is the implementation. No manual `ServerHttpObservationFilter` registration anywhere — that part of D-04 is non-negotiable. Beyond that hard rule, Plan 03's executor chooses single-lib-two-classes vs. two-sub-modules; both satisfy D-04.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 (Temurin) | All Spring services | TBD — verify at scaffold | ≥21.0.x | Install via `sdkman` or `actions/setup-java@v4` in CI |
| Gradle wrapper | Build system | self-bootstraps from `gradlew` | 8.14.x | none — wrapper committed |
| Node 20 | Frontend build | TBD — verify at scaffold | ≥20.10.x | `actions/setup-node@v4` in CI; nvm locally |
| pnpm 9 | Frontend pkg manager | Corepack auto-bootstraps from `packageManager` field | 9.15.x | `corepack enable && corepack prepare pnpm@9.15.0 --activate` |
| Docker Desktop 4.x (or OrbStack) | Compose stack | TBD — user-side | 4.30+ recommended for `include:` | If <4.30, drop root `docker-compose.yml` alias and document `docker compose -f infra/docker-compose.yml up` |
| `curl` (in service container images) | Compose healthchecks | YES — included in default Spring Boot images via openjdk base | n/a | Use `wget` or a Spring Boot Actuator-based healthcheck without curl by hitting `/actuator/health` from the host instead (less ideal) |
| Network outbound (Maven Central, npm registry) | Initial scaffold | TBD | n/a | Use a corp mirror if behind a proxy |

**Missing dependencies with no fallback:** None expected at this stage. Java 21 + Docker Desktop 4.x + Node 20 are baseline modern dev-env requirements. Document them in README prerequisites.

**Missing dependencies with fallback:** None.

**Action for plan:** First task in the plan should `command -v java && java --version | head -1` and same for `docker compose version` and `node --version` and `pnpm --version` to confirm.

## Validation Architecture

> Phase 0 has minimal automated tests (it is mostly infrastructure). The "test suite" is largely the docker-compose smoke-flow plus one Vitest unit test for the frontend mount. Validation strategy below.

### Test Framework

| Property | Value |
|----------|-------|
| Backend test framework | JUnit 5 (managed by SB 3.5 — 5.12.x) + Mockito (managed by SB 3.5 — 5.x) + Spring Boot Test |
| Backend integration framework | Testcontainers 1.21.x via `spring-boot-testcontainers` and `@ServiceConnection` (Phase 0 declares deps; first real test arrives in Phase 1) |
| Backend test config file | each service `build.gradle.kts` `test { useJUnitPlatform() }` |
| Backend quick run command | `./gradlew :services:<svc>:test` |
| Backend full suite command | `./gradlew check` (runs `test` + Jacoco + checkstyle stubs) |
| Frontend test framework | Vitest 3.x + React Testing Library 16.x + jsdom |
| Frontend test config file | `frontend/vite.config.ts` (with `test:` block) — Vitest reads this |
| Frontend quick run command | `pnpm --filter frontend test --run` |
| Frontend full suite command | `pnpm --filter frontend test --run && pnpm --filter frontend build` |
| Compose smoke command | `scripts/smoke.sh` (planner creates) — see "Sampling Rate" below |
| Phase gate full suite | `./gradlew check && pnpm --filter frontend test --run && pnpm --filter frontend build && bash scripts/smoke.sh` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| NFR-04 | "free tier only" — no paid SaaS deps introduced | manual + license scan | grep `package.json` and `libs.versions.toml` for any private/paid registries; OWASP Dependency-Check is Phase 10 | ❌ Wave 0 — but no automation needed; visual review is sufficient |
| ROADMAP SC #1 | `docker compose up` healthy in <60s | smoke (script) | `bash scripts/smoke.sh` (measures wall-clock from `up -d` to all containers `healthy`) | ❌ Wave 0 — `scripts/smoke.sh` does not exist; planner creates |
| ROADMAP SC #2 | All 4 services visible in Eureka dashboard | smoke (curl) | `bash scripts/smoke.sh` includes `curl -fs localhost:8761/eureka/apps \| grep -c '<application>'` ≥ 4 | ❌ Wave 0 |
| ROADMAP SC #3 | `curl localhost:8080/actuator/health` returns `{"status":"UP"}` | smoke (curl) | `curl -fs localhost:8080/actuator/health \| jq -e '.status == "UP"'` | ❌ Wave 0 (script) |
| ROADMAP SC #3 (extended) | `/__health/<svc>` end-to-end through gateway returns UP for each backend service | smoke (curl) | for svc in auth trip destination; do `curl -fs localhost:8080/__health/$svc`; done — each returns `{"service":"$svc-service","status":"UP","phase":0}` | ❌ Wave 0 |
| ROADMAP SC #4 | `localhost:5173` renders without console errors | manual or Vitest mount + Playwright (Phase 7+) | Vitest: `App.test.tsx` renders "Trip Planner" + "Your itinerary, day by day."; manual: open browser, check DevTools Console (UI-SPEC §Phase 0 Verification Checklist) | ⚠ partial — `App.test.tsx` exists in Wave 0 plan; full console-error check is manual until Playwright in Phase 10 |
| ROADMAP SC #5 | Per-service `*_flyway_schema_history` tables present | smoke (psql) | `psql -h localhost -U postgres -d tripplanner -c "\dt auth.*"` shows `auth_flyway_schema_history`; same for trip + destination | ❌ Wave 0 |
| Pitfall A verification | `flyway-database-postgresql` on every DB-backed service classpath | smoke (gradle) | `./gradlew :services:auth-service:dependencies --configuration runtimeClasspath \| grep flyway-database-postgresql` (and trip + destination) | ❌ Wave 0 (script step) |
| Pitfall C verification | `spring.application.name` set per service | smoke (yaml grep) | `grep "application:\\n.*name:" services/*/src/main/resources/application.yml` | ❌ Wave 0 |
| Pitfall E verification | Spring Cloud 2025.0.x (NOT 2024.0.x) | smoke (yaml grep) | `grep springCloud gradle/libs.versions.toml \| grep 2025.0` | ❌ Wave 0 |
| `libs/observability` AutoConfig loads | services log JSON with `traceId`/`spanId` MDC fields | smoke (log inspect) | `docker compose logs auth-service \| jq '.traceId' -r \| head -1` returns a non-empty trace ID after a request | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** Build only — `./gradlew :services:<svc>:compileJava` for backend changes; `pnpm --filter frontend build` for frontend changes. Avoids long compose smoke on every commit.
- **Per wave merge (Wave 0 → Wave 1, etc.):** `./gradlew check && pnpm --filter frontend test --run && pnpm --filter frontend build`. Pure code-level checks.
- **Phase gate (Phase 0 done):** Full smoke: `bash scripts/smoke.sh` → exits 0 only if all 5 ROADMAP SCs pass within 60s. Manual UI-SPEC §Phase 0 Verification Checklist for SC #4 (DevTools console-clean assertion).

### Wave 0 Gaps

- [ ] `scripts/smoke.sh` — wraps `docker compose up -d`, polls health endpoints with `wait-on` or curl loops with timeouts, asserts ROADMAP SCs #1-#5; exits 0 on success.
- [ ] `frontend/src/App.test.tsx` — Vitest smoke test rendering "Trip Planner" heading.
- [ ] `frontend/vitest.config.ts` (or `test:` block in `vite.config.ts`) with jsdom environment + setup file.
- [ ] `frontend/src/test/setup.ts` — `import '@testing-library/jest-dom'` global.
- [ ] No backend integration tests in Phase 0 — Phase 1 lands the first one.

**Smoke script outline (planner emits the actual file):**
```bash
#!/usr/bin/env bash
# scripts/smoke.sh — Phase 0 health gate
set -euo pipefail

START=$(date +%s)
docker compose -f infra/docker-compose.yml up -d --wait --wait-timeout 60

# SC #1: <60s healthy (--wait-timeout above enforces this)
ELAPSED=$(( $(date +%s) - START ))
echo "Compose healthy in ${ELAPSED}s"
[[ $ELAPSED -lt 60 ]] || { echo "FAIL: compose took ${ELAPSED}s (>60s)"; exit 1; }

# SC #2: 4 services in Eureka
COUNT=$(curl -fs http://localhost:8761/eureka/apps -H "Accept: application/json" \
    | jq '.applications.application | length')
[[ $COUNT -ge 4 ]] || { echo "FAIL: only $COUNT services in Eureka (expected 4)"; exit 1; }

# SC #3: gateway health UP
curl -fs http://localhost:8080/actuator/health | jq -e '.status == "UP"' >/dev/null \
    || { echo "FAIL: gateway /actuator/health not UP"; exit 1; }

# SC #3 ext: each /__health/<svc> reachable through gateway
for svc in auth trip destination; do
    curl -fs http://localhost:8080/__health/$svc | jq -e '.status == "UP"' >/dev/null \
        || { echo "FAIL: /__health/$svc not UP via gateway"; exit 1; }
done

# SC #5: per-service Flyway history tables exist
for svc in auth trip destination; do
    EXISTS=$(docker compose exec -T postgres psql -U postgres -d tripplanner \
        -tAc "SELECT 1 FROM information_schema.tables WHERE table_schema='$svc' AND table_name='${svc}_flyway_schema_history'")
    [[ "$EXISTS" == "1" ]] || { echo "FAIL: $svc.${svc}_flyway_schema_history missing"; exit 1; }
done

echo "Phase 0 smoke: all SCs pass"
```

### Health Gate Thresholds (Phase 0 done)

- ROADMAP SC #1: compose healthy <60s ✓ (smoke.sh enforces)
- ROADMAP SC #2: ≥4 services registered with Eureka ✓ (smoke.sh asserts)
- ROADMAP SC #3: `localhost:8080/actuator/health` `{"status":"UP"}` ✓ (smoke.sh asserts)
- ROADMAP SC #3 ext: `/__health/<svc>` reachable via gateway for all 3 backends ✓ (smoke.sh asserts; D-02 validation)
- ROADMAP SC #4: `localhost:5173` renders heading + subtitle, zero console errors ✓ (manual UI-SPEC checklist; Vitest smoke for mount-only)
- ROADMAP SC #5: per-service `*_flyway_schema_history` tables exist ✓ (smoke.sh asserts; Pitfall 3 verification)

## Security Domain

> Phase 0 ships **no auth, no Spring Security, no real endpoints**. Auth is owned by Phase 1 (gateway JWT validation) and Phase 2 (auth service signup/login). However, Phase 0 sets the security scaffolding posture (D-22 binds downstream ports to loopback) so a brief security domain summary is included.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | Schema-per-service + per-service DB user with schema-scoped grants (D-08) |
| V2 Authentication | no (Phase 0) | Phase 1+2 own |
| V3 Session Management | no (Phase 0) | Phase 2 owns (httpOnly cookie + refresh rotation) |
| V4 Access Control | yes (network-level) | Loopback-bind downstream service ports (D-22) — partial mitigation of Pitfall 1 in dev |
| V5 Input Validation | no (Phase 0 — no real endpoints) | Phase 1+ owns |
| V6 Cryptography | no (Phase 0 — no secret usage) | Phase 2 owns (bcrypt cost 12, HS256 JWT) |
| V7 Error Handling & Logging | yes | Logback JSON encoder must NOT log raw `Authorization` headers; redaction patterns for `password`/`token`/`secret` field names ship with `libs/observability` |
| V8 Data Protection | yes | `.env` gitignored; `.env.example` committed with placeholder values only |
| V14 Configuration | yes | `spring.jpa.hibernate.ddl-auto=validate`; `server.error.include-stacktrace=never` in `docker` profile |

### Known Threat Patterns for {Spring Boot 3.5 + Spring Cloud Gateway 2025.0 + React 18 stack}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| X-User-Id header spoofing on direct downstream port (Pitfall 1) | Spoofing | Phase 0: bind downstream ports to `127.0.0.1`. Phase 1: `JwtCommonFilter` rejects `X-User-Id` without matching JWT. Phase 0 partially mitigates by network ACL only. |
| Flyway checksum mismatch DOS (Pitfall 3) | DoS (service refuses to start) | Per-service `<svc>_flyway_schema_history` tables (D-09). |
| Trace context loss enabling repudiation (Pitfall 7) | Repudiation | Single `micrometer-tracing-bom` pin + `libs/observability` auto-config (D-04, D-26). |
| Compose port leakage to LAN | Information Disclosure | Bind downstream ports to `127.0.0.1` (D-22); only gateway, frontend, mailhog UI, zipkin UI bind to `0.0.0.0`. |
| Flyway DDL drift (`ddl-auto=update` would let JPA secretly diverge from migrations) | Tampering | `spring.jpa.hibernate.ddl-auto=validate` in every service (PITFALLS.md tech-debt patterns). |
| Logging Authorization headers in plaintext | Information Disclosure | Logback masking converter for `Authorization`, `password`, `token`, `secret` field names (Phase 1 ships the converter; Phase 0 reserves the design). |
| Default Postgres `postgres` superuser used by services | Privilege Escalation | Per-service users (`auth_svc`, `trip_svc`, `destination_svc`) with `USAGE, CREATE` only on their own schema; `postgres` superuser only used by `init.sql` (D-08). |

## Sources

### Primary (HIGH confidence)
- [Spring Cloud 2025.0.0 (Northfields) release announcement](https://spring.io/blog/2025/05/29/spring-cloud-2025-0-0-is-abvailable/) — confirms 2025.0 is the SB 3.5 train (Pitfall E correction)
- [Spring Cloud 2025.0.2 (Northfields) release announcement, April 2026](https://spring.io/blog/2026/04/02/spring-cloud-2025-0-2-aka-northfields-has-been-released/) — confirms latest 2025.0.2 patch + SB 3.5.13 base
- [Spring Cloud Supported Versions wiki](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions) — confirms 2024.0=SB3.4 / 2025.0=SB3.5 / 2025.1=SB4
- [Spring Cloud 2024.0.3 release announcement, Dec 2025](https://spring.io/blog/2025/12/12/spring-cloud-2024-0-3-aka-northfields-has-been-released/) — confirms 2024.0.3 is built on SB 3.4.12 (NOT 3.5)
- [Flyway issue #3969: Unsupported Database PostgreSQL 16 in Flyway 10.x](https://github.com/flyway/flyway/issues/3969) — confirms `flyway-database-postgresql` dep is mandatory (Pitfall A)
- [Maven Repository: org.flywaydb:flyway-database-postgresql](https://mvnrepository.com/artifact/org.flywaydb/flyway-database-postgresql) — versions catalog
- [Spring Boot 3 Observability blog post (Oct 2022)](https://spring.io/blog/2022/10/12/observability-with-spring-boot-3/) — Micrometer Tracing + OTel Zipkin exporter setup
- `./CLAUDE.md` Technology Stack table — locked version pins (with Spring Cloud train correction noted)
- `./.planning/phases/00-monorepo-scaffolding/00-CONTEXT.md` — locked decisions D-01 through D-29
- `./.planning/phases/00-monorepo-scaffolding/00-UI-SPEC.md` — frontend bootstrap design contract
- `./docs/02-architecture.md` — service decomposition, repo layout, observability stack
- `./docs/08-deployment.md` — Docker Compose layout, ports, env vars
- `./.planning/research/PITFALLS.md` — Pitfall 1, 3, 7, 10 verbatim source
- `./.planning/ROADMAP.md` — Phase 0 success criteria #1-#5

### Secondary (MEDIUM confidence)
- [Vite documentation Getting Started](https://vite.dev/guide/) — Vite 5 → Vitest pairing
- [Vitest Getting Started](https://vitest.dev/guide/) — Vite/Vitest major-version compatibility
- [shadcn/ui Tailwind v4 docs](https://ui.shadcn.com/docs/tailwind-v4) — v3 vs v4 init paths
- [Cache stampede prevention in Spring Boot, Medium](https://medium.com/@AlexanderObregon/cache-stampede-protection-in-spring-boot-applications-341f87b37649) — informational (not Phase 0)
- [Tracing in Spring Boot 3 WebFlux, Better Programming](https://betterprogramming.pub/tracing-in-spring-boot-3-webflux-d432d0c78d3e) — reactive↔servlet trace propagation
- [Eureka registration race conditions, Spring Cloud Netflix issue #3941](https://github.com/spring-cloud/spring-cloud-netflix/issues/3941) — Pitfall 10 background

### Tertiary (LOW confidence — validate before relying)
- General "How to set up Vite + React + Vitest 2026" tutorials — corroborate but not authoritative; Vite/Vitest official docs win.
- Boilerplate repos like joaopaulomoraes/reactjs-vite-tailwindcss-boilerplate — useful for shape, not for version pins.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every backend version pin verified against spring.io / GitHub releases / Flyway issue tracker; frontend pins verified against CLAUDE.md (which is locked).
- Architecture patterns: HIGH — D-01 through D-29 are user-locked; only Claude's discretion items have ambiguity, and those are flagged in §Open Questions.
- Pitfalls: HIGH — Pitfalls A-D are restated from PITFALLS.md (already HIGH-confidence research from a prior phase); Pitfall E is NEW and verified during this session against three primary spring.io sources; Pitfalls F-J are MEDIUM (corroborated against CLAUDE.md and ecosystem knowledge).
- Validation architecture: HIGH for the smoke-script approach (the success criteria are explicitly in ROADMAP); MEDIUM for "is the smoke script the right test harness?" — alternative is full-blown integration tests via Testcontainers, but Phase 0 has no domain logic to test, so smoke is right.

**Research date:** 2026-05-08
**Valid until:** 2026-06-08 — re-verify Spring Cloud / Spring Boot patch versions and shadcn CLI default behavior at scaffold time. Backend ecosystem is stable on monthly cadence; frontend (shadcn especially) shifts faster.
