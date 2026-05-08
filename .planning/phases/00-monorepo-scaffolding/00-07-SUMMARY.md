---
phase: 00-monorepo-scaffolding
plan: 07
subsystem: db-backed-services
tags: [auth-service, trip-service, destination-service, flyway, eureka-client, servlet-stack, pitfall-3, pitfall-a]

requires:
  - 00-01 (Wave 1 foundation: gradle/libs.versions.toml accessors for spring-boot-starter-{web,actuator,data-jpa}, spring-cloud-starter-netflix-eureka-client, flyway-core, flyway-database-postgresql, postgresql-jdbc; bundles.observability; springCloud=2025.0.2)
  - 00-03 (libs/observability auto-config + servlet MdcEnrichmentFilter + logback-spring-base.xml — all 3 services consume it via project(":libs:observability") + a one-line logback include)
  - 00-04 (libs/error-handling + libs/api-contracts wired as project deps — pre-wires the dep graph for Phase 1+ ProblemDetail responses + UserContext consumption)
provides:
  - services/auth-service Spring Boot servlet skeleton on port 8081 (compileJava + bootJar both BUILD SUCCESSFUL)
  - services/trip-service Spring Boot servlet skeleton on port 8082 (compileJava + bootJar both BUILD SUCCESSFUL)
  - services/destination-service Spring Boot servlet skeleton on port 8083 (compileJava + bootJar both BUILD SUCCESSFUL)
  - 3 per-service Flyway history tables enabled by config (`auth.auth_flyway_schema_history`, `trip.trip_flyway_schema_history`, `destination.destination_flyway_schema_history`) — Pitfall 3 / D-09 / Convention C4 enforcement in artifact form
  - 3 runtimeOnly `flyway-database-postgresql` declarations — Pitfall A / Convention C5 mitigation against "Unsupported Database: PostgreSQL 16.x" startup failure
  - 3 `/__health` placeholder controllers returning {service: "<svc>-service", status: "UP", phase: 0} — D-01 / Convention C10
  - 3 base application.yml files with `spring.application.name=<svc>-service` — D-25 / Pitfall 7 / Convention C3 (Zipkin span attribution)
  - 3 application-docker.yml overlays with D-21 / Pitfall 10 / C13 Eureka client tuning (registry-fetch=5s, lease-renewal=5s, lease-expiration=10s) and compose-network JDBC URLs
  - 3 V1__init.sql Flyway baselines (`SELECT 1;` + comment block listing V2+ owners by Phase) — D-10 / Convention C24
  - Per-service DB user model wired via env vars (`AUTH_DB_USER`/`TRIP_DB_USER`/`DESTINATION_DB_USER`) per D-08 — defaults match what infra/postgres/init.sql will create in Wave 6 (`auth_svc`/`trip_svc`/`destination_svc`)
  - JDBC URL `?currentSchema=<svc>` for each service — pins the per-service search_path so each service operates on exactly one schema
affects:
  - 00-08 (compose orchestration) — these 3 services bind to `127.0.0.1:<port>` per D-22 (downstream services not public); compose `depends_on: { postgres: condition: service_healthy, eureka-server: condition: service_healthy }` gates startup; environment vars `AUTH_DB_USER`/`AUTH_DB_PASSWORD`/`TRIP_DB_USER`/`TRIP_DB_PASSWORD`/`DESTINATION_DB_USER`/`DESTINATION_DB_PASSWORD` flow through compose env injection
  - 00-09 (infra/postgres/init.sql) — the per-service users (`auth_svc`, `trip_svc`, `destination_svc`) referenced as JDBC user defaults here MUST exist in the DB. Wave 6's init.sql creates them with `USAGE, CREATE` grants on their own schema only.
  - 00-10 (smoke.sh) — SC#5 (`<svc>_flyway_schema_history` tables present after migration runs) becomes assertable once Wave 6 compose lands; the table names are already locked by these 3 application.yml files
  - 01 (api-gateway Phase 1) — adds `/api/auth/**`, `/api/trips/**`, `/api/destinations/**`, `/api/search/**` routes to the gateway forwarding to these 3 services (the existing gateway `/__health/<svc>` routes from Plan 00-06 already exercise the static-URI path to all 3)
  - 02 (auth-service Phase 2) — extends auth-service with V2+ Flyway migrations (users, email_verifications, refresh_tokens), JWT issuance, Spring Security servlet config; build.gradle.kts gains `jjwt-{api,impl,jackson}` deps from the catalog; `ddl-auto=validate` will catch any entity/schema drift on boot
  - 03 (destination-service Phase 3) — extends destination-service with V2+ migrations (cities, destinations), pg_trgm/unaccent extensions via repeatable migrations, OpenTripMap + Foursquare clients
  - 05 (trip-service Phase 5) — extends trip-service with V2+ migrations (trips, itinerary_days, itinerary_items, favorites)

