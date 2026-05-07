# 08 — Deployment & DevOps

**Status**: Draft for review
**Last updated**: 2026-05-08

## 1. Local development (v1 target)

### 1.1 Prerequisites
- Docker Desktop (or OrbStack) with 4 GB+ allocated.
- JDK 21 (Temurin recommended).
- Node 20 + pnpm 9.
- `./gradlew` and `pnpm` only — no globally installed CLIs needed.

### 1.2 First-run flow
```bash
git clone …
cd trip-planner
cp .env.example .env                       # JWT_SECRET, SMTP, etc.

docker compose -f infra/docker-compose.yml up -d \
  postgres redis mailhog zipkin eureka-server
                                            # infrastructure only

./gradlew :services:auth-service:bootRun &
./gradlew :services:trip-service:bootRun &
./gradlew :services:destination-service:bootRun &
./gradlew :services:api-gateway:bootRun &

cd frontend && pnpm install && pnpm dev
                                            # http://localhost:5173
```

For a fully containerized run:
```bash
docker compose -f infra/docker-compose.yml up
```

### 1.3 Service ports
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

### 1.4 Environment variables (`.env.example`)
```
# Postgres
POSTGRES_DB=tripplanner
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Per-service DB users (matching infra/postgres/init.sql)
AUTH_DB_USER=auth_svc
AUTH_DB_PASSWORD=auth_svc
TRIP_DB_USER=trip_svc
TRIP_DB_PASSWORD=trip_svc
DESTINATION_DB_USER=destination_svc
DESTINATION_DB_PASSWORD=destination_svc

# Auth
AUTH_JWT_SECRET=dev-only-32-byte-secret-replace-in-prod
AUTH_JWT_ACCESS_TTL_SEC=900
AUTH_JWT_REFRESH_TTL_SEC=604800

# SMTP (Mailhog default)
SMTP_HOST=mailhog
SMTP_PORT=1025
SMTP_FROM=noreply@tripplanner.local

# External providers
OPENTRIPMAP_API_KEY=                          # optional; basic tier works without
FOURSQUARE_API_KEY=

# Observability
ZIPKIN_BASE_URL=http://zipkin:9411

# Frontend
VITE_API_URL=http://localhost:8080
```

`.env` is gitignored. `.env.example` is committed and updated whenever a new
required variable is added.

## 2. Docker Compose layout

```yaml
# infra/docker-compose.yml (excerpt)
services:
  postgres:
    image: postgres:16
    environment: { POSTGRES_PASSWORD: $POSTGRES_PASSWORD, ... }
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck: { test: ["CMD", "pg_isready", "-U", "postgres"] }

  redis:
    image: redis:7-alpine
    healthcheck: { test: ["CMD", "redis-cli", "ping"] }

  mailhog:
    image: mailhog/mailhog:latest
    ports: ["1025:1025", "8025:8025"]

  zipkin:
    image: openzipkin/zipkin:3
    ports: ["9411:9411"]

  eureka-server:
    build: { context: ../services/eureka-server }
    ports: ["8761:8761"]

  api-gateway:
    build: { context: ../services/api-gateway }
    ports: ["8080:8080"]
    depends_on: { eureka-server: { condition: service_healthy } }
    environment:
      EUREKA_URL: http://eureka-server:8761/eureka
      AUTH_JWT_SECRET: $AUTH_JWT_SECRET

  auth-service:
    build: { context: ../services/auth-service }
    depends_on:
      postgres: { condition: service_healthy }
      eureka-server: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/$POSTGRES_DB?currentSchema=auth
      SPRING_DATASOURCE_USERNAME: $AUTH_DB_USER
      SPRING_DATASOURCE_PASSWORD: $AUTH_DB_PASSWORD
      AUTH_JWT_SECRET: $AUTH_JWT_SECRET
      SPRING_MAIL_HOST: $SMTP_HOST

  trip-service: { ... similar ... }
  destination-service: { ... similar ... }

  frontend:
    build: { context: ../frontend }
    ports: ["5173:5173"]
    environment: { VITE_API_URL: http://localhost:8080 }

volumes:
  pgdata:
```

`docker-compose.override.yml` mounts source for hot reload during dev.

## 3. Cloud deployment path (v2, designed but not built)

| Component | Service | Free tier sufficient? |
|-----------|---------|----------------------|
| Backend services | Fly.io (one app per service) | Yes for portfolio scale |
| Postgres | Neon (serverless Postgres) | Yes |
| Redis | Upstash (serverless Redis) | Yes |
| SMTP | Resend (free tier 3k/mo) | Yes |
| Frontend | Vercel | Yes |
| Image registry | GitHub Container Registry | Yes (public images) |
| DNS / TLS | Cloudflare or Fly's built-in | Yes |
| Tracing | Self-hosted Zipkin on Fly OR Honeycomb free tier | Yes |
| Logs | Fly logs + Logtail free tier | Yes |

A `infra/cloud/` folder with Fly.io `fly.toml` files per service is on the v2 backlog.

## 4. CI/CD with GitHub Actions

### 4.1 Backend workflow (`.github/workflows/backend.yml`)
Trigger: push to any branch, PR to `main`.

