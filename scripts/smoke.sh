#!/usr/bin/env bash
# scripts/smoke.sh — Phase 0 phase-gate verifier (D-33).
#
# Asserts all 5 ROADMAP Phase 0 success criteria + NFR-04 (free-tier audit) against a
# running compose stack. Per D-33 this script lands in Wave 1 (NOT a final wave) so each
# subsequent wave's containers can be smoke-tested incrementally as they come online via
# `--criterion <N>` per-criterion gating.
#
# Sources:
#   - .planning/ROADMAP.md Phase 0 SC #1-#5 (verbatim)
#   - .planning/REQUIREMENTS.md NFR-04 (free-tier-only)
#   - .planning/phases/00-monorepo-scaffolding/00-CONTEXT.md D-01..D-33
#   - .planning/phases/00-monorepo-scaffolding/00-PATTERNS.md Bucket I (excerpt)
#
# Exit codes:
#   0 — all selected criteria PASS
#   1 — any selected criterion FAIL (label printed to stderr)
#
# Phase 1 extension (01-06-PLAN.md):
#   Adds 3 new criteria — phase-01-bypass / phase-01-routing /
#   phase-01-rate-limit — that exercise the assembled Phase 1 stack at
#   runtime. They complement (do NOT replace) the per-service IT suite
#   shipped by plans 01-04 (DirectServiceAccessIT) and 01-05 (gateway IT
#   suite). Source: .planning/phases/01-api-gateway/01-RESEARCH.md
#   "Validation Architecture / Wave 0 Gaps" lines 1280-1283; 01-CONTEXT.md
#   D-05/D-06 (rate-limit topology) + D-15 (every SC needs an IT/smoke);
#   01-PATTERNS.md Bucket E.

set -euo pipefail

# -----------------------------------------------------------------------------
# Configuration (settable from compose .env)
# -----------------------------------------------------------------------------
COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose.yml}"
POSTGRES_DB="${POSTGRES_DB:-tripplanner}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-60}"

# Eureka may take a few seconds after compose health to register all clients.
EUREKA_RETRY_SECONDS="${EUREKA_RETRY_SECONDS:-30}"

# -----------------------------------------------------------------------------
# Helper functions
# -----------------------------------------------------------------------------
fail() { echo "[smoke] FAIL: $1" >&2; exit 1; }
pass() { echo "[smoke] OK:   $1"; }
note() { echo "[smoke] note: $1"; }

# Detect jq once; criteria fall back to grep where possible.
HAVE_JQ=0
if command -v jq >/dev/null 2>&1; then
  HAVE_JQ=1
fi

usage() {
  cat <<'EOF'
scripts/smoke.sh — Phase 0 phase-gate verifier (D-33)

Usage:
  scripts/smoke.sh                       # full gate: SC#1..SC#5 + NFR-04
  scripts/smoke.sh --up                  # docker compose up -d --wait (no SC checks)
  scripts/smoke.sh --down                # docker compose down -v
  scripts/smoke.sh --list                # enumerate available criterion labels
  scripts/smoke.sh --criterion <N>       # run one criterion only
  scripts/smoke.sh --help | -h           # this message

Criteria (use with --criterion):
  1         SC#1: compose healthy <60s
  2         SC#2: 4 services in Eureka
  3         SC#3: gateway /actuator/health UP
  3-route   SC#3-route: /__health/<svc> via gateway
  4         SC#4: frontend reachable at :5173
  5         SC#5: per-service Flyway history tables present
  nfr-04    NFR-04: free-tier audit (no paid SaaS deps)
  phase-01-bypass     T-01-04 / Pitfall 1: direct :8082 with X-User-Id+no-JWT → 401
  phase-01-routing    SC#1: 4 gateway routes (/api/{auth,trips,search,destinations}) forward (no 502/503)
  phase-01-rate-limit SC#5 IP-only: 35 POST /api/auth/login → ≥1 × 429

Environment overrides (defaults shown):
  COMPOSE_FILE=infra/docker-compose.yml
  POSTGRES_DB=tripplanner
  POSTGRES_USER=postgres
  WAIT_TIMEOUT=60
  EUREKA_RETRY_SECONDS=30
EOF
}