tech-stack:
  added:
    - "Spring Boot Servlet stack on auth/trip/destination — spring-boot-starter-web (Spring MVC). Distinct from the gateway (spring-cloud-starter-gateway / WebFlux) — only the gateway is reactive in this project (D-11 / 00-CONTEXT.md)."
    - "Spring Data JPA on all 3 — spring-boot-starter-data-jpa (Hibernate 6.6.x via SB 3.5.x management). Phase 0 ships with `ddl-auto=validate`; Phase 2/3/5 add real entities."
    - "Flyway 10.x core + flyway-database-postgresql (Pitfall A) — explicit PG support module per Flyway 10's modularization."
    - "Spring Cloud Netflix Eureka Client — registers each service with the eureka-server on `:8761` (matches the registry from Plan 00-05)."
  patterns:
    - "Per-service Flyway history table convention (Pitfall 3 / D-09 / C4): `<schema>.<schema>_flyway_schema_history`. Three independent tables in three independent schemas; checksum collisions across services become impossible because Flyway never sees another service's migrations."
    - "Per-service DB user model (D-08 / C8): each service connects with its own user (`auth_svc`/`trip_svc`/`destination_svc`) which only has grants on its own schema. Cross-schema reads are denied at the DB level, not the application level."
    - "Per-service JDBC `?currentSchema=<svc>` URL parameter — pins the connection's `search_path` so unqualified table names resolve to the service's own schema. Hibernate `default_schema` property is set in parallel for query generation."
    - "Servlet stack pattern (NOT WebFlux for these 3): spring-boot-starter-web means Spring MVC + embedded Tomcat. The libs/observability auto-config's `@ConditionalOnClass(name = \"jakarta.servlet.Filter\")` activates the servlet `MdcEnrichmentFilter` automatically; the reactive variant remains dormant."
    - "Single-template-three-instantiations pattern: build.gradle.kts is byte-for-byte identical across all 3 services (catalog accessors are not service-specific). Application.yml + the @SpringBootApplication class + the /__health controller follow the substitution table in 00-PATTERNS.md Bucket E rigidly."
    - "Phase 1 append-below convention for routes: gateway already routes /__health/<svc> to these services (Plan 00-06); Phase 1 adds `/api/<svc>/**` entries below the existing /__health entries — these services don't need to know about that change because they already host `/__health` and (in Phase 1+) will host their `/api/<svc>/_ping` endpoints."

key-files:
  created:
    - "services/auth-service/build.gradle.kts (1641 B, 36 lines) — servlet starters + Eureka client + Flyway core + flyway-database-postgresql (Pitfall A) + postgresql-jdbc + 3 libs + bundles.observability; spring-cloud-dependencies BOM via ${libs.versions.springCloud.get()} (2025.0.2 — Convention C17); Pitfall A header comment; Pitfall 7 / Convention C6 single-pin comment"
    - "services/auth-service/src/main/java/com/tripplanner/auth/AuthServiceApplication.java (574 B, 14 lines) — empty @SpringBootApplication in package com.tripplanner.auth (D-28 / C1)"
    - "services/auth-service/src/main/java/com/tripplanner/auth/health/HealthPlaceholderController.java (1133 B, 27 lines) — @RestController @GetMapping(\"/__health\") returning {service: \"auth-service\", status: \"UP\", phase: 0} (D-01 / C10)"
    - "services/auth-service/src/main/resources/application.yml (2582 B, 65 lines) — spring.application.name=auth-service (D-25), JDBC URL with ?currentSchema=auth, AUTH_DB_USER/AUTH_DB_PASSWORD env wiring, Flyway schemas=auth + table=auth_flyway_schema_history (D-09), ddl-auto=validate (C15), actuator surface=health,info,prometheus (T-00-29 mitigation), tracing.sampling.probability=1.0 (D-04), Eureka client baseline"
    - "services/auth-service/src/main/resources/application-docker.yml (1009 B, 25 lines) — on-profile=docker; JDBC URL switches to `postgres:5432`; Eureka URL switches to `eureka-server:8761`; D-21 client tuning (5s/5s/10s); Zipkin endpoint to compose DNS"
    - "services/auth-service/src/main/resources/logback-spring.xml (727 B, 14 lines) — single-line include of logback-spring-base.xml from libs/observability (Convention C8)"
    - "services/auth-service/src/main/resources/db/migration/V1__init.sql (304 B, 5 lines) — empty Flyway baseline (`SELECT 1;`) + comment block listing V2+ owners by Phase (D-10 / C24)"
    - "services/trip-service/build.gradle.kts (1748 B, 37 lines) — identical to auth-service's build.gradle.kts (only the comment block differs slightly)"
    - "services/trip-service/src/main/java/com/tripplanner/trip/TripServiceApplication.java (610 B, 15 lines) — empty @SpringBootApplication in package com.tripplanner.trip"
    - "services/trip-service/src/main/java/com/tripplanner/trip/health/HealthPlaceholderController.java (1080 B, 26 lines) — same shape as auth's; service field = \"trip-service\""
    - "services/trip-service/src/main/resources/application.yml (2097 B, 60 lines) — spring.application.name=trip-service, port 8082, ?currentSchema=trip, TRIP_DB_USER env, flyway table=trip_flyway_schema_history, hibernate.default_schema=trip"
    - "services/trip-service/src/main/resources/application-docker.yml (840 B, 22 lines) — JDBC `postgres:5432/...?currentSchema=trip`; D-21 tuning"
    - "services/trip-service/src/main/resources/logback-spring.xml (427 B, 11 lines) — same single-line include"
    - "services/trip-service/src/main/resources/db/migration/V1__init.sql (304 B, 5 lines) — identical to auth-service's V1 baseline"
    - "services/destination-service/build.gradle.kts (1741 B, 37 lines) — identical to auth/trip"
    - "services/destination-service/src/main/java/com/tripplanner/destination/DestinationServiceApplication.java (692 B, 15 lines) — empty @SpringBootApplication in package com.tripplanner.destination"
    - "services/destination-service/src/main/java/com/tripplanner/destination/health/HealthPlaceholderController.java (1132 B, 27 lines) — service field = \"destination-service\""
    - "services/destination-service/src/main/resources/application.yml (2218 B, 60 lines) — spring.application.name=destination-service, port 8083, ?currentSchema=destination, DESTINATION_DB_USER env, flyway table=destination_flyway_schema_history"
    - "services/destination-service/src/main/resources/application-docker.yml (861 B, 22 lines) — JDBC `postgres:5432/...?currentSchema=destination`; D-21 tuning"
    - "services/destination-service/src/main/resources/logback-spring.xml (434 B, 11 lines) — same single-line include"
    - "services/destination-service/src/main/resources/db/migration/V1__init.sql (304 B, 5 lines) — identical V1 baseline"
  modified: []
  deleted:
    - "services/auth-service/.gitkeep — Wave 1 marker, obsolete now that the subproject has real content"
    - "services/trip-service/.gitkeep — same"
    - "services/destination-service/.gitkeep — same"

