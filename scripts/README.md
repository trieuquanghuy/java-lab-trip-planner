# Trip Planner — Scripts

This directory contains operational scripts. The primary one is `smoke.sh` — the Phase 0 phase-gate verifier with per-criterion gating per D-33.

---

## `smoke.sh` — Phase 0 Verifier

Asserts all 5 ROADMAP Phase 0 success criteria + NFR-04 (free-tier audit) pass against a running compose stack. Exits 0 on success; non-zero with a labelled failure message (`SC#1`..`SC#5` / `SC#NFR-04`) on any failure. Supports per-criterion gating so executors can verify incrementally as each wave lands artifacts (D-33).

### Usage

```bash
# Standalone full gate (assumes stack already running)
./scripts/smoke.sh

# Bring up the stack (no SC checks; --wait blocks until healthy or WAIT_TIMEOUT expires)
./scripts/smoke.sh --up

# Tear down + wipe volumes
./scripts/smoke.sh --down

# List available criterion labels
./scripts/smoke.sh --list

# Run ONE criterion only — useful during incremental wave execution
./scripts/smoke.sh --criterion 1          # SC#1 compose <60s
./scripts/smoke.sh --criterion 2          # SC#2 Eureka
./scripts/smoke.sh --criterion 3          # SC#3 gateway health
./scripts/smoke.sh --criterion 3-route    # SC#3-route /__health/<svc>
./scripts/smoke.sh --criterion 4          # SC#4 frontend
./scripts/smoke.sh --criterion 5          # SC#5 Flyway history tables
./scripts/smoke.sh --criterion nfr-04     # NFR-04 free-tier audit
./scripts/smoke.sh --criterion phase-01-bypass     # T-01-04 / Pitfall 1
./scripts/smoke.sh --criterion phase-01-routing    # SC#1 routing runtime
./scripts/smoke.sh --criterion phase-01-rate-limit # SC#5 IP-only / D-05
./scripts/smoke.sh --criterion auth-1              # Phase 2 SC#1: signup → MailHog → verify → login
./scripts/smoke.sh --criterion auth-2              # Phase 2 SC#2: unverified-cannot-login + opaque
./scripts/smoke.sh --criterion auth-3              # Phase 2 SC#3: authenticated logout + invalidation
./scripts/smoke.sh --criterion auth-4              # Phase 2 SC#4: refresh-rotation + post-logout revocation
./scripts/smoke.sh --criterion auth-5              # Phase 2 SC#5 / NFR-05: 6th login → 429

# Show usage help
./scripts/smoke.sh --help
```

### Environment overrides

| Variable | Default | Purpose |
|----------|---------|---------|
| `COMPOSE_FILE` | `infra/docker-compose.yml` | Path to the canonical compose file (Wave 4 deliverable per D-18). |
| `POSTGRES_DB` | `tripplanner` | Database name used by SC#5's `psql` queries. |
| `POSTGRES_USER` | `postgres` | Postgres superuser used by SC#5's `psql` queries (peer auth inside container — no password echoed). |
| `WAIT_TIMEOUT` | `60` | Seconds passed to `docker compose up -d --wait --wait-timeout`. SC#1 budget per ROADMAP. |
| `EUREKA_RETRY_SECONDS` | `30` | Bounded retry window for SC#2 — Eureka registration may complete shortly after compose health (D-21). |

### Criteria

Verbatim from `.planning/ROADMAP.md` Phase 0 Success Criteria #1-#5 (with the SC label the script emits), plus NFR-04:

