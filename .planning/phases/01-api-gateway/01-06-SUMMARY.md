---
phase: 01-api-gateway
plan: 06
subsystem: api-gateway
status: complete
tags: [docker-compose, smoke-testing, depends-on, rate-limiting, redis, security-regression]
dependency-graph:
  requires: [01-01, 01-02, 01-03, 01-04, 01-05]
  provides: [NFR-02, NFR-06, SC#1-runtime, SC#4-runtime, SC#5-runtime, SC#6-gate]
  affects: [infra/docker-compose.yml, scripts/smoke.sh, scripts/README.md, scripts/mint-test-token.sh]
tech-stack:
  added: []
  patterns:
    - Compose v2 healthcheck-gated depends_on chain (Convention C11-P1)
    - Smoke script extension pattern — backward-compatible layering of Phase 1 criteria over Phase 0 gate
    - mint-test-token.sh thin wrapper over JwtFixturesSmokeMintTask (W5 closure pattern)
key-files:
  created:
    - scripts/mint-test-token.sh
  modified:
    - infra/docker-compose.yml
    - scripts/smoke.sh
    - scripts/README.md
decisions:
  - "Pitfall H closed: api-gateway.depends_on.redis = service_healthy ensures gateway boots only after Redis is ready, preventing first-request RedisConnectionFailureException from RedisRateLimiter"
  - "Pitfall J closed: api-gateway.depends_on.{auth,trip,destination}-service = service_healthy ensures gateway boots only after all downstream services are healthy, preventing first-request 502 from NettyRoutingFilter static-URI routes"
  - "T-01-15 mitigated: smoke.sh uses SMOKE_PLACEHOLDER_TOKEN (obviously invalid) for routing check; rate-limit check uses no bearer at all; no AUTH_JWT_SECRET assignment anywhere in smoke or mint scripts"
  - "scripts/mint-test-token.sh is the single W5 committed mint helper — invokes JwtFixturesSmokeMintTask via gradle, no literal secret embedded"
metrics:
  duration: "~20 minutes"
  completed: "2026-05-09"
  tasks-completed: 3
  tasks-pending: 0
  files-changed: 4
---

# Phase 01 Plan 06: E2E Smoke Run + Wave 6 Final Integration Summary

**One-liner:** Compose depends_on chain closing Pitfall H (Redis) and Pitfall J (downstream services) + 3 new phase-01-* smoke criteria (bypass/routing/rate-limit) + mint-test-token.sh W5 helper — Phase 1 runtime gate CLEARED: clean compose up, smoke exit 0, Eureka 4 services, Zipkin SC#6 trace continuity confirmed.

**Status: COMPLETE — All 4 human-verify checks approved 2026-05-09**

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 6.1 | Extend api-gateway depends_on — Pitfall H + J | 63b1259 | infra/docker-compose.yml |
| 6.2 | Extend smoke.sh + README + mint-test-token.sh | 21ea323 | scripts/smoke.sh, scripts/README.md, scripts/mint-test-token.sh |
| 6.3 | Final Phase 1 stack validation — human-verify gate | (docs commit) | .planning/phases/01-api-gateway/01-06-SUMMARY.md |

## Runtime Verification Results (Task 6.3 — Approved)

All 4 manual checks passed:

| Check | Result |
|-------|--------|
| `docker compose down -v && up -d --wait` — all 10 services healthy <= 120s | PASS |
| `bash scripts/smoke.sh` — exit 0, Phase 0 SC#1-5 + NFR-04 + Phase 1 (bypass/routing/rate-limit) | PASS |
| Eureka dashboard :8761 — exactly 4 services registered | PASS |
| Zipkin :9411 — single trace ID spanning api-gateway + trip-service (SC#6) | PASS |

## Changes Delivered

### infra/docker-compose.yml

Extended `api-gateway.depends_on` with 4 new `condition: service_healthy` edges:
- `redis` — Pitfall H closure: gateway will not boot until redis-cli ping healthcheck passes
- `auth-service` — Pitfall J closure: prevents first-request 502 on /api/auth/**
- `trip-service` — Pitfall J closure: prevents first-request 502 on /api/trips/**
- `destination-service` — Pitfall J closure: prevents first-request 502 on /api/{search,destinations}/**

The existing Phase 0 redis service block (image: redis:7-alpine, port: 127.0.0.1:6379:6379, healthcheck: redis-cli ping | grep -q PONG) is byte-identical post-edit (T-01-14 / C13-P1 preserved).

### scripts/smoke.sh

Three new criterion handlers added after `check_nfr_04()`:
- `check_phase_01_bypass()` — T-01-04 / Pitfall 1 runtime regression gate: direct hit on `127.0.0.1:8082/api/trips/_ping` with crafted X-User-Id and no Authorization; asserts 401
- `check_phase_01_routing()` — SC#1 / Pitfall J runtime gate: 4 route prefixes `/api/{auth,trips,search,destinations}/anything` sent through gateway; asserts no 502/503
- `check_phase_01_rate_limit()` — D-05 IP-only leg gate: 35 successive POSTs to `/api/auth/login`; asserts at least one 429 (RedisRateLimiter with burstCapacity=30 + requestedTokens=900)

All 3 wired into `run_full_gate()` after `check_nfr_04`; dispatch cases in `run_criterion()`; enumerated in `usage()` and `list_criteria()`. Final echo updated to include Phase 1 criteria.

### scripts/README.md

- 3 new rows in Criteria table
- 3 new `--criterion` examples in the Usage block
- Wave 7 row in Per-wave usage map
- 4 new entries in Failure modes (bypass, routing-502, routing-503, rate-limit)
- New "Phase 1 manual verifications" subsection citing SC#6 Zipkin trace continuity and the mint-test-token.sh usage

### scripts/mint-test-token.sh (NEW)

W5 closure: thin shell wrapper that invokes `./gradlew :libs:jwt-common:test --tests JwtFixturesSmokeMintTask -q --console=plain` and extracts the JWT from stdout via a base64url segment regex. No literal secret. Executable bit set. Header carries C35-P1 + T-01-15 citations.

## Deviations from Plan

None — plan executed exactly as written. All 3 tasks executed cleanly. Human-verify gate cleared on first attempt with all 4 runtime checks green.

## Threat Surface Scan

No new network endpoints introduced. No new auth paths. No new file access patterns. No schema changes. All changes are to infra configuration and shell scripts.

| Flag | File | Description |
|------|------|-------------|
| T-01-15 (mitigated) | scripts/smoke.sh | Placeholder token `SMOKE_PLACEHOLDER_TOKEN` used; `AUTH_JWT_SECRET` never assigned; grep gate confirmed absent |
| T-01-14 (preserved) | infra/docker-compose.yml | redis port `127.0.0.1:6379:6379` unchanged; compose verify gate confirms invariant |

## Self-Check: PASSED

| Check | Result |
|-------|--------|
| `infra/docker-compose.yml` exists | FOUND |
| `scripts/smoke.sh` exists | FOUND |
| `scripts/README.md` exists | FOUND |
| `scripts/mint-test-token.sh` exists | FOUND |
| Commit `63b1259` exists | FOUND |
| Commit `21ea323` exists | FOUND |
| Runtime gate cleared (human-verify approved) | CONFIRMED |
