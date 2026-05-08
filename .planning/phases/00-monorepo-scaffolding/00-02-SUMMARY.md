---
phase: 00-monorepo-scaffolding
plan: 02
subsystem: tooling
tags: [smoke-test, phase-gate, verification, nfr-04, free-tier-audit, d-33]

requires:
  - 00-01 (Wave 1 foundation: gradle/libs.versions.toml exists for nfr-04 audit)
provides:
  - Executable phase-gate verifier `scripts/smoke.sh` enforcing all 5 ROADMAP SCs + NFR-04
  - Per-criterion gating (--criterion 1|2|3|3-route|4|5|nfr-04) per D-33 incremental verification
  - --up / --down / --list / --help dispatch for stack lifecycle and enumeration
  - Enumerated paid-SaaS deny-list for Java + npm + compose (BLOCKER 4 fix; concrete NFR-04 verification)
  - `scripts/README.md` with per-wave usage map, failure-mode pointers (Pitfall 3 + Pitfall A), manual-verification carve-outs
affects:
  - 00-03 (libs/observability) — `nfr-04` audit verifies no paid observability SDKs sneak in (e.g. datadog, sentry)
  - 00-04 (libs/error-handling, libs/api-contracts) — same audit guard
  - 00-05/06/07 (service skeletons) — `--criterion nfr-04` invocation valid after each lands
  - 00-08 (compose) — unlocks `--up`, `--criterion 1`, `--criterion 2`, `--criterion 5`
  - 00-09 (frontend) — unlocks `--criterion 4`; npm deny-list applies to `frontend/package.json`
  - 00-10 (final smoke validation + CI) — bare invocation `scripts/smoke.sh` is the Phase 0 final gate
  - All later phases — script doubles as the verification harness for `/gsd-verify-phase`

tech-stack:
  added:
    - "scripts/smoke.sh (bash; set -euo pipefail; jq detection with grep fallback)"
  patterns:
    - "Per-criterion gating via labelled `case` dispatch — D-33 incremental verification"
    - "Enumerated paid-SaaS deny-list (NOT a vague 'no paid deps' heuristic) — concrete NFR-04 verifier"
    - "Audit guards (`[ -f path ]`) so Wave 1 invocation is safe before later waves land their files"
    - "Bounded retry on SC#2 (Eureka registration may complete shortly after compose health, D-21)"
    - "Convention for future scripts: shebang + `set -euo pipefail` + labelled `fail()` + `--help`"

key-files:
  created:
    - "scripts/smoke.sh (15080 B, 423 lines, executable) — phase-gate verifier"
    - "scripts/README.md (9657 B) — usage, criteria, deny-list, per-wave map, failure modes"

key-decisions:
  - "jq detection at script start (HAVE_JQ flag) with grep fallback for SC#2/#3/#3-route/#4 — usable in minimal CI environments without an extra package install"
  - "SC#1 fails fast on bare invocation when no compose stack is running (uses `docker compose ps`, not `up --wait`); --up explicitly opts into the WAIT_TIMEOUT budget"
  - "SC#3-route enforces both `status: UP` AND `phase: 0` (D-02 validation) — proves the gateway routing path is live, not just service local actuator"
  - "Deny-list audit guards each file with `[ -f path ]` so Wave 1 sanity invocation succeeds before Wave 4/5 files exist"

requirements-completed: [NFR-04]

duration: 3min
completed: 2026-05-08
---

# Phase 0 Plan 2: Smoke Script + Phase-Gate Verifier Summary

**Executable `scripts/smoke.sh` (423 lines) enforcing all 5 ROADMAP Phase 0 success criteria + NFR-04 free-tier audit, with per-criterion gating per D-33 so each subsequent wave's containers can be smoke-tested incrementally as they come online.**

## Performance

- **Duration:** ~3min
- **Started:** 2026-05-08T04:03:39Z
- **Completed:** 2026-05-08T04:07:28Z
- **Tasks:** 2
- **Files created:** 2

## Accomplishments

