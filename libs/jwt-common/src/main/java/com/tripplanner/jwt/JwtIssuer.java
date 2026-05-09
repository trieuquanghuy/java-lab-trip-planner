// Source: docs/05-auth-security.md §1 (15-min TTL), §2 (claims iss/sub/iat/exp/email/ver/jti).
//         02-CONTEXT.md D-20 (HS256, jjwt 0.13.0).
//         02-PATTERNS.md §JwtIssuer (sibling-twin to JwtVerifier — constructor body byte-for-byte
//         from JwtVerifier.java lines 26-38).
//         02-RESEARCH.md §Code Examples lines 821-871 (verbatim shape).
//         02-CONTEXT.md Open Question 3 RESOLVED — JwtIssuer in libs/jwt-common.
//
// Convention C27-P1: jjwt 0.13.0 modern builder API — Jwts.builder().subject().issuedAt()...
//   .signWith(SecretKey).compact(). NEVER the deprecated 0.11.x byte-array signing-key shape.
// Convention C28-P1: HS256 RFC 7518 §3.2 mandates >= 256-bit (32-byte) keys. Constructor throws
//   IllegalStateException on misconfig — fails at startup, not at first /login (Pitfall B).
package com.tripplanner.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class JwtIssuer {

    private static final String ISSUER = "tripplanner-auth";
    private static final Duration TTL  = Duration.ofMinutes(15);   // docs/05 §1

    private final SecretKey signingKey;

    public JwtIssuer(String secret) {
        if (secret == null) {
            throw new IllegalStateException(
                "AUTH_JWT_SECRET must be set; got null. See .env.example for the dev placeholder.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        try {
            this.signingKey = Keys.hmacShaKeyFor(bytes);
        } catch (WeakKeyException ex) {
            throw new IllegalStateException(
                "AUTH_JWT_SECRET must be at least 32 bytes (256 bits) for HS256; got " + bytes.length, ex);
        }
    }

    /**
     * Mint a 15-minute access token per docs/05 §2.
     * Claims: iss=tripplanner-auth, sub=userId, iat=now, exp=now+15min, jti=UUID, email, ver.
     */
    public String issueAccess(String userId, String email, boolean emailVerified) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)                            // iss
                .subject(userId)                           // sub
                .issuedAt(Date.from(now))                  // iat
                .expiration(Date.from(now.plus(TTL)))      // exp
                .id(UUID.randomUUID().toString())          // jti
                .claim("email", email)
                .claim("ver", emailVerified)
                .signWith(signingKey)                      // alg auto-set HS256 from key
                .compact();
    }
}