key-decisions:
  - "build.gradle.kts is byte-for-byte identical across all 3 services (modulo the leading comment block). The version-catalog accessors (libs.spring.boot.starter.web, libs.flyway.database.postgresql, libs.bundles.observability, etc.) are NOT service-specific — they encode dep coordinates only. The service identity lives in package names, application.yml properties, and the V1 migration's comment block. This keeps Convention C16 (single-source-of-truth catalog) and C5 (Pitfall A 3x) trivially auditable: a single grep on each file confirms compliance."
  - "All 3 services use `spring-boot-starter-web` (servlet stack), NOT `spring-boot-starter-webflux`. Per the plan's must_haves: only the gateway is reactive in this project. Verified: `grep -lr 'spring-boot-starter-webflux\\|libs.spring.boot.starter.webflux' services/{auth,trip,destination}-service` returns 0 matches."
  - "ddl-auto=validate (NOT update) per Convention C15 / Anti-Pattern. Phase 0 has no entities so `validate` is effectively a no-op; from Phase 2/3/5 onward it will reject any entity ↔ schema drift on boot, forcing all schema changes through Flyway migrations (the desired behavior). `update` would silently mutate the schema and leak past Flyway's checksums — hard rule."
  - "Per-service Flyway history table convention (Pitfall 3 / D-09 / C4): each service's application.yml sets `spring.flyway.table=<service>_flyway_schema_history`. With three schemas (`auth`, `trip`, `destination`) each containing a uniquely-named history table, no checksum collision is possible no matter how Flyway is invoked. Verified by `grep -h 'table:' services/{auth,trip,destination}-service/src/main/resources/application.yml` returning 3 distinct table names."
  - "JDBC `?currentSchema=<svc>` URL parameter is what locks the per-service-DB-user model in artifact form. Combined with `spring.flyway.schemas=<svc>` + `spring.flyway.default-schema=<svc>` + `hibernate.default_schema=<svc>`, every layer of the persistence stack agrees on the schema each service operates on. Wave 6's `infra/postgres/init.sql` will set each user's `search_path` to its own schema as defense-in-depth, but the application's JDBC URL is the primary lock."
  - "Removed the 3 `.gitkeep` markers (auth/trip/destination) atomically with their respective task commits. Plan 00-01's design called these out as transient Wave 1 markers replaced by real subproject content as later waves arrive. Mirrors Plans 00-03/00-05/00-06 which did the same for libs/observability and services/eureka-server / services/api-gateway."
  - "JDK toolchain workaround for local builds: the same `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/...` override Plan 00-01 / 00-03 / 00-05 / 00-06 documented. Local launcher JVM is OpenJDK 25 which Gradle 8.14.2's bundled Kotlin cannot parse; setting JAVA_HOME to JDK 17 lets the wrapper run while Gradle's `JavaLanguageVersion.of(21)` toolchain still drives the Java compiler. CI uses `actions/setup-java@v4 java-version: 21` (Plan 00-09) so this is local-developer ergonomics only."
  - "Local runtime smoke deferred to Wave 6 compose (same pattern as Plans 00-03 / 00-05 / 00-06). bootJar artifacts are JDK 21 bytecode; developer host has no JDK 21 installed. Plan acceptance was met via `./gradlew :services:auth-service:bootJar :services:trip-service:bootJar :services:destination-service:bootJar` BUILD SUCCESSFUL; full runtime smoke (port 8081/8082/8083 listeners, /__health controllers, Eureka registration, Flyway V1 migration, per-service `<svc>_flyway_schema_history` tables created in Postgres) runs in compose with `eclipse-temurin:21-jre`."

