# Trip Planner

> Plan multi-day trips, discover attractions, and assemble itineraries.

A web app for travelers who want to go from "I want to visit somewhere" to a structured day-by-day itinerary in minutes. Users search a city or country, browse a curated list of attractions, open detail views with photos and opening hours, and assemble the ones they want into trips with drag-drop day-by-day scheduling. Trips and favorited destinations persist across sessions behind email-verified accounts.

This is a portfolio/practice project demonstrating Spring Boot microservices + React.

---

## Quickstart

### Prerequisites

- **Docker Desktop 4.30+** (for Compose `include:` directive support)
- **Java 21 (Temurin)** — for local IDE / non-Docker dev
- **Node 20 LTS** + **pnpm 9** (auto-bootstrapped via Corepack from `frontend/package.json`)

### Boot the stack

```bash
cp .env.example .env       # do NOT edit unless overriding defaults
docker compose up -d --wait
```

The root `docker-compose.yml` is a thin alias: it uses Docker Compose's `include:` directive
(Compose 2.20+ feature, per D-19) to pull in `infra/docker-compose.yml`. This means
`docker compose up` works from the repo root without any `-f` flag. Both files coexist by design.

### Browse

| Surface | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| API gateway health | http://localhost:8080/actuator/health |
| Eureka dashboard | http://localhost:8761 |
| Mailhog UI | http://localhost:8025 |
| Zipkin UI | http://localhost:9411 |

### Smoke test

```bash
bash scripts/smoke.sh
```

Exits 0 only if all 5 ROADMAP success criteria pass: compose healthy, all 4 backend
services in Eureka, gateway `/actuator/health` UP, frontend reachable, per-service
Flyway history tables present.

---

## Cold-start window note

Eureka registration adds a **30–60 second warm-up** on the first `docker compose up`.
The compose stack uses healthchecks + `depends_on: { condition: service_healthy }`
(D-20) and tuned Eureka intervals (`registry-fetch-interval-seconds: 5`,
`lease-renewal-interval-in-seconds: 5`, D-21) to keep this under 60 seconds.

If `bash scripts/smoke.sh` fails on the first run, wait 30s and re-run — registrations
should propagate by then.

---

## Architecture summary

5 backend services (api-gateway, auth-service, trip-service, destination-service,
eureka-server) + 3 shared libs (observability, error-handling, api-contracts) + a React
frontend. Single PostgreSQL 16 instance with schema-per-service (`auth`, `trip`,
`destination`) and per-service DB users (D-08, neutralizes Pitfall 3 cross-schema joins).

Per D-22, downstream services (auth :8081, trip :8082, destination :8083) and Postgres,
Redis, and Eureka all bind to **127.0.0.1 only** in Docker Compose — the gateway is the
only public surface in dev. Frontend and observability UIs (Mailhog 8025, Zipkin 9411)
bind to 0.0.0.0 so they're reachable from the host browser.

### Port assignments

| Service | Port |
|---------|------|
| frontend (Vite) | 5173 |
| api-gateway | 8080 |
| auth-service | 8081 |
| trip-service | 8082 |
| destination-service | 8083 |
| eureka-server | 8761 |
| postgres | 5432 |
| redis | 6379 |
| mailhog smtp / web | 1025 / 8025 |
| zipkin | 9411 |

Source of truth: `docs/08-deployment.md §1.3`.

---

## Build & test

### Backend

```bash
./gradlew check        # compile + test + jacoco for every subproject
```

Each service runs Flyway migrations against its own `<schema>_flyway_schema_history`
table (Pitfall 3 / D-09) so per-service migration histories never collide on the shared
Postgres instance.

### Frontend

```bash
pnpm --filter frontend test --run
pnpm --filter frontend build
```

Frontend uses Vitest 3.x (D-31) + React Testing Library 16.x; Playwright is added in
Phase 10 hardening, not Phase 0.

---

## Repository layout

```
.
├── services/
│   ├── eureka-server/
│   ├── api-gateway/
│   ├── auth-service/
│   ├── trip-service/
│   └── destination-service/
├── libs/
│   ├── observability/         # Spring Boot auto-config: tracing + JSON logging + MDC
│   ├── error-handling/        # ProblemDetailFactory + ErrorCode enum
│   └── api-contracts/         # Phase 1 lands UserContext here
├── frontend/                  # Vite + React 18 + TypeScript + Tailwind v3
├── infra/
│   ├── docker-compose.yml     # canonical compose definition
│   ├── postgres/init.sql      # creates schemas + per-service DB users
│   └── seeds/                 # Phase 3 lands cities-15000.tsv here
├── .github/workflows/
│   ├── backend.yml            # matrix-per-service ./gradlew check
│   └── frontend.yml           # pnpm install + lint + test + build
├── scripts/
│   └── smoke.sh               # enforces all 5 ROADMAP success criteria
├── docker-compose.yml         # thin alias of infra/docker-compose.yml (D-19)
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml  # SINGLE source of truth for every dep version
├── .env.example
└── docs/                      # PRD, architecture, data model, API spec, etc.
```

---

## Stack pins note

Backend uses **Spring Boot 3.5.x + Spring Cloud 2025.0.x (Northfields)**. The CLAUDE.md
tech-stack table cites Spring Cloud 2024.0.x — that pin is **incorrect**; 2024.0
(Moorgate) is the Spring Boot 3.4 train. We use 2025.0 per D-30. The `gradle/libs.versions.toml`
catalog is the single source of truth (D-26 / Convention C16).

Frontend pins enforce **Axios ≥ 1.15.0** (CVE-2025-62718, CVE-2026-40175 fixed),
**Tailwind v3.4.x** (NOT v4 — locked by `CLAUDE.md`), **React 18.3.x**, and
**react-leaflet v4.2.x** for Phase 8 (v5 requires React 19, incompatible).

---

## Documentation

- [`docs/01-prd.md`](docs/01-prd.md) — product requirements (22 FRs + 9 NFRs)
- [`docs/02-architecture.md`](docs/02-architecture.md) — service decomposition, repo layout, observability stack
- [`docs/03-data-model.md`](docs/03-data-model.md) — `auth`, `trip`, `destination` schemas
- [`docs/04-api-spec.md`](docs/04-api-spec.md) — REST API contract + RFC 7807 error codes
- [`docs/05-auth-security.md`](docs/05-auth-security.md) — JWT flow, refresh rotation, OWASP Top 10 mapping
- [`docs/06-frontend-design.md`](docs/06-frontend-design.md) — UI components, state management
- [`docs/07-test-strategy.md`](docs/07-test-strategy.md) — coverage targets + 8 mandatory security tests
- [`docs/08-deployment.md`](docs/08-deployment.md) — Docker Compose, env vars, CI workflows
- [`.planning/ROADMAP.md`](.planning/ROADMAP.md) — phase plan + acceptance criteria

---

## Status & License

**Portfolio / practice project.** Not for production use. Local-only deployment in v1
(`docker compose up`). Cloud (Fly.io / Neon / Upstash) is documented in
`docs/08-deployment.md §6` but not built.

Single developer; no contribution policy. License: MIT (see `LICENSE` once added).