list_criteria() {
  cat <<'EOF'
Available criteria for `scripts/smoke.sh --criterion <N>`:
  1         — SC#1: compose healthy <60s
  2         — SC#2: 4 services in Eureka
  3         — SC#3: gateway /actuator/health UP
  3-route   — SC#3-route: /__health/<svc> end-to-end via gateway
  4         — SC#4: frontend reachable at :5173
  5         — SC#5: per-service Flyway history tables present
  nfr-04    — NFR-04: free-tier audit (no paid SaaS deps)
  phase-01-bypass     — T-01-04 / Pitfall 1: direct :8082 with X-User-Id+no-JWT → 401
  phase-01-routing    — SC#1: 4 gateway routes forward (no 502/503)
  phase-01-rate-limit — SC#5 IP-only: 35 POST /api/auth/login → ≥1 × 429
EOF
}

# -----------------------------------------------------------------------------
# SC#1 — compose healthy <60s
# Verifies steady-state health AFTER the stack is up. The 60s budget is enforced
# by `docker compose up -d --wait --wait-timeout $WAIT_TIMEOUT` in --up mode.
# Bare invocation expects the caller has the stack already running and fails fast
# (does NOT hang for WAIT_TIMEOUT) — `docker compose ps` returns immediately.
# -----------------------------------------------------------------------------
check_sc1() {
  if ! command -v docker >/dev/null 2>&1; then
    fail "SC#1: docker CLI not found"
  fi

  # `docker compose ps --format json` returns either a JSON array (newer) or NDJSON
  # (older). Handle both. If empty/nothing-running → SC#1 fail.
  local ps_json
  if ! ps_json=$(docker compose -f "$COMPOSE_FILE" ps --format json 2>/dev/null); then
    fail "SC#1: 'docker compose ps' failed (compose file '$COMPOSE_FILE' missing or daemon down?)"
  fi

  if [ -z "$ps_json" ] || [ "$ps_json" = "[]" ]; then
    fail "SC#1: no compose services running — start the stack with: scripts/smoke.sh --up"
  fi

  if [ "$HAVE_JQ" -eq 1 ]; then
    # Normalize to array; tolerate both JSON-array and NDJSON output forms.
    local normalized
    normalized=$(printf '%s\n' "$ps_json" | jq -s 'if length == 1 and (.[0] | type) == "array" then .[0] else . end')
    if ! printf '%s' "$normalized" \
        | jq -e 'all(.[]; .State == "running" and (.Health == "healthy" or .Health == "" or .Health == null))' \
        >/dev/null 2>&1; then
      fail "SC#1: not all services running+healthy — run: docker compose -f $COMPOSE_FILE ps"
    fi
  else
    note "jq not found, using grep fallback for SC#1"
    # Without jq we can only assert that no service is in a non-running state.
    if printf '%s' "$ps_json" | grep -E '"State":"(exited|paused|dead|created|restarting)"' >/dev/null 2>&1; then
      fail "SC#1: at least one service is not running — run: docker compose -f $COMPOSE_FILE ps"
    fi
    if printf '%s' "$ps_json" | grep -E '"Health":"(unhealthy|starting)"' >/dev/null 2>&1; then
      fail "SC#1: at least one service is unhealthy/starting — run: docker compose -f $COMPOSE_FILE ps"
    fi
  fi

  pass "SC#1 compose healthy"
}

