---
milestone: v1
audited: 2026-05-19T13:17:00Z
status: gaps_found
scores:
  requirements: 18/31
  phases: 9/12
  integration: 7/12
  flows: partial
gaps:
  requirements:
    - id: "SRCH-01"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 3"
      evidence: "SearchController + CityRepository with FTS + Redis cache. VERIFICATION.md passed (code-only)."
    - id: "SRCH-02"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 4"
      evidence: "NearbyService + DetailService + DestinationController. 4 SUMMARY files. No VERIFICATION.md."
    - id: "DEST-01"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 4"
      evidence: "DetailService + DestinationDetailController return name, category, photos, hours, website."
    - id: "DEST-02"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 4"
      evidence: "PhotoCarousel.tsx in frontend renders swipeable photos with placeholder state."
    - id: "DEST-03"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 4"
      evidence: "DestinationDetailPage shows hours or 'Opening hours not available'."
    - id: "TRIP-03"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 6 + Phase 8"
      evidence: "ItemController.addItem + AddToTripDropdown.tsx in frontend."
    - id: "TRIP-04"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 6 + Phase 8"
      evidence: "ItemController.removeItem + ItineraryBoard delete button."
    - id: "TRIP-05"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 7"
      evidence: "Deferred intent stored in sessionStorage → restored after login. 07-05-SUMMARY confirms."
    - id: "SCHD-01"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 8"
      evidence: "dnd-kit DragOverlay + optimistic update. 08-03-SUMMARY confirms."
    - id: "SCHD-02"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 8"
      evidence: "Cross-day moves via handleDragEnd. 08-03-SUMMARY confirms."
    - id: "SCHD-03"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 8"
      evidence: "Time slot input on ItineraryItemCard. 08-03-SUMMARY confirms."
    - id: "TMAP-01"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 8"
      evidence: "TripMap.tsx with Leaflet markers + auto-fitBounds. 08-04-SUMMARY confirms."
    - id: "PERS-01"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 6"
      evidence: "ItineraryItemService sanitizes notes with Jsoup. 500 char limit. PATCH on blur in frontend."
    - id: "PERS-02"
      status: "partial"
      phase: "Phase 6"
      evidence: "FavoriteController + FavoriteService implemented. Frontend 'My Favorites' page shows NotFoundPage placeholder — NOT fully wired."
    - id: "PERS-03"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 6"
      evidence: "Trip.coverImageUrl field + fallback to first item photo in TripResponse."
    - id: "NFR-01"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 3"
      evidence: "Redis cache + GIN index FTS. VERIFICATION passed (code-only, Docker runtime deferred)."
    - id: "NFR-03"
      status: "satisfied (code complete, checkbox outdated)"
      phase: "Phase 4"
      evidence: "Resilience4j circuit breakers + cache fallback in NearbyService."
    - id: "NFR-07"
      status: "partial"
      phase: "Phase 8 + Phase 9"
      evidence: "KeyboardSensor registered in dnd-kit. Skip nav + route announcer added in Phase 9. axe audit NOT run."
    - id: "NFR-08"
      status: "partial"
      phase: "Phase 8 + Phase 9"
      evidence: "MobileNav + DayTabs + touch targets done. 360px viewport NOT formally tested."
    - id: "NFR-09"
      status: "unsatisfied"
      phase: "Phase 10"
      evidence: "Phase 10 not started. Micrometer tracing wired in Phase 0 but not load-tested."
  integration:
    - issue: "Phase 6 backend is complete but frontend favorites page routes to NotFoundPage"
      affected: ["PERS-02"]
    - issue: "Phase 8 UAT has only 1/10 tests passed — wizard, drag-drop, map, add-to-trip not manually verified"
      affected: ["SCHD-01", "SCHD-02", "SCHD-03", "TMAP-01"]
  flows:
    - flow: "Search → Discover → Add to Trip → See in Itinerary"
      status: "code-complete, UAT pending"
    - flow: "Signup → Verify → Login → Create Trip → Add Items → Reorder"
      status: "code-complete, UAT pending"
    - flow: "Favorites → Add to Trip from Favorites page"
      status: "broken (frontend /favorites → NotFoundPage)"
tech_debt:
  - phase: "02-auth-service"
    items:
      - "Bearer-JWT logout against live Tomcat container returns 204 (deferred to Phase 10)"
  - phase: "03-destination-service-search"
    items:
      - "Runtime integration tests not run (Docker required for Testcontainers)"
  - phase: "05-trip-service-trips-days"
    items:
      - "SC-5 concurrent PATCH test labeled incorrectly — tests 401 not concurrency"
      - "No automated concurrent multi-thread test for REPEATABLE_READ isolation"
  - phase: "08-frontend-trip-planner"
    items:
      - "UAT 9/10 scenarios pending human verification"
      - "08-01-SUMMARY.md missing (plan was executed but summary not written)"
  - phase: "09-polish"
    items:
      - "Pre-existing TypeScript errors in __tests__/ files (ProtectedRoute, ItineraryItemCard, trip.api, TripsPage)"
      - "Lighthouse audit not run"
      - "axe DevTools audit not run"
  - phase: "environment"
    items:
      - "Testcontainers incompatible with Docker 25.x (IllegalArgumentException: 25.0.2)"
      - "Backend tests cannot run locally until Testcontainers version is updated"
---

