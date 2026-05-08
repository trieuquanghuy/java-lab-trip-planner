// Source: 01-CONTEXT.md D-16 (AUTH_JWT_SECRET — no literal secret in shell scripts);
//         01-VALIDATION.md T-01-15 (JwtFixturesSmokeMintTask — scripts/mint-test-token.sh invokes this);
//         01-PATTERNS.md Bucket A (testFixtures source set wired in Plan 01-01).
//         Convention C35-P1.
//
// Single-method @Test that mints a valid JWT via JwtFixtures and prints to stdout.
// Plan 01-06's scripts/mint-test-token.sh captures the printed token via:
//   ./gradlew :libs:jwt-common:test --tests JwtFixturesSmokeMintTask -q --console=plain
package com.tripplanner.jwt;

import org.junit.jupiter.api.Test;

class JwtFixturesSmokeMintTask {

    @Test
    void mintAndPrint() {
        String token = JwtFixtures.mintValid("smoke-user", "smoke@example.com");
        System.out.println(token);
    }
}