| Criterion | What it asserts |
|-----------|-----------------|
| `SC#1` | `docker compose up` brings every container to healthy status in under 60 seconds with no manual intervention |
| `SC#2` | All 4 services (api-gateway, auth-service, trip-service, destination-service) registered in Eureka |
| `SC#3` | `curl localhost:8180/actuator/health` returns `{"status":"UP"}` through the gateway |
| `SC#3-route` | `/__health/{auth,trip,destination}` route through the gateway and return `{status: UP, phase: 0}` (D-02 validation) |
| `SC#4` | `localhost:5173` reachable (frontend dev server up) — note: console-error check is manual |
| `SC#5` | Per-service `*_flyway_schema_history` tables exist in their respective schemas |
| `SC#NFR-04` | `gradle/libs.versions.toml`, `frontend/package.json`, `infra/docker-compose.yml` contain no enumerated paid-SaaS deps (free-tier audit) |
| `phase-01-bypass` | Direct hit on `127.0.0.1:8082/api/trips/_ping` with crafted `X-User-Id` and no `Authorization` returns 401 (T-01-04 / Pitfall 1 runtime regression gate; complements plan 01-04's `DirectServiceAccessWithoutGatewayReturns401IT`) |
| `phase-01-routing` | 4 gateway route prefixes (`/api/{auth,trips,search,destinations}/anything`) forward through the gateway with no 502 or 503 (SC#1 + Pitfall J runtime gate) |
| `phase-01-rate-limit` | 35 successive `POST /api/auth/login` produce at least one 429 response (SC#5 IP-only leg / D-05 / RedisRateLimiter from plan 01-03) |
| `auth-1` | Phase 2 SC#1: `POST /api/auth/signup` → 201 + `userId`; MailHog API yields a 64-hex token in the verbatim UI-SPEC body; `GET /api/auth/verify?token=…` → 302 + `Location: …?status=success`; `POST /api/auth/login` → 200 + `accessToken` + `Set-Cookie: refresh_token=…`. Drives the full happy path through the wired stack; complements Plan 02-06's `AuthControllerIT`. |
| `auth-2` | Phase 2 SC#2 / docs/05 §9.1 enumeration policy: signup an account but skip verify; login → 403 with verbatim `$.code = auth.email_not_verified` + `$.detail = "Please verify your email before logging in."`; an unknown email also receives a generic 400 `auth.invalid_credentials`. |
| `auth-3` | Phase 2 SC#3 (D-11 / Open Q1 — no `/me`): re-login as smoke-1; `POST /api/auth/logout` (Bearer + cookie) → 204 + `Set-Cookie: refresh_token=…; Max-Age=0`; reusing the now-revoked cookie at `/api/auth/refresh` returns 401 `auth.refresh_invalid`. |
| `auth-4` | Phase 2 SC#4 (D-13 / D-10): fresh signup+verify+login; `/refresh` issues a new cookie B with value distinct from cookie A; `/logout` with cookie B → 204; subsequent `/refresh` with cookie B → 401 `auth.refresh_invalid`. Smoke proves the WIRED rotation + chain-revocation; Plan 02-06's `RotatedRefreshTokenCannotBeReusedIT` proves the replay path. |
| `auth-5` | Phase 2 SC#5 / NFR-05 IP+email leg (D-05 / D-06 / D-08): a fresh `smoke-5-<ts>@test.local` receives 5 wrong-password 400 `auth.invalid_credentials`; the 6th attempt → 429 with verbatim `$.code = auth.rate_limited` + `$.detail = "Too many attempts. Please try again later."`. |

### NFR-04 deny-list (enumerated)

The free-tier audit uses an explicit deny-list, NOT a vague "no paid deps" heuristic (BLOCKER 4 fix). The script body in `scripts/smoke.sh` is the source of truth; this README mirrors it so reviewers can audit without re-reading the script.

- **Java SDKs** (matched against `gradle/libs.versions.toml`):
  `algolia`, `auth0-java`, `mapbox-java`, `twilio`, `sentry-spring`, `sentry-logback`, `datadog-api-client`, `newrelic-java`, `rollbar-java`, `bugsnag-java`, `pingdom`
- **npm packages** (matched against `frontend/package.json`):
  `@algolia/`, `@mapbox/`, `@auth0/`, `@sentry/`, `@datadog/`, `@newrelic/`, `@bugsnag/`, `@rollbar/`, `@pingdom/`, `twilio`
- **Compose service refs** (matched against `infra/docker-compose.yml`):
  `datadoghq.com`, `sentry.io`, `algolia.net`, `auth0.com`, `mapbox.com`, `twilio.com`, `newrelic.com`, `bugsnag.com`, `rollbar.com`, `pingdom.com`

**To extend:** edit the `JAVA_DENY_LIST` / `NPM_DENY_LIST` / `COMPOSE_DENY_LIST` arrays in `scripts/smoke.sh` and update this README. Adds appear here at the next `/gsd-update-state` (or alongside the dep-introduction commit if added by a developer outside the GSD loop).

**Audit safety:** each file probe is guarded with `[ -f "$path" ]` so the audit does NOT fail simply because a file does not yet exist (Wave 1 invocation is safe before Wave 4/5 land their files).

### Per-wave usage map

D-33 mandates incremental smoke-testing as containers come online. The matrix below is the recommended invocation per wave; copy-pasteable from the right column.

| Wave | What landed | Recommended smoke invocation | What it asserts at this point |
|------|-------------|------------------------------|-------------------------------|
| 1 (build infra + smoke + observability + error-handling) | `gradle/libs.versions.toml`, `scripts/smoke.sh` itself | `scripts/smoke.sh --list` | Script is executable + flag handler works (Wave 1 sanity test — no other files needed) |
| 2 (libs) | `libs/{observability,error-handling,api-contracts}` | `scripts/smoke.sh --criterion nfr-04` | Free-tier audit runs (catalog file already exists from Wave 1) |
| 3 (service skeletons) | `services/{eureka-server,api-gateway,auth-service,trip-service,destination-service}` | `scripts/smoke.sh --criterion nfr-04` | Audit still passes after services land |
| 4 (compose stack) | `infra/docker-compose.yml`, `infra/postgres/init.sql` | `scripts/smoke.sh --up && scripts/smoke.sh --criterion 1 && scripts/smoke.sh --criterion 2` | Compose healthy <60s + Eureka registration |
| 5 (frontend) | `frontend/` (Vite + React + provider stack) | `scripts/smoke.sh --criterion 4` | Frontend reachable at :5173 |
| 6 (CI + final smoke) | `.github/workflows/{backend,frontend}.yml` | `scripts/smoke.sh` (no flag — full gate) | All SC#1-#5 + NFR-04 pass |
| 7 (Phase 1 final integration) | `infra/docker-compose.yml` redis depends_on + 3 phase-01-* criteria | `scripts/smoke.sh --up && scripts/smoke.sh` | Phase 0 SC#1-#5 + NFR-04 + Phase 1 bypass + routing + rate-limit all pass |
| 8 (Phase 2 final integration) | 5 `auth-N` criteria + auth-service container + redis-backed LoginRateLimiter + MailHog `@Async` send | `docker compose down -v && docker compose -f infra/docker-compose.yml up -d --wait && bash scripts/smoke.sh` | Phase 0 + 1 + Phase 2 SC#1-#5 (all `auth-N` criteria PASS) — final smoke prints `[OK] PHASE 2 SMOKE: 5/5 auth criteria passed` |

### Failure modes

When a criterion fails, look here first:

- **`SC#1` fails** → check `docker compose ps`; look for non-`healthy` services. Common causes: postgres not done initializing, eureka not up, or a backend service crashed during boot.
- **`SC#2` fails** → likely Eureka cold-start; the script already retries up to `EUREKA_RETRY_SECONDS` (30s default). If persistent, check `docker compose logs auth-service` (etc.) for registration errors. Verify D-21 tuning (`registry-fetch-interval-seconds: 5`, `lease-renewal-interval-in-seconds: 5`) is present in each service's `application-docker.yml`.
- **`SC#3` fails** → gateway misconfigured or downstream service missing; check `docker compose logs api-gateway`. The gateway must register with Eureka and have its own actuator endpoint healthy.
- **`SC#3-route` fails** → gateway route table missing the `/__health/<svc>` entry (per D-02), or downstream `/__health` controller missing (per D-01). The gateway's `application.yml` route table should have `/__health/auth` → `http://auth-service:8081/__health` (and similar for trip + destination); each downstream service's `HealthPlaceholderController` must return `{status: UP, phase: 0}`.
- **`SC#4` fails** → frontend container failed to start; check `docker compose logs frontend`. Note: browser console-error verification is **manual** per VALIDATION.md "Manual-Only Verifications".
- **`SC#5` fails** → **Pitfall 3** hit: per-service Flyway history table not configured. Check that `application.yml` for the service contains `spring.flyway.table=<service>_flyway_schema_history` (e.g. `auth_flyway_schema_history`), AND that **`flyway-database-postgresql`** is on the runtime classpath (**Pitfall A** — Flyway 10 modularized PostgreSQL support; missing it produces "Unsupported Database: PostgreSQL 16.x" at startup).
- **`SC#NFR-04` fails** → a paid-SaaS dep was added. Either (a) remove the dep, (b) replace with a free-tier alternative, or (c) get explicit dispensation from the developer + update REQUIREMENTS.md.
- **`phase-01-bypass` fails** → T-01-04 / Pitfall 1 has regressed. The gateway-bypass test hit 127.0.0.1:8082/api/trips/_ping with crafted X-User-Id and no Authorization but did NOT receive 401. Verify (a) `services/trip-service/src/main/java/com/tripplanner/trip/security/ServletSecurityConfig.java` exists and registers ServletJwtCommonFilter via `.addFilterBefore(...)` (plan 01-04 / Convention C26-P1), and (b) `libs/jwt-common`'s `JwtAutoConfiguration` is on the classpath (plan 01-02). The corresponding IT (`DirectServiceAccessWithoutGatewayReturns401IT`) should ALSO be failing; if not, suspect a runtime-only regression (e.g., a profile-conditional bean disabled in `docker` profile).
- **`phase-01-routing` fails with 502** → Pitfall J: gateway booted before downstream service was healthy. Verify `infra/docker-compose.yml` api-gateway.depends_on includes `auth-service / trip-service / destination-service` each with `condition: service_healthy` (plan 01-06 Task 6.1).
- **`phase-01-routing` fails with 503** → Pitfall H: gateway can't reach Redis. Verify `infra/docker-compose.yml` api-gateway.depends_on includes `redis: { condition: service_healthy }` AND `services/api-gateway/src/main/resources/application-docker.yml` has `spring.data.redis.host: redis` (plan 01-03).
- **`phase-01-rate-limit` fails (no 429 in 35 requests)** → RedisRateLimiter not enforcing. Verify `services/api-gateway/src/main/resources/application.yml` has the `RequestRateLimiter` filter on the auth-login route with `replenishRate=30, burstCapacity=30, requestedTokens=900` (plan 01-03 / RESEARCH.md Pattern 5). Also check that `redis` container is healthy (`docker compose -f infra/docker-compose.yml ps redis`).
- **`auth-1` fails on signup (HTTP 4xx/5xx)** → check `docker compose logs auth-service`. Common causes: Hibernate `ddl-auto: validate` mismatch (V2/V3/V4 schema vs `User`/`EmailVerificationToken`/`RefreshToken` entity drift — Plan 02-01 `@JdbcTypeCode(SqlTypes.CHAR)` fix is required); BCrypt encoder bean not wired (Plan 02-03 `SecurityConfig`); `AUTH_JWT_SECRET` not set in `.env` (Plan 02-03 environment passthrough).
- **`auth-1` fails at the MailHog step** ("No MailHog message found for smoke-1@test.local") → `@Async` send did not fire OR mailhog container is not on the compose network. Verify (a) `auth-service.depends_on.mailhog: { condition: service_started }` (Plan 02-04 / docker-compose.yml line 119), (b) `MAIL_FROM` env wired through (`infra/docker-compose.yml` env block line 133), (c) `auth-service` logs do not show D-04 `MailException` WARN, (d) `curl -sf http://localhost:8025/api/v2/messages | jq '.total'` returns >0 after a manual signup.
- **`auth-1` fails at verify step** (HTTP != 302) → `EmailVerificationService.consume` not wired OR redirect base URL not picked up. Check Plan 02-03 `AuthProperties.frontend.baseUrl` binds `FRONTEND_BASE_URL` env, and Plan 02-05 `AuthController.verify` returns `ResponseEntity.status(302).location(URI.create(...))`.
- **`auth-2` fails with HTTP 200 instead of 403** → unverified-account guard missing in `AuthService.login` (Plan 02-05 D-05 ordering: rate-limit → bcrypt → email_verified check). The `@Tag("security")` `EmailNotVerifiedCannotLoginIT` should ALSO be failing if so.
- **`auth-2` `$.detail` mismatch** → UI-SPEC §Server-Driven Copy Contract drift. The verbatim string `Please verify your email before logging in.` must appear in `AuthControllerAdvice.handleEmailNotVerified` (Plan 02-05 — exact strings are tracked in source per Plan 02-05 D-23 commitment).
- **`auth-3` post-logout `/refresh` returns 200** → `RefreshTokenService.revokeChain` did not mark the chain head revoked. Verify D-11 logout path in `AuthController.logout` (looks up by SHA-256 hash, walks chain, sets `revoked_at`). Plan 02-06's `DeletedRefreshTokenCannotBeUsedIT` should be failing in lockstep.
- **`auth-4` cookie A == cookie B** → `RefreshTokenService.rotate` is not actually rotating. Verify D-13 `@Transactional(isolation = REPEATABLE_READ)` + `findByTokenHashForUpdate` first step + new row INSERT before returning. The Plan 02-06 `RotatedRefreshTokenCannotBeReusedIT` will fail too.
- **`auth-5` 6th attempt returns 400 instead of 429** → LoginRateLimiter Lua INCR+EXPIRE script not atomic OR not wired into `AuthService.login` BEFORE bcrypt verify. Verify (a) `redis` container healthy, (b) Plan 02-03 `LoginRateLimiter` (Lua script via `RedisScript.of(LUA, Long.class)`) injected into `AuthService`, (c) D-05 ordering check happens BEFORE the bcrypt-12 verify, (d) Plan 02-06 `LoginRateLimiterTest` (real-Redis Lua atomicity) is GREEN.
- **`auth-5` 6th attempt has wrong `$.detail`** → UI-SPEC drift on `auth.rate_limited` copy. The verbatim string `Too many attempts. Please try again later.` must be returned by `AuthControllerAdvice.handleRateLimited` (Plan 02-05). No `Retry-After` header in v1 (deferred per CONTEXT §Deferred).

---

## Manual verifications (NOT covered by smoke)

Per `.planning/phases/00-monorepo-scaffolding/00-VALIDATION.md` "Manual-Only Verifications", smoke does NOT cover:

- **Eureka dashboard renders 4 services as registered** — visit `http://localhost:8761/` after `docker compose up -d` and confirm all four services (api-gateway, auth-service, trip-service, destination-service) appear in the registered apps list. (`SC#2` only asserts the JSON API; the human eyeballs the UI once.)
- **Frontend landing page renders without browser console errors** — open `http://localhost:5173` + DevTools, confirm zero console errors. (`SC#4` only asserts HTTP reachability + non-empty body.)
- **360 px viewport responsiveness check** — Chrome DevTools mobile sim; verifies NFR-08 baseline.
- **Dark-mode CSS variables wire correctly** — manually toggle the `.dark` class via DevTools and confirm `--background` / `--foreground` swap.
- **Time-to-healthy on a fresh checkout** — `docker compose down -v && time docker compose up -d --wait` from a clean state; confirm wall-clock time ≤60s. CI captures this; a developer must verify it locally before sign-off.

### Phase 1 manual verifications

- **SC#6 Zipkin trace continuity** — `scripts/smoke.sh` cannot scrape the Zipkin UI. After `bash scripts/smoke.sh --up && bash scripts/smoke.sh`, send one routed request:
  ```bash
  curl -s http://localhost:8180/api/trips/_ping -H 'Authorization: Bearer <smoke-token>'
  ```
  Then open `http://localhost:9411/` in a browser, search for the most recent trace, and confirm a SINGLE trace ID spans `api-gateway` and `trip-service` in the timeline. (D-19 / 01-CONTEXT.md). To mint a `<smoke-token>`, run `TOKEN=$(bash scripts/mint-test-token.sh)` — the helper invokes `./gradlew :libs:jwt-common:test --tests JwtFixturesSmokeMintTask -q --console=plain` and captures the JWT printed to stdout by `JwtFixturesSmokeMintTask` (Plan 01-02 Task 2.2). The script is created by Task 6.2 Part C.
- **Eureka dashboard renders 4 services as registered** — already documented above for Phase 0; re-verify after Phase 1 to ensure the gateway's expanded depends_on chain didn't break Eureka registration.

### Phase 2 manual verifications

- **MailHog UI verification email rendering** — Plan 02-07 phase-gate. `auth-1` parses the email body via the MailHog admin API, but visual fidelity (em-dash U+2014 rendering, line breaks, no HTML, plain-text-ness) needs a human eye. After running `bash scripts/smoke.sh`, open `http://localhost:8025/` and inspect the most recent email addressed to `smoke-1@test.local`:
  - **Subject:** `Verify your Trip Planner account` (exact)
  - **From:** `Trip Planner <no-reply@tripplanner.local>`
  - **Body** (plain text, no HTML rendering): matches UI-SPEC §Email Copy Contract verbatim — em-dash before "The Trip Planner team" renders as `—` (U+2014), NOT `--` or `-`; the verification URL is on its own line; "expires in 24 hours" line is present.
  This is the only Phase-2 success-criterion check that cannot be replaced by an automated assertion in v1 (Plan 02-07 §threat_model T-2-07-01). v2 may add a snapshot-test against captured GreenMail body bytes.
- **`@Tag("security")` IT suite GREEN locally** — `./gradlew :services:auth-service:test -PincludeTags=security` should report 4 tests passed: `DeletedRefreshTokenCannotBeUsedIT`, `RotatedRefreshTokenCannotBeReusedIT`, `EmailNotVerifiedCannotLoginIT`, `LoginRateLimitFailedAttemptsTriggersIT` (NFR-05 merge gate). Phase 10 wires this into CI alongside `phase-01-*` smoke runs.

---

## Adding more scripts

Future scripts (e.g., `scripts/seed-cities.sh` for Phase 3) should follow the same conventions:

- `#!/usr/bin/env bash` shebang
- `set -euo pipefail` at the top
- Labelled failure messages via a `fail()` helper that writes to stderr and exits non-zero
- `--help` / `-h` flag printing usage
- Idempotent where possible (re-runnable without manual cleanup)
- Environment-variable overrides documented in this README under the script's section
