// Source: 01-VALIDATION.md Wave 0 contract "JwtVerifier";
//         01-CONTEXT.md D-02 (JWT verified twice — gateway + downstream defense-in-depth);
//         01-PATTERNS.md Bucket B lines 286-326 (JwtVerifier shape).
//         Convention C35-P1.
//
// Tests all 7 required cases:
// (a) valid HS256 → UserContext with userId/email/verified
// (b) expired → JwtAuthenticationException with message containing "expired"
// (c) wrong signature → JwtAuthenticationException mapping to AUTH_INVALID_TOKEN
// (d) malformed → JwtAuthenticationException mapping to AUTH_INVALID_TOKEN
// (e) missing sub → JwtAuthenticationException with message containing "missing 'sub'"
// (f) null secret → IllegalStateException
// (g) secret < 32 bytes → IllegalStateException
package com.tripplanner.jwt;

import com.tripplanner.contracts.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierTest {

    private JwtVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new JwtVerifier(JwtFixtures.TEST_SECRET);
    }

    @Test
    void validTokenReturnsUserContext() {
        String token = JwtFixtures.mintValid("user-1", "user@example.com");
        UserContext ctx = verifier.verify(token);
        assertThat(ctx.userId()).isEqualTo("user-1");
        assertThat(ctx.email()).isEqualTo("user@example.com");
        assertThat(ctx.verified()).isTrue();
    }

    @Test
    void expiredTokenThrowsWithExpiredMessage() {
        String token = JwtFixtures.mintExpired("user-1", "user@example.com");
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("expired")
                .satisfies(ex -> assertThat(((JwtAuthenticationException) ex).reason())
                        .isEqualTo(JwtAuthenticationException.Reason.EXPIRED));
    }

    @Test
    void wrongSignatureTokenThrowsJwtAuthException() {
        String token = JwtFixtures.mintWrongSig("user-1", "user@example.com");
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void malformedTokenThrowsJwtAuthException() {
        String token = JwtFixtures.mintMalformed();
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(JwtAuthenticationException.class);
    }

    @Test
    void tokenMissingSubThrowsWithMissingSubMessage() {
        // Mint a token without subject claim — use JwtFixtures raw builder
        String noSubToken = io.jsonwebtoken.Jwts.builder()
                .claim("email", "user@example.com")
                .claim("ver", true)
                .issuedAt(java.util.Date.from(java.time.Instant.now()))
                .expiration(java.util.Date.from(java.time.Instant.now().plusSeconds(900)))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        JwtFixtures.TEST_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThatThrownBy(() -> verifier.verify(noSubToken))
                .isInstanceOf(JwtAuthenticationException.class)
                .hasMessageContaining("missing 'sub'");
    }

    @Test
    void nullSecretThrowsIllegalStateException() {
        assertThatThrownBy(() -> new JwtVerifier(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be set");
    }

    @Test
    void shortSecretThrowsIllegalStateException() {
        assertThatThrownBy(() -> new JwtVerifier("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