- D-33 Wave-1 placement honored: smoke script lands BEFORE compose/services so each subsequent wave's containers can be smoke-tested incrementally via `--criterion <N>`.
- All 5 ROADMAP SCs encoded as labelled assertions (`SC#1`..`SC#5`) — failure messages tell the developer exactly which criterion broke without rerunning a full gate.
- BLOCKER 4 fix: NFR-04 free-tier audit uses an **enumerated** grep deny-list (31 tokens across Java + npm + compose) — NOT a vague "no paid deps" heuristic. Reviewers can audit the deny-list without re-reading the script (mirrored in `scripts/README.md`).
- Per-criterion dispatcher (`--criterion 1|2|3|3-route|4|5|nfr-04`) plus stack lifecycle (`--up`/`--down`) plus enumeration (`--list`) plus help (`--help`/`-h`) — every reasonable invocation has a flag.
- Wave-1 sanity proven: `scripts/smoke.sh --list` and `scripts/smoke.sh --criterion nfr-04` both succeed standalone with no compose, no services, no frontend present (only `gradle/libs.versions.toml` from Plan 00-01).
- Bare invocation fails fast on SC#1 when no stack is running (uses `docker compose ps`, not `up --wait`) — does NOT hang for `WAIT_TIMEOUT` seconds.
- jq detection with grep fallback for SC#2/#3/#3-route/#4 — usable in minimal CI environments without an extra package install.
- README documents Pitfall 3 (per-service Flyway history table) and Pitfall A (`flyway-database-postgresql` driver dependency) explicitly in the SC#5 failure-mode pointer.

## Criterion-coverage map

7 criterion functions implemented in `scripts/smoke.sh`:

| Function | Label | Endpoint / query |
|----------|-------|------------------|
| `check_sc1` | `SC#1` | `docker compose ps --format json` (jq) or grep on `"State"`/`"Health"` (fallback) |
| `check_sc2` | `SC#2` | `curl -sf http://localhost:8761/eureka/apps` (bounded retry up to `EUREKA_RETRY_SECONDS`=30s) |
| `check_sc3` | `SC#3` | `curl -sf http://localhost:8080/actuator/health` |
| `check_sc3_route` | `SC#3-route` | `curl -sf http://localhost:8080/__health/{auth,trip,destination}` (status=UP AND phase=0) |
| `check_sc4` | `SC#4` | `curl -sf http://localhost:5173` (HTTP 200 + non-empty body) |
| `check_sc5` | `SC#5` | `docker compose exec postgres psql -tAc "SELECT 1 FROM information_schema.tables WHERE table_schema=$schema AND table_name=$table"` for each of `auth`/`trip`/`destination` × their `*_flyway_schema_history` |
| `check_nfr_04` | `SC#NFR-04` | grep deny-list (31 tokens) against `gradle/libs.versions.toml`, `frontend/package.json`, `infra/docker-compose.yml` |

## NFR-04 deny-list — token counts

- **Java SDKs** (matched against `gradle/libs.versions.toml`): **11 tokens** (`algolia`, `auth0-java`, `mapbox-java`, `twilio`, `sentry-spring`, `sentry-logback`, `datadog-api-client`, `newrelic-java`, `rollbar-java`, `bugsnag-java`, `pingdom`)
- **npm packages** (matched against `frontend/package.json`): **10 tokens** (`@algolia/`, `@mapbox/`, `@auth0/`, `@sentry/`, `@datadog/`, `@newrelic/`, `@bugsnag/`, `@rollbar/`, `@pingdom/`, `twilio`)
- **Compose service refs** (matched against `infra/docker-compose.yml`): **10 tokens** (`datadoghq.com`, `sentry.io`, `algolia.net`, `auth0.com`, `mapbox.com`, `twilio.com`, `newrelic.com`, `bugsnag.com`, `rollbar.com`, `pingdom.com`)
- **Total: 31 deny-list tokens**

## jq fallback implementation

`HAVE_JQ` flag set at script start via `command -v jq`. Each criterion that parses JSON has a grep fallback path:

- **SC#1** (jq): `all(.[]; .State == "running" and (.Health == "healthy" or .Health == "" or .Health == null))` — falls back to grep for `"State":"(exited|paused|dead|created|restarting)"` and `"Health":"(unhealthy|starting)"`.
- **SC#2** (jq): `.applications.application[]?.name` filter per service — falls back to literal `"name":"$svc"` grep.
- **SC#3** (jq): `.status == "UP"` — falls back to `"status":"UP"` grep.
- **SC#3-route** (jq): `.status == "UP" and .phase == 0` — falls back to two greps (`"status":"UP"` and `"phase":\s*0`).
- **SC#4**: no JSON involved — pure HTTP reachability + non-empty body.
- **SC#5**: `psql -tAc` returns plain `1` or empty string — no jq needed.
- **NFR-04**: pure grep against source files — no jq involved.