# -----------------------------------------------------------------------------
# SC#2 — 4 services registered with Eureka
# Bounded retry: Eureka registration may complete shortly after compose health.
# Per D-21, services tune lease-renewal to 5s in dev, so 30s ceiling is generous.
# -----------------------------------------------------------------------------
check_sc2() {
  local eureka_url="http://localhost:8761/eureka/apps"
  local services=("AUTH-SERVICE" "TRIP-SERVICE" "DESTINATION-SERVICE" "API-GATEWAY")
  local elapsed=0
  local body=""

  while [ "$elapsed" -lt "$EUREKA_RETRY_SECONDS" ]; do
    if body=$(curl -sf -H "Accept: application/json" "$eureka_url" 2>/dev/null); then
      local missing=()
      for svc in "${services[@]}"; do
        if [ "$HAVE_JQ" -eq 1 ]; then
          if ! printf '%s' "$body" | jq -e --arg s "$svc" '.applications.application[]?.name | select(. == $s)' >/dev/null 2>&1; then
            missing+=("$svc")
          fi
        else
          # Grep fallback: Eureka JSON contains "name":"<SERVICE>".
          if ! printf '%s' "$body" | grep -q "\"name\":\"$svc\""; then
            missing+=("$svc")
          fi
        fi
      done

      if [ "${#missing[@]}" -eq 0 ]; then
        pass "SC#2 4 services registered with Eureka"
        return 0
      fi
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  if [ -z "$body" ]; then
    fail "SC#2: Eureka not reachable at $eureka_url after ${EUREKA_RETRY_SECONDS}s"
  fi
  if [ "$HAVE_JQ" -eq 1 ]; then
    local present
    present=$(printf '%s' "$body" | jq -r '[.applications.application[]?.name] | join(",")' 2>/dev/null || echo "<unparseable>")
    fail "SC#2: not all services registered after ${EUREKA_RETRY_SECONDS}s. Found: [$present]; expected AUTH-SERVICE,TRIP-SERVICE,DESTINATION-SERVICE,API-GATEWAY"
  else
    note "jq not found, using grep fallback for SC#2"
    fail "SC#2: not all services registered after ${EUREKA_RETRY_SECONDS}s — inspect $eureka_url"
  fi
}