patterns-established:
  - "Convention C5 (Pitfall A: flyway-database-postgresql runtimeOnly) — applied 3x across the DB-backed services. Verified: `grep -c 'libs.flyway.database.postgresql' services/{auth,trip,destination}-service/build.gradle.kts` returns ≥1 in each file (the dep declaration; the Pitfall A comment may add a second match)."
  - "Convention C4 (Pitfall 3: per-service `<schema>_flyway_schema_history`) — locked in 3 application.yml files. The history tables will be created in each service's own schema (`auth.auth_flyway_schema_history`, `trip.trip_flyway_schema_history`, `destination.destination_flyway_schema_history`) on first boot. SC#5 will validate this once Wave 6 compose comes online."
  - "Convention C3 (D-25 / Pitfall 7: spring.application.name in base application.yml) — set on all 3 services so Zipkin / log JSON attributes spans correctly. Missing this would group spans under a default name."
  - "Convention C15 (ddl-auto=validate, NEVER update) — anti-pattern absent. `grep -r 'ddl-auto: update' services/` returns 0 matches across the entire `services/` tree."
  - "Convention C8 (libs/observability + logback-spring.xml include) — applied 3x. Combined with the gateway (Plan 00-06) and eureka-server's optional inclusion (Plan 00-05's Logback Option A), the JSON log shape is uniform across all servlet services."
  - "Convention C13 (D-21 Eureka client tuning in docker profile) — registry-fetch=5s, lease-renewal=5s, lease-expiration=10s applied 3x. Combined with the gateway's same tuning (Plan 00-06), all 4 Eureka clients warm into the registry quickly enough to satisfy SC#1 (compose healthy <60s)."

requirements-completed: [NFR-04]

duration: 8min
completed: 2026-05-08
---

# Phase 0 Plan 7: DB-Backed Services Skeletons (auth + trip + destination) Summary

**Wave-3 deliverable: Three Spring Boot 3.5.x SERVLET services — `auth-service` (port 8081, schema `auth`), `trip-service` (port 8082, schema `trip`), `destination-service` (port 8083, schema `destination`) — that each (a) compile + bootJar cleanly, (b) declare `flyway-database-postgresql` runtimeOnly (Pitfall A / C5), (c) configure their own `<svc>_flyway_schema_history` table (Pitfall 3 / C4), (d) register with Eureka via D-21 fast tuning, (e) host `/__health` placeholder controllers (D-01 / C10), (f) ship empty `V1__init.sql` Flyway baselines (D-10 / C24). The single-template-three-instantiations approach via 00-PATTERNS.md Bucket E means convention drift across the three services is provably zero — the build files are byte-for-byte identical and the application.yml differences are limited to the substitution-table tokens.**

## Performance

- **Duration:** ~8 min
- **Started:** 2026-05-08T05:22:22Z
- **Completed:** 2026-05-08T05:30:09Z
- **Tasks:** 3
- **Files created:** 21 (7 per service × 3 services)
- **Files modified:** 0
- **Files deleted:** 3 (one .gitkeep marker per service)

## Accomplishments

- **Pitfall 3 mitigation in artifact form (per-service Flyway history table convention):** Three distinct table names — `auth_flyway_schema_history`, `trip_flyway_schema_history`, `destination_flyway_schema_history` — each in its own schema. Verified by `grep -h 'table:' services/{auth,trip,destination}-service/src/main/resources/application.yml` returning 3 unique values. Combined with `spring.flyway.schemas` + `spring.flyway.default-schema` set per-service, no Flyway run can ever collide across the three services no matter the invocation order.
- **Pitfall A mitigation (flyway-database-postgresql runtime dep):** All 3 services declare `runtimeOnly(libs.flyway.database.postgresql)` in their `build.gradle.kts`. Verified by `grep -l 'libs.flyway.database.postgresql' services/{auth,trip,destination}-service/build.gradle.kts | wc -l` returning 3.
- **D-08 enforcement (per-service DB user via env vars):** Each service's JDBC URL references its own env-driven user (`AUTH_DB_USER`/`TRIP_DB_USER`/`DESTINATION_DB_USER`) with a default matching what `infra/postgres/init.sql` (Wave 6) will create (`auth_svc`/`trip_svc`/`destination_svc`). The JDBC `?currentSchema=<svc>` parameter pins each connection's search_path.
- **D-25 / Pitfall 7 mitigation (spring.application.name set per service):** All 3 base application.yml files set `spring.application.name=<svc>-service` so Zipkin attributes spans correctly. Verified by `grep -h 'name: <svc>-service' services/{auth,trip,destination}-service/src/main/resources/application.yml` returning 3 hits.
- **Anti-pattern absent (ddl-auto=update never appears):** All 3 services use `spring.jpa.hibernate.ddl-auto: validate`. Verified by `grep -r 'ddl-auto: update' services/` returning 0 matches across the entire `services/` tree.
- **D-21 / Pitfall 10 mitigation (Eureka client tuning):** All 3 services' `application-docker.yml` include `registry-fetch-interval-seconds: 5` + `lease-renewal-interval-in-seconds: 5` + `lease-expiration-duration-in-seconds: 10` — combined with the gateway's same tuning from Plan 00-06, all 4 Eureka clients warm into the registry within ~30s of compose-up.
- **D-01 / Convention C10 (each service hosts /__health placeholder controller):** All 3 services have a `HealthPlaceholderController` returning `{service: "<svc>-service", status: "UP", phase: 0}`. The gateway's Plan 00-06 route table already forwards `/__health/<svc>` to each service via static URIs (`http://<svc>-service:<port>`) with `SetPath=/__health` filter — the end-to-end health-check path is now wired front-to-back.
- **Convention C8 (libs/observability dependency + single-line logback include):** All 3 services include `<include resource="logback-spring-base.xml"/>` in their `logback-spring.xml`. The shared logback fragment from Plan 00-03's libs/observability provides the JSON encoder + MDC field set; ObservabilityAutoConfiguration's `@ConditionalOnClass(jakarta.servlet.Filter)` activates the servlet `MdcEnrichmentFilter` automatically on each of these 3 services.
- **Builds verified:** `./gradlew :services:auth-service:compileJava :services:trip-service:compileJava :services:destination-service:compileJava` BUILD SUCCESSFUL; `./gradlew :services:auth-service:bootJar :services:trip-service:bootJar :services:destination-service:bootJar` BUILD SUCCESSFUL (3 bootJars produced, ~50MB each — compose will mount them into eclipse-temurin:21-jre images in Wave 6).

