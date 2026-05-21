# Features Research — v1.1 Trip Enhancement

## Existing Features (already built)

- Full auth flow (signup, verify, login, refresh, logout)
- Destination search + detail views with photos/hours
- Trip CRUD with day materialization
- Drag-drop reorder + cross-day move
- Time slots + notes on itinerary items
- Map view (Leaflet)
- Cover image URL
- Favorites backend (API exists, no frontend page)

## Feature Categories for v1.1

### 1. Travel Time/Distance Between Items

**Table Stakes:**
- Show travel duration (minutes) between consecutive items in a day
- Show travel distance (km) between consecutive items in a day
- Auto-recalculate when items are reordered or added/removed

**Differentiators:**
- Transport mode selector (driving/walking/cycling) per trip or per segment
- Route visualization on map between items

**Anti-features (avoid):**
- Turn-by-turn navigation (not a trip planner concern)
- Real-time traffic (OSRM demo uses static data)

**Complexity:** Medium. Requires lat/lon for each destination (already stored), external API call, caching strategy for repeated routes.

### 2. Weather Forecast

**Table Stakes:**
- Show daily weather summary for each trip day
- Display: temperature high/low, weather icon, precipitation chance
- Only show for future dates within forecast range (≤16 days)

**Differentiators:**
- Color-coded weather indicators per day
- "Pack list" suggestions based on weather

**Anti-features (avoid):**
- Hourly breakdown (too granular for trip planning)
- Historical weather (adds complexity, different API)
- Weather alerts/warnings (push notification territory)

**Complexity:** Low-Medium. Simple REST call per trip location + date range. Cache-friendly.

### 3. Trip Sharing via Public Link

**Table Stakes:**
- Generate a shareable link for any trip
- Link shows read-only trip view (days, items, map) without auth
- Trip owner can revoke/regenerate share link
- Shared view clearly indicates "read-only"

**Differentiators:**
- "Duplicate to my trips" button for logged-in viewers

**Anti-features (avoid):**
- Collaborative editing via share link
- Permissions levels (viewer/editor)
- Social media preview cards (OpenGraph complexity)

**Complexity:** Medium. Requires public route bypassing JWT, security considerations.

### 4. Trip Duplication / Templates

**Table Stakes:**
- "Duplicate trip" button creates a copy of an existing trip
- Copy includes all days and itinerary items
- Dates reset to null (user picks new dates)
- New name defaults to "Copy of {original}"

**Differentiators:**
- "Use as template" — save trips as reusable templates
- Community templates (public templates from other users)

**Anti-features (avoid):**
- Template marketplace/ratings (scope creep)
- Auto-adjusting dates based on destination seasonality

**Complexity:** Low. Deep-copy CRUD operation. One new endpoint + frontend button.

### 5. Favorites Page (FR-21 carry-over)

**Table Stakes:**
- Favorites page listing all favorited destinations
- Unfavorite from the page
- Click destination navigates to detail view
- Empty state when no favorites

**Differentiators:**
- "Add to trip" directly from favorites page

**Anti-features (avoid):**
- Favorites folders/collections (overengineered)
- Favorite sharing

**Complexity:** Low. Backend already exists. Frontend page + TanStack Query hook.

## Feature Dependencies

```
Favorites (standalone, no deps)
Trip Duplication (standalone, no deps)
Trip Sharing (needs: trip detail read-only view concept)
Weather (needs: trip dates + destination lat/lon — both exist)
Travel Time (needs: destination lat/lon + day ordering — both exist)
```

## Build Order Suggestion

1. **Favorites** — Lowest risk, frontend-only, closes v1.0 carry-over
2. **Trip Duplication** — Simple backend CRUD, no external APIs
3. **Trip Sharing** — Moderate complexity, gateway changes for public route
4. **Weather** — External API, simple integration
5. **Travel Time** — External API + affects existing itinerary UI