# -----------------------------------------------------------------------------
# SC#3 — gateway /actuator/health UP
# -----------------------------------------------------------------------------
check_sc3() {
  local body
  if ! body=$(curl -sf http://localhost:8080/actuator/health 2>/dev/null); then
    fail "SC#3: gateway /actuator/health unreachable at localhost:8080"
  fi

  if [ "$HAVE_JQ" -eq 1 ]; then
    if ! printf '%s' "$body" | jq -e '.status == "UP"' >/dev/null 2>&1; then
      fail "SC#3: gateway /actuator/health did not return status UP — body: $body"
    fi
  else
    note "jq not found, using grep fallback for SC#3"
    if ! printf '%s' "$body" | grep -q '"status":"UP"'; then
      fail "SC#3: gateway /actuator/health did not return status UP — body: $body"
    fi
  fi

  pass "SC#3 gateway /actuator/health UP"
}

# -----------------------------------------------------------------------------
# SC#3-route — /__health/<svc> end-to-end via gateway (D-02)
# -----------------------------------------------------------------------------
check_sc3_route() {
  for svc in auth trip destination; do
    local url="http://localhost:8080/__health/$svc"
    local body
    if ! body=$(curl -sf "$url" 2>/dev/null); then
      fail "SC#3-route: $url unreachable"
    fi

    if [ "$HAVE_JQ" -eq 1 ]; then
      if ! printf '%s' "$body" | jq -e '.status == "UP" and .phase == 0' >/dev/null 2>&1; then
        fail "SC#3-route: $url did not return {status: UP, phase: 0} — body: $body"
      fi
    else
      note "jq not found, using grep fallback for SC#3-route"
      if ! printf '%s' "$body" | grep -q '"status":"UP"'; then
        fail "SC#3-route: $url did not return status UP — body: $body"
      fi
      if ! printf '%s' "$body" | grep -E '"phase":[[:space:]]*0' >/dev/null 2>&1; then
        fail "SC#3-route: $url did not return phase=0 — body: $body"
      fi
    fi
  done

  pass "SC#3-route /__health/<svc> UP for auth, trip, destination"
}

# -----------------------------------------------------------------------------
# SC#4 — frontend reachable at :5173 (browser-console-error check is manual per
# VALIDATION.md "Manual-Only Verifications")
# -----------------------------------------------------------------------------
check_sc4() {
  local out=/tmp/tripplanner_index.html
  if ! curl -sf -o "$out" http://localhost:5173 2>/dev/null; then
    fail "SC#4: frontend not reachable at localhost:5173"
  fi
  if [ ! -s "$out" ]; then
    fail "SC#4: frontend returned an empty body at localhost:5173"
  fi
  pass "SC#4 frontend reachable at :5173"
}

# -----------------------------------------------------------------------------
# SC#5 — per-service Flyway history tables (Pitfall 3)
# -----------------------------------------------------------------------------
check_sc5() {
  local pairs=(
    "auth:auth_flyway_schema_history"
    "trip:trip_flyway_schema_history"
    "destination:destination_flyway_schema_history"
  )

  for pair in "${pairs[@]}"; do
    local schema="${pair%%:*}"
    local table="${pair##*:}"
    local sql="SELECT 1 FROM information_schema.tables WHERE table_schema='$schema' AND table_name='$table'"
    local result
    if ! result=$(docker compose -f "$COMPOSE_FILE" exec -T postgres \
        psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$sql" 2>/dev/null); then
      fail "SC#5: postgres unreachable (cannot query $schema.$table) — is the stack up?"
    fi
    # Trim whitespace.
    result="${result//[$'\t\r\n ']}"
    if [ "$result" != "1" ]; then
      fail "SC#5: $schema.$table missing — check spring.flyway.table=${schema}_flyway_schema_history (Pitfall 3) and that flyway-database-postgresql is on the classpath (Pitfall A)"
    fi
  done

  pass "SC#5 per-service Flyway history tables present (auth/trip/destination)"
}

# -----------------------------------------------------------------------------
# NFR-04 — free-tier audit (BLOCKER 4 fix)
# Enumerated grep deny-list applied to gradle/libs.versions.toml,
# frontend/package.json, infra/docker-compose.yml. Files not yet present (early
# wave) are skipped silently — the audit fails ONLY on a positive match.
# -----------------------------------------------------------------------------
check_nfr_04() {
  # Source: NFR-04 (free-tier-only); CONTEXT.md "Out of scope: paid SaaS deps".
  # Extend these lists as new paid services are evaluated; document additions in
  # scripts/README.md.
  local JAVA_DENY_LIST=(
    "algolia"
    "auth0-java"
    "mapbox-java"
    "twilio"
    "sentry-spring"
    "sentry-logback"
    "datadog-api-client"
    "newrelic-java"
    "rollbar-java"
    "bugsnag-java"
    "pingdom"
  )
  local NPM_DENY_LIST=(
    "@algolia/"
    "@mapbox/"
    "@auth0/"
    "@sentry/"
    "@datadog/"
    "@newrelic/"
    "@bugsnag/"
    "@rollbar/"
    "@pingdom/"
    "twilio"
  )
  local COMPOSE_DENY_LIST=(
    "datadoghq.com"
    "sentry.io"
    "algolia.net"
    "auth0.com"
    "mapbox.com"
    "twilio.com"
    "newrelic.com"
    "bugsnag.com"
    "rollbar.com"
    "pingdom.com"
  )

  local catalog="gradle/libs.versions.toml"
  local pkg="frontend/package.json"
  local compose="$COMPOSE_FILE"

  if [ -f "$catalog" ]; then
    for token in "${JAVA_DENY_LIST[@]}"; do
      if grep -qi "$token" "$catalog" 2>/dev/null; then
        fail "SC#NFR-04: paid Java SDK '$token' found in $catalog"
      fi
    done
  fi

  if [ -f "$pkg" ]; then
    for token in "${NPM_DENY_LIST[@]}"; do
      if grep -q "$token" "$pkg" 2>/dev/null; then
        fail "SC#NFR-04: paid npm package '$token' found in $pkg"
      fi
    done
  fi

  if [ -f "$compose" ]; then
    for token in "${COMPOSE_DENY_LIST[@]}"; do
      if grep -q "$token" "$compose" 2>/dev/null; then
        fail "SC#NFR-04: paid SaaS reference '$token' found in $compose"
      fi
    done
  fi

  pass "NFR-04 free-tier audit (no paid SaaS deps in catalog/package.json/compose)"
}

# -----------------------------------------------------------------------------
# Phase 1 — phase-01-bypass: T-01-04 / Pitfall 1 keystone runtime regression gate
#
# Direct hit on trip-service's loopback-bound port (Phase 0 D-22 = 127.0.0.1:8082)
# carrying a crafted X-User-Id but NO Authorization header. ServletJwtCommonFilter
# (libs/jwt-common, plan 01-02) + ServletSecurityConfig (plan 01-04) MUST cause
# this to return 401. If it returns 200, Pitfall 1 has regressed and a future
# plan accidentally removed the defense-in-depth filter. This complements (does
# NOT replace) plan 01-04's DirectServiceAccessWithoutGatewayReturns401IT.
# -----------------------------------------------------------------------------
check_phase_01_bypass() {
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' \
          -H 'X-User-Id: attacker' \
          http://127.0.0.1:8082/api/trips/_ping 2>/dev/null || echo "000")
  if [ "$code" != "401" ]; then
    fail "phase-01-bypass: direct hit on 127.0.0.1:8082/api/trips/_ping with crafted X-User-Id and no Authorization returned HTTP $code (expected 401). T-01-04 / Pitfall 1 has regressed — verify ServletSecurityConfig (plan 01-04) and ServletJwtCommonFilter wiring (plan 01-02) are intact."
  fi
  pass "phase-01-bypass /api/trips/_ping with spoofed X-User-Id (no JWT) returns 401"
}

# -----------------------------------------------------------------------------
# Phase 1 — phase-01-routing: SC#1 / Pitfall J runtime gate
#
# Asserts the gateway's Path-predicate routing (plan 01-03 application.yml) is
# working AT RUNTIME by sending a curl to each of the 3 main route prefixes and
# asserting the response code is NOT 502 and NOT 503. A 502 means Pitfall J has
# regressed (downstream service unreachable / depends_on misconfigured); a 503
# typically means rate-limited-with-empty-bucket which can also indicate the
# Redis backing store is unreachable. Any other code (200/401/404/500) proves
# the gateway forwarded the request — Phase 1 doesn't ship real endpoints, so
# downstream may legitimately reject (e.g. 401 from a missing/invalid Bearer).
#
# The Authorization header carries a placeholder Bearer that may or may not be
# accepted by the JwtVerifier; we only need the gateway to FORWARD the request,
# not for the downstream to authenticate it.
# -----------------------------------------------------------------------------
check_phase_01_routing() {
  local paths=(
    "/api/auth/anything"
    "/api/trips/anything"
    "/api/search/anything"
    "/api/destinations/anything"
  )
  local placeholder_token="${SMOKE_PLACEHOLDER_TOKEN:-eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJzbW9rZSJ9.invalid-on-purpose}"
  for path in "${paths[@]}"; do
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' \
            -H "Authorization: Bearer $placeholder_token" \
            "http://localhost:8080$path" 2>/dev/null || echo "000")
    case "$code" in
      502)
        fail "phase-01-routing: $path returned 502 — Pitfall J: downstream service unreachable from gateway. Check api-gateway.depends_on in infra/docker-compose.yml (plan 01-06) and that the downstream service is up (docker compose ps)." ;;
      503)
        fail "phase-01-routing: $path returned 503 — likely Redis unreachable (Pitfall H) or rate-limit empty-bucket. Check redis container health and api-gateway → redis depends_on." ;;
      000)
        fail "phase-01-routing: $path — curl could not reach gateway at localhost:8080. Is the stack up? (scripts/smoke.sh --up)" ;;
      *)
        : # any other code (200/401/403/404/500) proves the gateway forwarded
        ;;
    esac
  done
  pass "phase-01-routing 4 route prefixes (/api/{auth,trips,search,destinations}) forward through gateway (no 502/503)"
}