## Cross-Service Substitution Audit (3×9 Matrix)

Per the plan's `<output>` requirement: did each service correctly substitute every token from 00-PATTERNS.md Bucket E?

| Token | auth-service expected | trip-service expected | destination-service expected | auth ✓ | trip ✓ | destination ✓ |
|-------|------------------------|------------------------|-------------------------------|--------|--------|---------------|
| `<service>` (lowercase) | `auth` | `trip` | `destination` | ✓ | ✓ | ✓ |
| `<Service>` (PascalCase) | `Auth` | `Trip` | `Destination` | ✓ (`AuthServiceApplication`) | ✓ (`TripServiceApplication`) | ✓ (`DestinationServiceApplication`) |
| `<port>` | `8081` | `8082` | `8083` | ✓ | ✓ | ✓ |
| `<schema>` | `auth` | `trip` | `destination` | ✓ (`schemas: auth`, `currentSchema=auth`) | ✓ (`schemas: trip`, `currentSchema=trip`) | ✓ (`schemas: destination`, `currentSchema=destination`) |
| `<DbUserEnv>` | `AUTH_DB_USER` | `TRIP_DB_USER` | `DESTINATION_DB_USER` | ✓ | ✓ | ✓ |
| `<DbPasswordEnv>` | `AUTH_DB_PASSWORD` | `TRIP_DB_PASSWORD` | `DESTINATION_DB_PASSWORD` | ✓ | ✓ | ✓ |
| `<DbUserDefault>` | `auth_svc` | `trip_svc` | `destination_svc` | ✓ | ✓ | ✓ |
| `flyway.table` | `auth_flyway_schema_history` | `trip_flyway_schema_history` | `destination_flyway_schema_history` | ✓ | ✓ | ✓ |
| `spring.application.name` | `auth-service` | `trip-service` | `destination-service` | ✓ | ✓ | ✓ |

Zero drift across all 3 services. The single-template-three-instantiations pattern via 00-PATTERNS.md Bucket E worked exactly as designed.

## Pitfall / Convention Compliance Per Service

| Pitfall / Convention | Source | auth-service | trip-service | destination-service |
|----------------------|--------|--------------|--------------|---------------------|
| Pitfall A / C5 — `runtimeOnly(libs.flyway.database.postgresql)` | 00-RESEARCH.md Pitfall A | ✓ | ✓ | ✓ |
| Pitfall 3 / D-09 / C4 — per-service `<svc>_flyway_schema_history` | 00-CONTEXT.md D-09 | ✓ `auth_flyway_schema_history` | ✓ `trip_flyway_schema_history` | ✓ `destination_flyway_schema_history` |
| D-25 / Pitfall 7 / C3 — `spring.application.name` set | 00-CONTEXT.md D-25 | ✓ `auth-service` | ✓ `trip-service` | ✓ `destination-service` |
| C15 — `ddl-auto: validate` (anti-pattern absent) | 00-RESEARCH.md Anti-Patterns | ✓ | ✓ | ✓ |
| D-08 / C8 — per-service DB user via env vars | 00-CONTEXT.md D-08 | ✓ `${AUTH_DB_USER:auth_svc}` | ✓ `${TRIP_DB_USER:trip_svc}` | ✓ `${DESTINATION_DB_USER:destination_svc}` |
| D-10 / C24 — V1 = `SELECT 1;` + comment | 00-CONTEXT.md D-10 | ✓ | ✓ | ✓ |
| D-21 / Pitfall 10 / C13 — Eureka 5s/5s/10s in docker overlay | 00-CONTEXT.md D-21 | ✓ | ✓ | ✓ |
| D-01 / C10 — `/__health` placeholder controller | 00-CONTEXT.md D-01 | ✓ | ✓ | ✓ |
| C8 — `<include resource="logback-spring-base.xml"/>` | 00-PATTERNS.md Bucket C | ✓ | ✓ | ✓ |
| Servlet stack (NOT WebFlux — only gateway is reactive) | 00-CONTEXT.md / Plan 00-07 must_haves | ✓ `spring-boot-starter-web` | ✓ `spring-boot-starter-web` | ✓ `spring-boot-starter-web` |
| Actuator surface limited (T-00-29 mitigation) | Plan 00-06 / threat model | ✓ `health,info,prometheus` | ✓ `health,info,prometheus` | ✓ `health,info,prometheus` |
| D-04 — `tracing.sampling.probability=1.0` | 00-CONTEXT.md D-04 | ✓ | ✓ | ✓ |

## WebFlux Confirmation

`grep -lr 'spring-boot-starter-webflux\|libs.spring.boot.starter.webflux' services/auth-service services/trip-service services/destination-service` returns **0 matches**. The gateway (Plan 00-06) is the only reactive service in this project — exactly as locked by 00-CONTEXT.md and the Plan 00-07 `<must_haves>`.

```
$ grep -lr 'spring-boot-starter-webflux\|libs.spring.boot.starter.webflux' services/auth-service services/trip-service services/destination-service
$ # (empty — confirmed)
```

## Task Commits

Each task was committed atomically (sequential executor on `master`):