Note printed once per fallback path (`[smoke] note: jq not found, using grep fallback for SC#X`) so the developer knows the audit ran but with reduced precision.

## Per-criterion gating verified

```bash
$ bash scripts/smoke.sh --list
Available criteria for `scripts/smoke.sh --criterion <N>`:
  1         — SC#1: compose healthy <60s
  2         — SC#2: 4 services in Eureka
  3         — SC#3: gateway /actuator/health UP
  3-route   — SC#3-route: /__health/<svc> end-to-end via gateway
  4         — SC#4: frontend reachable at :5173
  5         — SC#5: per-service Flyway history tables present
  nfr-04    — NFR-04: free-tier audit (no paid SaaS deps)

$ bash scripts/smoke.sh --criterion nfr-04
[smoke] OK:   NFR-04 free-tier audit (no paid SaaS deps in catalog/package.json/compose)

$ bash scripts/smoke.sh         # bare invocation, no stack running
[smoke] FAIL: SC#1: 'docker compose ps' failed (compose file 'infra/docker-compose.yml' missing or daemon down?)
$ echo $?
1
```

Wave-1 sanity proven: `--list` and `--criterion nfr-04` both succeed standalone before any compose / services / frontend exists.

## What the script CANNOT verify (manual checks deferred per VALIDATION.md)

`scripts/README.md` cites these as manual:

- **Eureka dashboard renders 4 services as registered** — visit `http://localhost:8761/`; SC#2 only asserts the JSON API.
- **Frontend landing page renders without browser console errors** — open `http://localhost:5173` + DevTools; SC#4 only asserts HTTP reachability + non-empty body.
- **360 px viewport responsiveness check** — Chrome DevTools mobile sim; verifies NFR-08 baseline.
- **Dark-mode CSS variables wire correctly** — manually toggle `.dark` class via DevTools.
- **Time-to-healthy on a fresh checkout** — `docker compose down -v && time docker compose up -d --wait`; CI captures this; a developer must verify it locally before sign-off.

## Task Commits

Each task was committed atomically (sequential executor on `master`):

1. **Task 2.1: Author scripts/smoke.sh — full SC#1-#5 + NFR-04 audit + per-criterion gating** — `ea79bb2` (feat)
2. **Task 2.2: Document smoke.sh usage in scripts/README.md** — `8ab2af0` (docs)

**Plan metadata commit:** _to be added by final commit step (SUMMARY.md + STATE.md + ROADMAP.md)_

## Files Created/Modified

### Created

| Path | Bytes | Purpose |
|------|-------|---------|
| `scripts/smoke.sh` | 15080 | Executable phase-gate verifier; 423 lines; 7 criterion functions; jq+grep paths |
| `scripts/README.md` | 9657 | Usage, criteria table, NFR-04 deny-list (mirror of script), per-wave usage map, failure-mode pointers, manual-verification carve-outs |

### Modified

None.

## Decisions Made

- **jq detection at script start with grep fallback for every JSON criterion** — the script must be usable in minimal CI environments without an extra `apt-get install jq` step. The grep fallback is correct for the specific JSON shapes Eureka, actuator, and `/__health` produce.
- **SC#1 fails fast on bare invocation; --up explicitly opts into the WAIT_TIMEOUT budget** — calling `bash scripts/smoke.sh` without flags MUST NOT hang for 60 seconds when the developer simply hasn't run `--up` yet. `docker compose ps` returns immediately and the failure message tells the developer what to do (`scripts/smoke.sh --up`).
- **SC#3-route enforces BOTH `status: UP` AND `phase: 0`** — `phase: 0` is the D-02 marker that proves the response came from the Phase 0 `/__health` placeholder controller (not, say, a stale Phase 1 `/api/<svc>/_ping` response). Catches a reasonable class of regressions.
- **Audit guards (`[ -f path ]`) so Wave 1 sanity invocation succeeds before Wave 4/5 files exist** — `scripts/smoke.sh --criterion nfr-04` must run cleanly when only `gradle/libs.versions.toml` exists (Plan 00-01 output). The audit fails ONLY on a positive grep match; missing files are silently skipped. This is correct: an absent file cannot contain a paid-SaaS dep.
- **Bounded retry on SC#2 (Eureka)** — the D-21 tuning makes registration fast (5s lease-renewal), but a 30s ceiling absorbs warm-up jitter without hanging CI.

