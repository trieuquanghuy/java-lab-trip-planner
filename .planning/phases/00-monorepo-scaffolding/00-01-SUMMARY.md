---
phase: 00-monorepo-scaffolding
plan: 01
subsystem: infra
tags: [gradle, version-catalog, spring-cloud, kotlin-dsl, monorepo]

requires: []
provides:
  - Gradle 8.14.2 wrapper (jar + scripts) committed and SHA-verified
  - Multi-module skeleton with 8 subprojects (3 libs + 5 services) — directories present, build files arrive in Wave 2+
  - gradle/libs.versions.toml as the SINGLE source of truth for every backend dep version (D-26 / Convention C16)
  - Spring Cloud 2025.0.2 pin (D-30 — corrects CLAUDE.md's stale 2024.0.x reference)
  - flyway-database-postgresql library entry (Pitfall A enforcement starts here)
  - Observability bundle catalogued ONCE (Pitfall 7 / Convention C6)
  - .env.example covering every var in docs/08-deployment.md §1.4 + per-service DB user pairs (D-08, D-24)
  - Root README documenting docker-compose quickstart, port table, 30-60s warm-up window, D-30 stack correction
  - .gitignore covering .gradle/, build/, frontend artifacts, .env, IDE files
affects:
  - 00-02 (services skeletons), 00-03 (libs/observability), 00-04 (libs/error-handling), 00-05 (libs/api-contracts)
  - 00-06 (DB-backed services + Flyway), 00-07 (infra/docker-compose), 00-08 (frontend), 00-09 (CI), 00-10 (smoke)
  - All later phases (1-9): every service build.gradle.kts will resolve deps from this catalog

tech-stack:
  added:
    - "Gradle 8.14.2 (Kotlin DSL, multi-module)"
    - "Spring Boot 3.5.14 (catalogued)"
    - "Spring Cloud 2025.0.2 / Northfields (catalogued; D-30 correction)"
    - "Flyway 10.21.0 + flyway-database-postgresql (catalogued; Pitfall A)"
    - "Micrometer Tracing 1.4.5 BOM (catalogued ONCE; Pitfall 7)"
    - "logstash-logback-encoder 7.4 (catalogued)"
    - "jjwt 0.13.0, Resilience4j 2.4.0, WireMock Spring Boot 3.9.0 (catalogued for Phase 1/4)"
  patterns:
    - "Single source of truth in gradle/libs.versions.toml (Convention C16)"
    - "subprojects {} block in root build.gradle.kts enforces Java 21 toolchain + Jacoco for every module"
    - "Subproject naming :services:<name> and :libs:<name> (D-28)"
    - ".env.example pattern: header 'DO NOT COMMIT' + canonical var list mirroring docs/08-deployment.md §1.4"
    - "Docker compose root-alias via include: directive (D-19) — documented but compose file lands in Wave 6"

key-files:
  created:
    - "settings.gradle.kts (896 B) — declares 8 subprojects + plugin/dependency repos"
    - "build.gradle.kts (663 B) — Java 21 toolchain + Jacoco for all subprojects"
    - "gradle.properties (97 B) — parallel/caching, -Xmx2g -Dfile.encoding=UTF-8"
    - "gradle/libs.versions.toml (4515 B) — version catalog (12 versions, 20 libraries, 1 bundle)"
    - "gradle/wrapper/gradle-wrapper.properties (253 B) — pins gradle-8.14.2-bin"
    - "gradle/wrapper/gradle-wrapper.jar (48966 B)"
    - "gradlew (8654 B), gradlew.bat (2896 B)"
    - ".editorconfig (260 B) — LF + UTF-8 + 2/4-space indent rules"
    - ".env.example (1163 B) — env template covering postgres/per-service DB users/eureka/zipkin/JWT/SMTP/providers/frontend"
    - "README.md (7040 B) — quickstart, port table, warm-up note, stack pins note (D-30 correction)"
    - "8 .gitkeep files in services/* and libs/* subproject directories"
  modified:
    - ".gitignore (377 B) — extended with Gradle/frontend/env/IDE entries"

key-decisions:
  - "Created .gitkeep files in all 8 subproject directories so Gradle 9 (homebrew launcher) does not error on missing project paths during settings evaluation"
  - "Manually downloaded the gradle-8.14.2-bin distribution and SHA256-verified against services.gradle.org checksum after a network flake; placed into ~/.gradle/wrapper/dists cache so the wrapper bootstrap succeeded"
  - "Catalogued forward-looking deps (jjwt, Resilience4j, wiremock-spring-boot) in libs.versions.toml so Phase 1/4 plans land without re-touching the catalog (D-26)"

patterns-established:
  - "Convention C16: gradle/libs.versions.toml is the SINGLE source of truth — no version literals in any service build.gradle.kts"
  - "Convention C17: Spring Cloud train is 2025.0.x (Northfields) — CLAUDE.md is wrong on this; 2024.0 (Moorgate) is for SB 3.4"
  - "Threat T-00-01 mitigation: .env in .gitignore + .env.example DO NOT COMMIT header (verified by git check-ignore .env)"

requirements-completed: [NFR-04]

duration: 1h 3min
completed: 2026-05-08
---

# Phase 0 Plan 1: Build Infrastructure Foundation Summary

**Gradle 8.14.2 multi-module skeleton with version catalog locking Spring Boot 3.5.14 + Spring Cloud 2025.0.2 (D-30 correction) + Flyway 10 PG driver + Micrometer Tracing BOM single-pin (Pitfall 7), plus repo-root infra (.env.example, .gitignore, .editorconfig, README).**

## Performance

- **Duration:** ~1h 3min (1 of which was network-flake + wrapper bootstrap retries)
- **Started:** 2026-05-08T02:55Z (approx)
- **Completed:** 2026-05-08T03:58Z
- **Tasks:** 3
- **Files created:** 14 (incl. 8 .gitkeep markers)
- **Files modified:** 1 (.gitignore extended)

## Accomplishments

- Gradle 8.14.2 wrapper committed and verified against services.gradle.org SHA256 (Threat T-00-03 mitigation)
- Version catalog with 12 versions, 20 libraries, 1 bundle — every Phase 0–9 backend dep is now reachable via `libs.<accessor>` without ever touching this file again
- D-30 Spring Cloud 2025.0.x correction encoded in catalog header AND README "Stack pins note" — future contributors won't propagate CLAUDE.md's stale 2024.0.x reference
- Pitfall A enforcement starts here: `flyway-database-postgresql` is catalogued so every DB-backed service in Wave 5 can `runtimeOnly(libs.flyway.database.postgresql)` without rediscovering the modularization gotcha
- Pitfall 7 enforcement: micrometer-tracing-bom pinned ONCE, observability bundle catalogued ONCE — services can never silently override
- `.env.example` is the union of every var in `docs/08-deployment.md §1.4` plus the 6 per-service DB user/password pairs from D-08
- Root README is the single-page entry point: quickstart, port table, warm-up window note, stack pins correction, repo layout

## Task Commits

Each task was committed atomically (sequential executor on `master`):

1. **Task 1.1: Gradle multi-module skeleton (settings, root build, gradle.properties, wrapper)** — `15e0540` (chore)
2. **Task 1.2: Author version catalog (gradle/libs.versions.toml) — single source of truth** — `0e811bc` (chore)
3. **Task 1.3: .gitignore, .editorconfig, .env.example, root README.md** — `4f34018` (docs)

**Plan metadata commit:** _to be added by final commit step (SUMMARY.md + STATE.md + ROADMAP.md)_

## Files Created/Modified

### Created

| Path | Bytes | Purpose |
|------|-------|---------|
| `settings.gradle.kts` | 896 | 8 subproject includes (3 libs + 5 services), pluginManagement, dependencyResolutionManagement |
| `build.gradle.kts` | 663 | Root build: allprojects group/version, subprojects {} block (Java 21 toolchain, Jacoco, JUnit Platform) |
| `gradle.properties` | 97 | parallel=true, caching=true, -Xmx2g -Dfile.encoding=UTF-8 |
| `gradle/libs.versions.toml` | 4515 | Version catalog — 12 versions, 20 libraries, 1 bundle, 2 plugins. SB 3.5.14, SC 2025.0.2, jjwt 0.13.0, Flyway 10.21.0 |
| `gradle/wrapper/gradle-wrapper.properties` | 253 | Pins gradle-8.14.2-bin distribution URL |
| `gradle/wrapper/gradle-wrapper.jar` | 48966 | Wrapper JAR generated by `gradle wrapper --gradle-version 8.14.2` |
| `gradlew` | 8654 | Unix wrapper script (executable) |
| `gradlew.bat` | 2896 | Windows wrapper script |
| `.editorconfig` | 260 | LF, UTF-8, 4-space java/kt/kts, 2-space ts/json/yml/md |
| `.env.example` | 1163 | Postgres + 3 per-service DB user pairs + Eureka + JWT placeholder + SMTP + provider keys + Zipkin + VITE_API_URL |
| `README.md` | 7040 | Quickstart, port table, warm-up window, include-alias compose layout, D-30 stack pins note, repo layout, doc links |
| `services/{eureka-server,api-gateway,auth-service,trip-service,destination-service}/.gitkeep` | 0 each | Subproject directory markers |
| `libs/{observability,error-handling,api-contracts}/.gitkeep` | 0 each | Subproject directory markers |

### Modified

| Path | Change |
|------|--------|
| `.gitignore` | Extended with Gradle (.gradle/, build/), frontend (node_modules, dist, .vite), env (.env, .env.local), IDE (.idea, *.iml, .vscode) entries |

## Decisions Made

- **Wrapper distribution side-loaded after network flake.** First two `./gradlew --version` runs failed mid-download (TLS read interrupt). Resolved by manually `curl`-fetching `gradle-8.14.2-bin.zip` + `.sha256`, verifying SHA256 (`7197a12f...0a6999`) against `services.gradle.org`, and dropping into `~/.gradle/wrapper/dists/gradle-8.14.2-bin/2pb3mgt1p815evrl3weanttgr/`. Wrapper now bootstraps cleanly. (Threat T-00-03 mitigation: SHA256 verified.)
- **Subproject directories created with .gitkeep markers.** Gradle 9 (the locally-installed launcher used to bootstrap the wrapper) is strict about `include(":path")` requiring an existing directory. Plan envisaged subprojects "declared but not built until later waves"; reality required physical directories. `.gitkeep` markers ensure git tracks them; Wave 2+ replaces them with real `build.gradle.kts`.
- **Forward-looking deps catalogued early.** Plan asked for jjwt 0.13.0 and Resilience4j 2.4.0 in catalog; I additionally catalogued `wiremock-spring-boot` (Phase 4) and the `spring-boot-testcontainers` library entry so Phase 4 doesn't need to re-edit the catalog. Convention C16 (single source of truth) is strengthened.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 — Blocking] Subproject directories required physical existence under Gradle 9 launcher**
- **Found during:** Task 1.1 (running `gradle wrapper --gradle-version 8.14.2`)
- **Issue:** Gradle 9.4.1 (the locally-installed launcher used only to bootstrap the wrapper) errored: `Configuring project ':services:trip-service' without an existing directory is not allowed`. The plan envisaged subprojects "declared in settings but build.gradle.kts arrives in later waves" — but newer Gradle requires the directory itself to exist.
- **Fix:** Created the 8 subproject directories (`services/{eureka-server,api-gateway,auth-service,trip-service,destination-service}` + `libs/{observability,error-handling,api-contracts}`) with `.gitkeep` files so git tracks them.
- **Files modified:** 8 new `.gitkeep` files
- **Verification:** `gradle wrapper --gradle-version 8.14.2 --distribution-type bin` then succeeds; `./gradlew --version` reports Gradle 8.14.2.
- **Committed in:** `15e0540` (Task 1.1 commit)
- **Plan reconciliation:** Wave 2/3/4/5 will replace each `.gitkeep` with the real `build.gradle.kts` when those subprojects are built out.

**2. [Rule 3 — Blocking] Manual SHA-verified gradle distribution side-load after network flake**
- **Found during:** Task 1.1 verification (`./gradlew --version`)
- **Issue:** Two consecutive `./gradlew --version` runs failed mid-download with `SocketException: Connection reset` during TLS read of `gradle-8.14.2-bin.zip`. The wrapper left a partial `.part` file plus a `.lck` lock that would not auto-recover.
- **Fix:** Manually `curl`-downloaded `gradle-8.14.2-bin.zip` + `.sha256`, computed `shasum -a 256` against the file (`7197a12f450794931532469d4ff21a59ea2c1cd59a3ec3f89c035c3c420a6999`), confirmed it matched the published checksum, and copied both files into `~/.gradle/wrapper/dists/gradle-8.14.2-bin/2pb3mgt1p815evrl3weanttgr/`. Wrapper now bootstraps cleanly without re-fetching. (This is a per-developer cache; CI's `actions/setup-java@v4` installs Java 21 fresh each run.)
- **Files modified:** none in repo (wrapper cache lives outside the workspace)
- **Verification:** `./gradlew --version` reports `Gradle 8.14.2`. (Threat T-00-03 mitigation: SHA256 matches services.gradle.org.)
- **Committed in:** N/A (out-of-repo cache fix; not a code change)

---

**Total deviations:** 2 auto-fixed (both Rule 3 — blocking)
**Impact on plan:** Both deviations were environmental (newer Gradle launcher strictness, network flake) and did not change any plan deliverable. Subproject directory creation is consistent with the plan's intent — the directories existing earlier than their build files is benign.

## Issues Encountered

- **Local launcher JVM is OpenJDK 25, not 21.** Running `./gradlew help` (which compiles `build.gradle.kts` Kotlin DSL) fails with `IllegalArgumentException: 25.0.2` from Kotlin compiler internals. Plan acknowledges this: Task 1.1 acceptance criterion 4 says "`./gradlew --version` outputs Gradle version 8.14.x and JVM 21 (run after Java 21 is available; in CI this is `actions/setup-java@v4 java-version: 21`)". The catalog file is correct; deeper Gradle parse will succeed once JDK 21 is on `JAVA_HOME` (Phase 0 Plan 2+ will require this).
- **No further issues during planned work.**

## Threat Register Outcomes

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-00-01 (`.env` leak) | mitigated | `.env` in `.gitignore`; `git check-ignore .env` returns `.env`; `.env.example` ships "DO NOT COMMIT" header |
| T-00-02 (supply chain via floating versions) | mitigated | Every dep version is a literal pin in `gradle/libs.versions.toml`; no `+` or `[]` ranges |
| T-00-03 (gradle-wrapper.jar tampered) | mitigated | Wrapper jar generated by official `gradle wrapper`; distribution side-load was SHA256-verified against services.gradle.org |
| T-00-04 (README port disclosure) | accepted (per plan) | README documents only loopback-bound dev ports; production deployment is out of scope |
| T-00-05 (Spring Cloud version drift CLAUDE.md vs catalog) | mitigated | Catalog header comment + README "Stack pins note" both call out the D-30 correction (2025.0.x, NOT 2024.0.x) |

## Self-Check

Verified each claim against the workspace:

- `[x] FOUND: settings.gradle.kts (896 B)`
- `[x] FOUND: build.gradle.kts (663 B)`
- `[x] FOUND: gradle.properties (97 B)`
- `[x] FOUND: gradle/libs.versions.toml (4515 B)`
- `[x] FOUND: gradle/wrapper/gradle-wrapper.properties (253 B; gradle-8.14.2-bin)`
- `[x] FOUND: gradle/wrapper/gradle-wrapper.jar (48966 B)`
- `[x] FOUND: gradlew (8654 B, executable)`
- `[x] FOUND: gradlew.bat (2896 B)`
- `[x] FOUND: .gitignore (377 B; includes .env, .gradle/, frontend/node_modules)`
- `[x] FOUND: .editorconfig (260 B; indent_size present)`
- `[x] FOUND: .env.example (1163 B; AUTH_JWT_SECRET=dev-only..., DO NOT COMMIT header, all 6 per-service DB user pairs)`
- `[x] FOUND: README.md (7040 B; docker compose up, 8761, 5173, Spring Cloud 2025.0)`
- `[x] FOUND commit: 15e0540 (Task 1.1)`
- `[x] FOUND commit: 0e811bc (Task 1.2)`
- `[x] FOUND commit: 4f34018 (Task 1.3)`
- `[x] FOUND assertion: springCloud "2025.0" count = 1; "2024.0" count = 0 (D-30)`
- `[x] FOUND assertion: git check-ignore .env returns .env (T-00-01 mitigation live)`

**Self-Check: PASSED**

## User Setup Required

None — no external service configuration required for this plan. Plan 00-07 (compose) and 00-09 (CI) introduce external surface; this plan is purely local repo infra.

## Next Phase Readiness

- **Wave 2 (libs/observability, libs/error-handling, libs/api-contracts) is ready to start.** All version catalog accessors needed (`libs.spring.boot.starter.actuator`, `libs.bundles.observability`, `libs.spring.cloud.starter.netflix.eureka.client`, etc.) are present.
- **Wave 5 (DB-backed services) is unblocked.** `libs.flyway.database.postgresql` is catalogued (Pitfall A enforcement available).
- **Open dependency:** Java 21 (Temurin) needs to be on `JAVA_HOME` for `./gradlew check` to compile. Locally, this is a developer prerequisite; in CI, `actions/setup-java@v4 java-version: 21` is set in plan 00-09.
- **No blockers for Plan 00-02.**

---
*Phase: 00-monorepo-scaffolding*
*Completed: 2026-05-08*