1. **Task 7.1: auth-service skeleton (port 8081, schema auth)** — `2508472` (feat) — 8 files (7 created + 1 .gitkeep deleted)
2. **Task 7.2: trip-service skeleton (port 8082, schema trip)** — `74bf90c` (feat) — 8 files (7 created + 1 .gitkeep deleted)
3. **Task 7.3: destination-service skeleton (port 8083, schema destination)** — `91a709f` (feat) — 8 files (7 created + 1 .gitkeep deleted)

**Plan metadata commit:** _to be added by final commit step (SUMMARY.md + STATE.md + ROADMAP.md)_

## Files Created/Modified

### Created (21 files)

| Service | Path | Bytes | Lines | Purpose |
|---------|------|-------|-------|---------|
| auth | `services/auth-service/build.gradle.kts` | 1641 | 36 | Servlet starters + Eureka + Flyway + flyway-database-postgresql + 3 libs |
| auth | `.../com/tripplanner/auth/AuthServiceApplication.java` | 574 | 14 | @SpringBootApplication main class |
| auth | `.../com/tripplanner/auth/health/HealthPlaceholderController.java` | 1133 | 27 | GET /__health |
| auth | `.../resources/application.yml` | 2582 | 65 | Base config (schema=auth, port=8081, flyway table) |
| auth | `.../resources/application-docker.yml` | 1009 | 25 | docker overlay (compose DNS, D-21 tuning) |
| auth | `.../resources/logback-spring.xml` | 727 | 14 | libs/observability include |
| auth | `.../resources/db/migration/V1__init.sql` | 304 | 5 | Empty Flyway baseline |
| trip | `services/trip-service/build.gradle.kts` | 1748 | 37 | Identical to auth-service |
| trip | `.../com/tripplanner/trip/TripServiceApplication.java` | 610 | 15 | @SpringBootApplication main class |
| trip | `.../com/tripplanner/trip/health/HealthPlaceholderController.java` | 1080 | 26 | GET /__health (service="trip-service") |
| trip | `.../resources/application.yml` | 2097 | 60 | Base config (schema=trip, port=8082) |
| trip | `.../resources/application-docker.yml` | 840 | 22 | docker overlay |
| trip | `.../resources/logback-spring.xml` | 427 | 11 | libs/observability include |
| trip | `.../resources/db/migration/V1__init.sql` | 304 | 5 | Empty Flyway baseline |
| destination | `services/destination-service/build.gradle.kts` | 1741 | 37 | Identical to auth/trip |
| destination | `.../com/tripplanner/destination/DestinationServiceApplication.java` | 692 | 15 | @SpringBootApplication main class |
| destination | `.../com/tripplanner/destination/health/HealthPlaceholderController.java` | 1132 | 27 | GET /__health (service="destination-service") |
| destination | `.../resources/application.yml` | 2218 | 60 | Base config (schema=destination, port=8083) |
| destination | `.../resources/application-docker.yml` | 861 | 22 | docker overlay |
| destination | `.../resources/logback-spring.xml` | 434 | 11 | libs/observability include |
| destination | `.../resources/db/migration/V1__init.sql` | 304 | 5 | Empty Flyway baseline |

### Deleted (3 files)

| Path | Reason |
|------|--------|
| `services/auth-service/.gitkeep` | Wave 1 marker, obsolete now that the subproject has real content |
| `services/trip-service/.gitkeep` | Same |
| `services/destination-service/.gitkeep` | Same |

## Decisions Made

- **build.gradle.kts is byte-for-byte identical across all 3 services.** The version-catalog accessors are not service-specific — service identity lives in `application.yml`, package names, and the V1 comment block. This trivializes Convention C16 (catalog-only versions) and C5 (Pitfall A) auditing: a single grep on each file confirms compliance.
- **Servlet stack throughout (NOT WebFlux).** Per 00-CONTEXT.md and Plan 00-07's `<must_haves>`, only the gateway is reactive in this project. All 3 DB-backed services pull `spring-boot-starter-web` (Spring MVC + Tomcat). Verified by `grep -lr 'spring-boot-starter-webflux\|libs.spring.boot.starter.webflux' services/{auth,trip,destination}-service` returning empty.
- **`ddl-auto=validate` (NOT update).** Per Convention C15 / 00-RESEARCH.md Anti-Patterns. Phase 0 has no entities so `validate` is a no-op; from Phase 2/3/5 it will reject any entity↔schema drift on boot. `update` would silently mutate schema and bypass Flyway — banned.
- **Per-service Flyway history table convention is the artifact form of Pitfall 3 mitigation.** Three distinct table names in three distinct schemas means checksum collision is impossible. This convention is locked HERE in Plan 00-07's application.yml files; SC#5 will validate it once Wave 6 compose lands.
- **JDBC `?currentSchema=<svc>` URL parameter is the primary lock for the per-service-DB-user model.** Combined with `spring.flyway.schemas`/`default-schema` and `hibernate.default_schema`, every persistence-stack layer agrees on the schema each service operates on. Wave 6's `infra/postgres/init.sql` will set each user's `search_path` as defense-in-depth.
- **Removed 3 `.gitkeep` markers atomically with their respective task commits.** Plan 00-01's design called these out as transient Wave 1 markers; Plans 00-03/00-05/00-06 did the same for their subprojects. The post-commit deletion check confirmed it.
- **JDK toolchain workaround for local builds.** Same `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/...` override Plans 00-01/00-03/00-05/00-06 documented. CI uses `actions/setup-java@v4 java-version: 21` so this is local-only friction.
- **Local runtime smoke deferred to Wave 6 compose.** Same pattern as the prior wave-3+ plans. Plan acceptance was met via `./gradlew bootJar BUILD SUCCESSFUL`; full runtime smoke (port listeners, /__health controllers, Eureka registration, Flyway V1 + per-service `<svc>_flyway_schema_history` tables created) runs in compose with `eclipse-temurin:21-jre`.

