// Source: 02-RESEARCH.md §Code Examples lines 821-871 (JwtIssuer reference shape);
//         02-PATTERNS.md §JwtIssuerTest (sibling-twin to JwtVerifierTest);
//         02-CONTEXT.md Open Question 3 RESOLVED — JwtIssuer in libs/jwt-common.
//
// RED-by-design — JwtIssuer doesn't exist until Task 2.2. Compilation will fail until 2.2 lands.
// Once 2.2 lands, this test must pass without modification.
//
// Tests all 7 required cases:
// (1) issued token round-trips through JwtVerifier (verified=true)
// (2) issued token round-trips through JwtVerifier (verified=false)
// (3) issued token has 15-minute expiry (docs/05 §1)
// (4) issued tokens have unique jti claims (UUID per token)
// (5) issued token carries iss=tripplanner-auth
// (6) constructor throws IllegalStateException on null secret
// (7) constructor throws IllegalStateException on secret < 32 bytes
package com.tripplanner.jwt;

import com.tripplanner.contracts.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtIssuerTest {

    private final JwtIssuer issuer = new JwtIssuer(JwtFixtures.TEST_SECRET);
    private final JwtVerifier verifier = new JwtVerifier(JwtFixtures.TEST_SECRET);

    @Test
    void issued_token_round_trips_through_verifier() throws Exception {
        String token = issuer.issueAccess("user-1", "u@example.com", true);
        UserContext ctx = verifier.verify(token);
        assertThat(ctx.userId()).isEqualTo("user-1");
        assertThat(ctx.email()).isEqualTo("u@example.com");
        assertThat(ctx.verified()).isTrue();
    }

    @Test
    void issued_token_unverified_account_round_trips() throws Exception {
        String token = issuer.issueAccess("user-2", "v@example.com", false);
        UserContext ctx = verifier.verify(token);
        assertThat(ctx.verified()).isFalse();
    }

    @Test
    void issued_token_has_15_minute_expiry() {
        String token = issuer.issueAccess("user-3", "x@e.com", true);
        Claims c = parsePayload(token);
        long ttl = c.getExpiration().toInstant().getEpochSecond() - c.getIssuedAt().toInstant().getEpochSecond();
        assertThat(ttl).isBetween(898L, 902L);   // 15 minutes ±2s for any millisecond rounding
    }

    @Test
    void issued_token_has_unique_jti() {
        Claims c1 = parsePayload(issuer.issueAccess("u", "a@b.c", true));
        Claims c2 = parsePayload(issuer.issueAccess("u", "a@b.c", true));
        assertThat(c1.getId()).isNotBlank();
        assertThat(c2.getId()).isNotBlank();
        assertThat(c1.getId()).isNotEqualTo(c2.getId());
    }

    @Test
    void issued_token_carries_iss_tripplanner_auth() {
        Claims c = parsePayload(issuer.issueAccess("u", "a@b.c", true));
        assertThat(c.getIssuer()).isEqualTo("tripplanner-auth");
    }

    @Test
    void issuer_throws_on_null_secret() {
        assertThatThrownBy(() -> new JwtIssuer(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    @Test
    void issuer_throws_on_short_secret() {
        assertThatThrownBy(() -> new JwtIssuer("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    /** jjwt 0.13.0 modern parser — Convention C27-P1. */
    private Claims parsePayload(String token) {
        SecretKey key = Keys.hmacShaKeyFor(JwtFixtures.TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        return jws.getPayload();
    }
}
