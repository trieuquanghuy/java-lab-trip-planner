# Phase 12: Trip Duplication - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-26
**Phase:** 12-trip-duplication
**Areas discussed:** Button Placement, Post-Duplication Behavior, Copy Semantics, Naming & Dates

---

## Button Placement

| Option | Description | Selected |
|--------|-------------|----------|
| Trip detail page only | Button in trip detail header/toolbar | |
| Both list and detail | Context menu/icon on TripCard + button on detail page | ✓ |
| Trip list only | Action on TripCard via overflow menu/icon | |

**User's choice:** Agent's discretion — selected "Both" for maximum discoverability
**Notes:** Non-destructive action benefits from easy access in both contexts

---

## Post-Duplication Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Confirmation dialog | Ask user to confirm before duplicating | |
| Immediate navigation | No dialog, navigate to new trip + toast | ✓ |
| Toast with link | Stay on current page, show toast with link to new trip | |

**User's choice:** Agent's discretion — immediate navigation per ROADMAP success criteria #5
**Notes:** Duplication is non-destructive so confirmation is unnecessary friction

---

## Copy Semantics

| Option | Description | Selected |
|--------|-------------|----------|
| Full deep copy | Copy days, items, time slots, notes, cover image | ✓ |
| Structural skeleton | Copy days + destination refs only, no notes/times | |
| Selective copy | Let user choose what to copy via checkboxes | |

**User's choice:** Agent's discretion — full deep copy gives the best starting point
**Notes:** Purpose of duplication is to reuse a trip as a template

---

## Naming & Dates

| Option | Description | Selected |
|--------|-------------|----------|
| "Copy of {name}" + null dates | Simple prefix, truncate at 120 chars, no date prompt | ✓ |
| "Copy of {name}" + date wizard | Prefix name, prompt for new dates immediately | |
| Custom name prompt | Ask user for new trip name before duplicating | |

**User's choice:** Agent's discretion — simplest path per spec requirements
**Notes:** "Copy of Copy of..." is acceptable; user can rename later

---

## Agent's Discretion

- Backend endpoint design (POST /api/trips/{id}/duplicate)
- Error handling (404 if trip not found)
- Transactional atomicity for the deep copy
- Frontend mutation hook structure and cache invalidation

## Deferred Ideas

None