# Milestone v1 Audit Report

**Audited:** 2026-05-19
**Status:** GAPS FOUND
**Requirement Score:** 18/31 checked in REQUIREMENTS.md (but 27/31 have code implemented)

---

## Phase Completion Status

| Phase | Code Complete | Plans Done | VERIFICATION.md | Status |
|-------|--------------|------------|-----------------|--------|
| 0. Monorepo Scaffolding | ✓ | 10/10 | passed | **DONE** |
| 1. API Gateway | ✓ | 6/6 | passed | **DONE** |
| 2. Auth Service | ✓ | 7/7 | passed (w/ override) | **DONE** |
| 3. Search | ✓ | 4/4 | passed (code-only) | **DONE** |
| 4. Providers + Cache | ✓ | 4/4 | MISSING | **CODE DONE, unverified** |
| 5. Trips + Days | ✓ | 5/5 | human_needed (SC-5) | **DONE** (minor gap) |
| 6. Items + Favorites | ✓ | 6/6 | MISSING | **CODE DONE, unverified** |
| 7. Frontend Auth + Discovery | ✓ | 6/6 | MISSING | **CODE DONE, unverified** |
| 8. Frontend Trip Planner | ✓ | 5/5 | MISSING (UAT 1/10) | **CODE DONE, UAT needed** |
| 9. Polish | ✓ | 4/4 | MISSING | **CODE DONE, unverified** |
| 9.1. M3 Design System | ✗ | 0/? | N/A | **NOT STARTED** |
| 10. Observability | ✗ | 0/? | N/A | **NOT STARTED** |

---

## Critical Gaps

### 1. Documentation Drift (HIGH priority)
The ROADMAP Progress Table and REQUIREMENTS.md checkboxes are severely outdated:
- 7 phases marked "Not started" are actually complete
- 13 requirements marked `[ ]` have working code

### 2. Missing Verifications (MEDIUM priority)
Phases 4, 6, 7, 8, 9 have no VERIFICATION.md. Code exists but isn't formally verified.

### 3. Favorites Frontend Gap (LOW priority)
`/favorites` route goes to `NotFoundPage` — backend is complete but frontend page not built.

### 4. Phase 8 UAT (MEDIUM priority)
Only 1/10 UAT scenarios passed. The 9 remaining need manual testing.

### 5. Remaining Phases (Phase 9.1 + 10)
Two phases not started. Phase 9.1 is design polish; Phase 10 is observability/perf.

---

## Requirement Coverage Detail

### Satisfied (code implemented + verified or verifiable)

| REQ | Phase | Evidence |
|-----|-------|----------|
| AUTH-01 | 2 | Signup endpoint + IT tests |
| AUTH-02 | 2 | Email verification flow + MailHog |
| AUTH-03 | 2 | Login + refresh + IT tests |
| AUTH-04 | 2 | Logout invalidates refresh token |
| SRCH-01 | 3 | FTS + population-weighted ranking |
| SRCH-02 | 4 | NearbyService + DestinationController |
| DEST-01 | 4 | DetailService + frontend detail page |
| DEST-02 | 4+7 | PhotoCarousel with swipe |
| DEST-03 | 4+7 | Opening hours or "not available" |
| TRIP-01 | 5 | CreateTrip + IT |
| TRIP-02 | 5 | Date range + 409 shrink guard |
| TRIP-03 | 6+8 | AddItem + AddToTripDropdown |
| TRIP-04 | 6+8 | RemoveItem + UI delete button |
| TRIP-05 | 7 | Deferred intent + sessionStorage |
| TRIP-06 | 5 | List trips endpoint + empty state |
| SCHD-01 | 8 | dnd-kit intra-day reorder |
| SCHD-02 | 8 | Cross-day drag-drop |
| SCHD-03 | 8 | Time slot HH:mm input |
| TMAP-01 | 8 | TripMap + Leaflet markers |
| PERS-01 | 6 | Notes with Jsoup sanitization |
| PERS-03 | 6 | Cover image + fallback |
| NFR-01 | 3 | Redis + FTS p95 design |
| NFR-02 | 1+5 | Ownership check + 404 pattern |
| NFR-03 | 4 | Circuit breakers + cache fallback |
| NFR-04 | 0 | Free-tier only verified |
| NFR-05 | 2 | 8 security ITs pass |
| NFR-06 | 6 | XSS sanitization + parameterized queries |

### Partial

| REQ | Phase | Gap |
|-----|-------|-----|
| PERS-02 | 6 | Backend complete; frontend /favorites is placeholder |
| NFR-07 | 8+9 | Keyboard nav implemented; axe audit not run |
| NFR-08 | 8+9 | Mobile responsive coded; 360px not formally tested |

### Unsatisfied

| REQ | Phase | Reason |
|-----|-------|--------|
| NFR-09 | 10 | Phase 10 not started |

---

## Recommendations

1. **Immediate: Update ROADMAP.md + REQUIREMENTS.md** — Fix checkboxes and progress table to match reality
2. **Phase 8 UAT**: Complete the remaining 9 UAT scenarios with manual testing
3. **Testcontainers fix**: Update Testcontainers BOM to support Docker 25.x
4. **Favorites page**: Build `/favorites` frontend page or remove from nav/routes
5. **Phase 9.1 + 10**: Decide if these are needed for v1 ship or can be deferred to v2
