# Requirements: Trip Planner

**Defined:** 2026-05-08
**Core Value:** A signed-in user can search a city, discover attractions, and assemble them into a multi-day itinerary that persists across sessions — with drag-drop reorder, optional time slots, and a map view.

Requirements derive from `docs/01-prd.md` (22 numbered FRs + 9 NFRs) and were validated by 4-agent project research (`.planning/research/`). Each REQ-ID below maps 1:1 to a PRD FR-ID for full traceability.

## v1 Requirements

### Authentication

- [ ] **AUTH-01**: User can sign up with a unique email and a password (min 8 chars). Generic error messages used to prevent account enumeration. *(maps to FR-12)*
- [ ] **AUTH-02**: User receives a verification email containing a unique 24-hour link; clicking the link activates the account. Account cannot log in before verification. *(maps to FR-13)*
- [ ] **AUTH-03**: User can log in with email and password and stay signed in across page refreshes; bad credentials return a generic error. *(maps to FR-14)*
- [ ] **AUTH-04**: User can log out, ending the session immediately and blocking access to authenticated routes. *(maps to FR-15)*

### Discovery (Search & Recommendations)

- [ ] **SRCH-01**: User can type ≥1 character into the search input and see up to 5 ranked, deduplicated city/country results (case-insensitive, partial match). Selecting a result becomes the active search value. Empty searches show "No attractions found". *(maps to FR-1)*
- [ ] **SRCH-02**: After a valid location search, user sees up to 20 attractions for that location with name, category/tags, rating/popularity, and thumbnail. Placeholder shown for any missing field. Default radius: 20 km for cities. *(maps to FR-2)*

### Destination Details

- [ ] **DEST-01**: User can open a destination detail view from list or detail link, showing name, category, short description, photos, address, opening hours, website, and an "Add to Trip" CTA. Detail opens even with missing fields. "Add to Trip" disabled when not logged in. *(maps to FR-3)*
- [ ] **DEST-02**: User can swipe through multiple destination photos when available; placeholder shown when no photos exist. *(maps to FR-4)*
- [ ] **DEST-03**: User sees opening hours when present, or an explicit "Opening hours not available" state when missing. *(maps to FR-5)*

### Trip Planning

- [ ] **TRIP-01**: User can create a trip with a required name (length 1–120). New trip appears in "My Trips" list and the planner opens immediately. *(maps to FR-6)*
- [ ] **TRIP-02**: User can set start and end dates (start ≤ end). One itinerary day is materialized per date in range. Reducing the range that would orphan planned items requires explicit confirmation (409 Conflict otherwise). *(maps to FR-7)*
- [ ] **TRIP-03**: User can add a destination to a chosen day of a chosen trip from list view OR from detail view. Destination appears immediately under the chosen day. *(maps to FR-8)*
- [ ] **TRIP-04**: User can remove a destination from a trip day; item disappears immediately after confirmation. *(maps to FR-9)*
- [ ] **TRIP-05**: Logged-out users prompted to log in when creating a trip or adding a destination. After successful login, the original action completes and user lands on the same trip/destination page. *(maps to FR-10)*
- [ ] **TRIP-06**: After login, returning user sees previously created trips, days, and items; empty state shown when no trips exist. *(maps to FR-11)*

### In-Day Scheduling

- [ ] **SCHD-01**: Logged-in user can drag-drop reorder items within a single day. Order persists immediately with optimistic update; rollback on server error. *(maps to FR-16)*
- [ ] **SCHD-02**: Logged-in user can drag an item from one day to another within the same trip. Order in the destination day persists. *(maps to FR-17)*
- [ ] **SCHD-03**: Each item supports an optional time-of-day (HH:mm). When set, items in the day display sorted by time; otherwise sorted by position. *(maps to FR-18)*

### Trip Map View

- [ ] **TMAP-01**: For a trip with at least one geocoded item, all items render as markers on an interactive map (Leaflet + OSM tiles). Marker click reveals item name. Map auto-fits bounds to all markers. *(maps to FR-19)*

### Personalization

- [ ] **PERS-01**: User can attach a free-text note (≤500 chars) to any itinerary item; notes are sanitized server-side (HTML stripped) to prevent stored XSS. Notes editable inline. *(maps to FR-20)*
- [ ] **PERS-02**: Logged-in user can favorite/unfavorite a destination from list or detail view; "My Favorites" page lists all favorites and supports add-to-trip from there. *(maps to FR-21)*
- [ ] **PERS-03**: User can set a cover image URL for a trip; defaults to the first item's photo if unset. Cover image displays on the trip card and trip header. *(maps to FR-22)*

### Non-Functional

- [ ] **NFR-01**: City/country search returns within 500 ms p95 for cached or seeded results (Redis L1 + Postgres FTS L2 + provider L3 cache tiers). *(maps to PRD NFR-1)*
- [ ] **NFR-02**: Per-user authorization enforced at the service layer for every authenticated endpoint; cross-user reads return `404` (not `403`) so resource existence is not leaked. *(maps to PRD NFR-2)*
- [ ] **NFR-03**: When OpenTripMap or Foursquare is unavailable, the system serves cached data and surfaces a degraded-mode indicator to the UI; circuit breakers prevent cascade failures. *(maps to PRD NFR-3)*
- [x] **NFR-04**: All v1 external services operate on free tiers; no credit-card-required signups required to run the app locally. *(maps to PRD NFR-4)*
- [ ] **NFR-05**: Backend service-layer line coverage ≥ 70%; auth + ownership-check paths achieve 100% branch coverage; 8 mandatory security integration tests gate every PR. *(maps to PRD NFR-5)*
- [ ] **NFR-06**: OWASP Top 10 (2021) explicitly addressed: parameterized queries (A03), bcrypt + JWT rotation (A07), CSP + sanitization (A03/XSS), service-layer ownership (A01), Dependency-Check + Dependabot (A06). *(maps to PRD NFR-6)*
- [ ] **NFR-07**: All interactive controls reachable by Tab; focus visible; drag-drop has keyboard alternative (dnd-kit KeyboardSensor); WCAG AA color contrast on text. *(maps to PRD NFR-7)*
- [ ] **NFR-08**: Trip planner usable on screens ≥ 360 px wide (mobile-responsive, not a native app). *(maps to PRD NFR-8)*
- [ ] **NFR-09**: All inter-service requests carry W3C trace context; structured JSON logs include `traceId`, `spanId`, `userId`, `requestId`. Single end-to-end trace visible in Zipkin. *(maps to PRD NFR-9)*

