---
phase: 00-monorepo-scaffolding
reviewed: 2026-05-08T07:20:33Z
depth: standard
files_reviewed: 87
files_reviewed_list:
  - .editorconfig
  - .env.example
  - .github/workflows/backend.yml
  - .github/workflows/frontend.yml
  - .gitignore
  - README.md
  - build.gradle.kts
  - docker-compose.yml
  - frontend/.eslintignore
  - frontend/.eslintrc.cjs
  - frontend/.gitignore
  - frontend/.npmrc
  - frontend/Dockerfile
  - frontend/README.md
  - frontend/components.json
  - frontend/index.html
  - frontend/package.json
  - frontend/postcss.config.js
  - frontend/src/App.test.tsx
  - frontend/src/App.tsx
  - frontend/src/env.d.ts
  - frontend/src/index.css
  - frontend/src/lib/axios.ts
  - frontend/src/lib/queryClient.ts
  - frontend/src/lib/store.ts
  - frontend/src/lib/utils.ts
  - frontend/src/main.tsx
  - frontend/src/test-setup.ts
  - frontend/tailwind.config.ts
  - frontend/tsconfig.json
  - frontend/tsconfig.node.json
  - frontend/vite.config.ts
  - gradle.properties
  - gradle/libs.versions.toml
  - gradle/wrapper/gradle-wrapper.properties
  - infra/README.md
  - infra/docker-compose.yml
  - infra/postgres/init.sql
  - libs/api-contracts/build.gradle.kts
  - libs/error-handling/build.gradle.kts
  - libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java
  - libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java
  - libs/observability/build.gradle.kts
  - libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java
  - libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java
  - libs/observability/src/main/java/com/tripplanner/observability/ReactiveMdcEnrichmentFilter.java
  - libs/observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  - libs/observability/src/main/resources/logback-spring-base.xml
  - scripts/README.md
  - scripts/smoke.sh
  - services/api-gateway/Dockerfile
  - services/api-gateway/build.gradle.kts
  - services/api-gateway/src/main/java/com/tripplanner/gateway/ApiGatewayApplication.java
  - services/api-gateway/src/main/java/com/tripplanner/gateway/health/GatewayHealthController.java
  - services/api-gateway/src/main/resources/application-docker.yml
  - services/api-gateway/src/main/resources/application.yml
  - services/api-gateway/src/main/resources/logback-spring.xml
  - services/auth-service/Dockerfile
  - services/auth-service/build.gradle.kts
  - services/auth-service/src/main/java/com/tripplanner/auth/AuthServiceApplication.java
  - services/auth-service/src/main/java/com/tripplanner/auth/health/HealthPlaceholderController.java
  - services/auth-service/src/main/resources/application-docker.yml
  - services/auth-service/src/main/resources/application.yml
  - services/auth-service/src/main/resources/db/migration/V1__init.sql
  - services/auth-service/src/main/resources/logback-spring.xml
  - services/destination-service/Dockerfile
  - services/destination-service/build.gradle.kts
  - services/destination-service/src/main/java/com/tripplanner/destination/DestinationServiceApplication.java
  - services/destination-service/src/main/java/com/tripplanner/destination/health/HealthPlaceholderController.java
  - services/destination-service/src/main/resources/application-docker.yml
  - services/destination-service/src/main/resources/application.yml
  - services/destination-service/src/main/resources/db/migration/V1__init.sql
  - services/destination-service/src/main/resources/logback-spring.xml
  - services/eureka-server/Dockerfile
  - services/eureka-server/build.gradle.kts
  - services/eureka-server/src/main/java/com/tripplanner/eureka/EurekaServerApplication.java
  - services/eureka-server/src/main/resources/application.yml
  - services/eureka-server/src/main/resources/logback-spring.xml
  - services/trip-service/Dockerfile
  - services/trip-service/build.gradle.kts
  - services/trip-service/src/main/java/com/tripplanner/trip/TripServiceApplication.java
  - services/trip-service/src/main/java/com/tripplanner/trip/health/HealthPlaceholderController.java
  - services/trip-service/src/main/resources/application-docker.yml
  - services/trip-service/src/main/resources/application.yml
  - services/trip-service/src/main/resources/db/migration/V1__init.sql
  - services/trip-service/src/main/resources/logback-spring.xml
  - settings.gradle.kts
