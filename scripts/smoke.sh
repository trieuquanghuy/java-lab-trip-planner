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
#
# Phase 2 extension (02-07-PLAN.md):
#   Adds 5 new criteria — auth-1 / auth-2 / auth-3 / auth-4 / auth-5 — that
#   exercise the wired auth-service end-to-end against the running compose
#   stack (gateway -> auth-service -> Postgres -> Redis -> MailHog). They
#   are the operational counterpart to Plan 02-06's IT suite (Testcontainers
#   + GreenMail) and map 1:1 to ROADMAP Phase 2 SC#1-#5. Source:
#   .planning/ROADMAP.md Phase 2 Success Criteria #1-#5; 02-CONTEXT.md
#   D-01..D-23 (signup/verify/login/refresh/logout); 02-UI-SPEC.md
#   §Server-Driven Copy Contract (verbatim error detail strings).
#
#   Test-only credentials used by these criteria (T-2-07-02):
#     emails — smoke-1@test.local, smoke-2@test.local, smoke-4@test.local,
#              smoke-5-<ts>@test.local
#     password — smokepassword (≥8 chars, satisfies @Size(min=8))
#   None of these reach production; they exist only to drive the local
#   compose stack through Phase 2's signup→verify→login→refresh→logout
#   flow.

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
  auth-1    Phase 2 SC#1: signup → MailHog token → verify (302) → login (200)
  auth-2    Phase 2 SC#2: unverified login → 403 auth.email_not_verified; unknown → 400 auth.invalid_credentials (opaque)
  auth-3    Phase 2 SC#3: authenticated logout (204 + Max-Age=0) → reused refresh cookie 401 auth.refresh_invalid
  auth-4    Phase 2 SC#4: refresh-rotation cookie B != A; logout(B) → /refresh(B) 401 auth.refresh_invalid
  auth-5    Phase 2 SC#5 / NFR-05 IP+email leg: 6th failed login → 429 auth.rate_limited

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
  auth-1              — Phase 2 SC#1: signup → MailHog → verify (302) → login (200)
  auth-2              — Phase 2 SC#2: unverified login 403 + unknown-account opaque
  auth-3              — Phase 2 SC#3: authenticated logout (204) + post-logout refresh 401
  auth-4              — Phase 2 SC#4: refresh rotation + post-logout chain revocation
  auth-5              — Phase 2 SC#5 / NFR-05: 6th failed login → 429 auth.rate_limited
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

# =============================================================================
# Phase 2 — auth-N criterion checks (Plan 02-07 / ROADMAP SC#1-#5)
#
# These criteria exercise the wired auth-service end-to-end against the
# running compose stack:
#
#   gateway (:8080) -> auth-service (:8081) -> Postgres -> Redis -> MailHog
#
# Each criterion drives one of the 5 ROADMAP Phase 2 success criteria via
# curl + jq (with grep fallback). They are the operational counterpart to
# Plan 02-06's Testcontainers + GreenMail IT suite — the ITs prove the
# logic; these smoke checks prove the WIRED system works on a real
# `docker compose up` stack.
#
# Test-only credentials (per T-2-07-02 disposition):
#   - emails are *@test.local (RFC 6761 reserved TLD; never deliverable)
#   - password literal "smokepassword" satisfies the @Size(min=8) rule
#
# Verbatim contract assertions (UI-SPEC §Server-Driven Copy Contract):
#   - "Please verify your email before logging in." (auth.email_not_verified)
#   - "Email or password is incorrect."             (auth.invalid_credentials)
#   - "Session expired. Please log in again."       (auth.refresh_invalid)
#   - "Too many attempts. Please try again later."  (auth.rate_limited)
# =============================================================================

# Common gateway base URL for all auth-N checks (Plan 01-03 routing).
SMOKE_GATEWAY_URL="${SMOKE_GATEWAY_URL:-http://localhost:8080}"
# MailHog admin API (set in compose at 8025; same UI port the human inspects
# during the Plan 02-07 visual checkpoint).
SMOKE_MAILHOG_URL="${SMOKE_MAILHOG_URL:-http://localhost:8025}"

# --- Helpers shared by auth-N criteria ---------------------------------------