# -----------------------------------------------------------------------------
# Phase 1 — phase-01-rate-limit: SC#5 D-05 IP-only leg runtime gate
#
# Fires 35 successive POSTs to /api/auth/login. Plan 01-03's RedisRateLimiter
# is configured per RESEARCH.md Pattern 5 lines 698-700 (replenishRate=30,
# burstCapacity=30, requestedTokens=900) — burst of 30 is consumed in the first
# 30 requests; the 31st should be 429. We fire 35 to give a 5-request margin.
# If at least one response is 429, the rate limiter is wired and Redis is
# reachable. The body content is meaningless to the rate-limiter (it counts
# before reaching auth-service); a stub /api/auth/login from Phase 1 will
# 401/404 on most requests, which is fine — the assertion is purely on 429
# being SEEN at least once in the 35-request burst.
# -----------------------------------------------------------------------------
check_phase_01_rate_limit() {
  local saw_429=0
  local i
  for i in $(seq 1 35); do
    local code
    code=$(curl -s -o /dev/null -w '%{http_code}' \
            -X POST \
            -H 'Content-Type: application/json' \
            -d '{"email":"smoke@example.com","password":"smoke"}' \
            "http://localhost:8080/api/auth/login" 2>/dev/null || echo "000")
    if [ "$code" = "429" ]; then
      saw_429=1
      break
    fi
  done
  if [ "$saw_429" -ne 1 ]; then
    fail "phase-01-rate-limit: 35 successive POST /api/auth/login returned no 429 — RedisRateLimiter not enforcing (plan 01-03 application.yml). Verify (a) redis container healthy (docker compose ps redis), (b) spring.data.redis.host=redis in application-docker.yml (plan 01-03), (c) RedisRateLimiter filter on the auth-login route with replenishRate=30 burstCapacity=30 requestedTokens=900 (Pattern 5)."
  fi
  pass "phase-01-rate-limit POST /api/auth/login burst → 429 observed within 35 requests (D-05 IP-only leg)"
}

