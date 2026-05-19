---
phase: 10-observability-performance-hardening
plan: 01
status: complete
started: 2026-05-19
completed: 2026-05-19
---

# Plan 10-01 Summary: Verify and Harden Distributed Tracing

## What Was Built
- **userId MDC enrichment**: Both `MdcEnrichmentFilter` (servlet) and `ReactiveMdcEnrichmentFilter` (reactive/gateway) now read the `X-User-Id` header and populate the `userId` MDC key. All 4 MDC keys (traceId, spanId, userId, requestId) are now active in structured JSON logs.
- **Trace verification script**: `scripts/trace-verify.sh` performs end-to-end trace verification by creating a trip via the gateway and querying Zipkin API to confirm spans from both api-gateway and trip-service share the same traceId.

## Key Files
- `libs/observability/src/main/java/com/tripplanner/observability/MdcEnrichmentFilter.java` — userId from X-User-Id header
- `libs/observability/src/main/java/com/tripplanner/observability/ReactiveMdcEnrichmentFilter.java` — userId from X-User-Id header (reactive)
- `scripts/trace-verify.sh` — automated Zipkin trace continuity check

## Decisions
- userId populated from `X-User-Id` header (injected by gateway's `XUserIdInjectionGlobalFilter`) rather than re-parsing JWT in downstream services — follows existing security architecture
- Trace verification script uses Zipkin HTTP API v2 (`/api/v2/traces`, `/api/v2/trace/{traceId}`)

## Self-Check: PASSED
- All 4 MDC keys present in both filter implementations
- Logback config already includes all 4 keys
- Sampling probability already at 1.0 in all services
- Actuator endpoints already restricted to health,info,prometheus
- trace-verify.sh is executable and properly structured
