<!-- GSD:project-start source:PROJECT.md -->
## Project

**Trip Planner**

A web app for travelers who want to go from "I want to visit somewhere" to a structured day-by-day itinerary in minutes. Users search a city or country, browse a curated list of attractions, open detail views with photos and opening hours, and assemble the ones they want into trips with drag-drop day-by-day scheduling. Trips and favorited destinations persist across sessions behind email-verified accounts.

This is a practice/portfolio project demonstrating Spring Boot microservices + React, not a product launch.

**Core Value:** A signed-in user can search a city, discover attractions, and assemble them into a multi-day itinerary that persists across sessions — with drag-drop reorder, optional time slots, and a map view. Anything else can fail; this primary flow cannot.

### Constraints

- **Tech stack — locked:** Java 21 + Spring Boot 3.3.x + Gradle Kotlin DSL multi-module + PostgreSQL 16 + Redis 7 + React 18 + Vite + TypeScript + Tailwind + shadcn/ui — User picked this stack; no negotiation on language/framework. (See `docs/02-architecture.md` for full table with rationale.)
- **Architecture — locked:** Monorepo with 5 services (api-gateway, auth-service, trip-service, destination-service, eureka-server) + 4 shared libs + frontend — User explicitly chose this over modular monolith.
- **Single Postgres, schema-per-service:** `auth`, `trip`, `destination` schemas with per-service DB users — Memory budget for laptop dev; ownership preserved without spinning up multiple DB instances.
- **Cost — free tier only:** No paid external APIs in v1. No credit-card-required signups — Portfolio scope; can't justify recurring cost.
- **Auth — JWT with email verification:** HS256 access (15 min) + refresh-token rotation (7 days httpOnly cookie); bcrypt cost 12; email verify required before login — Hand-rolled, not Spring Authorization Server (overkill for v1).
- **Local-only deployment in v1:** `docker compose up` is the ship target. Cloud (Fly.io/Neon/Upstash) is documented but not built — Portfolio doesn't require live cloud demo; local recording suffices.
- **Test discipline:** ≥70% backend service-layer line coverage; 100% on auth + ownership-check paths; 8 mandatory security integration tests gate every PR — Portfolio reviewers care about test discipline as much as feature breadth.
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Backend Stack
### Core Framework
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status |
|------------|---------------|----------------------|------------|--------|
| Java | 21 LTS | 21.x (stay on LTS) | HIGH | Stable. Virtual threads GA since Java 21. Java 25 is next LTS (late 2025); skip for now. |
| Spring Boot | 3.3.x | **3.5.x** (see gotcha below) | HIGH | 3.3.x reached OSS EOL June 30, 2025. Latest in-support 3.x is 3.5.14. Spring Boot 4.0 (Nov 2025) is the new major; requires Spring Framework 7 + Java 17 min — skip for this project. |
| Gradle | 8.x Kotlin DSL | 8.14.x | MEDIUM | Gradle 9.0 released Jan 2026 (Kotlin 2 runtime, config cache by default). Spring Boot 3.5.x toolchain still officially targets Gradle 8.x; 9.0 has breaking changes in build scripts. Stick with latest Gradle 8 patch. |
### Spring Cloud Components
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| Spring Cloud Gateway | Spring Cloud 2023.x (matches SB 3.3) | **Spring Cloud 2024.0.x** (matches SB 3.5) | HIGH | Spring Cloud release train must match Spring Boot minor. Moving to SB 3.5 means moving to Spring Cloud 2024.0.x train. Spring Cloud Gateway 4.2.x is the corresponding version. Spring Cloud 2025.0 (Northfields, May 2025) ships Gateway 5.0 on Spring Boot 4 — do NOT use with SB 3.5. |
| Spring Cloud Netflix Eureka | 4.x (in 2023 train) | **4.2.x** (in 2024.0 train) | MEDIUM | Still maintained within Spring Cloud, but Netflix OSS itself abandoned Eureka 2.0. Spring Cloud Netflix is maintenance-mode — no new features. Continues to receive CVE patches. Acceptable for portfolio use. |
### Persistence
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| PostgreSQL | 16 | **16.x** (stay on 16; PostgreSQL 17 is available but migration is optional) | HIGH | PostgreSQL 16 is fully supported through Nov 2028. PostgreSQL 17 (Sep 2024) adds VACUUM improvements and JSON_TABLE() — no feature in this project requires PG17. Stay on 16 for stability. |
| Hibernate | 6 (via Spring Data JPA) | **6.6.x** (managed by SB 3.5) | HIGH | SB 3.3.x ships Hibernate 6.5.x; SB 3.5.x upgrades to Hibernate 6.6.x. No breaking changes for standard JPA usage. |
| Flyway | 10 | **10.x** (latest in 10 branch, managed by Spring Boot) | HIGH | **CRITICAL GOTCHA:** Flyway 10 modularized database drivers. PostgreSQL support requires an explicit `flyway-database-postgresql` dependency in addition to `flyway-core`. Missing this causes "Unsupported Database: PostgreSQL 16.x" at startup. Spring Boot's `spring-boot-starter-data-jpa` does NOT pull it transitively. Add `org.flywaydb:flyway-database-postgresql` explicitly. Flyway OSS is now at 12.x but Spring Boot 3.5 manages 10.x; do not override. |
| Redis | 7 | **7.x** (stay on 7; Redis 8 is GA but a notable upgrade) | HIGH | Redis 7.x remains fully supported. Redis 8.0 GA'd May 1, 2025 with 30+ performance improvements, new data types (Vector Set, JSON, TimeSeries built-in), and a licensing change (AGPLv3 added as third option alongside RSALv2/SSPLv1). No v1 features require Redis 8 capabilities. Advisory: Redis 8 licensing is now clearer (AGPL counts as OSI-approved open source); worth tracking for v2. |
### Security
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| jjwt | 0.12.x | **0.12.7** (latest 0.12 patch) or **0.13.0** | MEDIUM | 0.12.6 fixed a decompression memory leak in concurrent environments. 0.12.7 added Maven BOM support. 0.13.0 is the newest release (Aug 2023) — it's the last Java-7-compatible branch and introduces no breaking API changes from 0.12.x. Upgrading from 0.12.x to 0.13.0 is safe. CVE-2024-31033 (key character ignoring) was initially filed but **withdrawn** — not an active CVE. |
### Resilience & Observability
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| Resilience4j | 2.x (managed by Spring Boot) | **2.4.0** | HIGH | Fully compatible with Spring Boot 3.x. `resilience4j-spring-boot3` module required. No breaking changes. Actively maintained. |
| Micrometer | Spring Boot managed | **1.14.x** (managed by SB 3.5) | HIGH | Micrometer Tracing ships built-in with Spring Boot 3.x. Spring Cloud Sleuth is EOL — do NOT use it. |
| OpenTelemetry / Zipkin | Bridge via Micrometer | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` | HIGH | Stable path. Spring Boot 3.x docs recommend OTLP as primary path; Zipkin exporter still fully supported. Add `management.tracing.sampling.probability=1.0` in dev. Spring Boot 4.x will push further toward OTLP-native — Zipkin remains usable for portfolio scope. |
### Testing
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| JUnit 5 | 5.x (Spring Boot managed) | **5.12.x** (managed by SB 3.5) | HIGH | Stable. No breaking changes. |
| Mockito | Spring Boot managed | **5.x** (managed by SB 3.5) | HIGH | Stable. |
| Testcontainers | Spring Boot managed | **1.21.x** | HIGH | Spring Boot 3.1+ provides `@ServiceConnection` for zero-config container wiring. The `spring-boot-testcontainers` artifact is the entry point. Check that `testcontainers-bom` version matches Spring Boot's managed version to avoid conflicts. |
| WireMock | Not Spring-managed | **3.9.0** (`org.wiremock.integrations:wiremock-spring-boot`) | MEDIUM | Official Spring Boot WireMock integration. Use `@EnableWireMock` annotation. Do NOT use the old `com.github.tomakehurst:wiremock` artifact — it's unmaintained. Use `org.wiremock:wiremock` (standalone) or the spring-boot integration module. |
## Frontend Stack
### Core Framework
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| React | 18 | **18.3.x** | HIGH | React 19 released December 2024 and is the current stable. React 18.3.x added deprecation warnings for APIs removed in 19. For this project: React 18 is fully supported, ecosystem is mature, and React 19 migration is non-trivial (removes legacy context, propTypes, createFactory). Stay on 18.3.x. Advisory: React 19 support in react-leaflet v5+ requires v6 or higher of react-leaflet. |
| TypeScript | 5 | **5.8.x** | HIGH | TypeScript 6.0 released March 2026 (last JS-based TS release; 7.0 will be a Go rewrite). TypeScript 5.8.x is safe, stable, and broadly compatible. TypeScript 6.0 introduces breaking changes; skip for this project. |
| Vite | 5 | **5.x** or **6.x** | MEDIUM | Vite 6.0 released Nov 2024 with breaking changes (default Sass API changed to modern, build target changed to `baseline-widely-available`, Runtime API renamed to Module Runner). Vite 5 still receives patches. Either works; Vite 6 is the safer new-project choice. If starting fresh, use **Vite 6.x** — migration from 5 to 6 is described as "straightforward for most projects." |
| pnpm | Not in locked spec but in `docs/06-frontend-design.md` | **9.x or 10.x** | MEDIUM | pnpm 9.x is stable. pnpm 10.x released 2025 — stricter defaults. Verify lockfile compatibility if switching. |
### Routing & State
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| React Router | 6 | **6.x** (lock at latest 6.x patch, e.g. `6.30.x`) OR upgrade to **7.x** | MEDIUM | React Router 7 (Nov 2024) is stable. Upgrade from v6 is non-breaking if future flags were enabled. RR7 simplified package structure (import from `react-router`, not `react-router-dom`). Latest stable: 7.14.2. Recommendation: start on **React Router 7** for a greenfield project — it is the maintained path. The locked spec says v6 but using v7 is advisable. |
| TanStack Query | v5 | **5.100.x** (latest in v5, as of April 2026: 5.100.6) | HIGH | Stable, actively maintained. v5 API is fully stable. `onSuccess/onError` callbacks removed from `useQuery` — mutations use `onSettled`. `keepPreviousData` removed in favor of `placeholderData`. |
| Zustand | 4.x (locked spec implies 4 but does not pin) | **5.0.x** | HIGH | Zustand v5 released late 2024. Drops React < 18. Uses native `useSyncExternalStore`. Latest: 5.0.13. For a new React 18 project, use **Zustand 5**. No significant API breaking changes from 4.x. |
### Forms & Validation
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| React Hook Form | 7.x | **7.x** (latest: 7.55.x) | HIGH | Stable, no major breaking changes. v8 not yet released as of May 2026. |
| Zod | 3.x | **3.x** OR **4.x** (with migration) | MEDIUM | **Zod v4 released 2025 with significant breaking changes:** `z.string().email()` → `z.email()`, `z.string().uuid()` → `z.uuid()` (RFC 4122 stricter), `z.record()` now requires two args, error customization API changed. RHF's `@hookform/resolvers` supports Zod v4 in its latest versions. **Recommendation for greenfield:** Use **Zod v3.x** initially to match the locked spec and avoid friction, then evaluate v4 migration during Phase 5+. Do NOT mix v3 and v4 in the same project. |
### HTTP & API
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| Axios | 1.x | **1.16.0** | HIGH | **SECURITY WARNING:** Every Axios version below **1.15.0** has two unpatched CVEs as of April 2026: CVE-2025-62718 (SSRF via NO_PROXY bypass) and CVE-2026-40175 (cloud metadata exfiltration). Pin to **≥ 1.15.0**. Use `1.16.0` (latest). |
### Drag-Drop
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| dnd-kit | `@dnd-kit/core` 6.x | Use **`@dnd-kit/core` 6.x** for React 18 projects | MEDIUM | dnd-kit is undergoing a major rewrite into a framework-agnostic architecture. There is a new `@dnd-kit/react` package (v0.4.0, experimental) with a redesigned API. The original `@dnd-kit/core`, `@dnd-kit/sortable`, `@dnd-kit/utilities` 6.x packages remain the stable choice for React 18. Do NOT use `@dnd-kit/react` yet — it is pre-1.0, API is unstable. |
### Maps
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| Leaflet | 1.9 | **1.9.x** | HIGH | Leaflet 1.9.x is stable and maintained. No major new releases in 2025-2026. |
| react-leaflet | Not pinned in spec | **4.2.x** (for React 18) | HIGH | **CRITICAL:** react-leaflet v5 requires React 19 as a peer dependency. Use **react-leaflet v4.2.x** with React 18. Do not upgrade to v5 without first migrating to React 19. |
### Styling
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| Tailwind CSS | 3 | **3.4.x** | HIGH | Tailwind v4.0 released January 2025 with a full engine rewrite (Oxide/Rust), CSS-first configuration (no `tailwind.config.js`), and browser support limited to Safari 16.4+/Chrome 111+/Firefox 128+. **Tailwind v3 and v4 are not compatible side-by-side.** For this locked spec (v3): use **3.4.x** (latest 3.x). Advisory: shadcn/ui now defaults to Tailwind v4 + React 19 for new `init` commands; when adding components use the `v3` legacy path or lock the CLI: `npx shadcn@latest add` with the old registry. |
| shadcn/ui | Not versioned (copy-paste model) | Stable (generate components using `npx shadcn@latest add` with Tailwind v3 flag) | MEDIUM | shadcn/ui is not a versioned npm package — components are generated/copied. The CLI has bifurcated: new projects default to Tailwind v4 + React 19. For Tailwind v3 projects, the generated components still work but confirm the CLI is generating v3-compatible classes. Check the shadcn changelog for any Tailwind v3/v4 flag. Existing generated components do not need upgrading. |
### Testing
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| Vitest | Not pinned in spec | **3.x** (for Vite 5/6) or **4.x** (Vite 6 only) | HIGH | Vitest 4.x (latest: 4.1.5) requires Vite ≥ 6.0 and Node.js ≥ 20. Vitest 3.x supports both Vite 5 and 6. Match Vitest major to Vite major: Vite 5 → Vitest 3.x; Vite 6 → Vitest 3.x or 4.x. |
| React Testing Library | Not pinned | **16.x** | HIGH | Stable. Works with React 18. |
| Playwright | Not pinned | **1.59.x** | HIGH | Latest stable: 1.59.1 (April 29, 2026). Actively maintained. New in 2025-2026: `page.screencast` API, `browser.bind()` API. No breaking changes for standard use. |
## Infrastructure Stack
| Technology | Locked Version | 2026 Recommended Pin | Confidence | Status / Gotcha |
|------------|---------------|----------------------|------------|-----------------|
| Docker Compose | v2 | **v2.x** (bundled with Docker Desktop 4.x) | HIGH | Docker Compose v2 is stable and included with Docker Desktop. The standalone `docker-compose` v1 binary (Python) is fully removed from Docker Desktop 4.x. Use `docker compose` (plugin syntax). |
| GitHub Actions | N/A | Current runners: `ubuntu-24.04` | HIGH | `ubuntu-22.04` is still available but `ubuntu-24.04` is now the recommended runner. Use `actions/setup-java@v4` for Java 21, `actions/setup-node@v4` for Node 20. Pin action versions by commit SHA for security. |
| GHCR | N/A | Stable | HIGH | No changes. Free for public repositories. |
## Consolidated Version Pins (Gradle libs.versions.toml)
# Java runtime
# Spring
# Persistence
# JWT
# Resilience / Observability
# Testing (backend)
# Frontend (package.json)
## Alternatives Considered
| Category | Locked Choice | 2026 Fresh Alternative | Why Not (for this project) |
|----------|--------------|------------------------|---------------------------|
| Spring Boot | 3.3.x (upgrade to 3.5.x) | Spring Boot 4.0.x | Requires Spring Framework 7; significant migration effort; Spring 6/3.5 remains supported until June 2026 |
| Service discovery | Eureka | Consul, Kubernetes DNS | Consul adds config management complexity; K8s DNS requires cluster; Eureka sufficient for portfolio monorepo |
| JWT auth | jjwt + custom | Spring Security's OAuth2 Resource Server (Nimbus JOSE) | Nimbus is already on classpath; eliminates external dep; ADR-5 chose custom for portfolio simplicity — valid choice |
| Redis | 7.x | Redis 8.x (GA May 2025) | No v1 feature needs Vector Set or built-in JSON; licensing is now clearer (AGPLv3 available) but v8 adds ops complexity |
| React | 18 | React 19 | react-leaflet v5 requires React 19; dnd-kit new API pre-1.0; Tailwind v3 + React 19 shadcn path is still maturing — too many moving parts for this portfolio build |
| Tailwind | v3 | Tailwind v4 | CSS-first config incompatible with v3 config; shadcn CLI defaults to v4 for new projects but v3 path still works; v4 browser support baseline (Safari 16.4+) drops ~2% of users |
| Zod | v3 | Zod v4 | v4 has significant API breaks (z.email(), z.uuid(), z.record() changes); worth planning as a Phase 5+ upgrade once core forms are stable |
| React Router | v6 | React Router v7 (stable 7.14.2) | v7 simplifies imports (no react-router-dom), adds data loaders; upgrade from v6 is non-breaking if future flags used; greenfield projects should prefer v7 |
| Vite | v5 | Vite v6 | Vite 6 breaks: Sass API default, build target name, Runtime API. Migration is "straightforward" per official guide; greenfield projects should prefer Vite 6 |
## Critical Flags for Phase Researchers
### Flags that affect Phase 0 (scaffold)
- **Spring Boot version:** Start on `3.5.x`, not `3.3.x`. The spec is out of date on this point.
- **Spring Cloud train:** Use `2024.0.x` to match SB 3.5.
- **Flyway + PostgreSQL:** Explicitly add `flyway-database-postgresql` dependency — it is not pulled transitively in Flyway 10.
- **react-leaflet:** Pin `v4.2.x`, not `v5`. v5 requires React 19.
- **dnd-kit:** Use `@dnd-kit/core` 6.x + `@dnd-kit/sortable`, NOT `@dnd-kit/react`.
- **Axios:** Must be `>= 1.15.0` for active CVE coverage.
### Flags that affect Phase 2+ (auth)
- **jjwt:** Upgrade to `0.13.0` (latest stable). The withdrawn CVE-2024-31033 does not require action, but `0.13.0` includes a BouncyCastle upgrade and decompression leak fix.
- **Spring Cloud Gateway CVEs:** Resolved by upgrading to Spring Cloud 2024.0 train (Gateway 4.2.x).
### Flags that affect Phase 5+ (forms / validation)
- **Zod v4 advisory:** Plan a migration from Zod v3 to v4 — the `z.string().email()` → `z.email()` rename and `z.record()` two-argument requirement are the highest-impact changes. The community codemod `zod-v3-to-v4` exists.
### Flags that affect Phase 9+ (performance)
- **Virtual threads:** Enable with `spring.threads.virtual.enabled=true`. Available since Spring Boot 3.2. Recommended for I/O-heavy services (destination-service makes external HTTP calls; this is the primary beneficiary).
## Sources
- [Spring Boot EOL dates — endoflife.date](https://endoflife.date/spring-boot) — Spring Boot version support matrix, confirmed 3.3 EOL June 2025, 3.5 current
- [Spring Boot 3.5 available now — spring.io](https://spring.io/blog/2025/05/22/spring-boot-3-5-0-available-now/) — 3.5 release announcement
- [HeroDevs — Spring Boot versions April 2026](https://www.herodevs.com/blog-posts/spring-boot-versions-eol-dates-and-latest-releases-april-2026) — Version support table, 4.0.5 latest as of March 2026
- [Spring Cloud 2025.0 Release Notes](https://github.com/spring-cloud/spring-cloud-release/wiki/Spring-Cloud-2025.0-Release-Notes) — Gateway module rename, new CVE mitigations
- [Spring Cloud Gateway CVE-2025-41235 / CVE-2025-41253](https://github.com/spring-cloud/spring-cloud-gateway/releases) — Security fixes in 4.2.x
- [jjwt Releases — GitHub](https://github.com/jwtk/jjwt/releases) — 0.13.0 is latest (Aug 2023); CVE-2024-31033 withdrawn
- [Flyway PostgreSQL modularization issue — GitHub](https://github.com/flyway/flyway/issues/3969) — "Unsupported Database: PostgreSQL 16.x" in Flyway 10.x without explicit driver dep
- [Resilience4j 2.4.0 — Maven Central](https://central.sonatype.com/artifact/io.github.resilience4j/resilience4j-spring-boot3) — Latest version, Spring Boot 3 module
- [Redis 8 GA announcement — redis.io](https://redis.io/blog/redis-8-ga/) — Features, licensing (AGPLv3 added)
- [React v19 stable — react.dev](https://react.dev/blog/2024/12/05/react-19) — React 19 release December 2024
- [TanStack Query v5 releases — GitHub](https://github.com/tanstack/query/releases) — v5.100.6 latest
- [Vite 6.0 announcement — vite.dev](https://vite.dev/blog/announcing-vite6) — Nov 2024 release, migration notes
- [Vitest releases — GitHub](https://github.com/vitest-dev/vitest/releases) — 4.1.5 latest; 4.x requires Vite 6+
- [Tailwind CSS v4.0 — tailwindcss.com](https://tailwindcss.com/blog/tailwindcss-v4) — Jan 2025, Oxide engine, CSS-first config
- [shadcn/ui Tailwind v4 docs](https://ui.shadcn.com/docs/tailwind-v4) — Tailwind v4 + React 19 support, v3 backward compat path
- [Zod v4 release notes — zod.dev](https://zod.dev/v4) — Breaking changes catalog
- [Axios CVE advisory — herodevs.com](https://www.herodevs.com/blog-posts/axios-versions-cves-and-safe-upgrade-path-updated-april-2026) — CVE-2025-62718, CVE-2026-40175 in Axios < 1.15.0
- [Playwright 1.59.1 — playwright.dev](https://playwright.dev/docs/release-notes) — April 2026 latest
- [react-leaflet releases — GitHub](https://github.com/PaulLeCam/react-leaflet/releases) — v5 requires React 19; v4 for React 18
- [dnd-kit/react 0.4.0 — npm](https://www.npmjs.com/package/@dnd-kit/react) — Pre-1.0 experimental package; use @dnd-kit/core 6.x instead
- [Zustand v5 announcement — pmnd.rs](https://pmnd.rs/blog/announcing-zustand-v5/) — Drops React < 18, uses native useSyncExternalStore
- [React Router 7 releases — GitHub](https://github.com/remix-run/react-router/releases) — 7.14.2 latest stable
- [TypeScript 6.0 announcement](https://devblogs.microsoft.com/typescript/announcing-typescript-5-8/) — TS 6.0 March 2026; TS 5.8.x recommended for this project
- [WireMock Spring Boot 3.9.0 — GitHub](https://github.com/wiremock/wiremock-spring-boot/releases) — Official Spring Boot integration
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
