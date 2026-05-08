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
  echo "[smoke] ALL PASS — Phase 0 ROADMAP success criteria #1-#5 + NFR-04 met"
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
        fail "--criterion requires a value (1, 2, 3, 3-route, 4, 5, nfr-04). Use --list to enumerate."
      fi
      run_criterion "$2"
      ;;
    *)
      fail "Unknown flag '$1'. Use --help for usage."
      ;;
  esac
}

main "$@"
