// Source: 01-RESEARCH.md Pattern 1 (lines 282-340); 01-CONTEXT.md D-02 (JWT verified twice —
//         gateway + downstream); 01-PATTERNS.md Bucket B lines 286-326.
//
// Convention C27-P1: jjwt 0.13.0 modern API ONLY — Jwts.parser().verifyWith(secretKey).build()
//   .parseSignedClaims(jws). NEVER use the deprecated parserBuilder().setSigningKey().
// Convention C28-P1: HS256 RFC 7518 §3.2 mandates ≥ 256-bit (32-byte) keys. Constructor throws
//   IllegalStateException so misconfig fails at startup, not at first request (Pitfall B).
package com.tripplanner.jwt;

import com.tripplanner.contracts.UserContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class JwtVerifier {

    private final SecretKey signingKey;

    public JwtVerifier(String secret) {
        if (secret == null) {
            throw new IllegalStateException(
                "AUTH_JWT_SECRET must be set; got null. See .env.example for the dev placeholder.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                "AUTH_JWT_SECRET must be at least 32 bytes (256 bits) for HS256; got " + bytes.length);
        }
        this.signingKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    public UserContext verify(String compactJws) throws JwtAuthenticationException {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(compactJws);

            Claims c = jws.getPayload();
            String sub = c.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new JwtAuthenticationException("token missing 'sub' claim");
            }
            String email = c.get("email", String.class);
            Boolean ver = c.get("ver", Boolean.class);
            return new UserContext(sub, email, Boolean.TRUE.equals(ver));

        } catch (ExpiredJwtException ex) {
            throw new JwtAuthenticationException("token expired", ex);
        } catch (JwtException ex) {
            throw new JwtAuthenticationException("token invalid", ex);
        }
    }
}
