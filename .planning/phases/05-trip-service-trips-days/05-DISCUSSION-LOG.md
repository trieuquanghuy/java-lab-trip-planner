# Phase 5: Trip Service — Trips + Days - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-15
**Phase:** 05-trip-service-trips-days
**Areas discussed:** Day materialization transaction, Trip API contract, Ownership enforcement, Empty state & error responses

---

## Day Materialization Transaction

| Option | Description | Selected |
|--------|-------------|----------|
| Separate bean (`DayMaterializationService`) | Avoids self-invocation proxy pitfall, explicit transaction boundary | ✓ |
| Self-injection (`@Lazy` self-reference) | Works but is a code smell, harder to test |  |
| `TransactionTemplate` programmatic TX | More control but more boilerplate |  |

**User's choice:** Agent auto-selected — separate bean (recommended by ROADMAP notes)
**Notes:** ROADMAP explicitly warns about self-invocation pitfall. Separate bean is the cleanest solution.

---

## Trip API Contract

| Option | Description | Selected |
|--------|-------------|----------|
| Nested resources (`/api/trips/{id}/days`) | RESTful, clear hierarchy | |
| Embedded days in trip detail | Fewer requests, days always returned with trip | ✓ |
| Flat endpoints with query params | Simpler routing but less RESTful | |

**User's choice:** Agent auto-selected — embedded days in trip detail response (days are always needed when viewing a trip)
**Notes:** PATCH for updates (not PUT). `confirmShorten` as query param on PATCH. Pagination for list with Spring Data Page convention.

---

## Ownership Enforcement

| Option | Description | Selected |
|--------|-------------|----------|
| Service-layer explicit check | Simple, follows destination-service pattern | ✓ |
| `@PreAuthorize` with SpEL | Declarative but adds complexity | |
| Repository-level `@Query` filter | Implicit, harder to debug | |

**User's choice:** Agent auto-selected — service-layer check (consistent with existing codebase pattern)
**Notes:** Every repository query includes `userId` filter. Missing match → 404 not 403.

---

## Empty State & Error Responses

| Option | Description | Selected |
|--------|-------------|----------|
| Standard paginated empty response | Consistent with Spring Data conventions | ✓ |
| Custom empty state body | More frontend-friendly but non-standard | |

**User's choice:** Agent auto-selected — standard paginated response with `content: []`
**Notes:** New `ErrorCode` entries: TRIP_NOT_FOUND, TRIP_SHORTEN_CONFLICT, TRIP_INVALID_DATES. 409 body includes orphanedDays array for frontend confirmation dialog.

---

## Agent's Discretion

- Entity naming, JPA mapping details
- Flyway migration numbering
- Test structure and naming
- `@ControllerAdvice` vs in-controller exception handling (recommended: `@ControllerAdvice`)

## Deferred Ideas

None
