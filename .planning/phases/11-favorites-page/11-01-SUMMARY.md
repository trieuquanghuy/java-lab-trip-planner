---
phase: 11-favorites-page
plan: 01
status: complete
started: 2026-05-25
completed: 2026-05-25
---

# Plan 11-01 Summary: Backend Batch Destinations Endpoint

## What Was Built

Added `POST /api/destinations/batch` endpoint to destination-service. Accepts a JSON body with a `refs` array of providerRef strings, looks them up in the destinations cache, and returns lightweight `NearbyItem` summaries (providerRef, name, category, rating, photoUrl, lat, lng).

## Key Files Created

| File | Purpose |
|------|---------|
| `BatchRequest.java` | Request DTO — `record BatchRequest(List<String> refs)` |
| `BatchResponse.java` | Response DTO — `record BatchResponse(List<NearbyItem> items)` |
| `BatchService.java` | Service layer — uses `findAllById` + `ProviderMapper::toNearbyItem` |
| `BatchController.java` | REST controller — input validation (max 50, regex check), delegates to service |
| `BatchServiceTest.java` | 3 unit tests (known refs, unknown refs, empty) |
| `BatchControllerIntegrationTest.java` | 5 integration tests (valid, empty, overflow, invalid format, SQL injection) |

## Decisions Made

- Reused existing `NearbyItem` record as batch response item (exact match for D-02 fields)
- Reused existing `ProviderMapper.toNearbyItem()` (already handles photo JSON parsing)
- Used `findAllById` from JpaRepository (inherited, no repo changes needed)
- Max 50 refs per request (DoS protection)
- Invalid refs rejected with 400 (same regex as DetailController)
- Unknown refs silently skipped (partial results, no error)

## Self-Check: PASSED

- [x] POST /api/destinations/batch compiles
- [x] 3 unit tests pass (BatchServiceTest)
- [x] 5 integration tests pass (BatchControllerIntegrationTest)
- [x] Input validation: max 50 refs, regex validated, empty returns empty
