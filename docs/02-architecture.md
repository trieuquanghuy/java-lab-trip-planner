# 02 — System Architecture (High-Level Design)

**Status**: Draft for review
**Last updated**: 2026-05-08

## 1. Architectural style

**Monorepo + microservices.** A single Gradle multi-module repository contains
four Spring Boot services and one React frontend. Services communicate over
HTTP through an API gateway. Each service owns its own data schema.

### Why this style for this project
- Microservices give a real portfolio-grade demonstration of distributed-system concerns: gateway routing, JWT propagation, distributed tracing, per-service migrations, resilience patterns.
- Monorepo keeps day-to-day developer ergonomics simple: one clone, one IDE workspace, one CI pipeline, atomic refactors across services.
- Service boundaries follow the natural domain seams (auth, planning, discovery) — there is real isolation, not arbitrary splits.

### Why not the alternatives
- **Pure monolith**: simpler but loses the architectural learning value and the ability to explain microservices choices in interviews.
- **Polyrepo microservices**: real-world option, but adds friction (cross-service refactors require multi-PR dance) without commensurate benefit at this scale.
- **Serverless/Lambda**: cold-start latency conflicts with NFR-1 search SLA; also less transferable as a "I built a Spring Boot system" demo.

## 2. Tech stack

### Backend
| Concern | Choice | Rationale |
|---------|--------|-----------|
| Language | Java 21 LTS | Modern records, pattern matching, virtual threads available |
| Framework | Spring Boot 3.3.x | Industry standard; Spring Cloud ecosystem for gateway/discovery |
| Build | Gradle 8.x (Kotlin DSL) | Better multi-module ergonomics than Maven |
| API Gateway | Spring Cloud Gateway | Reactive, native to Spring ecosystem, minimal config |
| Service discovery | Spring Cloud Netflix Eureka | Battle-tested, simple setup, common in Spring shops |
| HTTP client | Spring WebClient | Reactive, retries via Resilience4j |
| Persistence | Spring Data JPA + Hibernate 6 | Standard; per-service `EntityManager` |
| Database | PostgreSQL 16 | JSONB, full-text search, robust |
| Migrations | Flyway 10 | Per-service migrations under `services/<x>/src/main/resources/db/migration` |
| Cache | Redis 7 | Search-result cache, session-less |
| Resilience | Resilience4j | Circuit breaker, retry, timeout per provider call |
| Auth | Custom JWT (HS256) via `jjwt` | Light; Spring Authorization Server is overkill for v1 |
| Validation | Jakarta Bean Validation (Hibernate Validator) | Standard |
| Observability | Micrometer + Prometheus + Zipkin via OpenTelemetry | Standard Spring telemetry stack |
| Logging | Logback + `logstash-logback-encoder` (JSON) | Structured logs ready for any log backend |
| Testing | JUnit 5, Mockito, Testcontainers, WireMock, Spring Boot Test | Standard |

### Frontend
| Concern | Choice | Rationale |
|---------|--------|-----------|
| Framework | React 18 | Industry baseline; large ecosystem |
| Build | Vite 5 | Fast dev server, simple config |
| Language | TypeScript 5 | Type safety, IDE support |
| Routing | React Router 6 | Standard |
| Server state | TanStack Query v5 | Cache + invalidation + optimistic updates |
| Client state | Zustand | Tiny, no boilerplate, good for auth/UI state |
| Forms | React Hook Form + Zod | Performant, schema-first validation |
| HTTP | Axios with interceptors | JWT refresh-on-401 ergonomics |
| Drag-drop | dnd-kit | Accessible, mobile-friendly |
| Map | Leaflet + OpenStreetMap tiles | Free, no API key |
| Styling | Tailwind CSS + shadcn/ui | Modern, consistent, easy to skin |
| Testing | Vitest + Testing Library + Playwright | Standard test pyramid |

### Infrastructure
| Concern | Choice |
|---------|--------|
| Local orchestration | Docker Compose v2 |
| CI | GitHub Actions |
| Container registry | GitHub Container Registry (free for public) |
| Cloud (v2 path, not v1) | Backend → Fly.io · DB → Neon · Redis → Upstash · Frontend → Vercel · SMTP → Resend |

## 3. Service decomposition

