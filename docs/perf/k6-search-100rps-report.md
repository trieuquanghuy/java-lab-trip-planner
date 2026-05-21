# Search Endpoint Load Test Report

**Date:** 2026-05-19
**Endpoint:** `GET /api/destinations/search`
**Tool:** [k6](https://k6.io/)

## Configuration

| Parameter | Value |
|-----------|-------|
| Target RPS | 100 |
| Duration | 60s |
| Executor | constant-arrival-rate |
| Pre-allocated VUs | 150 |
| Max VUs | 300 |
| Gateway URL | http://localhost:8180 |

## How to Run

```bash
# Install k6 (macOS)
brew install k6

# Start infrastructure
docker compose -f infra/docker-compose.yml up -d

# Wait for services to be healthy, then run:
k6 run scripts/k6-search-load.js

# Or with custom base URL:
k6 run --env BASE_URL=http://localhost:8180 scripts/k6-search-load.js
```

## Results

> Run `k6 run scripts/k6-search-load.js` against running docker-compose environment and paste output below.

| Metric | Value | Threshold | Status |
|--------|-------|-----------|--------|
| p50 latency | _TBD_ | — | — |
| p95 latency | _TBD_ | < 500ms | _TBD_ |
| p99 latency | _TBD_ | — | — |
| Error rate | _TBD_ | < 5% | _TBD_ |
| Total requests | _TBD_ | ~6000 | — |
| Successful checks | _TBD_ | — | — |

## Threshold

**p95 < 500ms** — Pass/Fail determination:
- PASS: p95 response time is under 500ms at sustained 100 RPS
- FAIL: p95 exceeds 500ms, indicating capacity or query optimization needed

## Notes

- Virtual threads enabled in destination-service (`spring.threads.virtual.enabled=true`)
- Search queries randomized from: Tokyo, Paris, London, New York, Sydney, Rome, Bangkok, Berlin, Seoul, Madrid
- The test hits the public search endpoint (no authentication required)
- Database should be seeded with destinations before running (see `infra/seeds/`)
