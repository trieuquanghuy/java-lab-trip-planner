---
phase: 0
slug: monorepo-scaffolding
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-08
---

# Phase 0 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (backend)** | JUnit 5 (Spring Boot managed) + Gradle test task |
| **Framework (frontend)** | Vitest 3.x or 4.x + React Testing Library 16.x |
| **Framework (smoke)** | bash + curl + docker compose CLI |
| **Config file** | `gradle/libs.versions.toml`, `frontend/vitest.config.ts`, `scripts/smoke.sh` |
| **Quick run command (per service)** | `./gradlew :services:<svc>:test` |
| **Quick run command (frontend)** | `pnpm --filter frontend test --run` |
| **Full suite command (backend)** | `./gradlew check` (all services + libs) |
| **Full suite command (frontend)** | `pnpm --filter frontend lint && pnpm --filter frontend test --run && pnpm --filter frontend build` |
| **Phase-level smoke gate** | `./scripts/smoke.sh` (5-criterion ROADMAP validator) |
| **Estimated runtime (full)** | ~3–5 min including 60s compose warm-up |

---

## Sampling Rate

- **After every task commit:** Run the relevant unit test (`./gradlew :services:<svc>:test` or `pnpm --filter frontend test --run`).
- **After every plan wave:** Run `./gradlew check` + `pnpm --filter frontend lint && pnpm --filter frontend test --run`.
- **Before `/gsd-verify-work`:** `./scripts/smoke.sh` must exit 0 (all 5 ROADMAP criteria pass) AND both full suites green.
- **Max feedback latency:** ≤120 seconds for unit suites, ≤180 seconds for smoke.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| TBD     | TBD  | TBD  | NFR-04      | —          | TBD             | TBD       | TBD               | ❌ W0       | ⬜ pending |

*Planner populates this table during step 8 from the generated PLAN.md task list. Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky.*

---

## Wave 0 Requirements

- [ ] `gradle/wrapper/` — Gradle 8.14.x wrapper committed (prerequisite for any `./gradlew` invocation)
- [ ] `gradle/libs.versions.toml` — version catalog (single source of version pins; required before any service `build.gradle.kts` can resolve deps)
- [ ] `settings.gradle.kts` — multi-module structure declared (`:services:*`, `:libs:*`); without it nothing compiles
- [ ] `frontend/package.json` + `pnpm-lock.yaml` — frontend toolchain installable
- [ ] `frontend/vitest.config.ts` — test framework config so `pnpm test` resolves
- [ ] `scripts/smoke.sh` — executable smoke harness (chmod +x); covers all 5 ROADMAP success criteria
- [ ] `infra/docker-compose.yml` + root `docker-compose.yml` alias — required for any `docker compose up` smoke run
- [ ] `infra/postgres/init.sql` — schema/user creation; without it Flyway migrations can't run
- [ ] `.env.example` — env var template; without it services can't start outside CI

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Eureka dashboard renders 4 services as registered | ROADMAP SC #2 | Visual UI; smoke covers HTTP probe but human eyeballs the dashboard once | `docker compose up -d`, wait 60s, open `http://localhost:8761/`, confirm api-gateway / auth-service / trip-service / destination-service all show in the registered apps list |
| Frontend landing page renders without console errors | ROADMAP SC #4 | Browser console is the assertion target; can be partially automated with Playwright in Phase 10 but not Phase 0 | `docker compose up -d frontend`, open `http://localhost:5173`, open browser devtools, confirm zero console errors |
| `docker compose up` reaches healthy in <60s on fresh checkout | ROADMAP SC #1 | Time-to-healthy is reproduced reliably only on a clean machine; CI captures it but a developer must verify it locally before sign-off | `docker compose down -v && time docker compose up -d --wait` (use `--wait` flag to block until healthy); confirm wall-clock time ≤60s |

---

## Validation Sign-Off

- [ ] Every PLAN.md task has either an automated `<acceptance_criteria>` verifiable by grep/test/CLI, or a Wave 0 dependency listed above
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (use shared smoke step on task triplets where individual unit verify is impractical)
- [ ] Wave 0 covers all MISSING references (Gradle wrapper, frontend toolchain, smoke script, compose, init.sql, .env.example)
- [ ] No watch-mode flags in CI (`pnpm test --run`, NOT `pnpm test --watch`)
- [ ] Feedback latency budgets respected (≤120s unit, ≤180s smoke)
- [ ] `nyquist_compliant: true` set in frontmatter once planner populates the per-task verification map

**Approval:** pending
