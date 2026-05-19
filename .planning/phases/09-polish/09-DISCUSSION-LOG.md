# Phase 9: Polish - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-19
**Phase:** 09-polish
**Areas discussed:** Error Boundaries, Loading/Empty States, Accessibility, Mobile Responsive, Performance
**Mode:** --auto (all decisions auto-selected with recommended defaults)

---

## Error Boundaries & Error Pages

| Option | Description | Selected |
|--------|-------------|----------|
| Per-route ErrorBoundary | Each route wrapped independently; crash isolated to single page | ✓ |
| Global ErrorBoundary only | Single boundary at app root; any crash shows full-page error | |
| No ErrorBoundary (rely on query error states) | Only handle data-fetch errors, let React crashes bubble | |

**Auto-selected:** Per-route ErrorBoundary (recommended — isolates failures, better UX)
**Notes:** Uses `react-error-boundary` library. Matches pattern from established React apps.

---

## Loading States & Empty States

| Option | Description | Selected |
|--------|-------------|----------|
| Audit and fill gaps | Check each page for missing skeletons/empty states and add them | ✓ |
| Add generic spinner fallback | Single shared loading spinner for all pages | |
| Accept current coverage as sufficient | Most pages already have skeletons | |

**Auto-selected:** Audit and fill gaps (recommended — success criteria requires "every list and detail page")
**Notes:** DestinationDetailPage and TripDetailPage header sections may lack skeletons.

---

## Accessibility (a11y)

| Option | Description | Selected |
|--------|-------------|----------|
| Full WCAG AA audit + fix | Run axe DevTools, fix all violations, add skip nav, verify focus order | ✓ |
| Minimal a11y (only keyboard navigation) | Just ensure Tab works everywhere | |
| Defer to Phase 10 | Not a priority for polish phase | |

**Auto-selected:** Full WCAG AA audit + fix (recommended — SC#2 and SC#5 require this)
**Notes:** DayTabs and ItineraryItemCard already have good aria attributes. Focus on forms and navigation.

---

## Mobile Responsive

| Option | Description | Selected |
|--------|-------------|----------|
| 360px viewport verification + fix | Test every page at 360px, fix overflow/clipping | ✓ |
| 768px minimum only | Only verify tablet and up | |
| Rely on existing responsive classes | Tailwind responsive prefixes already handle it | |

**Auto-selected:** 360px viewport verification + fix (recommended — SC#3 requires specifically 360px)
**Notes:** Most grids use `grid-cols-1` at mobile. Need to verify forms, navbars, and detail pages.

---

## Performance

| Option | Description | Selected |
|--------|-------------|----------|
| Route-level code splitting + lazy loading | React.lazy() for all route pages, Suspense fallback | ✓ |
| No code splitting (bundle is small enough) | Single bundle, rely on Vite tree-shaking | |
| Full optimization (splitting + preloading + service worker) | Aggressive optimization with prefetch hints | |

**Auto-selected:** Route-level code splitting + lazy loading (recommended — standard practice, helps Lighthouse)
**Notes:** Vite handles chunk splitting automatically with dynamic imports.

---

## Agent's Discretion

- Skeleton animation style (continue using `animate-pulse`)
- Error illustration choice
- Whether to add `prefers-reduced-motion`

## Deferred Ideas

None — all items stay within phase scope.
