# Phase 8 Context — Frontend Trip Planner

## Phase Goal
The full itinerary editor works with drag-drop reorder, cross-day item moves, optional time slots, and an interactive map view.

## Requirements Covered
- **SCHD-01**: Drag-drop reorder items within a single day (optimistic update, rollback on error)
- **SCHD-02**: Drag an item from one day to another within the same trip
- **SCHD-03**: Optional time-of-day (HH:mm) per item; items sort by time when set
- **TMAP-01**: All items as Leaflet markers, click reveals name, auto-fit bounds
- **NFR-07**: All controls keyboard-reachable (dnd-kit KeyboardSensor), WCAG AA contrast
- **NFR-08**: Usable on 360px wide screens (mobile-responsive)

## Decisions

### Layout & Structure
| Area | Decision | Rationale |
|------|----------|-----------|
| Itinerary Board | **Kanban-style columns** — one column per day, horizontal scroll | Natural mental model for multi-day planning; each day is a droppable zone |
| Map Position | **Side panel** — map on one side, itinerary on the other (desktop) | Both visible simultaneously; user can see where items are while reordering |
| Mobile Layout | **Swipeable day tabs** — one day visible at a time, tap tabs or swipe | 360px constraint means columns can't coexist; tabs keep focus on one day |
| Trip Creation | **Multi-step wizard** — name → dates → add destinations | Guided flow reduces cognitive load for new users |
| Add to Trip | **Dropdown selector** — pick trip → pick day → appended at end of day | Explicit selection avoids confusion about where item goes |

### Drag-Drop UX
| Area | Decision | Rationale |
|------|----------|-----------|
| Visual Feedback | **Lift + placeholder ghost** — card lifts with shadow, placeholder shows origin | Clear spatial feedback; user sees both where item was and where it's going |
| Library | `@dnd-kit/core` 6.x + `@dnd-kit/sortable` | Locked in stack spec; framework-stable for React 18 |
| Keyboard | Register `KeyboardSensor` alongside `PointerSensor` | Required for WCAG NFR-07 |
| Overlay | Use `DragOverlay` with `opacity: 0` on original via `data-dragging` attr | Prevents visual duplication during drag |
| State Strategy | Local ephemeral state during drag → API call on drop → invalidate on error only | Optimistic UX; avoid flicker from refetch on success |

### Time Slots
| Area | Decision | Rationale |
|------|----------|-----------|
| Input Method | **Inline time picker on card** — HH:MM input, saves on blur | Fast edits without modal friction |
| Sorting | Items with time sort chronologically; items without time appear after | Matches natural expectation |
| API Behavior | PATCH on blur, not keystroke; show "Saving..."/"Saved" indicator | Avoids spam; uses TanStack Query mutation state |

### Map View
| Area | Decision | Rationale |
|------|----------|-----------|
| Library | `react-leaflet` 4.2.x + OpenStreetMap tiles | Locked in stack; v4 required for React 18 |
| Bounds | `fitBounds` on mount and when marker coordinates change (not on every render) | Prevents map jerk on unrelated state changes |
| Interaction | Click marker → show item name in popup | Simple, discoverable |

### Trip List & Empty States
| Area | Decision | Rationale |
|------|----------|-----------|
| Trip List Page | **Card grid with cover images** — trip name, dates, item count | Visual, scannable, consistent with destination cards |
| Empty Trip | **Illustration + "Add your first stop" CTA** → navigates to search | Friendly onboarding, reduces drop-off |

## Specifics
- Deferred login flow: persist `{destinationRef, tripId}` intent to `sessionStorage` before redirecting; restore on redirect-back
- Note edits: PATCH on blur, "Saved"/"Saving..." indicator from TanStack Query mutation state
- Cover image for trip card: use first destination's photo or a default gradient

## Technical Constraints
- `@dnd-kit/core` 6.x + `@dnd-kit/sortable` (NOT `@dnd-kit/react` — pre-1.0)
- `react-leaflet` 4.2.x (NOT v5 — requires React 19)
- Optimistic updates via TanStack Query mutation with `onMutate` / `onError` rollback
- Zustand 5 for local drag state (ephemeral, not persisted)
- Trip API endpoints from Phase 5 (trip-service): `GET /trips`, `POST /trips`, `GET /trips/{id}`, `PATCH /trips/{id}/items/{itemId}`

## Open Questions (for plan-phase to resolve)
- Exact component tree decomposition (TripBoard → DayColumn → ItineraryItem)
- Whether to use `useSensors` with distance constraint to avoid accidental drags
- Map panel collapse/expand behavior on tablet breakpoints