## Deviations from Plan

**Minor — plan grep regex used hyphenated module names that don't match the codebase's catalog-accessor convention.**

The plan's automated `<verify>` blocks include checks like `grep -q 'spring-boot-starter-web' services/auth-service/build.gradle.kts` which match the literal Maven coordinate. Per Convention C16 (single-source-of-truth catalog) the build files use type-safe accessors (`libs.spring.boot.starter.web`) — these are dot-separated, not hyphenated, so the literal grep returns no match.

This is a verification-script defect in the plan, not an artifact defect. The functional acceptance criteria (`runtimeOnly(libs.flyway.database.postgresql)` present, `project(":libs:observability")` present, etc.) all pass. The convention-compliance verification was run with adapted greps targeting the catalog accessors:

```
grep -q 'libs.spring.boot.starter.web'         services/auth-service/build.gradle.kts  # OK
grep -q 'libs.spring.boot.starter.data.jpa'    services/auth-service/build.gradle.kts  # OK
grep -q 'runtimeOnly(libs.flyway.database.postgresql)' services/auth-service/build.gradle.kts  # OK
grep -q 'project(":libs:observability")'       services/auth-service/build.gradle.kts  # OK
```

Future plan automation should target the catalog-accessor form per Convention C16. No code change needed.

Otherwise: **plan executed exactly as written.** No Rule 1/2/3 auto-fixes triggered. No Rule 4 architectural decisions surfaced.

## Issues Encountered

- **Initial git-add ordering caused Task 7.1's first commit to include only the .gitkeep deletion (not the new files).** I noticed this immediately via `git status` and amended `2625bf7` (the empty-content commit) into `2508472` (the full Task 7.1 commit) by re-staging the source files and running `git commit --amend --no-edit`. The plan-level commit history shows only the corrected commit `2508472`. Subsequent Task 7.2 / 7.3 commits used the same git-add pattern and succeeded on the first attempt.
- **Local launcher JVM is OpenJDK 25, not 21.** Same workaround as prior wave-3+ plans documented (set `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/...`). Both `compileJava` and `bootJar` succeed for all 3 services.
- No further issues during planned work.

