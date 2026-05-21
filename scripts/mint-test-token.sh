#!/usr/bin/env bash
# scripts/mint-test-token.sh — Phase 1 W5 closure (Plan 01-06 Task 6.2 Part C).
#
# Source: 01-CONTEXT.md D-16 (single AUTH_JWT_SECRET property);
#         01-RESEARCH.md JwtFixtures example lines 1021-1076 (Open Questions RESOLVED);
#         01-PATTERNS.md Bucket A (testFixtures source set wired in Plan 01-01).
# Convention C14-P1: script does NOT embed any literal secret; the gradle test class
#   `JwtFixturesSmokeMintTask` (libs/jwt-common test source set, Plan 01-02 Task 2.2)
#   uses `JwtFixtures.TEST_SECRET` from the testFixtures jar.
# T-01-15 mitigation: literal `AUTH_JWT_SECRET` value never appears in this script.
#
# Usage:
#   TOKEN=$(bash scripts/mint-test-token.sh)
#   curl -H "Authorization: Bearer $TOKEN" http://localhost:8180/api/trips/_ping
set -euo pipefail

cd "$(dirname "$0")/.."

# Run the mintTestToken JavaExec task quietly. MintTokenMain prints the JWT to stdout.
# Unlike the previous approach (running a @Test class), this does NOT persist the token
# in JUnit XML test reports under build/test-results/.
# Filter for the JWT shape (three base64-url segments separated by dots) and require
# length > 60 to avoid false matches on dotted class names.
./gradlew :libs:jwt-common:mintTestToken -q 2>/dev/null \
  | grep -E '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$' \
  | awk 'length > 60' \
  | head -n1
