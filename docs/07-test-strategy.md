# 07 — Test Strategy

**Status**: Draft for review
**Last updated**: 2026-05-08

## 1. Goals

- Catch regressions early — every PR runs full test suite in CI.
- Demonstrate testing discipline appropriate for portfolio review.
- Enforce: zero broken builds on `main`; security tests gate merges.

## 2. Test pyramid

```
                       ┌──────────────────┐
                       │  E2E (Playwright)│  ← slow, few, critical paths only
                       │       ~5 tests    │
                       ├──────────────────┤
                       │  Integration     │  ← per-service: HTTP + DB + cache
                       │  ~30–50 tests    │
                       ├──────────────────┤
                       │  Component       │  ← React: render + interact
                       │  ~50–80 tests    │
                       ├──────────────────┤
                       │  Unit            │  ← pure logic, mocked deps
                       │  ~150–250 tests  │
                       └──────────────────┘
```

## 3. Backend test layers

### 3.1 Unit tests
- **Tooling**: JUnit 5, Mockito 5, AssertJ.
- **Scope**: pure logic — services with mocked repositories, mappers, validators, position-calculation algorithm, JWT helper.
- **Speed target**: full unit suite of one service < 5 s.
- **Examples**:
  - `TripServiceTest.createTrip_validInput_persistsAndMaterializesDays`
  - `ItemPositioner_insertBetween_returnsMidpoint`
  - `JwtIssuer_signsAndDecodesRoundTrip`

### 3.2 Integration tests
- **Tooling**: Spring Boot Test, Testcontainers (Postgres 16, Redis 7), WireMock for external HTTP, JsonPath for assertions.
- **Scope**: real Spring context, real DB (Testcontainers), HTTP layer via `MockMvc` or `WebTestClient`.
- **Per service**:
  - auth-service: signup → verify → login → refresh → logout end-to-end against real DB.
  - trip-service: trip CRUD, day materialization on date change, item drag/move, ownership filters (cross-user 404 cases).
  - destination-service: search FTS against seeded `cities`, OpenTripMap stub via WireMock, cache hit/miss, circuit-breaker open path.
- **Naming**: `*IntegrationTest.java`, run in a separate Gradle task `integrationTest`.

### 3.3 Contract tests for external providers
- WireMock stubs in `src/test/resources/stubs/opentripmap/*.json` and `…/foursquare/*.json`.
- Test fixtures captured from one real provider call (sanitized) so the assumed shape stays in sync.
- A nightly CI job hits the real providers in a sandbox project and diffs the live shape vs the stubs — alert on divergence. (Optional, deferred; baseline tests use stubs only.)

### 3.4 Security tests (mandatory gate)
Tagged `@Tag("security")`. CI runs them as a separate job that **must pass** to merge.
List enumerated in [05-auth-security.md §10](./05-auth-security.md#10-authorization-tests-mandatory-in-ci).

### 3.5 Coverage targets
- Service layer: ≥ 70% line coverage.
- Auth + ownership-check paths: 100% branch coverage.
- Tooling: JaCoCo via Gradle; HTML report uploaded as CI artifact; threshold enforced in `build.gradle.kts`.

## 4. Frontend test layers

### 4.1 Unit / hook tests
- **Tooling**: Vitest, Testing Library, MSW (Mock Service Worker).
- **Scope**: hooks (`useAuth`, `useSearch`), pure utilities (`dates.ts`, `position.ts`), Zustand stores.

### 4.2 Component tests
- **Tooling**: Testing Library + Vitest. Backend mocked via MSW handlers per test file.
- **Scope**: each feature component renders, responds to user interaction, surfaces errors.
- **Examples**:
  - `SearchInput` debounces and calls API.
  - `DayColumn` reorders items on drag.
  - `LoginForm` shows generic error on bad credentials.
  - `AddToTripDialog` persists deferred action when unauthenticated.

### 4.3 E2E tests
- **Tooling**: Playwright Chromium against full Docker Compose stack (compose-up before tests in CI).
- **Critical flows** (≤5 tests, each under 60s):
  1. Signup → verify (consume token via Mailhog API) → login → land on `/`.
  2. Search "Tokyo" → see attractions → click detail → see photo carousel.
  3. Logged-in user creates trip → sets dates → adds 2 destinations to day 1 → drag one to day 2.
  4. Logged-out user clicks "Add to trip" → redirected to login → after login, item is added.
  5. Trip detail map view shows markers for added destinations.
- **Test data isolation**: each test creates its own user with a randomized email; cleanup via DB truncate between runs.

### 4.4 Visual regression (optional)
- Deferred. Could add Playwright screenshots in Phase 9 polish.

## 5. Tooling layout

```
services/<each>/
  build.gradle.kts             ← `test` task = unit; `integrationTest` task = integration
  src/test/java/…              ← unit
  src/integrationTest/java/…   ← integration

frontend/
  vitest.config.ts             ← unit + component
  tests/e2e/                   ← Playwright
  tests/setup.ts               ← MSW handlers, RTL setup

.github/workflows/
  backend.yml                  ← matrix: each service runs unit + integration
  frontend.yml                 ← unit/component, then E2E (separate job)
  security.yml                 ← runs only @Tag("security") tests on PR
```

## 6. CI enforcement matrix

| Check | Step | Blocking |
|-------|------|----------|
| Lint (Checkstyle Java + ESLint frontend) | `./gradlew check`, `pnpm lint` | yes |
| Unit tests | `./gradlew test`, `pnpm vitest --run` | yes |
| Integration tests | `./gradlew integrationTest` | yes |
| Security tests | `./gradlew test -PincludeTags=security` | yes |
| Coverage thresholds | JaCoCo verify | yes |
| Dependency audit | OWASP Dep-Check (CVSS ≥7), `pnpm audit --audit-level=high` | yes |
| E2E | Playwright | yes on PR to `main`; allowed to skip on draft PR |
| Lighthouse | manual at Phase 9 | no |

## 7. Test data strategy

- Backend: `@Sql` files for DB seed in integration tests; otherwise fixture builders (`TripFixtures.aTrip().withName(...).build()`).
- Frontend: factory functions in `tests/factories/` that mirror backend DTOs.
- E2E: each test signs up its own user with `mai+<uuid>@test.local`; SMTP captured by Mailhog; verification token retrieved via Mailhog REST API.

## 8. Performance / load (deferred)
Not built in v1. The roadmap (Phase 10) includes:
- k6 baseline test for search endpoint (target 100 RPS, p95 < 500 ms cached).
- Manual smoke under realistic data only — no SLO commitments for v1.

## 9. What is NOT tested in v1
- Mutation testing (PIT) — out of scope; portfolio gain doesn't justify CI time.
- Chaos / failure-injection tests — Resilience4j unit tests cover the policy; live chaos is v2.
- Cross-browser E2E — Chromium only in v1; Firefox/Safari via Playwright matrix in v2.

## 10. Definition of Done (per phase)

A phase is "done" when:
1. New code has unit + integration tests.
2. New endpoints have at least one happy-path and one error-path integration test.
3. Coverage thresholds still pass.
4. Security tests still pass.
5. Lint passes.
6. The phase's demo flow runs successfully via `docker compose up` + manual click-through.