# require_jq_for_auth — auth-N criteria need jq to parse JSON responses
# (signup body's userId, MailHog message bodies, error envelopes). The
# Phase 0 grep fallback works for top-level "$.code":"value" patterns but
# not for nested .items[0].Content.Body extraction. Fail clearly rather
# than silently degrade.
require_jq_for_auth() {
  if [ "$HAVE_JQ" -ne 1 ]; then
    fail "jq is required for auth-N criteria (MailHog body parsing + JSON envelope assertions); install jq or run inside CI"
  fi
}

# auth_signup — POST /api/auth/signup; assert 201; print body to stdout.
# Args: $1 = email, $2 = password
auth_signup() {
  local email="$1"
  local password="$2"
  local resp
  resp=$(curl -s -o /tmp/smoke_signup_body.json -w '%{http_code}' \
          -X POST \
          -H 'Content-Type: application/json' \
          -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
          "$SMOKE_GATEWAY_URL/api/auth/signup" 2>/dev/null || echo "000")
  if [ "$resp" != "201" ]; then
    fail "auth signup($email) returned HTTP $resp (expected 201). Body: $(cat /tmp/smoke_signup_body.json 2>/dev/null || echo '<empty>')"
  fi
  cat /tmp/smoke_signup_body.json
}

# auth_extract_token_from_mailhog — fetch most-recent MailHog message
# addressed to $1 and extract the 64-hex token following `?token=` in the
# body. UI-SPEC §Email Copy Contract locks the URL on its own line as
# `http://localhost:8080/api/auth/verify?token=<64-hex>`.
#
# IMPORTANT: MailHog stores message bodies in MIME quoted-printable
# encoding (each line ≤76 chars; soft line breaks are `=\n`; literal `=`
# is encoded as `=3D`; multi-byte UTF-8 like the U+2014 em-dash is
# `=E2=80=94`). Python `quopri.decodestring(...)` is the smallest portable
# tool to round-trip these. Falls back to a sed-based decoder if Python
# is unavailable (rare on macOS / linux).
#
# Polls MailHog for up to SMOKE_MAILHOG_TIMEOUT seconds (default 15) since
# the @Async send is decoupled from the signup transaction (D-01) and on
# a busy machine the queued send can take a few seconds to land.
#
# Args: $1 = recipient email
# Echos the token to stdout (or fails).
auth_extract_token_from_mailhog() {
  local recipient="$1"
  local timeout="${SMOKE_MAILHOG_TIMEOUT:-15}"
  local body=""
  local elapsed=0
  while [ "$elapsed" -lt "$timeout" ]; do
    local mailhog_body
    if ! mailhog_body=$(curl -sf "$SMOKE_MAILHOG_URL/api/v2/messages" 2>/dev/null); then
      fail "MailHog API unreachable at $SMOKE_MAILHOG_URL/api/v2/messages — is mailhog container up?"
    fi

    # Find the most recent message with a To.Mailbox+Domain matching the
    # recipient. MailHog v2 API returns .items[] sorted newest-first.
    body=$(printf '%s' "$mailhog_body" \
            | jq -r --arg r "$recipient" \
                '[.items[] | select(.To[]? | (.Mailbox + "@" + .Domain) == $r)] | .[0].Content.Body')
    if [ -n "$body" ] && [ "$body" != "null" ]; then
      break
    fi
    sleep 1
    elapsed=$((elapsed + 1))
  done

  if [ -z "$body" ] || [ "$body" = "null" ]; then
    fail "No MailHog message found for $recipient after ${timeout}s (searched .items[].To[]). Did the @Async send fire? Check auth-service logs for D-04 MailException WARN."
  fi

  # Decode MIME quoted-printable so the token is contiguous and = signs
  # round-trip cleanly. Python is the cheapest tool available on every
  # CI runner; fall back to the awk approximation if missing.
  local decoded
  if command -v python3 >/dev/null 2>&1; then
    decoded=$(printf '%s' "$body" | python3 -c 'import sys, quopri; sys.stdout.write(quopri.decodestring(sys.stdin.read().encode("utf-8")).decode("utf-8", "replace"))')
  else
    # Awk fallback: strip soft line breaks (`=\n`) then decode `=HH` runs.
    decoded=$(printf '%s' "$body" | awk 'BEGIN{RS=""}{gsub(/=\n/,""); n=split($0,a,""); for(i=1;i<=n;i++){if(a[i]=="=" && i+2<=n){h=a[i+1] a[i+2]; printf "%c", strtonum("0x" h); i+=2}else{printf "%s", a[i]}}}')
  fi

  # Extract the 64-hex token. UI-SPEC body has the URL on its own line:
  # `http://localhost:8080/api/auth/verify?token=<64hex>`.
  local token
  token=$(printf '%s' "$decoded" | grep -oE 'token=[a-f0-9]{64}' | head -1 | cut -d= -f2)
  if [ -z "$token" ]; then
    fail "Could not extract 64-hex token from MailHog body for $recipient. Decoded preview: $(printf '%s' "$decoded" | head -c 200)"
  fi

  printf '%s' "$token"
}

