---
phase: 08-frontend-trip-planner
plan: 04
status: complete
duration: previously-executed
tasks_completed: 2
files_created:
  - frontend/src/features/trips/TripMap.tsx
  - frontend/src/features/trips/AddToTripDropdown.tsx
files_modified:
  - frontend/src/pages/TripDetailPage.tsx
  - frontend/src/pages/DestinationDetailPage.tsx
  - frontend/src/index.css
---

# Plan 04 Summary: Map Sidebar + Add-to-Trip Flow

## What Was Built

### Task 1: TripMap Component
- Leaflet CSS imported at top of `index.css`
- `TripMap` renders `MapContainer` with OpenStreetMap tiles, markers with Popups showing destination name
- `FitBounds` child component auto-fits map bounds on mount and when marker coordinates change (prevCoordsRef comparison prevents unnecessary map jerks)
- Leaflet icon fix for Vite bundler (unpkg CDN fallback for marker icons)
- Empty state: "Add destinations to see them on the map"
- Exports `MarkerData` interface for consumers

### Task 2: Map Integration + AddToTripDropdown
- **TripDetailPage** — Map toggle button (MapIcon/X), split layout with TripMap as side panel (400px sticky), fetches destination lat/lng via `useQueries` for each unique destinationRef, only enabled when map is visible
- **AddToTripDropdown** — Two-step selection (trip → day), uses `useTrips()` for list + `useTrip(id)` for day options, calls `useAddItem` mutation, success toast with auto-close, auth-gated (returns null without token), "Create a trip first" link when no trips exist
- **DestinationDetailPage** — Renders AddToTripDropdown with destinationRef, name, and photoUrl when user is authenticated

## Verification
- Vite build passes (0 errors)
- TripMap uses react-leaflet with fitBounds optimization
- AddToTripDropdown auth-gated and uses correct mutations
- DestinationDetailPage integrates dropdown
