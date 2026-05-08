// Source: 01-CONTEXT.md D-04 (UserContext shape — userId/email/verified);
//         01-CONTEXT.md D-16 (AUTH_JWT_SECRET single-property contract);
//         01-RESEARCH.md lines 1021-1076 (JwtFixtures code example, Open Questions RESOLVED);
//         01-PATTERNS.md Bucket A (testFixtures source set wired in Plan 01-01).
//
// Used by: 01-04 DirectServiceAccessIT (trip + destination), 01-05 PingControllerIT,
//          01-06 JwtFixturesSmokeMintTask (-> scripts/mint-test-token.sh).
// Convention C28-P1: TEST_SECRET is >= 32 bytes (HS256 RFC 7518 §3.2). 36-byte placeholder.
package com.tripplanner.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public final class JwtFixtures {

    public static final String TEST_SECRET = "phase-1-jwt-fixture-secret-32bytes!!";

    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtFixtures() {}

    public static String mintValid(String userId, String email) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("ver", true)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(900)))  // 15 min
                .signWith(KEY)
                .compact();
    }

    public static String mintExpired(String userId, String email) {
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("ver", true)
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))  // expired 1 min ago
                .signWith(KEY)
                .compact();
    }

    public static String mintWrongSig(String userId, String email) {
        // Sign with a DIFFERENT 32-byte key so JwtVerifier (using TEST_SECRET) rejects.
        byte[] otherBytes = "DIFFERENT-32-byte-key-for-wrong-sig!".getBytes(StandardCharsets.UTF_8);
        SecretKey otherKey = Keys.hmacShaKeyFor(otherBytes);
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .expiration(Date.from(Instant.now().plusSeconds(900)))
                .signWith(otherKey)
                .compact();
    }

    public static String mintMalformed() {
        // Not a JWT at all — three random non-base64 segments.
        return "not.a.real.jwt";
    }
}