# auth_verify_token — GET /api/auth/verify?token=…; assert 302 and that
# Location ends with /verify?status=success. Spring's controller emits
# `Location: ${app.frontend.base-url}/verify?status=<value>` per UI-SPEC
# §Redirect Query-Param Contract.
# Args: $1 = token
auth_verify_token() {
  local token="$1"
  local headers_file=/tmp/smoke_verify_headers.txt
  local code
  # -L NOT used; we want the 302 itself, not the followed redirect.
  code=$(curl -s -o /dev/null -D "$headers_file" -w '%{http_code}' \
          "$SMOKE_GATEWAY_URL/api/auth/verify?token=$token" 2>/dev/null || echo "000")
  if [ "$code" != "302" ]; then
    fail "auth verify(token) returned HTTP $code (expected 302). Headers: $(cat "$headers_file" 2>/dev/null || echo '<none>')"
  fi
  if ! grep -iE '^location:.*[?&]status=success' "$headers_file" >/dev/null 2>&1; then
    fail "auth verify(token) Location header did not contain ?status=success. Headers: $(cat "$headers_file")"
  fi
}

# auth_login — POST /api/auth/login; on success persists access JWT + cookie
# jar to /tmp/smoke_<tag>.{jwt,cookies}. Asserts HTTP 200 + body has
# accessToken + Set-Cookie has refresh_token.
# Args: $1 = email, $2 = password, $3 = tag (used to namespace the temp files)
auth_login() {
  local email="$1"
  local password="$2"
  local tag="$3"
  local body_file="/tmp/smoke_login_${tag}.json"
  local cookies_file="/tmp/smoke_login_${tag}.cookies"
  local code
  code=$(curl -s -o "$body_file" -c "$cookies_file" -w '%{http_code}' \
          -X POST \
          -H 'Content-Type: application/json' \
          -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
          "$SMOKE_GATEWAY_URL/api/auth/login" 2>/dev/null || echo "000")
  if [ "$code" != "200" ]; then
    fail "auth login($email) returned HTTP $code (expected 200). Body: $(cat "$body_file" 2>/dev/null || echo '<empty>')"
  fi
  if ! jq -e '.accessToken | type == "string" and length > 0' "$body_file" >/dev/null 2>&1; then
    fail "auth login($email) body missing .accessToken: $(cat "$body_file")"
  fi
  if ! grep -q 'refresh_token' "$cookies_file"; then
    fail "auth login($email) did not set refresh_token cookie. Cookie jar: $(cat "$cookies_file")"
  fi
  jq -r '.accessToken' "$body_file"
}

# -----------------------------------------------------------------------------
# auth-1 — Signup → verify-via-MailHog → login E2E (ROADMAP SC#1)
#
# Full happy-path chain through the wired stack. This is the only criterion
# that touches MailHog's admin API to extract the verification token (the
# token never reaches the SPA per D-02 — it's server-side only). Asserts
# the redirect-status-success contract (UI-SPEC §Redirect Query-Param
# Contract) and that login mints a refresh cookie + access JWT.
# -----------------------------------------------------------------------------
check_auth_1() {
  require_jq_for_auth
  local email="smoke-1@test.local"
  local password="smokepassword"

  # Step 1: signup → 201
  local signup_body
  signup_body=$(auth_signup "$email" "$password")
  if ! printf '%s' "$signup_body" | jq -e '.userId | type == "string" and length > 0' >/dev/null 2>&1; then
    fail "auth-1: signup body missing .userId: $signup_body"
  fi

  # Step 2/3: poll MailHog (the helper waits up to SMOKE_MAILHOG_TIMEOUT seconds
  # for the D-01 @Async send to land — no fixed sleep so we don't waste budget).
  local token
  token=$(auth_extract_token_from_mailhog "$email")

  # Step 4: GET /api/auth/verify?token=<token> → 302 + Location ?status=success
  auth_verify_token "$token"

  # Step 5: login → 200 + accessToken + refresh_token cookie
  auth_login "$email" "$password" "auth1" >/dev/null

  pass "auth-1 signup → MailHog token → verify (302) → login (200) E2E (SC#1)"
}

