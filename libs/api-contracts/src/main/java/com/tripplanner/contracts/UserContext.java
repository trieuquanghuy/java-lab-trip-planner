// Source: 01-CONTEXT.md D-04 (UserContext shape — userId/email/verified principal exposed
//         to @AuthenticationPrincipal); 01-RESEARCH.md Open Question 2 (record implements
//         Principal so getName()=userId is auditable); 01-PATTERNS.md Bucket B lines 220-249.
//         Convention C32-P1.
//
// Phase 1 ServletJwtCommonFilter and api-gateway's WebFluxSecurityConfig populate this
// principal after JWT verification. Controllers consume via @AuthenticationPrincipal UserContext.
package com.tripplanner.contracts;

import java.security.Principal;

public record UserContext(String userId, String email, boolean verified) implements Principal {
    @Override
    public String getName() {
        return userId;
    }
}
