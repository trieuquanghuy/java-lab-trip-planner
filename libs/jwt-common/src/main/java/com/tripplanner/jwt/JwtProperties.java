// Source: 01-CONTEXT.md D-16 (single AUTH_JWT_SECRET env var loaded via
//         @ConfigurationProperties("auth.jwt") in libs/jwt-common — all four services
//         consume the same property); 01-RESEARCH.md "Recommended Project Structure" line 227.
//
// Spring Boot env-property binding: AUTH_JWT_SECRET → auth.jwt.secret. Phase 0 .env.example
// ships placeholder "dev-only-32-byte-secret-replace-in-prod" (39 chars, satisfies Pitfall B's
// ≥ 32-byte HS256 minimum). Plan 01-08 wires the env var through compose.
package com.tripplanner.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.jwt")
public class JwtProperties {

    private String secret;

    public String getSecret() { return secret; }

    public void setSecret(String secret) { this.secret = secret; }
}