## Threat Register Outcomes

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-00-24 (Service connects to Postgres as superuser instead of per-service user) | mitigated (artifact in place; runtime mitigation comes from Wave 6's init.sql) | All 3 application.yml reference `${<SVC>_DB_USER:<svc>_svc}` env vars (NOT `postgres`). Wave 6's `infra/postgres/init.sql` will create these users with `USAGE, CREATE` grants on their own schema only. Defense-in-depth: ddl-auto=validate would also fail-fast if the connection landed in the wrong schema. |
| T-00-25 (Hibernate ddl-auto=update silently mutates schema) | mitigated | All 3 services set `spring.jpa.hibernate.ddl-auto: validate`. `grep -r 'ddl-auto: update' services/` returns 0 matches. On boot from Phase 2+, Hibernate will reject any entity↔schema mismatch. |
| T-00-26 (Flyway checksum collision across services) | mitigated | Per-service `<svc>_flyway_schema_history` (3 distinct values across the 3 application.yml files). Combined with `spring.flyway.schemas=<svc>` + `default-schema=<svc>`, no service can ever see another service's migration history. Validation in Wave 6 / SC#5. |
| T-00-27 (Service errors at startup with "Unsupported Database: PostgreSQL 16.x") | mitigated | All 3 build.gradle.kts declare `runtimeOnly(libs.flyway.database.postgresql)` (Pitfall A / C5). 3 of 3 verified. |
| T-00-28 (Direct LAN access to downstream service port bypasses gateway) | mitigated (Wave 6 owns runtime enforcement) | Compose binding to `127.0.0.1:<port>` lands in Wave 6 per D-22 — these 3 application.yml files don't enforce binding; that's the compose layer's job. Phase 1 will additionally ship JwtCommonFilter to enforce auth even when bypass occurs. |
| T-00-29 (Actuator /env, /configprops exposed) | mitigated | All 3 services set `management.endpoints.web.exposure.include: health,info,prometheus`. /env, /configprops, /beans, /mappings NOT exposed. |
| T-00-30 (Stack traces in error responses) | mitigated (Phase 1) | `server.error.include-stacktrace` not set in Phase 0; default is `never` for application.yml. Phase 1's GlobalExceptionHandler fully owns this. |

## Self-Check

Verified each claim against the workspace:

- `[x] FOUND: services/auth-service/build.gradle.kts (1641 B, 36 lines)`
- `[x] FOUND: services/auth-service/src/main/java/com/tripplanner/auth/AuthServiceApplication.java (574 B)`
- `[x] FOUND: services/auth-service/src/main/java/com/tripplanner/auth/health/HealthPlaceholderController.java (1133 B)`
- `[x] FOUND: services/auth-service/src/main/resources/application.yml (2582 B)`
- `[x] FOUND: services/auth-service/src/main/resources/application-docker.yml (1009 B)`
- `[x] FOUND: services/auth-service/src/main/resources/logback-spring.xml (727 B)`
- `[x] FOUND: services/auth-service/src/main/resources/db/migration/V1__init.sql (304 B)`
- `[x] FOUND: services/trip-service/build.gradle.kts (1748 B)`
- `[x] FOUND: services/trip-service/src/main/java/com/tripplanner/trip/TripServiceApplication.java (610 B)`
- `[x] FOUND: services/trip-service/src/main/java/com/tripplanner/trip/health/HealthPlaceholderController.java (1080 B)`
- `[x] FOUND: services/trip-service/src/main/resources/application.yml (2097 B)`
- `[x] FOUND: services/trip-service/src/main/resources/application-docker.yml (840 B)`
- `[x] FOUND: services/trip-service/src/main/resources/logback-spring.xml (427 B)`
- `[x] FOUND: services/trip-service/src/main/resources/db/migration/V1__init.sql (304 B)`
- `[x] FOUND: services/destination-service/build.gradle.kts (1741 B)`
- `[x] FOUND: services/destination-service/src/main/java/com/tripplanner/destination/DestinationServiceApplication.java (692 B)`
- `[x] FOUND: services/destination-service/src/main/java/com/tripplanner/destination/health/HealthPlaceholderController.java (1132 B)`
- `[x] FOUND: services/destination-service/src/main/resources/application.yml (2218 B)`
- `[x] FOUND: services/destination-service/src/main/resources/application-docker.yml (861 B)`
- `[x] FOUND: services/destination-service/src/main/resources/logback-spring.xml (434 B)`
- `[x] FOUND: services/destination-service/src/main/resources/db/migration/V1__init.sql (304 B)`
- `[x] FOUND commit: 2508472 (Task 7.1 feat — auth-service)`
- `[x] FOUND commit: 74bf90c (Task 7.2 feat — trip-service)`
- `[x] FOUND commit: 91a709f (Task 7.3 feat — destination-service)`
- `[x] FOUND assertion: 3 distinct flyway.table values (auth_/trip_/destination_flyway_schema_history)`
- `[x] FOUND assertion: 3 services declare runtimeOnly(libs.flyway.database.postgresql) (Pitfall A / C5)`
- `[x] FOUND assertion: 3 services set spring.application.name=<svc>-service (D-25 / C3)`
- `[x] FOUND assertion: 0 ddl-auto: update matches across services/ (C15 anti-pattern absent)`
- `[x] FOUND assertion: 0 spring-boot-starter-webflux references in DB-backed services (servlet stack only)`
- `[x] FOUND assertion: ./gradlew :services:auth-service:compileJava :services:trip-service:compileJava :services:destination-service:compileJava BUILD SUCCESSFUL`
- `[x] FOUND assertion: ./gradlew :services:auth-service:bootJar :services:trip-service:bootJar :services:destination-service:bootJar BUILD SUCCESSFUL (3 bootJars produced)`

**Self-Check: PASSED**

## User Setup Required

None — no external service configuration required for this plan. The per-service DB users (`auth_svc`/`trip_svc`/`destination_svc`) and the Postgres schemas (`auth`/`trip`/`destination`) will be created by Wave 6's `infra/postgres/init.sql` on first compose-up.

Optional: a developer running individual `./gradlew :services:<svc>:bootRun` outside compose can override the JDBC URL / credentials via the env vars already wired into application.yml (`POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `<SVC>_DB_USER`, `<SVC>_DB_PASSWORD`).

## Next Phase Readiness

- **Wave 4+ plans (00-08 compose orchestration, 00-09 infra/postgres/init.sql, 00-10 smoke.sh) are unblocked.** The 3 services have Eureka client registration wired, Flyway baseline migrations in place, and per-service DB users referenced via env vars.
- **Plan 00-09 (infra/postgres/init.sql)** must create 3 schemas (`auth`, `trip`, `destination`) + 3 users (`auth_svc`, `trip_svc`, `destination_svc`) with `USAGE, CREATE` grants on their own schema only. The user names + schema names are locked HERE — Plan 00-09's init.sql must match exactly.
- **Plan 00-10 (smoke.sh) SC#5** can validate `<svc>_flyway_schema_history` table presence in each schema once Wave 6 compose lands. The 3 expected table names are: `auth.auth_flyway_schema_history`, `trip.trip_flyway_schema_history`, `destination.destination_flyway_schema_history`.
- **Phase 1 readiness:** the gateway (Plan 00-06) already routes `/__health/{auth,trip,destination}` to these services via static URIs. Phase 1 will append `/api/<svc>/**` route entries below the existing `/__health` entries — no changes to these 3 services' application.yml files required at that point. Phase 1 also adds Spring Security servlet config + JwtCommonFilter to trip + destination (auth-service likely uses a different security path); the build.gradle.kts files have `libs/observability`, `libs/error-handling`, `libs/api-contracts` already on the classpath so Phase 1 only needs to add the JWT-specific deps.
- **Phase 2 / 3 / 5 readiness:** each service's V1 migration is empty + the comment block points to which Phase owns V2+. `ddl-auto=validate` will catch any drift; per-service `<svc>_flyway_schema_history` will track each service's migration timeline independently. No plan-level coordination needed across the three domain phases.
- **No blockers for Plan 00-08.**

---
*Phase: 00-monorepo-scaffolding*
*Completed: 2026-05-08*
