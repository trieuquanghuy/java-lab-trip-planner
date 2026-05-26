# Phase 12: Trip Duplication - Context

**Gathered:** 2026-05-26
**Status:** Ready for planning

<domain>
## Phase Boundary

A single backend endpoint + frontend action that deep-copies a trip (with all days and itinerary items) into a new trip owned by the same user. The duplicated trip has null dates and a "Copy of {original}" name. User is navigated to the new trip after creation.

</domain>

<decisions>
## Implementation Decisions

### Button Placement
- **D-01:** Duplicate button appears on trip detail page header as a visible icon button (Copy icon + "Duplicate" label)
- **D-02:** TripCard in the trip list shows a small duplicate icon on hover (secondary access point)

### Post-Duplication Behavior
- **D-03:** No confirmation dialog — duplication is non-destructive
- **D-04:** User is navigated immediately to the new duplicated trip after creation
- **D-05:** A toast notification confirms "Trip duplicated successfully"

### Copy Semantics
- **D-06:** Full deep copy of all days, itinerary items, time slots, notes, and cover image URL
- **D-07:** Each copied entity gets a new UUID — no shared references with the original

### Naming & Dates
- **D-08:** Duplicated trip name = "Copy of {originalName}", truncated to 120 chars if needed
- **D-09:** Dates (startDate, endDate) are set to null on the duplicate — user sets new dates later
- **D-10:** No special handling for already-prefixed names ("Copy of Copy of..." is acceptable)

### Agent's Discretion
- Backend endpoint design (POST path, request/response shape)
- Error handling approach (what if original trip not found mid-duplication)
- Whether to use @Transactional for the copy operation (yes — atomic)
- Frontend mutation hook structure (invalidation strategy)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend
- `services/trip-service/src/main/java/com/tripplanner/trip/service/TripService.java` — Existing trip CRUD patterns
- `services/trip-service/src/main/java/com/tripplanner/trip/domain/Trip.java` — Trip entity (120 char name limit)
- `services/trip-service/src/main/java/com/tripplanner/trip/domain/ItineraryDay.java` — Day entity structure
- `services/trip-service/src/main/java/com/tripplanner/trip/domain/ItineraryItem.java` — Item entity with all fields to copy
- `services/trip-service/src/main/java/com/tripplanner/trip/api/TripController.java` — Controller patterns

### Frontend
- `frontend/src/features/trips/trip.api.ts` — API client patterns
- `frontend/src/features/trips/trip.hooks.ts` — TanStack Query hook patterns
- `frontend/src/features/trips/TripCard.tsx` — Where list duplicate icon goes
- `frontend/src/pages/TripDetailPage.tsx` — Where detail duplicate button goes

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TripService` pattern: fetch trip + days + items in one transaction (use for reading source trip)
- `TripCard` component: needs minor addition of duplicate icon on hover
- `trip.api.ts`: extend with `duplicate(id: string)` method
- `trip.hooks.ts`: extend with `useDuplicateTrip` mutation hook

### Established Patterns
- Backend: `@Transactional` service methods, UUID-based IDs, `findByIdAndUserId` ownership check
- Frontend: TanStack Query mutations with `onSuccess` invalidation, `useNavigate` for redirects, `sonner` toasts
- API: RESTful paths (`/api/trips/{id}/...`), ownership enforced via JWT userId

### Integration Points
- Backend: New method on `TripService`, new endpoint on `TripController`
- Frontend: Mutation hook called from trip detail page + TripCard
- Navigation: `useNavigate` to `/trips/{newId}` after duplication

</code_context>

<specifics>
## Specific Ideas

- Backend endpoint: `POST /api/trips/{id}/duplicate` — reads source trip, creates new trip + days + items in one transaction
- The copy must be atomic: if any part fails, nothing is created
- dayIndex values are preserved in the copy (relative ordering maintained)
- item positions are preserved within each day

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 12-trip-duplication*
*Context gathered: 2026-05-26*
