# Phase 6: Trip Service — Itinerary Items + Favorites - Context

**Gathered:** 2026-05-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Itinerary item CRUD (add, update/move, delete) with gap-spaced position reorder and concurrent write serialization. Favorites (user-level, not trip-scoped). Note sanitization against stored XSS. Cover image fallback logic. All endpoints require authentication with ownership enforcement (same pattern as Phase 5).

Phase 6 does NOT include: drag-drop UI (Phase 8), frontend favorites page (Phase 7/8), cross-day drag UX (Phase 8), time-slot sorting UI (Phase 8).

</domain>

<decisions>
## Implementation Decisions

### Position & Reorder Logic
- **D-01:** Keep `position` as `INT` (no migration to BIGINT). Project scale does not require BIGINT — INT handles millions of reorders before overflow.
- **D-02:** Proactive reindex when gap < 2 — reindex the entire day's items in the same transaction (100, 200, 300...) using a single SQL window function UPDATE, then insert the new item. Transparent to the caller, no retry logic.
- **D-03:** `SELECT ... FOR UPDATE` on the parent `itinerary_days` row before reading/writing positions. Serializes all concurrent position writes to the same day. Acceptable for portfolio-scale (low contention).

### Note Sanitization (XSS Prevention)
- **D-04:** Use Jsoup with `Safelist.none()` — strip ALL HTML tags. Notes are plain text only. Dependency: `org.jsoup:jsoup`.
- **D-05:** Sanitize on write (before persisting to database). Stored value is already clean; no read-time processing needed.

### Favorites Architecture
- **D-06:** Separate `FavoriteController` + `FavoriteService` — favorites are user-level (not trip-scoped), separate entity, cleaner separation from trip logic.
- **D-07:** No special "add to trip from favorites" endpoint. Frontend calls existing `POST /api/trips/{tripId}/days/{dayId}/items` with the `destinationRef` from the favorite. The API is already sufficient.
- **D-08:** Idempotent re-favorite: attempt insert → on conflict (composite PK `user_id, destination_ref`) → return existing row with HTTP 200. First-time favorite returns 201.

### Cover Image Logic
- **D-09:** First item's photo = first item by `(day_index ASC, position ASC)` across all days of the trip where `photo_url IS NOT NULL`. First match wins.
- **D-10:** Cover image fallback computed on read — when `trip.cover_image_url` is null, query for the first item's photo. No caching, no invalidation complexity. Portfolio-scale reads are fine.
- **D-11:** Denormalize: add optional `photo_url VARCHAR(2048)` column to `itinerary_items`. Populated at item creation time (frontend sends it in the request body). Avoids cross-service calls to destination-service for a display-only field.

### Item Add & Cross-Day Move
- **D-12:** No `destinationRef` validation against destination-service. Accept any non-blank string (format: `provider:id`). Frontend already validated it exists before showing "Add to Trip." Avoids coupling and handles destination-service downtime gracefully.
- **D-13:** Cross-day move via PATCH with new `itineraryDayId`. Frontend specifies exact `position` in the PATCH body. If `position` is null during a day-move, default to append (`MAX(position) + 100`). Target day must belong to the same trip — verified server-side.
- **D-14:** Allow duplicate `destinationRef` in the same day. No unique constraint on `(itinerary_day_id, destination_ref)`. Real travelers visit the same place at different times.

### Error Codes & Response Shapes
- **D-15:** Full item object returned on 201 (add) and 200 (patch): `{ id, itineraryDayId, destinationRef, position, timeSlot, note, photoUrl, createdAt, updatedAt }`.
- **D-16:** `GET /api/trips/{id}` response includes `items[]` array nested inside each day object, sorted by position ASC. Extends Phase 5's trip detail response.
- **D-17:** Favorites list (`GET /api/favorites`) is a simple unpaginated list: `{ "items": [...] }`. No pagination for v1 (users realistically favorite < 100 destinations).
- **D-18:** New error codes to add to `ErrorCode` enum: `TRIP_DAY_NOT_IN_TRIP` (400), `TRIP_ITEM_NOT_FOUND` (404). Reuse existing `VALIDATION_FAILED` for blank destinationRef, note > 500 chars, etc.

