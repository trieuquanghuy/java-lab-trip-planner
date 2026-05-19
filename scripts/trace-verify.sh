#!/bin/bash
# trace-verify.sh — End-to-end trace continuity verification against Zipkin.
# Verifies NFR-09: W3C trace context propagated across api-gateway → trip-service.
#
# Prerequisites: docker compose running (infra/docker-compose.yml), services healthy.
# Usage: ./scripts/trace-verify.sh

set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8180}"
ZIPKIN_URL="${ZIPKIN_URL:-http://localhost:9411}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[FAIL]${NC} $*"; }

# --- Step 1: Wait for services to be healthy ---
info "Checking service health..."

wait_for_health() {
  local url="$1"
  local name="$2"
  local max_attempts=30
  local attempt=0
  while [ $attempt -lt $max_attempts ]; do
    if curl -sf "${url}/actuator/health" > /dev/null 2>&1; then
      info "  $name is healthy"
      return 0
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  error "  $name not healthy after ${max_attempts} attempts"
  return 1
}

wait_for_health "$GATEWAY_URL" "api-gateway"

# --- Step 2: Mint a test JWT ---
info "Minting test JWT..."
if [ -f "$SCRIPT_DIR/mint-test-token.sh" ]; then
  TOKEN=$(bash "$SCRIPT_DIR/mint-test-token.sh" 2>/dev/null | tail -1)
else
  error "mint-test-token.sh not found at $SCRIPT_DIR/mint-test-token.sh"
  exit 1
fi

if [ -z "$TOKEN" ]; then
  error "Failed to mint test token"
  exit 1
fi
info "  Token minted successfully"

# --- Step 3: Create a trip (generates trace spanning gateway + trip-service) ---
info "Creating trip to generate trace..."
CREATE_RESPONSE=$(curl -sf -w "\n%{http_code}" \
  -X POST "${GATEWAY_URL}/api/trips" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: trace-verify-$(date +%s)" \
  -d '{"name":"Trace Verify Trip","description":"Automated trace verification","startDate":"2026-06-01","endDate":"2026-06-03"}')

HTTP_CODE=$(echo "$CREATE_RESPONSE" | tail -1)
BODY=$(echo "$CREATE_RESPONSE" | sed '$d')

if [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -ge 300 ]; then
  error "Create trip failed with HTTP $HTTP_CODE"
  echo "$BODY"
  exit 1
fi

TRIP_ID=$(echo "$BODY" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$TRIP_ID" ]; then
  # Try numeric id format
  TRIP_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
fi
info "  Created trip: $TRIP_ID"

# --- Step 4: Wait briefly for traces to flush to Zipkin ---
info "Waiting for traces to flush to Zipkin (3s)..."
sleep 3

# --- Step 5: Query Zipkin for recent traces ---
info "Querying Zipkin for traces..."

# Find traces from trip-service in the last 5 minutes
END_TS=$(($(date +%s) * 1000))
START_TS=$((($(date +%s) - 300) * 1000))

TRACES=$(curl -sf "${ZIPKIN_URL}/api/v2/traces?serviceName=trip-service&endTs=${END_TS}&lookback=300000&limit=5")

if [ -z "$TRACES" ] || [ "$TRACES" = "[]" ]; then
  error "No traces found in Zipkin for trip-service"
  warn "Ensure Zipkin is running at $ZIPKIN_URL and services are sending traces"
  exit 1
fi

# --- Step 6: Verify trace spans multiple services ---
info "Verifying trace continuity..."

# Extract the most recent trace and check for multi-service spans
TRACE_ID=$(echo "$TRACES" | grep -o '"traceId":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "$TRACE_ID" ]; then
  error "Could not extract traceId from Zipkin response"
  exit 1
fi

info "  Found traceId: $TRACE_ID"

# Get full trace details
TRACE_DETAIL=$(curl -sf "${ZIPKIN_URL}/api/v2/trace/${TRACE_ID}")

# Check for api-gateway spans
GW_SPANS=$(echo "$TRACE_DETAIL" | grep -o '"localEndpoint":{"serviceName":"api-gateway"' | wc -l | tr -d ' ')
# Check for trip-service spans
TRIP_SPANS=$(echo "$TRACE_DETAIL" | grep -o '"localEndpoint":{"serviceName":"trip-service"' | wc -l | tr -d ' ')

info "  api-gateway spans: $GW_SPANS"
info "  trip-service spans: $TRIP_SPANS"

# Verify all spans share the same traceId
UNIQUE_TRACE_IDS=$(echo "$TRACE_DETAIL" | grep -o '"traceId":"[^"]*"' | sort -u | wc -l | tr -d ' ')

PASS=true

if [ "$GW_SPANS" -eq 0 ]; then
  error "No api-gateway spans found in trace $TRACE_ID"
  PASS=false
fi

if [ "$TRIP_SPANS" -eq 0 ]; then
  error "No trip-service spans found in trace $TRACE_ID"
  PASS=false
fi

if [ "$UNIQUE_TRACE_IDS" -ne 1 ]; then
  error "Multiple traceIds found in single trace (expected 1, got $UNIQUE_TRACE_IDS)"
  PASS=false
fi

# --- Step 7: Report results ---
echo ""
if [ "$PASS" = true ]; then
  info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  info "  TRACE VERIFICATION PASSED"
  info "  traceId: $TRACE_ID"
  info "  Services: api-gateway ($GW_SPANS spans) + trip-service ($TRIP_SPANS spans)"
  info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  exit 0
else
  error "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  error "  TRACE VERIFICATION FAILED"
  error "  See diagnostics above"
  error "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  exit 1
fi