```
                            ┌──────────────────────┐
                            │  React SPA (Vite)    │
                            │  http://localhost:5173│
                            └──────────┬───────────┘
                                       │ all /api/** calls
                                       ▼
                            ┌──────────────────────┐
                            │  api-gateway :8080   │
                            │  Spring Cloud Gateway│
                            └─┬───────────┬────────┘
                              │           │
        ┌─────────────────────┘           └────────────────────┐
        ▼                                                      ▼
 ┌──────────────────┐    ┌──────────────────┐    ┌────────────────────────┐
 │ auth-service     │    │ trip-service     │    │ destination-service    │
 │ :8081            │    │ :8082            │    │ :8083                  │
 │ schema: auth     │    │ schema: trip     │    │ schema: destination    │
 └────────┬─────────┘    └────────┬─────────┘    └────────────┬───────────┘
          │                       │                            │
          └───────────────────────┴────────────────────────────┘
                                  │
                  ┌───────────────┼───────────────────────┐
                  ▼               ▼                       ▼
           ┌──────────────┐ ┌──────────┐         ┌──────────────────┐
           │ PostgreSQL 16│ │ Redis 7  │         │ External APIs:   │
           │ (one cluster,│ │ (cache)  │         │ OpenTripMap,     │
           │ 3 schemas)   │ │          │         │ Foursquare, SMTP │
           └──────────────┘ └──────────┘         └──────────────────┘

           ┌──────────────────┐
           │ eureka-server    │  service discovery
           │ :8761            │  (gateway + 3 services register here)
           └──────────────────┘
```

### 3.1 Service responsibilities