## Deviations from Plan

None. Plan executed exactly as written.

## Issues Encountered

None during planned work. The script builds, syntax-checks, and self-verifies on a workspace where only `gradle/libs.versions.toml`, `README.md`, and the planning artifacts exist — no compose, services, or frontend yet.

## Threat Register Outcomes

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-00-06 (smoke.sh logs containing postgres credentials in CI output) | mitigated | `psql` invocation uses peer auth inside container — no `-W` / no `PGPASSWORD` env passed; `POSTGRES_USER` env var defaults to `postgres` (no password echoed in any code path). |
| T-00-07 (smoke.sh hangs indefinitely on a flaky service) | mitigated | `WAIT_TIMEOUT` (60s default, env-overridable) on `--up`; `EUREKA_RETRY_SECONDS` (30s default) caps SC#2; curl uses `-sf`; bare invocation fails fast on `docker compose ps`. |
| T-00-08 (future contributors edit smoke.sh to relax assertions) | accepted | Per plan: human review + Phase 10 hardening adds CI-side dependency-check. No automated mitigation in Phase 0. |
| T-00-09 (developer adds a paid-SaaS dep with a name not in the deny-list) | accepted (process control) | Per plan: deny-list is positive-affirmation, not complete defense. README documents extension procedure (`JAVA_DENY_LIST` / `NPM_DENY_LIST` / `COMPOSE_DENY_LIST` arrays in `scripts/smoke.sh`). |

## Self-Check

Verified each claim against the workspace:

- `[x] FOUND: scripts/smoke.sh (15080 B, executable)`
- `[x] FOUND: scripts/README.md (9657 B)`
- `[x] FOUND commit: ea79bb2 (Task 2.1: feat(00-02): add scripts/smoke.sh phase-gate verifier)`
- `[x] FOUND commit: 8ab2af0 (Task 2.2: docs(00-02): document smoke.sh usage)`
- `[x] FOUND assertion: bash -n scripts/smoke.sh exits 0`
- `[x] FOUND assertion: scripts/smoke.sh --list exits 0 with criterion labels`
- `[x] FOUND assertion: scripts/smoke.sh (bare) exits non-zero with SC#1 label (no compose stack running)`
- `[x] FOUND assertion: scripts/smoke.sh --criterion nfr-04 exits 0 (catalog has no paid deps)`
- `[x] FOUND assertion: 7 criterion functions present (check_sc1..check_sc5, check_sc3_route, check_nfr_04)`
- `[x] FOUND assertion: deny-list = 11 Java + 10 npm + 10 compose = 31 tokens`
- `[x] FOUND assertion: README cites Pitfall 3 + Pitfall A in SC#5 failure-mode pointer`

**Self-Check: PASSED**

## User Setup Required

None — no external service configuration required for this plan.

## Next Phase Readiness

- **Wave 2 (libs/observability, libs/error-handling, libs/api-contracts) is unblocked.** Smoke script's `--criterion nfr-04` already passes against the current catalog; libs work won't break the audit unless someone deliberately adds a paid SDK.
- **Wave 3+ executors can use `scripts/smoke.sh --criterion nfr-04` after each plan completes** as a quick regression gate.
- **Wave 4 (compose) unlocks `--up`, `--criterion 1`, `--criterion 2`, `--criterion 5`** — the per-wave usage map in `scripts/README.md` documents the recommended invocation per wave.
- **Wave 6 (CI + final smoke) will run `scripts/smoke.sh` (no flag — full gate) as the Phase 0 final ROADMAP success-criteria gate.**
- **No blockers for Plan 00-03.**

---
*Phase: 00-monorepo-scaffolding*
*Completed: 2026-05-08*