### Agent's Discretion
- Flyway migration numbering (V5, V6, etc.)
- Exact Jsoup version (use Spring Boot managed or latest stable)
- Internal service method signatures and naming
- Test structure (follow existing trip-service patterns from Phase 5)
- Whether to use a `@Service` annotation or component scan for FavoriteService (follow existing pattern)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Data Model
- `docs/03-data-model.md` §3.5–3.6 — itinerary_items table (position algorithm, gap-spaced reindex) and favorites table (composite PK, indexes)

### API Specification
- `docs/04-api-spec.md` — Item endpoints (POST/PATCH/DELETE), Favorites endpoints (POST/DELETE/GET), error codes

### Auth & Security
- `docs/05-auth-security.md` — JWT validation, ownership model
- `libs/api-contracts/src/main/java/com/tripplanner/contracts/UserContext.java` — `@AuthenticationPrincipal` target record
- `libs/error-handling/src/main/java/com/tripplanner/errors/ErrorCode.java` — existing error code enum (extend with item/favorite codes)
- `libs/error-handling/src/main/java/com/tripplanner/errors/ProblemDetailFactory.java` — RFC 7807 factory

### Existing Patterns (follow these)
- `services/trip-service/src/main/java/com/tripplanner/trip/service/TripService.java` — service layer pattern, ownership enforcement
- `services/trip-service/src/main/java/com/tripplanner/trip/api/TripController.java` — controller pattern, UserContext injection
- `services/trip-service/src/main/java/com/tripplanner/trip/api/TripControllerAdvice.java` — exception-to-RFC 7807 mapping
- `services/trip-service/src/main/java/com/tripplanner/trip/service/DayMaterializationService.java` — transactional service with isolation level pattern
- `services/trip-service/src/main/java/com/tripplanner/trip/domain/ItineraryItem.java` — existing entity (needs expansion)
- `services/trip-service/src/main/java/com/tripplanner/trip/repository/ItineraryItemRepository.java` — existing repository (needs expansion)

### Phase 5 Context (dependency)
- `.planning/phases/05-trip-service-trips-days/05-CONTEXT.md` — Trip CRUD decisions (D-01 through D-13)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ItineraryItem` entity — exists but minimal (only id, itineraryDayId, destinationRef, position, timeSlot, note, createdAt, updatedAt). Needs expansion: add `photoUrl` field, full constructor, getters/setters.
- `ItineraryItemRepository` — exists with `countByDayIds` and `deleteByDayIds`. Needs expansion: findByDayId, position queries, FOR UPDATE query.
- `ItineraryDayRepository` — has `findByTripIdOrderByDayIndex`. Can be used for cover image fallback query.
- `TripControllerAdvice` — existing advice handles TripNotFoundException, ShortenConflictException, InvalidDateRangeException. Extend with item/favorite exceptions.
- `libs/error-handling` — `ProblemDetailFactory.of(status, code, detail)` for RFC 7807 responses.

### Established Patterns
- Service-layer ownership: every method takes `userId` from controller, repository filters by ownership chain (trip → user_id).
- `@Transactional(isolation = Isolation.REPEATABLE_READ)` for concurrent safety (Phase 5 DayMaterializationService pattern).
- Constructor injection, `@RestController`, `@RequestMapping`.
- Integration tests with Testcontainers (Phase 5 pattern).

### Integration Points
- V4 migration already created `itinerary_items` table. Phase 6 adds V5 for `photo_url` column + V6 for `favorites` table.
- Trip detail response (`TripResponse`) currently includes `days[]` without items — extend to include items.
- Security config: `anyRequest().authenticated()` already covers all `/api/**` paths.

</code_context>

<specifics>
## Specific Ideas

- ROADMAP note: "Store position as BIGINT" → decided to keep INT per user preference (project scale doesn't need BIGINT)
- ROADMAP note: "SELECT ... FOR UPDATE on parent itinerary_days row" → implemented in D-03
- ROADMAP note: "Reindex as single SQL window function UPDATE" → implemented in D-02
- ROADMAP note: "NFR-06 mapped here: note sanitization completes stored-XSS item (A03)" → implemented via Jsoup in D-04/D-05
- Success criteria #2 mentions "after 50 random reorders no two items share a position" → proactive reindex in D-02 guarantees this

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 06-trip-service-itinerary-items-favorites*
*Context gathered: 2026-05-18*