```yaml
jobs:
  detect-changes:
    runs-on: ubuntu-latest
    outputs:
      services: ${{ steps.filter.outputs.services }}
    steps:
      - uses: actions/checkout@v4
      - uses: dorny/paths-filter@v3
        id: filter
        with:
          filters: |
            auth: 'services/auth-service/**'
            trip: 'services/trip-service/**'
            destination: 'services/destination-service/**'
            gateway: 'services/api-gateway/**'
            shared: 'libs/**'

  test-backend:
    needs: detect-changes
    if: ${{ needs.detect-changes.outputs.services != '[]' }}
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: ${{ fromJson(needs.detect-changes.outputs.services) }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '21' }
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew :services:${{ matrix.service }}:check
      - run: ./gradlew :services:${{ matrix.service }}:integrationTest
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: jacoco-${{ matrix.service }}
          path: services/${{ matrix.service }}/build/reports/jacoco/

  security-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: ./gradlew test -PincludeTags=security

  dependency-check:
    runs-on: ubuntu-latest
    steps:
      - run: ./gradlew dependencyCheckAggregate
      - uses: actions/upload-artifact@v4
        with: { name: owasp-report, path: build/reports/owasp/ }
```

If any service in `libs/` changes, all services rebuild.

### 4.2 Frontend workflow (`.github/workflows/frontend.yml`)
```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v3
      - uses: actions/setup-node@v4
        with: { node-version: 20, cache: pnpm, cache-dependency-path: frontend/pnpm-lock.yaml }
      - run: pnpm --filter frontend install
      - run: pnpm --filter frontend lint
      - run: pnpm --filter frontend test --run
      - run: pnpm --filter frontend build

  e2e:
    runs-on: ubuntu-latest
    needs: test
    if: github.event_name == 'pull_request'
    steps:
      - uses: actions/checkout@v4
      - run: docker compose -f infra/docker-compose.yml up -d
      - run: |
          # wait for stack
          npx wait-on http://localhost:8080/actuator/health http://localhost:5173
      - run: pnpm --filter frontend exec playwright test
      - uses: actions/upload-artifact@v4
        if: failure()
        with: { name: playwright-trace, path: frontend/playwright-report/ }
```

### 4.3 Image build (deferred to v2)
A separate `release.yml` builds Docker images per service tagged with the
commit SHA, pushes to GHCR, and (eventually) deploys to Fly.io. Not v1.

## 5. Observability stack

### 5.1 Logging
- All services use Logback with `logstash-logback-encoder` for JSON output to stdout.
- MDC fields: `traceId`, `spanId`, `userId`, `requestId`.
- Sensitive fields (password, token) are redacted by a custom Logback converter that masks any field matching a name allowlist.
- Local: `docker compose logs -f auth-service`. Cloud: platform-native log viewer.

### 5.2 Metrics
- Each service exposes `/actuator/prometheus`.
- Standard JVM, HTTP, datasource metrics out of the box.
- Custom metrics:
  - `tripplanner.provider.calls` (tags: provider, outcome) — count + latency.
  - `tripplanner.cache.hits` / `misses` (tags: cache_name).
  - `tripplanner.itinerary.position_reindexes_total` — operational health signal.
- Prometheus is **not** part of the v1 compose stack (would add bloat). Endpoints exist; scrape locally with `curl` or wire up Prometheus when needed in Phase 10.

### 5.3 Tracing
- Micrometer Tracing + OpenTelemetry exporter → Zipkin at `:9411`.
- W3C `traceparent` header propagated by gateway → services → DB queries (via `p6spy`).
- Frontend includes `X-Request-Id` header per request (UUID v4); gateway uses it as the trace id seed when no `traceparent` is present.

### 5.4 Health
- Each service: `/actuator/health` with `livenessState` and `readinessState` differentiated.
- `livenessState`: app is up.
- `readinessState`: app is up AND can reach DB and (where relevant) Redis. Eureka uses this to control traffic routing.

## 6. Secrets management

- v1 (local): `.env` file gitignored.
- v2 (deployed): `flyctl secrets set ...` per service.
- Never commit secrets. CI uses GitHub Actions secrets for the security workflow if it ever needs network egress.
- Secret rotation: documented procedure in v2 (requires graceful rolling restart of services). Not v1.

## 7. Backup & recovery (deferred)

For local dev: Postgres data is in a Docker volume; `docker compose down -v`
wipes it. Document this in README.

For cloud: Neon includes point-in-time recovery on free tier. No additional
backup required for portfolio scope.

## 8. Runbook (minimal v1)

| Symptom | Diagnostic | Fix |
|---------|------------|-----|
| `docker compose up` hangs on auth-service | check Eureka not yet healthy | Wait; or start eureka first then services |
| Frontend shows "Network error" on login | check gateway logs at `:8080` | Most likely auth-service is not registered with Eureka |
| Search returns 0 results for known city | check `cities` table populated | Run `./gradlew :services:destination-service:flywayMigrate` |
| Search slow (>1 s) | Redis container down or unreachable | `docker compose up -d redis`; service falls back to DB FTS automatically |
| Verification email not received | check Mailhog UI at `localhost:8025` | All dev emails land there |
| 401 on every authenticated call | access token expired and refresh failing | Check `Set-Cookie` was issued at login; check `withCredentials: true` on axios |
