# Retrospective: v1.0 MVP — Trip Planner

**Milestone:** v1.0 MVP
**Shipped:** 2026-05-20
**Duration:** 12 days (2026-05-08 → 2026-05-19)
**Commits:** 256
**LOC:** ~16,100 (Java + TypeScript)

---

## Metrics

| Metric | Value |
|--------|-------|
| Phases completed | 12 |
| Plans executed | 64 |
| Tasks completed | 68 |
| Requirements delivered | 31/32 (97%) |
| Known gap | FR-21 frontend (backend done) |
| Services | 5 (eureka, gateway, auth, trip, destination) |
| Shared libraries | 4 (api-contracts, error-handling, jwt-common, observability) |
| Backend test coverage | ≥70% enforced via JaCoCo |
| Frontend tests | 116 tests across 23 files |
| CI pipeline | GitHub Actions (test + coverage + OWASP) |

---

## What Worked

1. **Phase-based planning with GSD workflow** — Each phase had clear scope, plans with 2-3 tasks kept quality consistent across the entire build.
2. **Shared library approach** — `api-contracts`, `error-handling`, `jwt-common`, `observability` eliminated duplication and ensured consistent behavior across all 5 services.
3. **Schema-per-service on single Postgres** — Gave ownership semantics without the ops cost of separate databases; Flyway migrations per service worked cleanly.
4. **Spring Cloud Gateway + JWT propagation** — Clean security boundary; X-User-Id injection from JWT eliminated per-service auth concerns for downstream services.
5. **TanStack Query for server state** — Caching, deduplication, and optimistic updates "for free"; reduced frontend code significantly vs manual fetch+state.
6. **Observability added last** — Phase 10 was the right time; services were stable so adding tracing/metrics was additive, not disruptive.
7. **Virtual threads for destination-service** — Simple config flag, measurable improvement for I/O-bound external API calls.

## What Was Inefficient

1. **Early phases were over-planned** — Phase 0 had 10 plans (some trivially small); later phases averaged 4-5 plans with better task sizing.
2. **Frontend authentication flow** — Required multiple iterations to get token refresh + redirect-after-login correct. Should have spiked this in isolation first.
3. **shadcn/ui + M3 design system refactor** — Phase 9.1 was a mid-stream pivot after Phase 9 polish felt inconsistent. Would have saved time to pick the design system in Phase 7.
4. **Docker Compose configuration drift** — `infra/docker-compose.yml` vs root `docker-compose.yml` caused confusion; should have consolidated earlier.

## Key Lessons

1. **Test the auth boundary first, not last** — Security integration tests in Phase 2 caught gateway issues that would have been expensive to fix later.
2. **Plan size: 2-3 tasks is the sweet spot** — Larger plans (4+ tasks) led to context exhaustion and lower quality in later tasks.
3. **Shared libs should ship in Phase 0** — Having `error-handling` and `jwt-common` ready before any service started eliminated duplicated error handling patterns.
4. **Flyway + PostgreSQL 16 requires explicit driver dep** — `flyway-database-postgresql` not pulled transitively; this bit us in Phase 0.
5. **React-leaflet v4 for React 18** — v5 requires React 19; this was correctly identified in research and avoided a painful upgrade.

## Patterns Established

- **Gateway security model:** Strip inbound headers → validate JWT → inject X-User-Id → forward
- **Service security:** Reject requests without X-User-Id header (401) — simple and effective
- **Error responses:** RFC 7807 Problem Detail everywhere; `GlobalExceptionHandler` in each service
- **Frontend data flow:** TanStack Query for server state, Zustand for UI-only state
- **Testing:** Unit (Mockito) → Integration (Testcontainers + `@ServiceConnection`) → Security ITs
- **Commit messages:** `type(scope): description` with phase reference

## What to Change for v1.1

1. **Design system first** — Pick color tokens, spacing scale, and component variants before any UI work.
2. **Spike complex flows early** — If a feature involves multi-step UX (like favorites page with optimistic updates), spike it in isolation before planning.
3. **Consolidate Docker files** — One `docker-compose.yml` at root with profiles (`dev`, `full`, `infra-only`).
4. **Consider contract testing** — Services now have stable APIs; Spring Cloud Contract or Pact would catch drift.

---

## Deferred to v1.1

- FR-21 frontend: Favorites page (backend API complete, needs React page + route)
- OAuth login (Google/GitHub)
- Trip sharing via public link
- Travel time between items

---
