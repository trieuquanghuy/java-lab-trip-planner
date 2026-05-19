# Phase 9: Polish - Context

**Gathered:** 2026-05-19
**Status:** Ready for planning

<domain>
## Phase Boundary

This phase delivers production-quality polish: error boundaries, complete loading/empty states, a11y compliance (axe DevTools 0 violations), mobile-responsive verification (360px), and Lighthouse ≥90 performance / ≥95 accessibility. No new features — only hardening what Phases 7–8 built.

</domain>

<decisions>
## Implementation Decisions

### Error Boundaries & Error Pages
- **D-01:** React ErrorBoundary wraps each route (per-page granularity) — a single route crashing doesn't take down the whole app. Use `react-error-boundary` library (lightweight, widely adopted).
- **D-02:** Fallback UI shows a friendly illustration + "Something went wrong" message + "Try again" button that calls `resetErrorBoundary()`. No stack traces visible to users.
- **D-03:** Generic 500-style error page at `/error` route for server errors caught by Axios interceptor. Style matches existing NotFoundPage (centered, illustration, CTA to go home).
- **D-04:** Query error states handled per-component with inline "Failed to load" + "Retry" button (using TanStack Query's `isError` + `refetch()`).

### Loading States & Empty States Audit
- **D-05:** Every page that fetches data MUST show skeleton loading state. Audit and fill gaps: DestinationDetailPage, TripDetailPage (partial — only itinerary has loading), VerifyEmailPage.
- **D-06:** Empty states with actionable CTAs for all list views. Existing: DestinationList ("No attractions found"), TripEmptyState ("Add your first stop"), TripsPage (has empty state). Verify all are present.
- **D-07:** Loading skeletons match the shape of content they replace (content-aware skeletons, not generic bars).

### Accessibility (a11y)
- **D-08:** All interactive elements reachable by Tab key. Focus order follows visual layout (no `tabindex > 0`).
- **D-09:** All images have `alt` text (decorative images use `alt=""`). All icon buttons have `aria-label`.
- **D-10:** Form inputs have associated `<label>` elements or `aria-label`. Error messages linked via `aria-describedby`.
- **D-11:** Color contrast meets WCAG AA (4.5:1 for text, 3:1 for large text). Tailwind defaults satisfy this with the current palette.
- **D-12:** Skip navigation link as first focusable element on the page ("Skip to main content").
- **D-13:** Announce route changes to screen readers (use `aria-live` region or react-router's built-in announcements).

### Mobile Responsive (360px)
- **D-14:** All pages verified at 360px viewport. No horizontal overflow, no clipped text, no unreachable controls.
- **D-15:** Touch targets minimum 44x44px for all buttons and interactive elements on mobile.
- **D-16:** Navigation collapses to hamburger menu or simplified layout on mobile (currently the Layout component needs verification).
- **D-17:** Photo carousels and cards stack vertically on mobile — no horizontal scroll unless intentional (day tabs).

### Performance
- **D-18:** Code-split routes with `React.lazy()` + `Suspense` for all page components. Only HomePage loads eagerly.
- **D-19:** Images use `loading="lazy"` attribute (already present on DestinationCard). Verify all image usages.
- **D-20:** Bundle analysis to ensure no oversized chunks. Target: no single chunk > 200KB gzipped.

### Agent's Discretion
- Exact skeleton dimensions and animation style (existing `animate-pulse` pattern is fine)
- Error boundary illustration choice (simple icon or SVG illustration)
- Specific axe rule exceptions if any are false positives (document with `// eslint-disable` comment explaining why)
- Whether to add `prefers-reduced-motion` media query for animations

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Design & UX
- `docs/06-frontend-design.md` — Frontend design spec with component patterns and responsive requirements
- `docs/01-prd.md` — Product requirements including NFR-07 (accessibility) and NFR-08 (mobile-responsive)

### Prior Phase Context
- `.planning/phases/07-frontend-auth-discovery/07-CONTEXT.md` — Auth/discovery UI decisions (D-01 through D-16)
- `.planning/phases/08-frontend-trip-planner/08-CONTEXT.md` — Trip planner UI decisions (layout, drag-drop, map)

### Accessibility Standards
- WCAG 2.1 AA compliance (external standard — axe DevTools implements this)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `components/ui/skeleton.tsx` — Skeleton component with `animate-pulse` pattern (used throughout)
- `components/ui/button.tsx` — Button component with multiple variants
- `pages/NotFoundPage.tsx` — Existing 404 page pattern to mirror for error pages
- `features/destinations/DestinationCardSkeleton.tsx` — Content-aware skeleton example
- `features/trips/TripCardSkeleton.tsx` — Another content-aware skeleton

### Established Patterns
- Loading states: `isLoading` from TanStack Query → show Skeleton components
- Empty states: Dedicated component with illustration + CTA (TripEmptyState pattern)
- Responsive grids: `grid-cols-1 sm:grid-cols-2 lg:grid-cols-3` pattern (DestinationList, TripsPage)
- Accessibility: `aria-label`, `role`, `aria-selected` used in DayTabs and ItineraryItemCard

### Integration Points
- `App.tsx` — Route definitions; wrap with ErrorBoundary here
- `components/Layout.tsx` — Global layout; add skip nav link here
- `lib/axios.ts` — Axios interceptor; add global error redirect for 500s
- `lib/queryClient.ts` — TanStack Query client; configure global error handler

</code_context>

<specifics>
## Specific Ideas

- Error boundary style should match the clean, minimal aesthetic of NotFoundPage
- Lighthouse audit should be run against `vite preview` (production build) not dev server
- axe DevTools audit targets: HomePage and TripDetailPage (per success criteria)
- Console error check: ensure no React warnings (missing keys, deprecated APIs) in production build

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. Phase 10 (Observability + Performance Hardening) handles backend performance, tracing, and security audit.

</deferred>

---

*Phase: 09-polish*
*Context gathered: 2026-05-19*