# -----------------------------------------------------------------------------
# auth-2 — Unverified account cannot log in; opaque error (ROADMAP SC#2)
#
# Asserts (a) the verbatim UI-SPEC detail string for auth.email_not_verified
# and (b) the account-enumeration policy from docs/05 §9.1 — both
# `auth.email_not_verified` (existing-but-unverified) and
# `auth.invalid_credentials` (unknown email) are emitted from /login such
# that an attacker cannot distinguish "no account" from "exists but
# unverified" via a single error code (the codes themselves differ but
# both response shapes are generic and timing is balanced via D-05's
# bcrypt-of-empty-string dummy hash).
# -----------------------------------------------------------------------------
check_auth_2() {
  require_jq_for_auth
  local email="smoke-2@test.local"
  local password="smokepassword"

  # Step 1: signup → 201 (account exists but stays unverified — skip verify)
  auth_signup "$email" "$password" >/dev/null

  # Step 2: login the unverified account → 403 auth.email_not_verified
  local body_file=/tmp/smoke_auth2_login.json
  local code
  code=$(curl -s -o "$body_file" -w '%{http_code}' \
          -X POST \
          -H 'Content-Type: application/json' \
          -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
          "$SMOKE_GATEWAY_URL/api/auth/login" 2>/dev/null || echo "000")
  if [ "$code" != "403" ]; then
    fail "auth-2: unverified login returned HTTP $code (expected 403). Body: $(cat "$body_file")"
  fi
  if ! jq -e '.code == "auth.email_not_verified"' "$body_file" >/dev/null 2>&1; then
    fail "auth-2: unverified login body .code != \"auth.email_not_verified\". Body: $(cat "$body_file")"
  fi
  if ! jq -e '.detail == "Please verify your email before logging in."' "$body_file" >/dev/null 2>&1; then
    fail "auth-2: unverified login .detail does not match UI-SPEC verbatim 'Please verify your email before logging in.'. Body: $(cat "$body_file")"
  fi

  # Step 3: login an unknown account → 400 auth.invalid_credentials
  # (Account-enumeration policy: response shape is generic; an attacker
  # cannot infer existence solely from the error code/detail/timing.)
  body_file=/tmp/smoke_auth2_unknown.json
  code=$(curl -s -o "$body_file" -w '%{http_code}' \
          -X POST \
          -H 'Content-Type: application/json' \
          -d '{"email":"never@registered.local","password":"smokepassword"}' \
          "$SMOKE_GATEWAY_URL/api/auth/login" 2>/dev/null || echo "000")
  if [ "$code" != "400" ]; then
    fail "auth-2: unknown-account login returned HTTP $code (expected 400). Body: $(cat "$body_file")"
  fi
  if ! jq -e '.code == "auth.invalid_credentials"' "$body_file" >/dev/null 2>&1; then
    fail "auth-2: unknown-account login body .code != \"auth.invalid_credentials\". Body: $(cat "$body_file")"
  fi

  pass "auth-2 unverified-cannot-login (403 auth.email_not_verified) + opaque unknown (400 auth.invalid_credentials) (SC#2)"
}

