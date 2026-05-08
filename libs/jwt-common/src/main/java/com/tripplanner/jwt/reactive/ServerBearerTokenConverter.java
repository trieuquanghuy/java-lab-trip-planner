// Source: 01-RESEARCH.md Pattern 2 (lines 412-420 ServerBearerTokenConverter);
//         01-CONTEXT.md D-03 (Spring Security WebFlux on the gateway).
//
// Spring Security's documented ServerAuthenticationConverter pattern. Returns Mono.empty()
// on missing/wrong scheme so SecurityWebFilterChain falls through to permitAll() routes
// (e.g. /api/auth/login). On valid Bearer header, emits an unauthenticated token that
// ReactiveJwtAuthenticationManager will verify.
package com.tripplanner.jwt.reactive;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;

public class ServerBearerTokenConverter implements ServerAuthenticationConverter {

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String h = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (h == null || !h.startsWith("Bearer ")) return Mono.empty();
        String token = h.substring("Bearer ".length()).trim();
        if (token.isEmpty()) return Mono.empty();
        return Mono.just(new BearerTokenAuthentication(token));
    }

    public static class BearerTokenAuthentication extends AbstractAuthenticationToken {
        private final String token;
        public BearerTokenAuthentication(String token) {
            super(Collections.emptyList());
            this.token = token;
            setAuthenticated(false);
        }
        @Override public Object getCredentials() { return token; }
        @Override public Object getPrincipal()   { return null; }
    }
}
