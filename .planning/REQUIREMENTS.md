# Requirements: v1.1 Trip Enhancement

**Milestone:** v1.1
**Created:** 2026-05-21
**Status:** Active

---

## v1.1 Requirements

### Favorites (PERS)

- [ ] **PERS-01**: User can view a Favorites page listing all favorited destinations
- [ ] **PERS-02**: User can unfavorite a destination from the Favorites page
- [ ] **PERS-03**: User can navigate to destination detail from Favorites page
- [ ] **PERS-04**: User sees an empty state when no favorites exist

### Trip Duplication (DUP)

- [ ] **DUP-01**: User can duplicate an existing trip via a "Duplicate" button
- [ ] **DUP-02**: Duplicated trip includes all days and itinerary items from the original
- [ ] **DUP-03**: Duplicated trip has dates reset to null and name prefixed "Copy of"

### Trip Sharing (SHARE)

- [ ] **SHARE-01**: User can generate a shareable link for any trip they own
- [ ] **SHARE-02**: Anyone with the link can view the trip in read-only mode without authentication
- [ ] **SHARE-03**: Shared view displays days, items, and map (no edit controls)
- [ ] **SHARE-04**: Trip owner can revoke or regenerate the share link

### Weather Forecast (WEATHER)

- [ ] **WEATHER-01**: User sees daily weather summary (temp high/low, icon, precipitation) for each trip day
- [ ] **WEATHER-02**: Weather is only shown for future dates within 16-day forecast range
- [ ] **WEATHER-03**: Weather data is cached and loads asynchronously (does not block trip view)

### Travel Time (TRAVEL)

- [ ] **TRAVEL-01**: User sees travel duration (minutes) between consecutive itinerary items in a day
- [ ] **TRAVEL-02**: User sees travel distance (km) between consecutive itinerary items
- [ ] **TRAVEL-03**: Travel times auto-recalculate when items are reordered, added, or removed
- [ ] **TRAVEL-04**: Travel time loads asynchronously and gracefully handles API unavailability

---

## Future Requirements (deferred past v1.1)

- Push/email notifications
- Budget tracking
- File-uploaded cover image
- Admin tooling
- Export to PDF/.ics
- OAuth (Google, GitHub) login

## Out of Scope

- Real-time multi-user collaboration
- Native mobile apps
- Internationalization / multi-currency
- GDPR-grade data export
- Trip sharing with edit permissions (collab editing)
- Transport mode selector for travel time (driving only in v1.1)
- Weather-based pack list suggestions
- Community trip templates

---

## Traceability

| Requirement | Phase | Plan | Status |
|-------------|-------|------|--------|
| PERS-01 | 11 | — | Planned |
| PERS-02 | 11 | — | Planned |
| PERS-03 | 11 | — | Planned |
| PERS-04 | 11 | — | Planned |
| DUP-01 | 12 | — | Planned |
| DUP-02 | 12 | — | Planned |
| DUP-03 | 12 | — | Planned |
| SHARE-01 | 13 | — | Planned |
| SHARE-02 | 13 | — | Planned |
| SHARE-03 | 13 | — | Planned |
| SHARE-04 | 13 | — | Planned |
| WEATHER-01 | 14 | — | Planned |
| WEATHER-02 | 14 | — | Planned |
| WEATHER-03 | 14 | — | Planned |
| TRAVEL-01 | 15 | — | Planned |
| TRAVEL-02 | 15 | — | Planned |
| TRAVEL-03 | 15 | — | Planned |
| TRAVEL-04 | 15 | — | Planned |
