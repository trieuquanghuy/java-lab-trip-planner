# Phase 11: Favorites Page - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-21
**Phase:** 11-favorites-page
**Areas discussed:** Data Enrichment, Card Presentation, Unfavorite UX, Sort & Empty State

---

## Data Enrichment

| Option | Description | Selected |
|--------|-------------|----------|
| Client-side enrichment | N+1 fetches per favorite, no backend changes | |
| New batch endpoint | POST /api/destinations/batch, one request for all | ✓ |
| Denormalize at write time | Store name/photo in favorites table | |

**User's choice:** New batch endpoint
**Notes:** Cleaner frontend, single request regardless of favorites count.

**Follow-up: Response shape**

| Option | Description | Selected |
|--------|-------------|----------|
| Light summary | name, category, rating, photoUrl, lat, lng | ✓ |
| Full detail | Reuse DestinationDetailResponse | |

**User's choice:** Light summary — only what the card needs.

---

## Card Presentation

| Option | Description | Selected |
|--------|-------------|----------|
| Wrap with overlay | DestinationCard unchanged, wrapper adds unfavorite button | ✓ |
| Extend with prop | Add onRemove prop to DestinationCard | |
| New FavoriteCard | Distinct layout for favorites | |

**User's choice:** Wrap with overlay (composition approach)

---

## Unfavorite UX

| Option | Description | Selected |
|--------|-------------|----------|
| Optimistic removal | Instant disappear, rollback on error | ✓ |
| Wait-for-server | Spinner, then remove | |
| Optimistic with undo | Instant disappear + 5s undo toast | |

**User's choice:** Optimistic removal

**Follow-up: Animation**

| Option | Description | Selected |
|--------|-------------|----------|
| Brief fade-out | ~200ms opacity transition | ✓ |
| Instant removal | No animation | |

**User's choice:** Brief fade-out (~200ms opacity transition)

---

## Sort & Empty State

**Sorting:**

| Option | Description | Selected |
|--------|-------------|----------|
| Newest first only | No sort controls | ✓ |
| Sort dropdown | Newest/Oldest/Alphabetical | |

**User's choice:** Newest first only

**Empty state:**

| Option | Description | Selected |
|--------|-------------|----------|
| Keep as-is | Heart icon + message + CTA | ✓ |
| Add illustration | More detailed SVG | |

**User's choice:** Keep as-is

---

## Agent's Discretion

- Loading skeleton count/layout
- Toast component for error feedback
- Unfavorite button styling details
- Whether to show "Saved on" date on cards

## Deferred Ideas

None