# -----------------------------------------------------------------------------
# Subcommand dispatch — first arg selects mode.
# -----------------------------------------------------------------------------
run_full_gate() {
  check_sc1
  check_sc2
  check_sc3
  check_sc3_route
  check_sc4
  check_sc5
  check_nfr_04
  check_phase_01_bypass
  check_phase_01_routing
  check_phase_01_rate_limit
  echo "[smoke] ALL PASS — Phase 0 SC#1-#5 + NFR-04 + Phase 1 (bypass / routing / rate-limit) met"
}

run_criterion() {
  local CRITERION="$1"
  case "$CRITERION" in
    1)        check_sc1 ;;
    2)        check_sc2 ;;
    3)        check_sc3 ;;
    3-route)  check_sc3_route ;;
    4)        check_sc4 ;;
    5)        check_sc5 ;;
    nfr-04)   check_nfr_04 ;;
    phase-01-bypass)     check_phase_01_bypass ;;
    phase-01-routing)    check_phase_01_routing ;;
    phase-01-rate-limit) check_phase_01_rate_limit ;;
    *)        fail "Unknown criterion '$CRITERION'. Use --list to see available criteria." ;;
  esac
}

main() {
  if [ "$#" -eq 0 ]; then
    run_full_gate
    return
  fi

  case "$1" in
    --help|-h)
      usage
      ;;
    --list)
      list_criteria
      ;;
    --up)
      if ! command -v docker >/dev/null 2>&1; then
        fail "--up: docker CLI not found"
      fi
      docker compose -f "$COMPOSE_FILE" up -d --wait --wait-timeout "$WAIT_TIMEOUT"
      ;;
    --down)
      if ! command -v docker >/dev/null 2>&1; then
        fail "--down: docker CLI not found"
      fi
      docker compose -f "$COMPOSE_FILE" down -v
      ;;
    --criterion)
      if [ "$#" -lt 2 ]; then
        fail "--criterion requires a value (1, 2, 3, 3-route, 4, 5, nfr-04, phase-01-bypass, phase-01-routing, phase-01-rate-limit). Use --list to enumerate."
      fi
      run_criterion "$2"
      ;;
    *)
      fail "Unknown flag '$1'. Use --help for usage."
      ;;
  esac
}

main "$@"
