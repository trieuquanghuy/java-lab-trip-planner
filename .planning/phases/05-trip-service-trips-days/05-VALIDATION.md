---
phase: 5
slug: trip-service-trips-days
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-15
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Mockito + Testcontainers |
| **Config file** | `services/trip-service/src/test/resources/application-test.yml` |
| **Quick run command** | `./gradlew :services:trip-service:test` |
| **Full suite command** | `./gradlew :services:trip-service:check` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :services:trip-service:test`
- **After every plan wave:** Run `./gradlew :services:trip-service:check`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 05-01-01 | 01 | 1 | TRIP-01 | — | N/A | migration | `./gradlew :services:trip-service:test` | ❌ W0 | ⬜ pending |
| 05-02-01 | 02 | 1 | TRIP-01, TRIP-02 | — | N/A | unit | `./gradlew :services:trip-service:test` | ❌ W0 | ⬜ pending |
| 05-03-01 | 03 | 2 | TRIP-01, TRIP-02, TRIP-06 | — | Ownership check returns 404 not 403 | integration | `./gradlew :services:trip-service:test` | ❌ W0 | ⬜ pending |
| 05-04-01 | 04 | 3 | TRIP-02 | — | Concurrent PATCH no orphans | integration | `./gradlew :services:trip-service:test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/resources/application-test.yml` — test profile config
- [ ] `src/test/resources/db/test-migration/V0__create_trip_schema.sql` — schema bootstrap
- [ ] `TripIntegrationTestBase.java` — singleton Testcontainers + JWT fixtures

*Existing infrastructure partially covers: jwt-common testFixtures, H2 security IT base.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| SC-5: Concurrent PATCHes | TRIP-02 | Timing-dependent race condition | Run two PATCH requests with same trip in parallel threads; verify either success+409 or both 409 |

---

## Success Criteria Coverage

| SC# | Requirement | Validation Method |
|-----|-------------|-------------------|
| SC-1 | TRIP-01, TRIP-02 | Integration test: POST with dates → GET detail → assert 5 days |
| SC-2 | TRIP-02 | Integration test: PATCH shrink → 409 → retry confirmShorten=true → 200 |
| SC-3 | TRIP-06 | Integration test: create trips → re-GET list → verify; new user → empty |
| SC-4 | NFR-02 | Integration test: user A creates, user B GETs → 404 |
| SC-5 | TRIP-02 | Concurrent thread test or serialization failure response check |
