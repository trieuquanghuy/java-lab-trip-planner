---
status: testing
phase: 08-frontend-trip-planner
source: [08-01-PLAN.md, 08-02-PLAN.md, 08-03-PLAN.md, 08-04-PLAN.md, 08-05-PLAN.md]
started: "2026-05-18T22:44:00.000Z"
updated: "2026-05-18T22:44:00.000Z"
---

## Current Test

number: 2
name: Create Trip Wizard
expected: |
  Click "New Trip" button on /trips page. A 3-step modal wizard appears: (1) enter trip name, (2) pick start/end dates, (3) confirm. After submitting, navigates to the new trip's detail page.
awaiting: user response

## Tests

### 1. Trip List Page
expected: Navigate to /trips while logged in. See a responsive card grid of trips (or empty state with "Create your first trip" CTA if no trips exist). Each card shows cover image (or gradient fallback), trip name, and date range. Skeleton loading state appears while fetching.
result: pass

### 2. Create Trip Wizard
expected: Click "New Trip" button on /trips page. A 3-step modal wizard appears: (1) enter trip name, (2) pick start/end dates, (3) confirm. After submitting, navigates to the new trip's detail page.
result: [pending]

### 3. Trip Detail — Inline Name Edit
expected: On /trips/:tripId, click the trip name. It becomes an editable input. Type a new name, blur or press Enter. The name persists (PATCH request sent). Press Escape to cancel without saving.
result: [pending]

### 4. Drag-Drop Intra-Day Reorder
expected: On the itinerary board, drag an item within the same day column. Drop it at a different position. The item stays at its new position (optimistic update). If you refresh, the position is persisted.
result: [pending]

### 5. Drag-Drop Cross-Day Move
expected: Drag an item from one day column and drop it into a different day column. The item moves to the target day. Position is saved via PATCH.
result: [pending]

### 6. Time Slot Input
expected: On an itinerary item card, click the time input. Set a time (e.g., 09:30). On blur, the time is saved. Items with time slots sort chronologically above items without.
result: [pending]

### 7. Map Toggle
expected: On trip detail page, click the map icon button in the header. A Leaflet map panel appears on the right (desktop) or full-width (mobile). Markers appear for destinations that have coordinates. Click the X/map button again to hide.
result: [pending]

### 8. Add to Trip from Destination Detail
expected: On a destination detail page while logged in, click "Add to Trip". A dropdown appears with trip selector → day selector → Add button. After adding, a success message shows. The item appears in the trip's itinerary.
result: [pending]

### 9. Mobile Responsive — Day Tabs
expected: On a small screen (<1024px), the itinerary board shows day tabs instead of horizontal columns. Tapping a tab shows that day's items. On desktop (>=1024px), horizontal columns are shown.
result: [pending]

### 10. Keyboard Accessibility
expected: Itinerary items have visible focus rings (focus-visible:ring-2). The drag handle is focusable and has aria-label "Drag to reorder". DayTabs have role="tablist" with aria-selected on active tab.
result: [pending]

## Summary

total: 10
passed: 1
issues: 0
pending: 9
skipped: 0

## Gaps

[none yet]