## v2 Requirements

Deferred to a future milestone. Tracked but not in current roadmap. Sourced from `docs/09-roadmap.md` v2 backlog.

### Sharing & Templates

- **SHRE-01**: Read-only public link for a trip (`/share/<slug>`) accessible without authentication
- **SHRE-02**: Duplicate a trip as a template (deep copy of days + items into a new trip)

### Travel Intelligence

- **WTHR-01**: Weather forecast for trip dates (Open-Meteo, free tier) shown on trip detail
- **TRVL-01**: Travel time/distance between consecutive items (OSRM public API), cached per `(fromRef, toRef, mode)`

### Export & Integrations

- **EXPT-01**: Export trip to PDF (server-side render via OpenHTMLtoPDF)
- **EXPT-02**: Export trip to .ics calendar file (ical4j)
- **OAUTH-01**: OAuth login (Google, GitHub) via Spring Authorization Server or Keycloak

### Product Depth

- **BDGT-01**: Budget tracking — optional `cost_amount`/`cost_currency` per item with trip totals
- **TAGS-01**: Trip categories/tags (beach, city, family); filter trips by tag
- **PWA-01**: Service worker, offline-cache trips, install banner
- **PUSH-01**: Web push notifications ("trip starts in 7 days") with VAPID keys
- **COLLAB-01**: Multi-traveler collaboration with co-owners and CRDT-based item ordering
- **UPLD-01**: File-uploaded cover image (S3 + pre-signed URL pipeline + image processing)
- **I18N-01**: Internationalization (i18next + backend message externalization)
- **ADMIN-01**: Read-only ops dashboard (system health, user delete with cascade)
- **MOBL-01**: Native mobile app (React Native or Flutter sharing the v1 API)

## Out of Scope

Explicitly excluded from this milestone. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Trip sharing via public link | Adds public-vs-authenticated route mode and slug generation; v2 |
| Trip duplication / templates | Small CRUD addition; deferred to keep v1 focused on core flow |
| Real-time multi-user collaboration | Requires CRDT or pessimistic locking; out of portfolio scope |
| Native mobile apps | Mobile-responsive web (NFR-08) covers basic mobile; native is a separate project |
| Push / email notifications | Requires VAPID keys, background jobs, deliverability ops; v2 |
| Budget tracking | Adds complexity for low portfolio value |
| Weather forecast | Nice-to-have; not core to the planning loop |
| Travel time/distance | Depends on routing API; v2 |
| PDF / .ics export | Out-of-band concern; v2 |
| OAuth (Google, GitHub) | Adds Spring Authorization Server overhead; v2 |
| File-uploaded cover image | URL-only in v1 to avoid S3/upload pipeline; v2 |
| Internationalization | English-only v1 |
| Multi-currency | Single-currency-free v1 |
| Admin tooling | DB access via psql/pgAdmin sufficient for portfolio scope |
| GDPR-grade data export | Out-of-band per request only |
| Real-time chat | Not core to trip planning |
| AI itinerary generation | Roam Around lane; this project is in the structured-planner lane |

## Traceability

Mapping of requirements to roadmap phases.

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 2 | Pending |
| AUTH-02 | Phase 2 | Pending |
| AUTH-03 | Phase 2 | Pending |
| AUTH-04 | Phase 2 | Pending |
| SRCH-01 | Phase 3 | Pending |
| SRCH-02 | Phase 4 | Pending |
| DEST-01 | Phase 4 | Pending |
| DEST-02 | Phase 4 | Pending |
| DEST-03 | Phase 4 | Pending |
| TRIP-01 | Phase 5 | Pending |
| TRIP-02 | Phase 5 | Pending |
| TRIP-03 | Phase 6 | Pending |
| TRIP-04 | Phase 6 | Pending |
| TRIP-05 | Phase 7 | Pending |
| TRIP-06 | Phase 5 | Pending |
| SCHD-01 | Phase 8 | Pending |
| SCHD-02 | Phase 8 | Pending |
| SCHD-03 | Phase 8 | Pending |
| TMAP-01 | Phase 8 | Pending |
| PERS-01 | Phase 6 | Pending |
| PERS-02 | Phase 6 | Pending |
| PERS-03 | Phase 6 | Pending |
| NFR-01 | Phase 3 | Pending |
| NFR-02 | Phase 1 | Pending |
| NFR-03 | Phase 4 | Pending |
| NFR-04 | Phase 0 | Complete |
| NFR-05 | Phase 2 | Pending |
| NFR-06 | Phase 6 | Pending |
| NFR-07 | Phase 8 | Pending |
| NFR-08 | Phase 8 | Pending |
| NFR-09 | Phase 10 | Pending |

**Coverage:**
- v1 requirements: 31 total (22 functional + 9 non-functional)
- Mapped to phases: 31 ✓
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-08*
*Last updated: 2026-05-08 — traceability populated by gsd-roadmapper after roadmap creation*