#### api-gateway (`:8080`)
- Single entry point for the SPA.
- Routes `/api/auth/**` → auth-service; `/api/trips/**` and `/api/favorites/**` → trip-service; `/api/search/**` and `/api/destinations/**` → destination-service.
- JWT signature validation (rejects unsigned/expired tokens before they reach downstream services).
- Injects `X-User-Id` and `X-User-Email` headers extracted from JWT claims for trusted downstream services.
- CORS handling (single source of CORS truth).
- Per-route rate limiting (Redis-backed via Spring Cloud Gateway's `RequestRateLimiter`).
- Centralized request/response logging with correlation IDs.

#### auth-service (`:8081`)
- Owns: `users`, `email_verification_tokens` (in `auth` schema).
- Issues JWT access tokens (15 min) and refresh tokens (7 days, httpOnly cookie).
- Sends verification emails via SMTP (Mailhog dev, Resend prod path).
- Enforces password policy + bcrypt hashing.
- Endpoints: signup, verify, login, refresh, logout.
- Stateless re: business logic; no calls to other services.

#### trip-service (`:8082`)
- Owns: `trips`, `itinerary_days`, `itinerary_items`, `favorites` (in `trip` schema).
- Trip CRUD, itinerary item CRUD, favorites CRUD.
- Day materialization on trip date set/change.
- Drag-drop position arithmetic.
- Trusts gateway-injected `X-User-Id` (also independently validates JWT for defense in depth).
- Stores only `destination_ref` (opaque "provider:id") — does not call destination-service for routine operations.

#### destination-service (`:8083`)
- Owns: `cities` (seeded from GeoNames), `destinations_cache` (in `destination` schema).
- City/country search (Postgres FTS on `cities` + Redis hot-cache).
- POI search by lat/lng (OpenTripMap → Foursquare enrichment → cached in `destinations_cache`).
- POI detail by `provider_ref`.
- Stateless re: users; endpoints public-readable but rate-limited.

#### eureka-server (`:8761`)
- Service registry. Each service registers on startup. Gateway resolves by service name.
- Single-instance for v1; clustering deferred.

## 4. Inter-service communication

### 4.1 Communication patterns
- **Sync only in v1**: HTTP/JSON via gateway → service.
- **No service-to-service calls in v1**: each service is self-contained for its endpoints. (Avoiding the temptation to make trip-service call auth-service to "validate user" — JWT is sufficient proof.)
- **Async (deferred to v2)**: if user deletion ever needs to cascade to trips, use a domain event on a message broker (RabbitMQ or Kafka). Not v1.

### 4.2 Trust model between services
- Gateway validates JWT signature and extracts user claims.
- Gateway adds `X-User-Id`, `X-User-Email`, `X-Request-Id` headers.
- Downstream services accept `X-User-Id` only when accompanied by a valid JWT (defense in depth — `jwt-common` lib re-validates).
- Direct (non-gateway) traffic to downstream service ports is firewalled in production; in dev it is allowed for debugging but the service still requires JWT.

### 4.3 Cross-service data
- Foreign keys do not cross schemas. `trips.user_id` is a UUID with no DB-level FK to `auth.users`.
- Cross-service joins are forbidden. If a UI ever needs a join, it composes via two API calls (and v1 doesn't need any).
- `destination_ref` is an opaque string — trip-service never inspects it; destination-service is the only owner.

## 5. Repository layout

```
trip-planner/                                  ← monorepo root
├── settings.gradle.kts                        ← lists all modules
├── build.gradle.kts                           ← root: shared plugins, deps versions, conventions
├── gradle/libs.versions.toml                  ← Gradle version catalog (single source of truth for deps)
│
├── services/
│   ├── api-gateway/
│   │   ├── build.gradle.kts
│   │   ├── src/main/java/com/tripplanner/gateway/...
│   │   └── src/main/resources/application.yml
│   ├── auth-service/
│   │   ├── build.gradle.kts
│   │   ├── src/main/java/com/tripplanner/auth/...
│   │   ├── src/main/resources/db/migration/      ← Flyway, auth schema
│   │   └── src/test/...
│   ├── trip-service/
│   │   └── (similar layout)
│   ├── destination-service/
│   │   └── (similar layout)
│   └── eureka-server/
│       └── (similar layout)
│
├── libs/                                      ← shared Java libraries
│   ├── jwt-common/                            ← JWT verify, user-context filter
│   ├── api-contracts/                         ← shared DTOs / OpenAPI codegen output
│   ├── error-handling/                        ← ProblemDetail, error codes, global handlers
│   └── observability/                         ← Micrometer + tracing autoconfig
│
├── frontend/                                  ← React app, own package.json
│   ├── package.json
│   ├── vite.config.ts
│   ├── src/...
│   └── tests/...
│
├── infra/
│   ├── docker-compose.yml                     ← local: db, redis, mailhog, eureka, 4 services, frontend
│   ├── docker-compose.override.yml            ← dev-only overrides (volume mounts, hot reload)
│   ├── postgres/init.sql                      ← creates auth, trip, destination schemas
│   └── seeds/
│       └── cities-15000.tsv                   ← GeoNames seed for destination-service
│
├── .github/workflows/
│   ├── backend.yml                            ← per-service test/build matrix, change-detection
│   └── frontend.yml
│
├── docs/                                      ← this folder
└── README.md
```

## 6. Cross-cutting concerns

### 6.1 Configuration
- Each service has its own `application.yml` + `application-{profile}.yml` (dev, test, docker, prod).
- Sensitive values (JWT secret, SMTP credentials) sourced from environment variables; never committed.
- Spring Cloud Config server is **not** introduced in v1 (overkill for 4 services); deferred to v2 if a 5th service appears.

### 6.2 Error handling
- All services produce RFC 7807 `application/problem+json` responses via shared `error-handling` lib.
- Error codes are stable strings (e.g. `auth.invalid_credentials`, `trip.not_found`) — UI displays based on code, not message.
- See [04-api-spec.md §6](./04-api-spec.md) for canonical error catalog.

### 6.3 Observability
- **Logs**: Logback JSON, fields: `timestamp`, `level`, `service`, `traceId`, `spanId`, `userId`, `message`. Logs go to stdout (Docker captures).
- **Metrics**: Micrometer → Prometheus endpoint at `/actuator/prometheus` per service. Standard JVM, HTTP, datasource metrics.
- **Tracing**: Micrometer Tracing with OpenTelemetry exporter → Zipkin (single Zipkin instance in compose).
- **Health**: `/actuator/health` with deep checks (DB, Redis, Eureka registration).
- See [08-deployment.md](./08-deployment.md) for full observability setup.

### 6.4 Resilience
- Resilience4j on every external HTTP call (OpenTripMap, Foursquare, SMTP):
  - Connect timeout 1s, read timeout 3s.
  - Circuit breaker opens after 50% failure rate over 20 calls in 60s.
  - Retry: 2 retries with exponential backoff for 5xx and timeouts.
- Database: HikariCP pool size 10 per service; connection-acquire timeout 5s.

## 7. Deployment topology (v1 = local)

```
docker compose:
  postgres        :5432   (one instance, three schemas)
  redis           :6379
  mailhog         :8025 (web), :1025 (smtp)
  zipkin          :9411
  eureka-server   :8761
  api-gateway     :8080
  auth-service    :8081
  trip-service    :8082
  destination-service :8083
  frontend (vite) :5173

Total memory budget: ~2.5 GB. Runs on a 16 GB laptop alongside an IDE.
```

A cloud deployment topology is described in [08-deployment.md §3](./08-deployment.md) but is **not built in v1**.

## 8. Architectural decision records (ADRs) summary

ADRs are inline with the design rather than as separate files for v1.
Key decisions captured here for traceability:

| ADR | Decision | Rationale |
|-----|----------|-----------|
| ADR-1 | Monorepo + microservices over monolith | Portfolio signal; clear domain seams |
| ADR-2 | Gradle Kotlin DSL over Maven | Multi-module ergonomics, version catalog |
| ADR-3 | Single Postgres with per-service schemas vs separate DB instances | Memory budget; ownership preserved by schema; no cross-schema joins |
| ADR-4 | Eureka over Consul/static URLs | Spring-native, minimal config |
| ADR-5 | Custom JWT over Spring Authorization Server / Keycloak | Scope-appropriate; OAuth2 server is overkill for email+password |
| ADR-6 | Sync HTTP only in v1, no message broker | YAGNI; can introduce when a real async use case appears |
| ADR-7 | TanStack Query + Zustand over Redux Toolkit | Better DX for server-cached data; Zustand for the small UI state slice |
| ADR-8 | Leaflet+OSM over Google Maps | No API key, no billing setup, sufficient for v1 |

## 9. Known architectural risks

See [10-risks.md](./10-risks.md) for full risk register. Highlights:

- R-1: Distributed tracing complexity — mitigated by adding it from Phase 0.
- R-2: External API rate limits — mitigated by aggressive caching and circuit breaking.
- R-3: Microservices boilerplate slowing development — accepted cost; mitigated by shared `libs/`.