# -----------------------------------------------------------------------------
# auth-3 — Authenticated route + logout invalidation (ROADMAP SC#3)
#
# CONTEXT D-23 + RESEARCH Open Q1: auth-service does NOT ship /api/auth/me;
# the SPA decodes the JWT client-side. The "authenticated route" assertion
# is therefore proven by /api/auth/logout itself — it is a Bearer-protected
# endpoint, returns 204, and clears the cookie. After logout, the same
# refresh cookie at /api/auth/refresh must return 401 auth.refresh_invalid
# (D-11 — chain head revoked).
# -----------------------------------------------------------------------------
check_auth_3() {
  require_jq_for_auth

  # Re-use smoke-1's verified user; mint a fresh JWT + cookie.
  local email="smoke-1@test.local"
  local password="smokepassword"
  local cookies_file=/tmp/smoke_login_auth3.cookies
  local body_file=/tmp/smoke_login_auth3.json

  local code
  code=$(curl -s -o "$body_file" -c "$cookies_file" -w '%{http_code}' \
          -X POST \
          -H 'Content-Type: application/json' \
          -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
          "$SMOKE_GATEWAY_URL/api/auth/login" 2>/dev/null || echo "000")
  if [ "$code" != "200" ]; then
    fail "auth-3: setup login($email) returned HTTP $code (expected 200). If smoke-1 was not verified earlier, run --criterion auth-1 first. Body: $(cat "$body_file")"
  fi
  local jwt
  jwt=$(jq -r '.accessToken' "$body_file")

  # Step 1: POST /api/auth/logout with Bearer + refresh cookie → 204 +
  # Set-Cookie has Max-Age=0 (D-11 + D-12 cookie-clear).
  local headers_file=/tmp/smoke_logout_headers.txt
  code=$(curl -s -o /dev/null -D "$headers_file" -w '%{http_code}' \
          -X POST \
          -H "Authorization: Bearer $jwt" \
          -b "$cookies_file" \
          "$SMOKE_GATEWAY_URL/api/auth/logout" 2>/dev/null || echo "000")
  if [ "$code" != "204" ]; then
    fail "auth-3: logout returned HTTP $code (expected 204). Headers: $(cat "$headers_file" 2>/dev/null || echo '<none>')"
  fi
  if ! grep -iE '^set-cookie:.*refresh_token=' "$headers_file" | grep -iE 'max-age=0' >/dev/null 2>&1; then
    fail "auth-3: logout did not emit Set-Cookie: refresh_token=...; Max-Age=0. Headers: $(cat "$headers_file")"
  fi

  # Step 2: POST /api/auth/refresh with the SAME (now-revoked) cookie → 401
  # auth.refresh_invalid (D-11 chain head revoked).
  body_file=/tmp/smoke_auth3_refresh.json
  code=$(curl -s -o "$body_file" -w '%{http_code}' \
          -X POST \
          -b "$cookies_file" \
          "$SMOKE_GATEWAY_URL/api/auth/refresh" 2>/dev/null || echo "000")
  if [ "$code" != "401" ]; then
    fail "auth-3: post-logout /refresh returned HTTP $code (expected 401). Body: $(cat "$body_file")"
  fi
  if ! jq -e '.code == "auth.refresh_invalid"' "$body_file" >/dev/null 2>&1; then
    fail "auth-3: post-logout /refresh body .code != \"auth.refresh_invalid\". Body: $(cat "$body_file")"
  fi

  pass "auth-3 authenticated route + logout invalidation (204 + Max-Age=0 + post-logout 401 auth.refresh_invalid) (SC#3)"
}

