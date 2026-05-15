---
phase: 04-destination-service-providers-cache
plan: 02
subsystem: destination-service
tags: [provider-clients, resilience4j, circuit-breaker, rest-client, dto]

requires: []
provides: [OtmClient, FoursquareClient, OtmPlace, OtmPlaceDetail, FoursquareVenue, ProviderClientConfig]
affects: [destination-service]

tech-stack:
  added: [resilience4j-spring-boot3, spring-boot-starter-aop, wiremock-spring-boot]
  patterns: [CircuitBreaker-per-provider, RestClient-based-HTTP, fallback-methods]

key-files:
  created:
    - services/destination-service/src/main/java/com/tripplanner/destination/provider/otm/OtmClient.java
    - services/destination-service/src/main/java/com/tripplanner/destination/provider/otm/OtmPlace.java
    - services/destination-service/src/main/java/com/tripplanner/destination/provider/otm/OtmPlaceDetail.java
    - services/destination-service/src/main/java/com/tripplanner/destination/provider/fsq/FoursquareClient.java
    - services/destination-service/src/main/java/com/tripplanner/destination/provider/fsq/FoursquareVenue.java
    - services/destination-service/src/main/java/com/tripplanner/destination/provider/fsq/FoursquareSearchResponse.java
    - services/destination-service/src/main/java/com/tripplanner/destination/provider/ProviderClientConfig.java
  modified:
    - services/destination-service/build.gradle.kts
    - services/destination-service/src/main/resources/application.yml

key-decisions:
  - FoursquareVenue deliberately omits photos/hours/rating fields (Premium-only on free tier)
  - Separate RestClient beans per provider for independent configuration
  - @SuppressWarnings("unused") on fallback methods to suppress IDE warnings

requirements-completed: [SRCH-02, DEST-01, NFR-03]

duration: 3 min
completed: 2026-05-15
---

# Phase 04 Plan 02: Provider Clients + DTOs + Resilience4j Summary

Provider HTTP clients for OpenTripMap and Foursquare using Spring RestClient with per-provider Resilience4j circuit breakers (50%/10/30s), provider-specific record DTOs, and externalized API key configuration.

## Tasks Completed

| # | Task | Files | Commit |
|---|------|-------|--------|
| 1 | DTOs + Config + Dependencies | 7 files + build.gradle.kts + application.yml | e52af0c |
| 2 | Client implementations (combined with Task 1) | OtmClient.java, FoursquareClient.java | e52af0c |

## Deviations from Plan

- **[Rule 3 - Efficiency] Combined Tasks 1+2 into single commit** — All files are tightly coupled; separate commits would be artificial. Single atomic commit.
- **[Rule 1 - Bug Prevention] Added WireMock dependency early** — Plan 04-04 needs it; adding to build.gradle.kts now avoids a second modification to the same file in Wave 3.

**Total deviations:** 2 auto-fixed. **Impact:** Minor — cleaner commit history.

## Issues Encountered

None.

## Next Phase Readiness

Ready for Plan 04-03 (NearbyService pipeline + DetailService).
