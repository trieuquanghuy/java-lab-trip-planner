# Phase 7: Frontend — Auth + Discovery - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can search for destinations and view their details in the browser; auth pages (signup, verify, login) work end-to-end including the deferred "Add to Trip" intent flow. This phase delivers the first interactive frontend — routing, pages, API integration, auth state management, and the search-to-detail flow.

</domain>

<decisions>
## Implementation Decisions

### Search UX & Layout
- **D-01:** Hero search on home page — large centered search input with clean landing page (strong portfolio first-impression)
- **D-02:** City suggestions appear as a dropdown overlay (max 5 results) with 250ms debounce
- **D-03:** Selecting a city renders destination cards inline below the search (single-page flow, no route transition for results)
- **D-04:** Destination cards in a responsive grid: 1 col mobile, 2 col tablet, 3 col desktop. Each card shows: thumbnail (or placeholder gradient), name, category badge, star rating

### Auth Flow UX
- **D-05:** Login and signup are separate routed pages (`/login`, `/signup`) — not modals
- **D-06:** After signup, redirect to a "Check your email" instructional page
- **D-07:** After clicking verification link, auto-redirect to `/login` with a success toast ("Email verified — please log in")
- **D-08:** Form validation errors shown inline per-field; network/server errors shown as toast notifications
- **D-09:** Minimal auth forms: email + password only (no social login in v1)

### Destination Detail Presentation
- **D-10:** Destination detail is a full routed page (`/destinations/:providerRef`), not a dialog/drawer
- **D-11:** Photo carousel: horizontal scroll with dots indicator; placeholder gradient card when no photos exist
- **D-12:** Missing data handled gracefully: "Opening hours not available" explicit text, "No description available" in muted text, placeholder image with travel-themed gradient
- **D-13:** "Add to Trip" CTA button is prominent on desktop, sticky-bottom on mobile

### Deferred Login Intent (TRIP-05)
- **D-14:** When logged-out user clicks "Add to Trip": show a tooltip/popover ("Log in to add destinations to your trip") with a "Log in" button — no immediate redirect
- **D-15:** After login completes with `addToTripContext` stored in Zustand: auto-navigate back to the destination page AND auto-open the AddToTrip dialog to complete the intent seamlessly
- **D-16:** If no deferred context exists after login: navigate to `/trips` (trip list page)

### Agent's Discretion
- Loading states: skeleton cards during search/destination loading (shimmer effect)
- Error boundaries: generic "Something went wrong" with retry button per section
- Empty states: "No destinations found" with a subtle illustration or icon
- Toast library: use shadcn/ui's built-in toast (Sonner)
- Form library: React Hook Form + Zod as specified in `docs/06-frontend-design.md`

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Frontend Architecture
- `docs/06-frontend-design.md` — Folder layout, routing structure, state management strategy, API client pattern, all user flows (search, add-to-trip, drag-drop)

### Auth & Security
- `docs/05-auth-security.md` — JWT flow, refresh token rotation, session lifecycle
- `.planning/research/PITFALLS.md` §Pitfall 9 — Axios 401 retry loop prevention: `isRefreshing` flag + `failedQueue` pattern with full TypeScript implementation

### Backend API Contracts
- `libs/api-contracts/src/main/` — Request/response DTOs that frontend types must mirror
- `services/auth-service/src/main/java/com/tripplanner/auth/api/` — Auth endpoint contracts (signup, login, refresh, verify)
- `services/destination-service/src/main/java/com/tripplanner/destination/api/` — Search and destination detail endpoint contracts

### Stack Constraints (from ROADMAP.md notes)
- react-leaflet pinned to **4.2.x** (v5 requires React 19)
- Axios **≥1.16.0** (CVE-2025-62718, CVE-2026-40175)
- `@dnd-kit/core` **6.x** + `@dnd-kit/sortable` (NOT `@dnd-kit/react`)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `src/lib/axios.ts` — apiClient with `withCredentials: true` and X-Request-Id interceptor (Phase 7 wires the 401 refresh flow here)
- `src/lib/queryClient.ts` — TanStack Query configured (`staleTime: 30s`, `retry: 1`) — needs `retry: false` override for auth queries
- `src/lib/store.ts` — Zustand store (currently only theme); auth store to be added
- `src/lib/utils.ts` — `cn()` classname utility (Tailwind merge)
- `components.json` — shadcn/ui configured and ready for component generation

### Established Patterns
- Tailwind 3.4 + shadcn/ui for all UI components
- Vite 6 + TypeScript 5.8 build toolchain
- pnpm as package manager
- Vitest + React Testing Library for unit tests

### Integration Points
- `src/App.tsx` — Currently a static shell; Phase 7 replaces with React Router `<Routes/>`
- `src/main.tsx` — Entry point; needs QueryClientProvider + BrowserRouter wrapping
- Backend runs on `localhost:8080` (VITE_API_URL env var)

</code_context>

<specifics>
## Specific Ideas

- All design decisions made by agent based on portfolio-optimized UX patterns
- Hero search layout inspired by modern travel apps (Airbnb, Google Travel)
- Single-page search flow avoids route thrashing for a smoother demo experience
- Sticky CTA on mobile ensures "Add to Trip" is always reachable on destination detail

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-frontend-auth-discovery*
*Context gathered: 2026-05-18*
