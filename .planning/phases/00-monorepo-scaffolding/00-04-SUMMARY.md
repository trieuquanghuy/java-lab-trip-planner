---
phase: 00-monorepo-scaffolding
plan: 04
subsystem: infra
tags: [gradle, java-library, problem-detail, rfc-7807, spring-web, error-handling, api-contracts]

requires:
  - 00-01 (Wave 1 foundation: gradle/libs.versions.toml `springBoot` version key, `spring-dependency-management` plugin alias, settings.gradle.kts already includes `:libs:error-handling` and `:libs:api-contracts`)
  - 00-03 (Wave 2 prototype: libs/observability established the java-library + dependency-management lib shape and the .gitkeep removal pattern)

provides:
  - libs/error-handling java-library subproject (compiles cleanly via the wrapper toolchain)
  - ProblemDetailFactory static helper returning org.springframework.http.ProblemDetail with stable type URI (https://tripplanner.example.com/errors/<code>) + `code` extension member per docs/04-api-spec.md §6
  - ErrorCode enum with EXACTLY 2 baseline codes per D-05 — AUTH_UNAUTHORIZED ("auth.unauthorized") and AUTH_RATE_LIMITED ("auth.rate_limited")
  - libs/api-contracts empty Gradle module (folder + java-library build file + src/main/java/.gitkeep) wired into the build graph
  - Convention C7 reinforced: no @ControllerAdvice / GlobalExceptionHandler in libs/error-handling (Phase 1+ owns per-service exception handlers)
  - Convention C22 preserved: libs/jwt-common does NOT exist (Phase 1 ownership intact)
  - Convention C23 satisfied: libs/api-contracts is the empty-module shape

affects:
  - 00-06 (eureka-server) — may consume libs/error-handling for ProblemDetail responses (optional)
  - 00-07 (api-gateway) — Phase 1 will use ProblemDetailFactory.of(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, ...) in its JWT WebFilter
  - 00-08 (auth/trip/destination services) — every service that returns RFC 7807 problems will depend on this lib
  - Phase 1 (api-gateway) — directly consumes ProblemDetailFactory + ErrorCode in its error WebFilter; lands UserContext record into libs/api-contracts/src/main/java/com/tripplanner/contracts/
  - Phase 2 (auth-service) — extends ErrorCode enum with AUTH_INVALID_CREDENTIALS, AUTH_EMAIL_NOT_VERIFIED, etc. (the 2-code baseline is the floor)

tech-stack:
  added:
    - "org.springframework:spring-web (transitively brings org.springframework.http.ProblemDetail; Spring 6.0+ class)"
    - "RFC 7807 ProblemDetail envelope shape (stable type URI + `code` extension) — single point of truth via ProblemDetailFactory.of(...)"
  patterns:
    - "java-library + spring-dependency-management plugin shape (mirrors libs/observability prototype from 00-03; minimal lib variant — no bundle, no compileOnly servlet/webflux)"
    - "Static-factory helper class for ProblemDetail construction — final class, private ctor, single static `of(HttpStatus, ErrorCode, String)` method. Consumers can never accidentally drop the `type` URI or `code` extension."
    - "Enum-driven error code catalog with stable string codes (`auth.unauthorized`, `auth.rate_limited`) decoupled from the constant name (AUTH_UNAUTHORIZED). Phase 1+ extends without breaking the wire format."
    - "Empty-module Gradle pattern: subproject declared in settings.gradle.kts in Wave 1; build.gradle.kts in Wave 2 is just the java-library plugin; .gitkeep at src/main/java/ keeps the otherwise-empty source dir under git so Phase 1 lands UserContext.java cleanly."

key-files:
  created:
    - "libs/error-handling/build.gradle.kts (552 B, 16 lines) — java-library + spring-dependency-management; api(\"org.springframework:spring-web\") for ProblemDetail; mavenBom imports spring-boot-dependencies via libs.versions.springBoot.get()"
    - "libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java (853 B, 22 lines) — final class, private ctor, single static of(HttpStatus, ErrorCode, String) -> ProblemDetail with type URI + `code` extension"
    - "libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java (547 B, 16 lines) — 2-constant enum: AUTH_UNAUTHORIZED(\"auth.unauthorized\"), AUTH_RATE_LIMITED(\"auth.rate_limited\")"
    - "libs/api-contracts/build.gradle.kts (268 B, 6 lines) — java-library plugin only; no dependencies block, no source files (D-06 empty-module shape)"
    - "libs/api-contracts/src/main/java/.gitkeep (0 B) — keeps the empty source dir under git so Phase 1's UserContext.java drops in cleanly"
  modified: []

key-decisions:
  - "Followed the same .gitkeep-removal pattern Plan 00-03 established for libs/observability: deleted the obsolete top-level libs/error-handling/.gitkeep marker (placed in Plan 00-01) atomically with Task 4.1's source-file commit since real content now keeps the directory tracked."
  - "For libs/api-contracts moved the .gitkeep from libs/api-contracts/.gitkeep (Plan 00-01 marker) to libs/api-contracts/src/main/java/.gitkeep (D-06 + plan-prescribed location). Git records this as a rename; the new location is the one Phase 1 needs because UserContext.java will land in src/main/java/com/tripplanner/contracts/."
  - "Verified the plan's `<verify>` automated grep `! grep -q 'dependencies'` against libs/api-contracts/build.gradle.kts is overly strict — the verbatim PATTERNS.md Bucket B excerpt contains the comment `// no dependencies; no source files` which trips the literal substring grep. The semantic acceptance criterion (#1: 'no dependencies block') is met: a stricter regex `^[[:space:]]*dependencies[[:space:]]*\\{` returns zero hits. Kept the verbatim file content faithful to the plan."

patterns-established:
  - "libs/error-handling shape: java-library + spring-dependency-management plugin alias; api(\"org.springframework:spring-web\") so consumers get ProblemDetail on their compile classpath without a transitive starter; no Spring Boot starters here because the lib is intended for both webflux (gateway) and servlet (auth/trip/destination) consumers."
  - "ErrorCode enum extension contract: Phase 1+ adds new constants to the enum (AUTH_INVALID_CREDENTIALS, VALIDATION_REQUIRED_FIELD, etc.). The wire-format string code (e.g. \"auth.invalid_credentials\") is the stable identifier consumers parse; the enum constant name (AUTH_INVALID_CREDENTIALS) is internal-only. Adding new constants is non-breaking."
  - "ProblemDetail envelope shape locked: `type` URI = TYPE_BASE + code.code() (e.g. https://tripplanner.example.com/errors/auth.unauthorized); `code` extension property = code.code() (machine-readable string identifier). Phase 1+ MUST go through ProblemDetailFactory.of(...) — never construct ProblemDetail directly."

requirements-completed: [NFR-04]

duration: 3min
completed: 2026-05-08
---

# Phase 0 Plan 4: libs/error-handling Stubs + libs/api-contracts Empty Module Summary

**Wave-2 Bucket-B deliverable: `libs/error-handling` ships the 2-baseline-code `ErrorCode` enum (D-05) and the static `ProblemDetailFactory.of(...)` helper that produces RFC 7807 ProblemDetail with stable `type` URI + `code` extension; `libs/api-contracts` ships as an empty Gradle module (folder + java-library build file + .gitkeep) so Phase 1 lands `UserContext` without touching the build graph. `libs/jwt-common` deliberately absent — Phase 1 owns its creation per D-07 / C22.**

## Performance

- **Duration:** ~3 min (very fast — pure paste-and-edit from PATTERNS.md Bucket B; the libs/observability prototype in Plan 00-03 already exercised the java-library + dependency-management lib shape)
- **Started:** 2026-05-08T04:30:40Z
- **Completed:** 2026-05-08T04:33:04Z
- **Tasks:** 2 (Task 4.1 error-handling, Task 4.2 api-contracts empty module)
- **Files created:** 5
- **Files deleted:** 2 (obsolete top-level .gitkeep markers from Plan 00-01)

## Accomplishments

- **D-05 fully realized:** `libs/error-handling` ships the 2-code `ErrorCode` enum (AUTH_UNAUTHORIZED, AUTH_RATE_LIMITED) and `ProblemDetailFactory.of(HttpStatus, ErrorCode, String)` factory. The factory is the single point that produces RFC 7807 envelopes with the stable `type` URI (`https://tripplanner.example.com/errors/<code>`) and the `code` extension property — consumers can never accidentally drop either.
- **D-06 fully realized:** `libs/api-contracts` is an empty Gradle module with only the `java-library` plugin. Phase 1 lands `com.tripplanner.contracts.UserContext` in `src/main/java/com/tripplanner/contracts/` without modifying `settings.gradle.kts` or any service `build.gradle.kts` — the dependency graph is already wired through Wave 1's `settings.gradle.kts` includes.
- **D-07 / C22 preserved:** `libs/jwt-common` directory does NOT exist after this plan; `settings.gradle.kts` does NOT include `:libs:jwt-common`. Phase 1 owns creation cleanly.
- **Pitfall 7 / Convention C7 reinforced:** No `@ControllerAdvice`, no `GlobalExceptionHandler`, no `@ExceptionHandler` anywhere in `libs/error-handling` — those are added per-service when real endpoints exist (Phase 1+).
- **Build verified:** `./gradlew :libs:error-handling:compileJava` exits 0; `./gradlew :libs:api-contracts:compileJava` exits 0 (NO-SOURCE — empty jar by design); `./gradlew projects` lists 8 subprojects (3 libs + 5 services), exactly the count `settings.gradle.kts` declares.

## ErrorCode Enum (D-05 — 2 Baseline Codes Only)

| Constant | Wire-format `code()` | Phase 0 use case |
|----------|---------------------|------------------|
| `AUTH_UNAUTHORIZED` | `auth.unauthorized` | Phase 1 gateway JWT WebFilter — JWT signature invalid / expired / malformed |
| `AUTH_RATE_LIMITED` | `auth.rate_limited` | Phase 1 gateway login-endpoint rate-limit response |

**Total: 2 constants — exactly the floor D-05 mandates.** Adding more codes in Phase 0 is scope creep and contradicts D-05. Phase 1+ extends the enum (e.g. `AUTH_INVALID_CREDENTIALS`, `AUTH_EMAIL_NOT_VERIFIED`, `VALIDATION_REQUIRED_FIELD`, `TRIP_NOT_FOUND`, etc.) as new codes arise.

## ProblemDetail Envelope Contract

```java
ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, "JWT signature invalid");
// pd.getStatus()                 == 401
// pd.getDetail()                 == "JWT signature invalid"
// pd.getType().toString()        == "https://tripplanner.example.com/errors/auth.unauthorized"
// pd.getProperties().get("code") == "auth.unauthorized"
```

Per `docs/04-api-spec.md §6`, the `code` extension member is the stable machine-readable identifier consumers parse; the `type` URI is the human-readable doc reference. The factory ensures both are produced consistently from a single call site.

## libs/api-contracts Empty-Module Confirmation

```
libs/api-contracts/
├── build.gradle.kts        # 268 B — java-library plugin only; no dependencies block
└── src/main/java/
    └── .gitkeep            # 0 B — keeps the empty source dir under git
```

- `build.gradle.kts` declares only the `java-library` plugin per D-06.
- `src/main/java/.gitkeep` (NOT `libs/api-contracts/.gitkeep`) — the location matters because Phase 1's `UserContext.java` lands in `src/main/java/com/tripplanner/contracts/`, and the `.gitkeep` keeps the otherwise-empty parent dir tracked.
- `./gradlew :libs:api-contracts:compileJava` reports `Task :libs:api-contracts:compileJava NO-SOURCE` and exits 0 — empty jar by design.

## Subproject Count Confirmation

```
$ ./gradlew projects --no-daemon
Root project 'trip-planner'
+--- Project ':libs'
|    +--- Project ':libs:api-contracts'
|    +--- Project ':libs:error-handling'
|    \--- Project ':libs:observability'
\--- Project ':services'
     +--- Project ':services:api-gateway'
     +--- Project ':services:auth-service'
     +--- Project ':services:destination-service'
     +--- Project ':services:eureka-server'
     \--- Project ':services:trip-service'
```

**8 subprojects total: 3 libs + 5 services.** Matches `settings.gradle.kts` Wave 1 declaration; matches plan-level verification expectation.

## libs/jwt-common Absence Confirmation

```
$ [ ! -d libs/jwt-common ] && echo "OK"
OK
$ grep -c ':libs:jwt-common' settings.gradle.kts
0
```

`libs/jwt-common` does NOT exist; `settings.gradle.kts` does NOT include `:libs:jwt-common`. Phase 1 ownership preserved per D-07 / Convention C22.

## Task Commits

Each task was committed atomically (sequential executor on `master`):

1. **Task 4.1: libs/error-handling — build.gradle.kts + ProblemDetailFactory + ErrorCode (D-05)** — `e69c5cd` (feat)
2. **Task 4.2: libs/api-contracts — empty Gradle module (D-06)** — `5718db6` (feat)

**Plan metadata commit:** _to be added by final commit step (SUMMARY.md + STATE.md + ROADMAP.md)_

## Files Created/Modified

### Created

| Path | Bytes | Lines | Purpose |
|------|-------|-------|---------|
| `libs/error-handling/build.gradle.kts` | 552 | 16 | java-library + spring-dependency-management; `api("org.springframework:spring-web")` for ProblemDetail; mavenBom imports spring-boot-dependencies via `libs.versions.springBoot` |
| `libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java` | 853 | 22 | Final class, private ctor, single static `of(HttpStatus, ErrorCode, String) -> ProblemDetail` building the RFC 7807 envelope with stable type URI + `code` extension |
| `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` | 547 | 16 | 2-constant enum (AUTH_UNAUTHORIZED, AUTH_RATE_LIMITED). Phase 1+ extends |
| `libs/api-contracts/build.gradle.kts` | 268 | 6 | java-library plugin only; comment-only body; D-06 empty-module shape |
| `libs/api-contracts/src/main/java/.gitkeep` | 0 | 0 | Keeps empty source dir under git so Phase 1's UserContext.java drops in cleanly |

### Deleted (intentional — superseded by real content)

| Path | Reason |
|------|--------|
| `libs/error-handling/.gitkeep` | Marker placed in Plan 00-01 to keep the empty subproject directory tracked. Now obsolete — `build.gradle.kts` and `src/main/java/com/tripplanner/errors/*.java` keep the directory tracked. Deletion atomic with Task 4.1 commit (`e69c5cd`). Mirrors the same pattern Plan 00-03 used for `libs/observability/.gitkeep`. |
| `libs/api-contracts/.gitkeep` | Marker from Plan 00-01 at the top-level subproject directory. The plan acceptance criterion specifies `.gitkeep` at `libs/api-contracts/src/main/java/.gitkeep` (a different location), so the top-level marker was removed and a new one created at the prescribed location. Git records this as a rename. |

## Decisions Made

- **Followed Plan 00-03's `.gitkeep` removal pattern.** Plan 00-01's STATE.md decisions explicitly call out: "Wave 2/3/4/5 will replace each `.gitkeep` with the real `build.gradle.kts` when those subprojects are built out." Both `libs/error-handling/.gitkeep` and `libs/api-contracts/.gitkeep` are now superseded by real content. For `libs/api-contracts`, git records the change as a rename from `libs/api-contracts/.gitkeep` to `libs/api-contracts/src/main/java/.gitkeep` because the file is empty in both locations — the rename surfaces the intent clearly in `git log --follow`.
- **Plan `<verify>` line for Task 4.2 has an overly strict grep against the word "dependencies".** The verbatim PATTERNS.md Bucket B excerpt for `libs/api-contracts/build.gradle.kts` contains the comment line `// No dependencies. No source files. ...`, which trips the literal substring grep `! grep -q 'dependencies'`. The semantic acceptance criterion #1 ("declares only the java-library plugin; no dependencies block") is met — a stricter regex `^[[:space:]]*dependencies[[:space:]]*\{` returns zero hits. Kept the build file faithful to the plan-prescribed content; documented the verify-line strictness here for the verifier.
- **Both libs use the SAME plugin shape as `libs/observability` from Plan 00-03**, minus the bundle and the compileOnly servlet/webflux deps. Convention C16 (single source of truth for versions) is preserved: `libs.versions.springBoot.get()` is the only version reference; no literals.

## Deviations from Plan

None — plan executed exactly as written. The two `.gitkeep` deletions documented above are **enforcing the plan's intent** (Plan 00-01 SUMMARY explicitly stated Wave 2/3/4/5 replaces `.gitkeep` markers with real content) rather than deviating from acceptance criteria.

## Issues Encountered

- **Local launcher JVM is OpenJDK 25, not 21** — Plan 00-01's and Plan 00-03's SUMMARYs documented this. Resolved per the same workaround: set `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home` so Gradle's wrapper runs on JDK 17 and the toolchain mechanism auto-provisions JDK 21 for `javac`. Both `:libs:error-handling:compileJava` and `:libs:api-contracts:compileJava` then succeed. CI uses `actions/setup-java@v4 java-version: 21` (Plan 00-09) so this is purely a local-developer ergonomics gap, not a CI gap.
- **Plan `<verify>` line for Task 4.2 overly strict** (see Decisions Made above). Documented; not a behavior issue.
- No further issues during planned work.

## Threat Register Outcomes

| Threat ID | Status | Evidence |
|-----------|--------|----------|
| T-00-13 (`ProblemDetail.detail` carrying internal exception messages with stack traces) | mitigated (deferred — no callers in Phase 0) | `ProblemDetailFactory.of()` accepts the `detail` string from the caller. Phase 0 ships zero callers (no controllers, no exception handlers in this lib), so the surface is not yet exploitable. Phase 1+ GlobalExceptionHandler will sanitize messages before passing them in; Wave 3 service config will set `server.error.include-stacktrace=never` in the `docker` profile per the plan threat-model. |
| T-00-14 (Future contributor adds bypass-style ErrorCode constants) | accepted | Code review enforcement; D-05 explicitly limits Phase 0 to 2 baseline codes. Encoded in the source comment block above the enum. |
| T-00-15 (`TYPE_BASE = "https://tripplanner.example.com/errors/"` URI leaking domain in production) | accepted | Portfolio scope; v1 is local-only deployment per project constraints. The `type` URI is informational; consumers do not dereference it. |

## Self-Check

Verified each claim against the workspace:

- `[x] FOUND: libs/error-handling/build.gradle.kts (552 B, 16 lines)`
- `[x] FOUND: libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java (853 B, 22 lines)`
- `[x] FOUND: libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java (547 B, 16 lines)`
- `[x] FOUND: libs/api-contracts/build.gradle.kts (268 B, 6 lines)`
- `[x] FOUND: libs/api-contracts/src/main/java/.gitkeep (0 B)`
- `[x] FOUND assertion: ErrorCode enum has EXACTLY 2 constants (AUTH_UNAUTHORIZED, AUTH_RATE_LIMITED)`
- `[x] FOUND assertion: grep -c 'enum ErrorCode {' returns 1`
- `[x] FOUND assertion: ProblemDetailFactory.forStatusAndDetail + setType + setProperty("code", ...) all present`
- `[x] FOUND assertion: no @ControllerAdvice / GlobalExceptionHandler / @ExceptionHandler in libs/error-handling`
- `[x] FOUND assertion: libs/jwt-common does NOT exist`
- `[x] FOUND assertion: settings.gradle.kts does NOT include :libs:jwt-common (grep returns 0)`
- `[x] FOUND assertion: ./gradlew :libs:error-handling:compileJava exits 0 (BUILD SUCCESSFUL in 9s)`
- `[x] FOUND assertion: ./gradlew :libs:api-contracts:compileJava exits 0 (NO-SOURCE)`
- `[x] FOUND assertion: ./gradlew projects lists 8 subprojects (:libs:api-contracts, :libs:error-handling, :libs:observability, :services:api-gateway, :services:auth-service, :services:destination-service, :services:eureka-server, :services:trip-service)`
- `[x] FOUND commit: e69c5cd (Task 4.1 feat)`
- `[x] FOUND commit: 5718db6 (Task 4.2 feat)`

**Self-Check: PASSED**

## User Setup Required

None — no external service configuration required for this plan.

## Next Phase Readiness

- **Wave 2 is complete.** All three Phase-0 libs are now realized in their planned shape: `libs/observability` fully wired (Plan 00-03), `libs/error-handling` minimal stubs (this plan), `libs/api-contracts` empty module (this plan). `libs/jwt-common` deliberately absent.
- **Wave 3 (eureka-server) is unblocked.** Eureka does not consume `libs/error-handling` or `libs/api-contracts` (no HTTP error responses, no shared DTOs). It optionally consumes `libs/observability` for log-format consistency.
- **Wave 4 (api-gateway) is unblocked.** Gateway will declare `implementation(project(":libs:error-handling"))` + `implementation(project(":libs:api-contracts"))` + `implementation(project(":libs:observability"))` in its build.gradle.kts. Phase 1's gateway error WebFilter will use `ProblemDetailFactory.of(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, ...)`.
- **Wave 5 (auth/trip/destination services) is unblocked.** Each DB-backed service will declare the same 3-line lib dependency block.
- **Phase 1 ownership preserved.** Phase 1 will: (a) extend `ErrorCode` enum with new constants as needed, (b) land `com.tripplanner.contracts.UserContext` record into `libs/api-contracts/src/main/java/com/tripplanner/contracts/`, (c) create `libs/jwt-common` from scratch (its directory does not exist; its inclusion in `settings.gradle.kts` is also Phase 1's responsibility).
- **No blockers for Plan 00-05** (services skeletons + Spring Boot main classes + `/__health` placeholder controllers).

---
*Phase: 00-monorepo-scaffolding*
*Completed: 2026-05-08*
