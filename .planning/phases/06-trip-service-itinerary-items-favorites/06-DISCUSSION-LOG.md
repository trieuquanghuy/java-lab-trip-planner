# Phase 6: Trip Service — Itinerary Items + Favorites - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 06-trip-service-itinerary-items-favorites
**Areas discussed:** Position & Reorder Logic, Note Sanitization, Favorites Architecture, Cover Image Logic, Item Add & Cross-Day Move, Error Codes & Response Shapes

---

## Position & Reorder Logic

| Option | Description | Selected |
|--------|-------------|----------|
| Keep INT | Project scale doesn't need BIGINT, INT handles millions of reorders | ✓ |
| Migrate to BIGINT | V5 ALTER COLUMN, pushes overflow further out | |

| Option | Description | Selected |
|--------|-------------|----------|
| (a) Proactive reindex | Reindex entire day in same transaction when gap < 2, then insert | ✓ |
| (b) Error and retry | Return error, let frontend retry after background reindex | |

**User's choice:** Keep INT, proactive reindex (a), SELECT FOR UPDATE on day row
**Notes:** User stated project is not big enough to need BIGINT. Concurrency via SELECT FOR UPDATE on day row accepted for portfolio scale.

---

## Note Sanitization (XSS Prevention)

| Option | Description | Selected |
|--------|-------------|----------|
| Jsoup Safelist.none() | Strip all HTML, plain text only | ✓ |
| OWASP Java HTML Sanitizer | Whitelist-based, allows safe subset | |
| Custom regex | Fragile, bypassable | |

| Option | Description | Selected |
|--------|-------------|----------|
| Strip ALL HTML | Notes are plain text only | ✓ |
| Allow safe subset | Bold, italic, links permitted | |

**User's choice:** Jsoup with Safelist.none(), strip all HTML
**Notes:** Meets success criteria exactly. No rendering complexity on frontend.

---

## Favorites Architecture

| Option | Description | Selected |
|--------|-------------|----------|
| Separate controller/service | Favorites are user-level, not trip-scoped | ✓ |
| Bundle into TripController | All trip-service logic in one controller | |

| Option | Description | Selected |
|--------|-------------|----------|
| No special endpoint | Frontend calls existing add-item endpoint | ✓ |
| Special "add from favorites" | Dedicated endpoint for the flow | |

| Option | Description | Selected |
|--------|-------------|----------|
| Idempotent 200 on re-favorite | On conflict return existing row | ✓ |
| Error on duplicate | Return 409 on re-favorite | |

**User's choice:** Separate FavoriteController/FavoriteService, no special endpoint, idempotent 200
**Notes:** None

---

## Cover Image Logic

| Option | Description | Selected |
|--------|-------------|----------|
| First by (day_index, position) | First item with non-null photo across all days | ✓ |
| First added (createdAt) | Chronologically first item with photo | |

| Option | Description | Selected |
|--------|-------------|----------|
| (a) Compute on read | Query for first item's photo when cover_image_url is null | ✓ |
| (b) Cache on write | Store computed cover on trip row when items change | |

| Option | Description | Selected |
|--------|-------------|----------|
| (b) Denormalize photo_url | Add optional column to itinerary_items, frontend sends at add-time | ✓ |
| (a) Cross-service call | Call destination-service to resolve photo | |

**User's choice:** Agent's recommendation — first by day_index+position, computed on read, denormalized photo_url
**Notes:** User deferred to agent. Avoids cross-service coupling and cache invalidation complexity.

---

## Item Add & Cross-Day Move

| Option | Description | Selected |
|--------|-------------|----------|
| Accept any non-blank string | No cross-service validation of destinationRef | ✓ |
| Validate against destination-service | Cross-service call to verify ref exists | |

| Option | Description | Selected |
|--------|-------------|----------|
| Frontend-specified position | PATCH body contains exact position; null defaults to append | ✓ |
| Always append | Cross-day move always appends to end of target day | |

| Option | Description | Selected |
|--------|-------------|----------|
| Allow duplicates | No unique constraint on (day_id, destination_ref) | ✓ |
| Prevent duplicates | Unique constraint, error on duplicate | |

**User's choice:** All recommended options confirmed
**Notes:** None

---

## Error Codes & Response Shapes

| Option | Description | Selected |
|--------|-------------|----------|
| Full item on response | 201 (add) and 200 (patch) return full item object | ✓ |
| ID only | Return just the created/updated ID | |

| Option | Description | Selected |
|--------|-------------|----------|
| Items nested in trip detail | GET /api/trips/{id} includes items[] inside each day | ✓ |
| Items separate endpoint | Separate GET /api/trips/{id}/days/{dayId}/items | |

| Option | Description | Selected |
|--------|-------------|----------|
| Simple unpaginated list | GET /api/favorites returns all favorites | ✓ |
| Paginated | Standard page/size pagination | |

**User's choice:** All recommended options confirmed
**Notes:** None

---

## Agent's Discretion

- Flyway migration numbering (V5, V6, etc.)
- Jsoup version selection
- Internal method signatures and naming
- Test structure (follow Phase 5 patterns)

## Deferred Ideas

None — discussion stayed within phase scope.