# -----------------------------------------------------------------------------
# auth-4 — Refresh rotation + post-logout chain revocation (ROADMAP SC#4)
#
# D-13 (REPEATABLE_READ + SELECT FOR UPDATE) and D-10 (BOTH-directions chain
# revocation on replay) are the production-code anchors. Smoke-level
# assertions:
#   1. /refresh issues a NEW cookie (cookie B != cookie A).
#   2. logout(B) revokes the chain head; subsequent /refresh(B) → 401.
#
# We don't replay cookie A here — that's Plan 02-06's
# RotatedRefreshTokenCannotBeReusedIT (security IT). Smoke verifies the
# WIRED rotation + revocation behavior end-to-end against compose.
# -----------------------------------------------------------------------------
check_auth_4() {
  require_jq_for_auth
  local email="smoke-4@test.local"
  local password="smokepassword"

  # Setup: signup + verify + login (full chain — smoke-4 is fresh per run).
  auth_signup "$email" "$password" >/dev/null
  local token
  token=$(auth_extract_token_from_mailhog "$email")
  auth_verify_token "$token"

  local cookies_a=/tmp/smoke_auth4_a.cookies
  local cookies_b=/tmp/smoke_auth4_b.cookies
  local body_file=/tmp/smoke_auth4.json
  local code

  # Login → cookie A
  code=$(curl -s -o "$body_file" -c "$cookies_a" -w '%{http_code}' \
          -X POST \
          -H 'Content-Type: application/json' \
          -d "{\"email\":\"$email\",\"password\":\"$password\"}" \
          "$SMOKE_GATEWAY_URL/api/auth/login" 2>/dev/null || echo "000")
  if [ "$code" != "200" ]; then
    fail "auth-4: login($email) returned HTTP $code (expected 200). Body: $(cat "$body_file")"
  fi
  local cookie_a_value
  cookie_a_value=$(grep -i 'refresh_token' "$cookies_a" | awk '{print $7}' | tail -1)
  if [ -z "$cookie_a_value" ]; then
    fail "auth-4: cookie A (refresh_token) not present in jar. Jar: $(cat "$cookies_a")"
  fi

  # Step 1: /refresh with cookie A → 200 + new accessToken + cookie B != A
  code=$(curl -s -o "$body_file" -c "$cookies_b" -w '%{http_code}' \
          -X POST \
          -b "$cookies_a" \
          "$SMOKE_GATEWAY_URL/api/auth/refresh" 2>/dev/null || echo "000")
  if [ "$code" != "200" ]; then
    fail "auth-4: /refresh(A) returned HTTP $code (expected 200). Body: $(cat "$body_file")"
  fi
  if ! jq -e '.accessToken | type == "string" and length > 0' "$body_file" >/dev/null 2>&1; then
    fail "auth-4: /refresh(A) body missing .accessToken: $(cat "$body_file")"
  fi
  local cookie_b_value
  cookie_b_value=$(grep -i 'refresh_token' "$cookies_b" | awk '{print $7}' | tail -1)
  if [ -z "$cookie_b_value" ]; then
    fail "auth-4: cookie B (refresh_token) not present in jar after rotation. Jar: $(cat "$cookies_b")"
  fi
  if [ "$cookie_a_value" = "$cookie_b_value" ]; then
    fail "auth-4: rotation did NOT issue a new refresh_token cookie value (A == B). D-13 RefreshTokenService.rotate is not rotating."
  fi

  # Step 2: /logout with cookie B → 204
  local headers_file=/tmp/smoke_auth4_logout_headers.txt
  code=$(curl -s -o /dev/null -D "$headers_file" -w '%{http_code}' \
          -X POST \
          -b "$cookies_b" \
          "$SMOKE_GATEWAY_URL/api/auth/logout" 2>/dev/null || echo "000")
  if [ "$code" != "204" ]; then
    fail "auth-4: logout(B) returned HTTP $code (expected 204). Headers: $(cat "$headers_file" 2>/dev/null || echo '<none>')"
  fi

  # Step 3: /refresh with cookie B (now revoked) → 401 auth.refresh_invalid
  code=$(curl -s -o "$body_file" -w '%{http_code}' \
          -X POST \
          -b "$cookies_b" \
          "$SMOKE_GATEWAY_URL/api/auth/refresh" 2>/dev/null || echo "000")
  if [ "$code" != "401" ]; then
    fail "auth-4: post-logout /refresh(B) returned HTTP $code (expected 401). Body: $(cat "$body_file")"
  fi
  if ! jq -e '.code == "auth.refresh_invalid"' "$body_file" >/dev/null 2>&1; then
    fail "auth-4: post-logout /refresh(B) body .code != \"auth.refresh_invalid\". Body: $(cat "$body_file")"
  fi

  pass "auth-4 refresh-rotation (cookie B != A) + post-logout chain revocation (401 auth.refresh_invalid) (SC#4)"
}

