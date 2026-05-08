# Trip Planner — Infrastructure

This directory contains the canonical Docker Compose stack and Postgres
initialization for the Trip Planner local-development environment. The root
`docker-compose.yml` at the repo root is a thin `include:` alias for
`infra/docker-compose.yml` (per D-19) so `docker compose up` works from either
location without flags.

---

## Quickstart

```bash
# From repo root (uses include: alias). NO prior `./gradlew bootJar` is needed —
# backend Dockerfiles are multi-stage and build their jars inside the container.
cp .env.example .env
docker compose up -d --wait

# OR explicitly from the infra dir
docker compose -f infra/docker-compose.yml up -d --wait
```

Notes:
- `--wait` blocks until all healthchecks report `healthy`.
- Add `--wait-timeout 60` to enforce ROADMAP SC#1's <60s budget on a warm
  cache: `docker compose up -d --wait --wait-timeout 60`.
- The first `docker compose up` after a fresh checkout takes longer than 60s
  because the multi-stage Docker images are being built (Gradle dependency
  download + Spring Boot bootJar). Subsequent ups reuse the BuildKit image
  cache and complete <60s.

---

## Service Ports

Source of truth: `docs/08-deployment.md §1.3` + `00-CONTEXT.md D-22`.

| Service             | Port  | Bind            | Purpose |
|---------------------|-------|-----------------|---------|
| api-gateway         | 8080  | 0.0.0.0         | Public API entry |
| auth-service        | 8081  | 127.0.0.1 (D-22)| Internal — auth flow (Phase 2+) |
| trip-service        | 8082  | 127.0.0.1 (D-22)| Internal — trip CRUD (Phase 5+) |
| destination-service | 8083  | 127.0.0.1 (D-22)| Internal — search/details (Phase 3+) |
| eureka-server       | 8761  | 127.0.0.1 (D-22)| Service registry dashboard |
| frontend            | 5173  | 0.0.0.0         | Static bundle served by nginx (built inside container by Plan 09's Dockerfile) |
| postgres            | 5432  | 127.0.0.1       | DB |
| redis               | 6379  | 127.0.0.1       | Cache (Phase 3+ uses) |
| mailhog SMTP        | 1025  | 127.0.0.1       | SMTP listener (Phase 2+ uses) |
| mailhog UI          | 8025  | 0.0.0.0         | Mailbox web UI |
| zipkin UI           | 9411  | 0.0.0.0         | Trace UI |

---

## Multi-stage backend builds

All 5 backend services use multi-stage Dockerfiles where the **builder stage**
runs `./gradlew :services:<svc>:bootJar` inside an
`eclipse-temurin:21-jdk-alpine` container. The **runtime stage** copies the
resulting jar into a slim `eclipse-temurin:21-jre-alpine` image.

Compose service `build:` blocks set `context: ..` (repo root, where the Gradle
multi-module root lives — `gradlew`, `settings.gradle.kts`, `build.gradle.kts`,
`gradle/`, and shared `libs/`) and `dockerfile: services/<svc>/Dockerfile`.

This means `docker compose up --wait` works on a fresh checkout — **no prior
`./gradlew bootJar` is needed** (ROADMAP SC#1 "no manual intervention"). The
frontend uses an analogous multi-stage Dockerfile (Plan 09) with a
`node:20-alpine` builder + `nginx:alpine` runtime.

---

## Postgres init

`postgres/init.sql` runs on FIRST container init only (mounted at
`/docker-entrypoint-initdb.d/init.sql`). It is idempotent — safe to re-run
after `docker compose down -v`. It creates:

- 3 schemas (`auth`, `trip`, `destination`)
- 3 users with schema-scoped `USAGE, CREATE` grants (`auth_svc`, `trip_svc`,
  `destination_svc`)
- `search_path` defaults so each user lands in its own schema

Per-service Flyway migrations operate within their own schema only; per-service
Flyway history tables (`auth.auth_flyway_schema_history`,
`trip.trip_flyway_schema_history`, `destination.destination_flyway_schema_history`)
are created on first boot of each DB-backed service (Pitfall 3 / D-09).

---

## Healthcheck strategy

Per Pitfall 10 (Eureka registration lag → gateway 503 cold-start) every service
has a `healthcheck:` block, and every dependent uses
`depends_on: { <dep>: { condition: service_healthy } }`. Combined with the
Eureka client tuning per D-21 (5s fetch / 5s renewal / 10s expiration) the
cold-start window stays under 60s.

If you see 503s on `/__health/<svc>` immediately after `docker compose up`,
wait up to 60s for Eureka registration to converge. Persistent 503s indicate a
service health failure — check `docker compose logs <svc>`.

---

## Loopback binding (D-22)

Downstream service ports + postgres + redis + the eureka dashboard bind to
`127.0.0.1` only — they are **not reachable from your LAN**. Only
`api-gateway:8080`, `frontend:5173`, `mailhog UI:8025`, and `zipkin UI:9411`
bind to `0.0.0.0` for ergonomic access during dev. This is a defense-in-depth
measure: the gateway is the only public surface; downstream services rely on
JWT verification by the gateway (Phase 1+) but do not assume they are
unreachable from the LAN.

---

## `include:` portability note (Pitfall F)

The root `docker-compose.yml` uses Compose 2.20+'s `include:` directive, which
ships with Docker Desktop 4.30+ and standalone Docker Engine 26+. If your
install is older and `docker compose up` from the repo root errors with
`unknown field include`, run from `infra/` instead:

```bash
docker compose -f infra/docker-compose.yml up -d --wait
```

---

## Directory layout

```
infra/
├── README.md                  # this file
├── docker-compose.yml         # canonical compose file (D-18)
├── postgres/
│   └── init.sql               # idempotent schemas + users + grants (D-08)
└── seeds/
    └── .gitkeep               # reserves dir for Phase 3 cities-15000.tsv
```