findings:
  blocker: 1
  warning: 8
  info: 7
  total: 16
status: issues_found
---

# Phase 00: Code Review Report

**Reviewed:** 2026-05-08T07:20:33Z
**Depth:** standard
**Files Reviewed:** 87
**Status:** issues_found

## Summary

Phase 0 scaffolding is largely well-structured: gradle catalog is single-source-of-truth, Spring Cloud BOM correctly pins to 2025.0.x (D-30 supersedes CLAUDE.md's stale 2024.0.x reference), Pitfall A is honored everywhere (`flyway-database-postgresql` is a `runtimeOnly` dep on every DB-backed service), actuator endpoints are limited to `health,info,prometheus` per T-00-29, downstream services bind to 127.0.0.1 per D-22, Eureka client tuning is uniform (5s/5s/10s) per D-21, and Axios is pinned `^1.16.0` (>= 1.15.0 CVE floor). The multi-stage Dockerfiles are disciplined (build context `..` repo root with `libs/` + `services/<svc>/` selectively copied) and healthchecks are wired into every dependent.

The chief defect is a real correctness bug: **the Postgres init script hard-codes role names (`auth_svc`, `trip_svc`, `destination_svc`) while the services read those names from `AUTH_DB_USER` / `TRIP_DB_USER` / `DESTINATION_DB_USER` environment variables**. Any deviation from the literal defaults (e.g., a developer changing `AUTH_DB_USER` in `.env` for any reason) silently desynchronizes the cluster from the role catalog and the auth-service will fail to log in even though Compose comes up "healthy" before the JDBC connection is attempted.

Beyond that BLOCKER, there are eight WARNING-level concerns spanning JDBC URL fragility (`${POSTGRES_DB}` lacks a default in the docker profiles, contradicting the dev profile's `${POSTGRES_DB:tripplanner}`), a known-unsafe WebFlux MDC propagation pattern in `ReactiveMdcEnrichmentFilter` (acknowledged in comments, but a real cross-request data-leak hazard worth flagging), an over-broad `@ConditionalOnClass` that registers a reactive bean on servlet apps, hard-coded compose-network DNS in the gateway's dev profile (will not run outside Docker), and a smoke-script SC#3-route gap.

INFO items cover the dev-only literal Postgres passwords (per phase-0 spec — explicitly downgraded to Info per review brief), the placeholder JWT secret, CI cache absence, etc.

---

## Blocker Issues

### BL-01: Postgres init.sql hard-codes role names but services read them from env vars — silent auth failure on any rename

**File:** `infra/postgres/init.sql:18-25, 31-41`
**Also affects:** `.env.example:14-19`, `services/auth-service/src/main/resources/application.yml:26-27` (and the equivalent in trip / destination)

**Issue:**
The init script creates roles with **string literals**:
```sql
CREATE ROLE auth_svc LOGIN PASSWORD 'auth_svc';
CREATE ROLE trip_svc LOGIN PASSWORD 'trip_svc';
CREATE ROLE destination_svc LOGIN PASSWORD 'destination_svc';
...
GRANT USAGE, CREATE ON SCHEMA auth TO auth_svc;
ALTER ROLE auth_svc SET search_path = auth;
```
But every service application reads the credentials from env:
```yaml
username: ${AUTH_DB_USER:auth_svc}
password: ${AUTH_DB_PASSWORD:auth_svc}
```
And `.env.example` exposes all six variables (`AUTH_DB_USER`, `AUTH_DB_PASSWORD`, `TRIP_DB_USER`, ...) as if they were configurable.

This contract is broken. If a developer (or operator preparing a pre-prod copy) sets `AUTH_DB_USER=auth_v2` in `.env`, the postgres container still creates a role named `auth_svc` (the literal in `init.sql`) and the auth-service then fails its first JDBC connection with `FATAL: role "auth_v2" does not exist`. Compose reports SC#1 healthy because the postgres container itself is up; the cascading service failure shows up only when the auth-service tries to connect a few seconds later. The error is non-obvious because the surface invariant ("env-driven user names") looks intact.

Two failure modes follow:
1. **Drift hazard:** the comment in `init.sql:13-16` explicitly says "match defaults in .env.example" — but nothing enforces that match (no tests, no validation, no shared substitution). The next person who edits one and not the other will introduce a silent break.
2. **Phase-2 password-rotation hazard:** `.env.example` documents these as "Per-service DB users (D-08; matches infra/postgres/init.sql)" — strongly implying they are configurable. They are not.

**Fix:**

Two acceptable fixes; pick one.

**Option A — make init.sql dynamic** via Postgres `\set` variables passed through Docker's `POSTGRES_INITDB_ARGS` is not natively supported, but you can switch to an `entrypoint` script that templates the SQL. Cleaner alternative: drop `init.sql` and put role bootstrap inside each service's V1 Flyway migration as a no-op SELECT (you already have `V1__init.sql` files that do `SELECT 1`). However Flyway runs as the per-service user, not as superuser, so role creation cannot live there — Option B is more realistic.

**Option B — remove the env-driven illusion (recommended for Phase 0).** Delete the per-service user / password env vars from `.env.example` (lines 13-19), hard-code `auth_svc/auth_svc`, `trip_svc/trip_svc`, `destination_svc/destination_svc` in each service's `application.yml`, and add a comment in `application.yml` and `init.sql` pointing at the other file as the binding source of truth. Concretely:

```yaml
# services/auth-service/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:tripplanner}?currentSchema=auth
    username: auth_svc       # MUST match infra/postgres/init.sql (Phase 0 hard-coded; Phase 2 introduces secret rotation)
    password: auth_svc       # dev-only literal; .env.example does NOT override
```

And in `.env.example`, replace the per-service block with a single comment explaining that Phase 0 hard-codes these and Phase 2 will template them. This makes the Phase 0 promise of "non-overridable defaults" explicit.

If you keep env-var indirection (Option C), the script must `psql -c "CREATE ROLE \"$AUTH_DB_USER\" ..."` rather than running a static `init.sql` — switch to a shell-templated entrypoint.

---

## Warning Issues

### WR-01: ReactiveMdcEnrichmentFilter leaks MDC across requests in WebFlux

**File:** `libs/observability/src/main/java/com/tripplanner/observability/ReactiveMdcEnrichmentFilter.java:38-47`

**Issue:**
The filter sets MDC from inside `doOnEach` and clears it inside `doFinally`:
```java
return chain.filter(exchange)
        .doOnEach(signal -> {
            var span = tracer.currentSpan();
            if (span != null) {
                MDC.put("traceId", span.context().traceId());
                MDC.put("spanId", span.context().spanId());
            }
            MDC.put("requestId", reqId);
        })
        .doFinally(signalType -> MDC.clear());
```
This is the well-known WebFlux MDC anti-pattern. `doOnEach` invokes the lambda on whichever thread emits the signal — for any non-trivial reactive chain that thread differs from the thread that runs `doFinally`. The result:

- MDC values get installed on every emitting thread the chain visits.
- `doFinally` clears MDC on **only one** of those threads.
- The MDC state on the other threads persists into the next unrelated request that lands on those threads (the gateway's Reactor-Netty event loop pool is small and shared).

Concretely: request A finishes, request B starts; if request B is logged from a thread previously polluted by A's `doOnEach` and the polluting thread doesn't itself emit a signal that re-enters the filter, B's logs carry A's `traceId` / `requestId` / `spanId`. This is a **cross-request data-correlation defect**, not a theoretical concern — in production it presents as occasional `requestId` collisions in logs.

The comment acknowledges that "WebFlux MDC propagation across thread switches is non-trivial; Phase 10 hardening may switch to contextWrite + Hooks.enableAutomaticContextPropagation()" — which is the right Phase-10 fix. But the current code is not just imperfect; it is actively wrong, and the comment doesn't say that the Phase-0 implementation can leak across requests.

The Phase 0 acceptable mitigation is to **only put MDC values on the request thread (not in `doOnEach`)** and accept that downstream operators won't see them. Specifically: do the MDC mutation imperatively before `chain.filter(exchange)`, then clear in `doFinally`. The values will be lost across the first thread hop, but they won't pollute other requests.

**Fix:**
```java
@Override
public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    if (requestId == null || requestId.isBlank()) {
        requestId = UUID.randomUUID().toString();
    }
    final String reqId = requestId;
    // Phase 0 minimal contract: only the request-entry thread gets MDC. Downstream
    // reactive operators see empty MDC; Phase 10 lifts this with contextWrite +
    // Hooks.enableAutomaticContextPropagation(). Putting MDC inside doOnEach is
    // unsafe — see https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html
    var span = tracer.currentSpan();
    if (span != null) {
        MDC.put("traceId", span.context().traceId());
        MDC.put("spanId", span.context().spanId());
    }
    MDC.put("requestId", reqId);
    try {
        return chain.filter(exchange).doFinally(signalType -> MDC.clear());
    } catch (RuntimeException ex) {
        MDC.clear();
        throw ex;
    }
}
```
Update the comment to explicitly call out the cross-request leak risk that motivated this minimal shape, and reference the Phase 10 task that lifts it.

---

### WR-02: ReactiveConfig auto-config registers WebFilter bean on servlet apps too

**File:** `libs/observability/src/main/java/com/tripplanner/observability/ObservabilityAutoConfiguration.java:37-44`

**Issue:**
```java
@Configuration
@ConditionalOnClass(name = "org.springframework.web.server.WebFilter")
static class ReactiveConfig {
    @Bean
    public ReactiveMdcEnrichmentFilter reactiveMdcEnrichmentFilter(Tracer tracer) {
        return new ReactiveMdcEnrichmentFilter(tracer);
    }
}
```
`org.springframework.web.server.WebFilter` lives in `spring-web`, which is on the classpath of **every** Spring Boot app that uses the web tier (servlet OR reactive). `spring-boot-starter-web` pulls `spring-web`. So the conditional matches in auth-service, trip-service, destination-service (servlet apps) and unconditionally creates an unused `ReactiveMdcEnrichmentFilter` bean in their context. It's a no-op there (servlet stack does not invoke `WebFilter`s), but:

- It increases startup memory and bean-graph noise.
- A future audit would find a "reactive filter registered in a servlet app" and waste time chasing a phantom bug.
- The intent — "this code runs only on WebFlux apps" — is silently violated.

The corresponding `ServletConfig` block uses the right discriminator (`jakarta.servlet.Filter`, present only on servlet apps because `libs/observability` declares it `compileOnly`). The reactive side needs an analogous reactive-only marker.

**Fix:**
Switch the conditional to a marker only present in reactive stacks:
```java
@Configuration
@ConditionalOnClass(name = "org.springframework.web.reactive.DispatcherHandler")
static class ReactiveConfig { ... }
```
`DispatcherHandler` is in `spring-webflux`, which servlet apps do NOT pull. Equivalently, `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)` is the Spring-Boot-idiomatic switch and is more readable. Apply the symmetric `ConditionalOnWebApplication.Type.SERVLET` to the servlet block while you're there — `jakarta.servlet.Filter` is reliable but `@ConditionalOnWebApplication` is the documented API.

---

### WR-03: Docker profile JDBC URLs lack `${POSTGRES_DB}` fallback — silent malformed URL on missing env var

**File:** `services/auth-service/src/main/resources/application-docker.yml:15`
**Also affects:** `services/trip-service/src/main/resources/application-docker.yml:12`, `services/destination-service/src/main/resources/application-docker.yml:12`

**Issue:**
The dev profile (`application.yml`) has a defensive default:
```yaml
url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:tripplanner}?currentSchema=auth
```
The docker profile drops the default:
```yaml
url: jdbc:postgresql://postgres:5432/${POSTGRES_DB}?currentSchema=auth
```
If `POSTGRES_DB` is unset in the environment that loads the docker profile (e.g., a developer who ran `docker compose up -d auth-service` directly without first sourcing `.env`, or a CI runner that sets `SPRING_PROFILES_ACTIVE=docker` for an integration test without forwarding the env), Spring's placeholder resolution leaves `${POSTGRES_DB}` literal in the URL, producing `jdbc:postgresql://postgres:5432/?currentSchema=auth`. This either fails with a confusing JDBC parse error or silently picks the JDBC default database — neither is safe.

Compose does forward `POSTGRES_DB` from `.env` (verified in `infra/docker-compose.yml:106`), so the canonical path works. The risk is operator misuse, and the fix is one keystroke per file.

**Fix:**
Add the same default the dev profile uses:
```yaml
url: jdbc:postgresql://postgres:5432/${POSTGRES_DB:tripplanner}?currentSchema=auth
```
(and the equivalent for trip and destination). This makes the docker profile behave identically to the dev profile when env-var resolution fails.

---

### WR-04: api-gateway `application.yml` route URIs are compose-network DNS — gateway will not run on host

**File:** `services/api-gateway/src/main/resources/application.yml:36-52`

**Issue:**
The gateway's dev (default) profile hard-codes compose-network DNS hostnames in its route table:
```yaml
- id: health-auth
  uri: http://auth-service:8081
- id: health-trip
  uri: http://trip-service:8082
- id: health-destination
  uri: http://destination-service:8083
```
These hostnames only resolve inside the compose network. A developer running `./gradlew :services:api-gateway:bootRun` against locally-launched downstream services on `localhost:8081-8083` will get NXDOMAIN on every `/__health/<svc>` request.

There is no `application-docker.yml` override for these routes; the docker profile only adds Eureka tuning. So the dev profile is the docker profile in practice. This contradicts the convention of "dev-profile defaults are localhost-friendly; docker overrides them" used everywhere else in the codebase.

This was not caught because the smoke script always runs against compose. SC#3-route would fail in a "host-mode dev" workflow, but no one has tried that.

**Fix:**
Move the DNS-name URIs into `application-docker.yml` and put localhost URIs in `application.yml`:
```yaml
# application.yml — DEFAULT (dev) profile
spring:
  cloud:
    gateway:
      routes:
        - id: health-auth
          uri: http://localhost:8081
        - id: health-trip
          uri: http://localhost:8082
        - id: health-destination
          uri: http://localhost:8083
        # /__health/gateway alias unchanged
```
```yaml
# application-docker.yml — overrides for compose
spring:
  cloud:
    gateway:
      routes:
        - id: health-auth
          uri: http://auth-service:8081
        - id: health-trip
          uri: http://trip-service:8082
        - id: health-destination
          uri: http://destination-service:8083
```
(Spring Cloud Gateway merges the route lists by `id`, so the docker profile's entries cleanly override the dev profile's.)

Same applies to the `/__health/gateway` route's `uri: http://localhost:8080` — that one is correct in both profiles and needs no split.

---

### WR-05: smoke.sh SC#3-route does not test `/__health/gateway`

**File:** `scripts/smoke.sh:209-233`

**Issue:**
`check_sc3_route` iterates `for svc in auth trip destination` only. The gateway's own `/__health/gateway` route is declared in `application.yml:29-34` and the comment explicitly says it exists "for naming consistency with the /__health/<svc> family. The smoke script can iterate over a uniform set including 'gateway' if SC#3-route ever needs to verify the gateway placeholder through the route table." But the smoke script does not include it, so the route is shipped without coverage.

If a Phase-1 refactor breaks the SetPath filter for `/__health/gateway` (for example a typo in the predicates list that affects all routes), SC#3-route still passes because the gateway placeholder is never probed. This is a coverage gap that contradicts the explicit pattern documented in `application.yml`.

**Fix:**
```bash
check_sc3_route() {
  for svc in gateway auth trip destination; do
    local url="http://localhost:8080/__health/$svc"
    ...
```
(`gateway` first so a route-table regression surfaces before the cross-service forwarding ones.)

---

### WR-06: smoke.sh `set -u` + `missing+=("$svc")` array-append bombs on empty-array re-eval under bash 4.0 / 4.1

**File:** `scripts/smoke.sh:147-158`

**Issue:**
Inside `check_sc2`'s retry loop:
```bash
local missing=()
for svc in "${services[@]}"; do
  ...
  missing+=("$svc")
  ...
done
```
With `set -u` (line 19) and an array declared as empty `()`, bash 4.0 and 4.1 will fail on `"${missing[@]}"` expansion with "missing[@]: unbound variable". Bash 4.2+ added the special-case "empty array != unset", which is what every modern macOS / Ubuntu install uses, so this works in practice. But the script's portability claim is broken on older runners.

Less hypothetically: the `local` declaration is INSIDE the `while` loop, so on each retry iteration `missing` is reset (good — the retry is idempotent). But the `if [ "${#missing[@]}" -eq 0 ]` check on line 161 references `missing` outside the `for` loop, in the same scope — fine.

This is a portability rough edge, not an active bug, but worth noting because the project pins to `ubuntu-24.04` runners (Bash 5.x) — so CI is safe — but a local developer on an old container is not.

**Fix:**
Either declare with explicit "empty-but-set" idiom:
```bash
local missing=("")     # one-element array with empty string
unset missing[0]       # now empty but "set"
```
Or simpler — disable `nounset` for the array operations:
```bash
set +u
local missing=()
for svc in "${services[@]}"; do
  if ! ...; then missing+=("$svc"); fi
done
set -u
```
Or just document the bash >= 4.2 requirement at the top of the file alongside the `set -euo pipefail` line.

---

### WR-07: `init.sql` does not REVOKE default `public` privileges — every service user has CREATE on `public` schema

**File:** `infra/postgres/init.sql:9-33`

**Issue:**
The script grants schema-scoped USAGE+CREATE on the per-service schemas (`auth`, `trip`, `destination`) but does nothing about the default `public` schema. PostgreSQL 15+ tightened the default — non-owners no longer get CREATE on public — so this is OK on PG 16 (the locked version). But the comment claims "schema-scoped grants — per-service user has USAGE + CREATE on its own schema only (D-08 cross-schema denial)" — and that claim only holds because of PG 16's default. If the project ever has to run on PG <15 (e.g., a CI runner using a stale image, or a downgrade for compatibility), the per-service users gain CREATE on `public` and the "schema-scoped" claim silently breaks.

It also leaves the per-service users with USAGE on `public` (they need to read `pg_catalog`-equivalent system views via the public search path), which is fine, but the audit story is "we explicitly chose this" only if the file says so.

**Fix:**
Add an explicit revoke at the bottom for defense-in-depth and to make the audit claim hold across PG versions:
```sql
-- Defense-in-depth: PG 16 already disallows CREATE on public for non-owners,
-- but state it explicitly so a downgrade or off-version probe doesn't widen the surface.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE CREATE ON SCHEMA public FROM auth_svc, trip_svc, destination_svc;
-- USAGE on public is intentionally retained — JDBC drivers occasionally hit
-- pg_catalog-equivalent paths through it.
```
This is one of those lines that costs nothing now and prevents a real Pitfall during a future Postgres-version bump.

---

### WR-08: `auth-service` JPA `default_schema=auth` while `flyway.schemas=auth` — mismatch with future entities

**File:** `services/auth-service/src/main/resources/application.yml:34-38`
**Also affects:** `services/trip-service/src/main/resources/application.yml`, `services/destination-service/src/main/resources/application.yml`

**Issue:**
```yaml
jpa:
  hibernate:
    ddl-auto: validate
  properties:
    hibernate.default_schema: auth
flyway:
  default-schema: auth
```
With `ddl-auto: validate` and `hibernate.default_schema: auth`, Hibernate validates entities against the `auth` schema regardless of the JDBC `currentSchema` parameter. That is correct for Phase 0 (no entities exist yet, so validate passes trivially against the empty schema). The risk arrives in Phase 2: any `@Entity` declared without `@Table(schema="auth")` will get `default_schema=auth` applied — which works — but if a developer adds `@Table(schema="public")` or forgets the schema annotation entirely while running against a JDBC URL using `currentSchema=public` (e.g., for an integration test), the validate step will fail with confusing "table not found" errors that don't make the schema mismatch obvious.

This isn't a Phase 0 bug per se — `ddl-auto: validate` against an empty schema is a no-op — but the configuration is internally redundant: `hibernate.default_schema` AND JDBC `?currentSchema=auth` AND `flyway.default-schema` all encode the same choice three times. The next person adding an entity will pick one and forget the other two, leading to silent split-brain.

**Fix:**
Pick ONE source of truth for "this service's schema is `auth`" and document it. The cleanest pattern for Phase 0:
- Keep `flyway.default-schema: auth` and `flyway.schemas: auth` (Pitfall 3 hard requirement).
- Keep JDBC `?currentSchema=auth` (drives `search_path`).
- **Remove** `hibernate.default_schema: auth` — let entities' `@Table(schema=...)` annotations be the per-entity authoritative path, with the JDBC `currentSchema` as the fallback.

Alternative (if you prefer central config): keep `hibernate.default_schema` and add a comment block in `application.yml` listing all three knobs and the rule "all three must agree; Pitfall 3 makes flyway.* mandatory; the others are derived."

This is a refactor for clarity, not a correctness bug today. Worth doing before Phase 2 lands real `@Entity` classes.

---

## Info Issues

### IN-01: Postgres dev passwords are literal `auth_svc`/`trip_svc`/`destination_svc` (= username)

**File:** `infra/postgres/init.sql:18-25`
**Also affects:** `.env.example:15-19`

**Issue:**
Per the review brief, this is **explicitly accepted as Phase 0 dev convention** — flagged Info per instructions. But the credential pattern is the worst-case: password equals username, no rotation hook, identical across all three users. In any non-dev context this is an instant audit failure.

**Fix:**
None required for Phase 0. Add a `WARN: NOT FOR PROD` block to `infra/postgres/init.sql` and a phase-2-or-later TODO to `.planning/ROADMAP.md` for secret rotation. Suggested header in `init.sql`:
```sql
-- WARNING: Phase 0 dev-only literal passwords. Phase 2+ MUST template these
-- via Compose secrets / env-substitution before any cloud deployment.
-- Cross-reference: ROADMAP.md Phase 2 / docs/05-auth-security.md §Secrets.
```

---

### IN-02: `AUTH_JWT_SECRET=dev-only-32-byte-secret-replace-in-prod` is shipped in `.env.example`

**File:** `.env.example:25`

**Issue:**
The placeholder is intentional and matches the comment "real prod secrets come from environment variables, never YAML." Counted: 39 ASCII chars (>= 32 bytes for HS256). Phase 0 does not consume the secret, so this is purely a dev-facing template. No leak risk because it's a template, not a real key. Flagged Info because it's the kind of string a careless `grep -r 'secret'` audit will surface in five other phases.

**Fix:**
None for Phase 0. When Phase 2 lands the `JwtService`, add a startup assertion that the secret is NOT the literal `dev-only-32-byte-secret-replace-in-prod` when `SPRING_PROFILES_ACTIVE` does not contain `dev`. That makes the placeholder unweaponizable.

---

### IN-03: Backend CI workflow does not cache Gradle dependencies

**File:** `.github/workflows/backend.yml:57-72`

**Issue:**
Each matrix job pulls all Gradle dependencies fresh (no `actions/cache@v4` step keyed on `gradle/wrapper/gradle-wrapper.properties` + `**/build.gradle.kts`). With 5 matrix entries, this multiplies download time by 5. The performance class is explicitly out of v1 review scope per the agent brief, but missing Gradle cache here is a quality-of-life concern that becomes painful in Phase 1+ when more deps land. Logging here so it isn't lost.

**Fix (deferred):**
After the `setup-java` step:
```yaml
- name: Gradle dependency cache
  uses: actions/cache@v4
  with:
    path: |
      ~/.gradle/caches
      ~/.gradle/wrapper
    key: gradle-${{ runner.os }}-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties', 'gradle/libs.versions.toml') }}
    restore-keys: gradle-${{ runner.os }}-
```
Or switch to `gradle/actions/setup-gradle@v3` which handles caching and wrapper validation in one step.

---

### IN-04: Frontend CI workflow does not cache pnpm store

**File:** `.github/workflows/frontend.yml:17-24`

**Issue:**
Symmetric to IN-03. `pnpm install --frozen-lockfile` re-fetches the entire store on every run. Given the ~1k subdep tree from Vite + Vitest + RTL + the shadcn deps, this is a 60-90 second tax per CI run.

**Fix (deferred):**
```yaml
- uses: actions/setup-node@v4
  with:
    node-version: '20'
    cache: 'pnpm'      # built-in pnpm cache support since setup-node@v4
    cache-dependency-path: frontend/pnpm-lock.yaml
```
(`cache: 'pnpm'` requires `corepack enable` to run BEFORE `setup-node` reads `packageManager`; alternatively use `pnpm/action-setup@v3` for explicit control.)

---

### IN-05: Root `build.gradle.kts` applies Jacoco to subprojects but no coverage threshold task is configured

**File:** `build.gradle.kts:13-21`

**Issue:**
```kotlin
subprojects {
    apply(plugin = "java")
    apply(plugin = "jacoco")
    ...
}
```
Jacoco's `jacocoTestReport` task is now wired everywhere, but no `jacocoTestCoverageVerification` is configured and `check` does not depend on it. CLAUDE.md mandates "≥70% backend service-layer line coverage; 100% on auth + ownership-check paths" but Phase 0 ships the plugin without enforcement. Phase 1 or Phase 10 will need to wire this; logging here so the gap is visible.

**Fix (Phase 1+):**
Add to `subprojects` block:
```kotlin
tasks.withType<JacocoReport> {
    dependsOn(tasks.withType<Test>())
}
tasks.withType<JacocoCoverageVerification> {
    violationRules {
        rule {
            limit { minimum = 0.70.toBigDecimal() }
        }
    }
}
tasks.named("check") { dependsOn(tasks.withType<JacocoCoverageVerification>()) }
```
Carve out `:services:eureka-server` (no business logic to test) and `:libs:api-contracts` (empty) via `if (project.path !in listOf(...))`.

---

### IN-06: `frontend/src/lib/axios.ts` uses `crypto.randomUUID()` — only available in secure contexts

**File:** `frontend/src/lib/axios.ts:13`

**Issue:**
```ts
config.headers['X-Request-Id'] = crypto.randomUUID();
```
`crypto.randomUUID()` is only available on `https://` origins or `http://localhost`. If a developer runs `pnpm dev --host` and accesses the frontend from a LAN IP (`http://192.168.x.x:5173`), `crypto.randomUUID()` is undefined and every request throws `TypeError: crypto.randomUUID is not a function`. Phase 0's `vite.config.ts` already binds `host: '0.0.0.0'`, so this is reachable.

**Fix (low-priority):**
Add a safe polyfill:
```ts
const genRequestId = () =>
  (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function')
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
...
config.headers['X-Request-Id'] = genRequestId();
```
The fallback is not cryptographically random but request IDs don't need to be; they need to be unique-enough for log correlation.

---

### IN-07: `frontend/.eslintrc.cjs` does not configure Vitest globals; tests work today but linting drift later is likely

**File:** `frontend/.eslintrc.cjs:6-13`

**Issue:**
The current `App.test.tsx` works because every Vitest API is imported explicitly:
```ts
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
```
A future test that uses `globals: true` from `vite.config.ts` and omits the explicit import will trip ESLint's `no-undef` rule. The `vite.config.ts` test block already enables `globals: true`, so the codebase is one un-imported test file away from a lint-vs-runtime contradiction.

**Fix (low-priority):**
Either:
- Drop `globals: true` from `vite.config.ts` (forces explicit imports, matches current style), OR
- Add `eslint-plugin-vitest` and `extends: ['plugin:vitest/recommended']` to `.eslintrc.cjs`, OR
- Add `env: { 'vitest-globals/env': true }` after installing `eslint-plugin-vitest-globals`.

The cheapest fix is option 1 (drop `globals: true`) since the project's only test file already imports explicitly.

---

_Reviewed: 2026-05-08T07:20:33Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
