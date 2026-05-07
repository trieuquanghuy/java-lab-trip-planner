# Phase 0: Monorepo Scaffolding - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-08
**Phase:** 0-monorepo-scaffolding
**Areas discussed:** Boot-time surface per service, Shared libs initial content, Frontend Phase 0 scope, Flyway V1 + CI + compose location bundle

---

## Boot-time surface per service

| Option | Description | Selected |
|--------|-------------|----------|
| Actuator only | @SpringBootApplication + Spring Boot Actuator + Eureka client + Flyway V1 (DB-backed). NO application controllers. Cleanest separation. | |
| Actuator + /__health placeholder controller per service | Each service ships GET /__health (double-underscore prefix) returning service name + version. Lets you smoke-test gateway routing in Phase 0 without conflicting with Phase 1's /api/<svc>/_ping. | ✓ |
| Actuator + gateway routes /actuator through to each service | Gateway exposes /api/<svc>/actuator/health routing to downstream actuators. Risk: actuators not normally public. | |

**User's choice:** Actuator + /__health placeholder controller per service.
**Notes:** Smoke-tests end-to-end routing in Phase 0; double-underscore namespace prevents collision with Phase 1's `_ping` controllers. Codified as D-01.

### Follow-up: gateway routing

| Option | Description | Selected |
|--------|-------------|----------|
| Gateway routes /__health/<svc> to each service via static URIs | Adds 4 minimal route entries to gateway's application.yml. Phase 1 reuses the static-URI pattern. | ✓ |
| Gateway only exposes its own /actuator/health | No routing config in Phase 0. Phase 1 adds the full route table from scratch. | |

**User's choice:** Gateway routes /__health/<svc> to each service.
**Notes:** Codified as D-02. Pattern lines up with Phase 1 D-08 (static URIs).

---

## Shared libs initial content (libs/observability, libs/error-handling, libs/api-contracts)

| Option | Description | Selected |
|--------|-------------|----------|
| Mixed: observability fully wired, error-handling minimal stubs, api-contracts empty | observability ships Micrometer+Zipkin+JSON logging+MDC autoconfig (Pitfall 7 satisfied). error-handling: ProblemDetail factory + 2 baseline ErrorCodes. api-contracts: empty Gradle module. | ✓ |
| All three fully scaffolded with Phase 1–ready stubs | Adds GlobalExceptionHandler base + UserContext record. Phase 1 starts faster but Phase 0 takes decisions Phase 1 should own. | |
| All three as empty Gradle modules | Phase 0 puts Micrometer/Logback config inline per service. Phase 1 has more lifting. | |

**User's choice:** Mixed.
**Notes:** Codified as D-04 (observability fully wired), D-05 (error-handling minimal stubs), D-06 (api-contracts empty), D-07 (libs/jwt-common explicitly NOT in Phase 0). Pitfall 7 enforcement codified across D-04, D-25, D-26.

---

## Frontend Phase 0 scope

| Option | Description | Selected |
|--------|-------------|----------|
| Wired provider stack, no real pages | Vite + React 18 + TS + Tailwind v3.4 + shadcn/ui CLI initialized + BrowserRouter + QueryClientProvider + axios + Zustand store skeleton. App.tsx renders single 'Trip Planner' element. | ✓ |
| Bare floor: App.tsx 'Hello' only | Vite + React + TS + Tailwind installed. No router, no query client, no axios. | |
| Full shell with placeholder routes | Provider stack + AppLayout + placeholder routes for /, /login, /search, /trips, /favorites returning stubs. | |

**User's choice:** Wired provider stack, no real pages.
**Notes:** Codified as D-12, D-13 (pnpm 9.x), D-14 (Vitest + RTL with smoke test). shadcn CLI is initialized but no components are generated yet — Phase 7 generates them.

---

## Flyway V1 + CI + compose location bundle

### Flyway V1 migration content

