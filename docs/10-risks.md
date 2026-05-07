# 10 — Risk Register

**Status**: Draft for review
**Last updated**: 2026-05-08

Likelihood: L (low) / M (medium) / H (high). Impact: same scale.
Exposure = L × I (rough): low / medium / high.

## Engineering risks

| ID   | Risk                                                                         | L   | I   | Exposure | Mitigation                                                                                                                                                                                              |
| ---- | ---------------------------------------------------------------------------- | --- | --- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| R-1  | Microservices boilerplate slows progress; project never finishes             | M   | H   | High     | Shared `libs/` modules absorb common code (JWT, error handling, observability). Phase 0 includes the boilerplate so subsequent phases benefit. If Phase 0 takes > 1 week, drop to monolith and re-plan. |
| R-2  | Distributed tracing / log correlation hard to set up                         | M   | M   | Medium   | Add Zipkin from Phase 0. Use Spring Boot starters that wire Micrometer Tracing automatically. Validate trace propagation manually at end of Phase 1.                                                    |
| R-3  | OpenTripMap or Foursquare free-tier rate limit hit during dev                | H   | M   | Medium   | Aggressive caching (24h `destinations_cache` TTL, 1h Redis search cache). Stub provider in dev with `WIREMOCK` profile.                                                                                 |
| R-4  | OpenTripMap or Foursquare API contract drift                                 | M   | M   | Medium   | WireMock stubs based on real captured responses; nightly contract diff job (deferred but scheduled in v2). Defensive deserialization (skip unknown fields).                                             |
| R-5  | NFR-1 (search ≤500 ms p95) not met                                           | L   | M   | Low      | Three-tier cache (Redis + DB FTS + provider). Indexed `cities.search_tsv` GIN. Phase 3 includes a synthetic perf assertion.                                                                             |
| R-6  | Postgres FTS quality poor for non-Latin scripts                              | M   | L   | Low      | `unaccent` extension covers diacritics. For CJK, fall back to `ILIKE` prefix. Acceptable for portfolio.                                                                                                 |
| R-7  | Drag-drop position-reindex bug corrupts ordering                             | L   | H   | Medium   | Position algorithm has unit tests. Lazy reindex (gap < 2) renumbers atomically in a single UPDATE. Integration test: 50 random reorders, assert no duplicates.                                          |
| R-8  | Email delivery in deployed env fails silently                                | L   | M   | Low      | Verification flow has explicit success/error UI. Resend offers a webhook for failures. v1 is local-only so this is deferred.                                                                            |
| R-9  | JWT secret leaked via .env in git                                            | L   | H   | Medium   | `.env` gitignored; only `.env.example` committed. Pre-commit hook scans for high-entropy strings (gitleaks).                                                                                            |
| R-10 | Session-fixation/CSRF on refresh-token cookie                                | L   | M   | Low      | `SameSite=Strict` + `HttpOnly` + `Secure` (in deployed). Origin validation on refresh endpoint.                                                                                                         |
| R-11 | Cross-user data leak through ID-guessing                                     | M   | H   | High     | Service-layer ownership filter on every endpoint. Mandatory security tests (5 of the 8) prove cross-user 404. CI gate.                                                                                  |
| R-12 | Stored XSS in note / cover-image-URL fields                                  | L   | H   | Medium   | Server-side OWASP HTML Sanitizer (allowlist empty). React escapes by default. CSP header from gateway. Test: store `<script>` tag, assert sanitized on read.                                            |
| R-13 | Dependency CVE in transitive library                                         | M   | M   | Medium   | Dependabot weekly. OWASP Dep-Check fails CI on CVSS ≥ 7. Gradle version catalog centralizes upgrades.                                                                                                   |
| R-14 | Eureka single instance fails locally                                         | M   | L   | Low      | Eureka clients have a 30-second cache; brief outages don't break running services. Documented runbook entry.                                                                                            |
| R-15 | Schema-per-service ownership gradually erodes (someone joins across schemas) | M   | M   | Medium   | Per-service DB users (`auth_svc`, `trip_svc`, `destination_svc`) only have `USAGE` on their own schema. Cross-schema query fails with permission error.                                                 |
| R-16 | Frontend bundle size grows past 250 KB target                                | M   | L   | Low      | Code-split per route; Leaflet and dnd-kit only loaded on TripDetailPage. Vite analyzer artifact in CI.                                                                                                  |
| R-17 | TanStack Query cache + Zustand auth state desync after logout                | M   | M   | Medium   | `clearSession()` calls `queryClient.clear()`. Component test verifies cache emptied on logout.                                                                                                          |

## Product risks

| ID | Risk | L | I | Exposure | Mitigation |
|----|------|---|---|----------|-----------|
| R-18 | Scope creep — v2 backlog leaks into v1 | H | M | High | Explicit roadmap with phase boundaries. Backlog docs exist precisely so items can be deferred without losing them. Self-discipline check: before adding mid-phase, ask "is this in the PRD?" |
| R-19 | Provider data quality is poor for some cities | M | L | Low | Foursquare enrichment fills gaps. Where photos/hours unavailable, UI gracefully shows placeholder (FR-4, FR-5). Acceptable for portfolio. |
| R-20 | "No mobile app" is unacceptable to portfolio reviewer | L | L | Low | Mobile-responsive web (NFR-8) covers basic mobile use. Native app is on v2 backlog. |

## Process / operational risks

| ID | Risk | L | I | Exposure | Mitigation |
|----|------|---|---|----------|-----------|
| R-21 | Local Docker stack uses too much memory | M | M | Medium | Infrastructure containers idle ~250 MB each; services ~250 MB each. Total ~2.5 GB. Documented prerequisite (4 GB Docker allocation, 16 GB system). If this is too much, profile and remove Zipkin / Eureka in dev. |
| R-22 | CI minutes exhausted on free tier | L | L | Low | Path filter in `backend.yml` rebuilds only changed services. E2E only on PR (not every push). |
| R-23 | Test flakiness on CI (timing-sensitive E2E) | M | M | Medium | Playwright auto-wait. `wait-on` for service readiness before tests start. Quarantine tag for any flaky test until fixed. |
| R-24 | Single-developer bus factor on a portfolio project | n/a | n/a | n/a | Acceptable; this is a portfolio project. |

## Unknowns flagged for review

These are open questions the spec deliberately does not answer; the reviewer
should weigh in:

| ID   | Question                                            | Default if unanswered                     |
| ---- | --------------------------------------------------- | ----------------------------------------- |
| OQ-1 | Cover image: URL-only or file upload in v1?         | URL-only                                  |
| OQ-2 | Country search: top attractions or force city pick? | Force city pick                           |
| OQ-3 | Item time slot: `LocalTime` vs `Instant`?           | `LocalTime`                               |
| OQ-4 | Email provider for v2: Resend, Postmark, SendGrid?  | Resend (best free tier in 2026)           |
| OQ-5 | Cloud target for v2: Fly.io, Railway, Render?       | Fly.io (best for multi-service free tier) |

## Risk review schedule

This register is reviewed at the start of each phase. New risks discovered
during a phase are added immediately. Risks that have materialized are moved
to a "Realized" section with the chosen response.