# -----------------------------------------------------------------------------
# auth-5 — IP+email login rate-limit at 6th attempt (ROADMAP SC#5 / NFR-05)
#
# D-05 / D-06 / D-07 / D-08: LoginRateLimiter Lua script keys on
# `rl:login:fail:{ip}:{email_lower}`, threshold ≥ 5 (attempts 1-5 OK; 6th
# trips). Failed attempts only — successful login DELETEs the key. We use
# a freshly-minted email per run to defeat any leftover Redis state.
#
# UI-SPEC verbatim assertions:
#   - $.code == "auth.rate_limited"
#   - $.detail == "Too many attempts. Please try again later."
# -----------------------------------------------------------------------------
check_auth_5() {
  require_jq_for_auth
  local email="smoke-5-$(date +%s)@test.local"
  local password="smokepassword"
  local body_file=/tmp/smoke_auth5.json

  # Attempts 1-5: each returns 400 auth.invalid_credentials (D-09 — email
  # case-normalized; account does not exist; D-05 dummy bcrypt hash drives
  # constant-latency timing). The rate-limit key is INCR'd on every failed
  # attempt regardless of whether the account exists.
  local i
  for i in 1 2 3 4 5; do
    local code
    code=$(curl -s -o "$body_file" -w '%{http_code}' \
            -X POST \
            -H 'Content-Type: application/json' \
            -d "{\"email\":\"$email\",\"password\":\"wrongpassword$i\"}" \
            "$SMOKE_GATEWAY_URL/api/auth/login" 2>/dev/null || echo "000")
    if [ "$code" != "400" ]; then
      fail "auth-5: attempt $i returned HTTP $code (expected 400 auth.invalid_credentials). Body: $(cat "$body_file")"
    fi
    if ! jq -e '.code == "auth.invalid_credentials"' "$body_file" >/dev/null 2>&1; then
      fail "auth-5: attempt $i body .code != \"auth.invalid_credentials\". Body: $(cat "$body_file")"
    fi
  done

  # Attempt 6: trips the rate limiter → 429 auth.rate_limited.
  local code
  code=$(curl -s -o "$body_file" -w '%{http_code}' \
          -X POST \
          -H 'Content-Type: application/json' \
          -d "{\"email\":\"$email\",\"password\":\"wrongpassword6\"}" \
          "$SMOKE_GATEWAY_URL/api/auth/login" 2>/dev/null || echo "000")
  if [ "$code" != "429" ]; then
    fail "auth-5: 6th attempt returned HTTP $code (expected 429 auth.rate_limited). Body: $(cat "$body_file"). Verify (a) Redis healthy, (b) LoginRateLimiter wired into AuthService.login (D-05), (c) Lua INCR+EXPIRE script atomic (Plan 02-03)."
  fi
  if ! jq -e '.code == "auth.rate_limited"' "$body_file" >/dev/null 2>&1; then
    fail "auth-5: 6th attempt body .code != \"auth.rate_limited\". Body: $(cat "$body_file")"
  fi
  if ! jq -e '.detail == "Too many attempts. Please try again later."' "$body_file" >/dev/null 2>&1; then
    fail "auth-5: 6th attempt .detail does not match UI-SPEC verbatim 'Too many attempts. Please try again later.'. Body: $(cat "$body_file")"
  fi

  pass "auth-5 6th failed login → 429 auth.rate_limited + verbatim UI-SPEC detail (SC#5 / NFR-05 IP+email leg)"
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
  check_auth_1
  check_auth_2
  check_auth_3
  check_auth_4
  check_auth_5
  echo "[smoke] ALL PASS — Phase 0 SC#1-#5 + NFR-04 + Phase 1 (bypass / routing / rate-limit) + Phase 2 (auth-1..auth-5) met"
  echo "[OK] PHASE 2 SMOKE: 5/5 auth criteria passed"
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
    auth-1)   check_auth_1 ;;
    auth-2)   check_auth_2 ;;
    auth-3)   check_auth_3 ;;
    auth-4)   check_auth_4 ;;
    auth-5)   check_auth_5 ;;
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
        fail "--criterion requires a value (1, 2, 3, 3-route, 4, 5, nfr-04, phase-01-bypass, phase-01-routing, phase-01-rate-limit, auth-1, auth-2, auth-3, auth-4, auth-5). Use --list to enumerate."
      fi
      run_criterion "$2"
      ;;
    *)
      fail "Unknown flag '$1'. Use --help for usage."
      ;;
  esac
}

main "$@"