| Option | Description | Selected |
|--------|-------------|----------|
| Empty placeholder per service | Each service ships V1__init.sql with no-op marker. Phase 2/3/5 own real V2+ schemas. Cleanest ownership. Pitfall 3's per-service history table validated with no-op migrations. | ✓ |
| Baseline schemas in Phase 0 | Phase 0 ships full V1 for users, trips, destinations etc. Validates docs/03 upfront but couples Phase 0 to schemas Phase 2/3/5 should own. | |

**User's choice:** Empty placeholder per service.
**Notes:** Codified as D-10. Pitfall 3 enforced via D-08 + D-09.

### CI workflow scope

| Option | Description | Selected |
|--------|-------------|----------|
| Skeleton CI: backend + frontend that build empty repo | backend.yml matrix with `./gradlew check`; frontend.yml lint + test + build. Skip OWASP dep-check, security-tag tests, Playwright E2E. | ✓ |
| Full docs/08 CI now | Complete backend.yml (with security tests + dependency-check) and frontend.yml (with Playwright E2E). | |
| Defer all CI to Phase 10 | No .github/workflows/ in Phase 0. | |

**User's choice:** Skeleton CI.
**Notes:** Codified as D-15, D-16 (ubuntu-24.04 + setup-java@v4 + setup-node@v4), D-17 (push + PR-to-main triggers).

### docker-compose.yml location

| Option | Description | Selected |
|--------|-------------|----------|
| infra/docker-compose.yml + root symlink/wrapper | Honor docs/02 + docs/08 layout. Ship root-level alias so `docker compose up` works from repo root. | ✓ |
| Root docker-compose.yml only (deviate from docs) | Move compose to repo root for ergonomics. | |
| infra/docker-compose.yml only (no root alias) | Strictly follow docs. Every command says `docker compose -f infra/docker-compose.yml ...`. | |

**User's choice:** infra/docker-compose.yml + root alias.
**Notes:** Codified as D-18 (canonical at infra/), D-19 (root alias via Compose `include:` directive).

---

## Claude's Discretion

- Exact filename / format for the gateway's `/__health/<svc>` route configuration (`application.yml` route table vs `@Configuration` Java DSL).
- ESLint base config: `eslint-plugin-react` + `eslint-plugin-react-hooks` + `@typescript-eslint` triad unless researcher finds a 2026 baseline.
- `infra/postgres/init.sql` user-creation strategy (templating vs init script reading env).
- Vite version pin: 5.x or 6.x (must match Vitest major).
- React Router v6.30.x (locked spec) vs v7.x (advised for greenfield) — defaulting to v6.30.x.
- Exact `npx shadcn` invocation for Tailwind v3-compatible component generation (registry flag).
- Logback JSON encoder choice (`logstash-logback-encoder` recommended; equivalent allowed if MDC fields propagate).
- Root `docker-compose.yml` alias mechanism: `include:` (Compose 2.20+) vs `extends:`. Verify default Docker Desktop 4.x supports `include:`.

## Deferred Ideas

- `libs/jwt-common` — Phase 1 owns creation.
- Real V2+ Flyway migrations — Phase 2 / Phase 3 / Phase 5.
- OWASP dependency-check, security-tagged JUnit suite, Playwright E2E in CI — Phase 10.
- Image build + GHCR push + Fly.io release workflow — v2 backlog.
- shadcn/ui component generation — Phase 7 generates components when first used.
- React Router placeholder routes for `/login`, `/search`, `/trips`, `/favorites` — Phase 7.
- AppLayout (header/nav/footer) + theme toggle UI — Phase 7 / Phase 9.
- JWT secret rotation, CORS allowlist, Redis rate-limiter keys — Phase 1.
- `lb://` Eureka-routed gateway traffic — Phase 10 hardening.
- HikariCP / connection pool tuning beyond Spring Boot defaults — Phase 10.
- `prod` Spring profile — out of scope for v1 (local-only deployment).
- Postgres `pg_trgm` / `unaccent` extensions — Phase 3 adds via repeatable Flyway migrations.
