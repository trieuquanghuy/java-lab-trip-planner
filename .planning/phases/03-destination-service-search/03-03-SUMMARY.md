# Plan 03-03 Summary: SearchService + SearchController + security config

**Status:** Complete
**Committed:** feat(destination): implement SearchService with Redis cache + SearchController

## What was built

1. **SearchService.java** — Full cache-aside pattern with:
   - Input normalization (trim, blank check)
   - Limit capping at MAX_LIMIT=5
   - Redis cache read with `SEARCH:{q_lower}:{type}:{limit}` key format
   - Single-flight lock via `SET lock:search:{key} 1 NX EX 5` to prevent stampede
   - Double-check cache after lock acquisition
   - 1h cache TTL, 5s lock TTL
   - Graceful degradation: Redis failures fall through to Postgres query
   - JSON serialization via ObjectMapper

2. **SearchController.java** — REST controller at `/api/search`:
   - `@GetMapping` with `q` (default ""), `type` (default "city,country"), `limit` (default 5)
   - Always returns 200 (empty results = empty array, not 404)

3. **ServletSecurityConfig.java** — Added `/api/search/**` to `permitAll()` matchers

## Key decisions

- No ErrorCode changes needed — search always returns 200
- Lock-wait fallback: 5 retries × 100ms, then direct Postgres query (never blocks indefinitely)
- Thread.sleep interrupted → restore interrupt flag and fall through

## Deviations

- Did not modify `ErrorCode.java` (plan noted it was unnecessary)
