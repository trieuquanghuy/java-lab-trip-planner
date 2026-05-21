# Phase 11: Favorites Page - Context

**Gathered:** 2026-05-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Ship the frontend Favorites page so users can view, manage, and navigate favorited destinations. Includes a small backend addition (batch endpoint on destination-service) to support rich card display.

</domain>

<decisions>
## Implementation Decisions

### Data Enrichment
- **D-01:** Add a new batch endpoint `POST /api/destinations/batch` on destination-service. Accepts `{ "refs": ["ref1", "ref2", ...] }` and returns destination summaries in one call. Frontend calls this after loading favorites to enrich the bare `destinationRef` values with display data.
- **D-02:** Batch endpoint returns a light summary per destination: `{ providerRef, name, category, rating, photoUrl, lat, lng }`. Not the full detail response — only what the card needs.

### Card Presentation
- **D-03:** Reuse existing `DestinationCard` component unchanged. Wrap each card in a container that adds an unfavorite button via absolute positioning (top-right corner). Composition over modification — keeps DestinationCard stable for search results.

### Unfavorite UX
- **D-04:** Optimistic removal — card disappears instantly on unfavorite click. If API call fails, card reappears and an error toast is shown. Uses TanStack Query `onMutate` for optimistic update and `onError` for rollback.
- **D-05:** Brief fade-out animation (~200ms opacity transition) when card is removed. Not instant disappear — provides visual feedback that something happened.

### Sort & Empty State
- **D-06:** Newest first only (backend default `created_at DESC`). No sort controls on the page — keeps it simple.
- **D-07:** Keep current empty state as-is (heart icon circle + "No favorites yet" + "Discover Destinations" CTA button). Already functional and friendly.

### Agent's Discretion
- Loading skeleton count and layout while batch endpoint resolves
- Toast library/component choice for error feedback (use whatever's already in the project)
- Exact button styling for the unfavorite overlay (heart icon filled, destructive color on hover)
- Whether to show "Saved on [date]" metadata on cards or keep it clean with just destination info

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend (destination-service)
- `services/destination-service/src/main/java/com/tripplanner/destination/` — Existing controller/service patterns for the batch endpoint
- `services/trip-service/src/main/java/com/tripplanner/trip/api/FavoriteController.java` — Current favorites API returning `{ destinationRef, createdAt }`
- `services/trip-service/src/main/java/com/tripplanner/trip/api/dto/FavoriteResponse.java` — Response shape

### Frontend
- `frontend/src/pages/FavoritesPage.tsx` — Existing page (skeleton to enhance)
- `frontend/src/features/destinations/favorites.api.ts` — Current API client
- `frontend/src/features/destinations/favorites.hooks.ts` — TanStack Query hooks
- `frontend/src/features/destinations/DestinationCard.tsx` — Card component to reuse
- `frontend/src/features/destinations/destinations.api.ts` — Existing destination fetch patterns
- `frontend/src/types/api.ts` — Shared type definitions (NearbyItem, DestinationDetailResponse)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DestinationCard` — Full card with photo, name, category badge, rating. Links to detail page.
- `DestinationCardSkeleton` — Loading skeleton matching card dimensions.
- `useFavorites()` hook — TanStack Query wrapper for `GET /api/favorites`.
- `useRemoveFavorite()` hook — Mutation with `invalidateQueries` on success (to be enhanced with optimistic update).
- `favoritesApi` — Axios client for favorites CRUD.

### Established Patterns
- TanStack Query for all server state (query keys, mutations, invalidation)
- Optimistic updates via `onMutate` / `onError` rollback (used in trip itinerary)
- Grid layout: `grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4`
- Tailwind + shadcn/ui components (Card, Button, Badge)
- M3 design system tokens (from Phase 9.1)
- Lucide icons for all iconography

### Integration Points
- Route `/favorites` already exists in the router
- Layout component wraps all pages (header with nav)
- `apiClient` (Axios instance) handles auth token injection automatically

</code_context>

<specifics>
## Specific Ideas

- The batch endpoint should handle missing/unknown refs gracefully (return what's found, skip unknowns) so the page still renders even if a destination was deleted upstream.
- Fade-out animation: use CSS transition on a state-driven class (e.g., `opacity-0 transition-opacity duration-200`) rather than a JS animation library.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 11-favorites-page*
*Context gathered: 2026-05-21*
