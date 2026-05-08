// Source: 01-RESEARCH.md Pattern 2 (lines 422-435 ReactiveAuthenticationManager);
//         01-CONTEXT.md D-03 (Spring Security WebFlux owns the public-route allowlist).
//
// Wraps JwtVerifier into Spring Security's reactive auth API. On JwtAuthenticationException
// → BadCredentialsException so SecurityWebFilterChain's authenticationEntryPoint
// (ProblemDetailAuthEntryPoint, Wave 2) is invoked.
package com.tripplanner.jwt.reactive;

import com.tripplanner.contracts.UserContext;
import com.tripplanner.jwt.JwtAuthenticationException;
import com.tripplanner.jwt.JwtVerifier;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import reactor.core.publisher.Mono;

import java.util.Collection;

public class ReactiveJwtAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtVerifier verifier;

    public ReactiveJwtAuthenticationManager(JwtVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        try {
            UserContext principal = verifier.verify(token);
            return Mono.just(new PreAuthenticatedAuthentication(
                    principal, token, AuthorityUtils.createAuthorityList("ROLE_USER")));
        } catch (JwtAuthenticationException ex) {
            return Mono.error(new BadCredentialsException(ex.getMessage(), ex));
        }
    }

    public static class PreAuthenticatedAuthentication extends AbstractAuthenticationToken {
        private final UserContext principal;
        private final String token;
        public PreAuthenticatedAuthentication(UserContext p, String tok, Collection<? extends GrantedAuthority> auths) {
            super(auths);
            this.principal = p;
            this.token = tok;
            setAuthenticated(true);
        }
        @Override public Object getCredentials() { return token; }
        @Override public Object getPrincipal()   { return principal; }
    }
}
