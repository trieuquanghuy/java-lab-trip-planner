# Phase 7: Frontend — Auth + Discovery - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-18
**Phase:** 07-frontend-auth-discovery
**Areas discussed:** Search UX & Layout, Auth Flow UX, Destination Detail Presentation, Deferred Login Intent

---

## Search UX & Layout

| Option | Description | Selected |
|--------|-------------|----------|
| Hero search | Large centered search bar, dropdown suggestions, results inline below | ✓ |
| Persistent top bar | Search in header/nav, home has other content | |
| Single-page flow | Search at top, results inline, no page transition | (merged into Hero) |

**Display format:**

| Option | Description | Selected |
|--------|-------------|----------|
| Card grid (responsive) | 1/2/3 columns by breakpoint, thumbnail + name + category + rating | ✓ |
| Vertical list | Single column, more detail per item | |

**User's choice:** Delegated all decisions to agent
**Notes:** Agent chose hero search for strong portfolio first-impression, card grid for visual appeal

---

## Auth Flow UX

| Option | Description | Selected |
|--------|-------------|----------|
| Separate pages | /login, /signup as full routes | ✓ |
| Modal dialogs | Auth forms in overlay dialogs | |

**Post-signup flow:**

| Option | Description | Selected |
|--------|-------------|----------|
| "Check your email" page | Clear instructions, wait for verification | ✓ |
| Inline message on signup form | Less obvious | |

**Post-verification flow:**

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-redirect to /login + success toast | Smooth, confirms verification worked | ✓ |
| Success page with manual "Go to login" link | Extra click | |

**User's choice:** Delegated all decisions to agent
**Notes:** Agent chose separate pages for cleaner URLs and portfolio demo flow

---

## Destination Detail Presentation

| Option | Description | Selected |
|--------|-------------|----------|
| Full routed page | /destinations/:providerRef, room for photos + info + CTA | ✓ |
| Dialog/drawer overlay | Less URL-friendly, cramped | |

**Photo carousel:**

| Option | Description | Selected |
|--------|-------------|----------|
| Horizontal scroll + dots | Native feel, supports swipe on mobile | ✓ |
| Lightbox gallery | Heavier, less inline | |

**User's choice:** Delegated all decisions to agent
**Notes:** Full page chosen for portfolio depth; placeholder gradients for missing photos

---

## Deferred Login Intent (TRIP-05)

| Option | Description | Selected |
|--------|-------------|----------|
| Tooltip/popover with "Log in" button | Non-jarring, explains why, user initiates navigation | ✓ |
| Immediate redirect to /login | Abrupt, loses context visually | |

**Post-login with context:**

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-navigate + auto-open AddToTrip dialog | Seamless intent completion | ✓ |
| Navigate back, user clicks "Add to Trip" again | Extra step, friction | |

**User's choice:** Delegated all decisions to agent
**Notes:** Tooltip approach is less jarring; auto-completing the intent after login provides seamless UX

---

## Agent's Discretion

- Loading skeletons, error boundaries, empty states, toast library (Sonner via shadcn)
- All standard patterns — no user input needed

## Deferred Ideas

None
