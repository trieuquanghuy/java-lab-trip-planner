// KeyResolver beans for Spring Cloud Gateway RequestRateLimiter (D-05 IP / D-06 userId).
//
// Source: 01-RESEARCH.md Pattern 5 (lines 772-800); 01-CONTEXT.md D-05, D-06.
//
// Convention C30-P1 (Pitfall G): every KeyResolver returns Mono<String> non-empty so empty-key
// bypass is impossible — `switchIfEmpty(Mono.just(...))` on every path.
// Convention C33-P1 (CVE-2025-41235): X-Forwarded-For trust is OFF; ipKeyResolver uses
// ServerHttpRequest.getRemoteAddress().getAddress().getHostAddress() directly.
// Convention C36-P1 (Assumption A5): in compose, Docker bridge NAT means many requests share
// one bridge IP — acceptable trade-off for portfolio v1; T-01-05 acknowledges Phase 2 will
// add the IP+email leg in auth-service for /api/auth/login.
package com.tripplanner.gateway.routing;

import com.tripplanner.contracts.UserContext;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfig {

    /**
     * IP-based key resolver for rate limiting.
     * Uses the actual remote address; X-Forwarded-For is NOT trusted (CVE-2025-41235 / C33-P1).
     * The defensive ternary satisfies C30-P1 (non-empty Mono guarantee).
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            var remote = exchange.getRequest().getRemoteAddress();
            return Mono.just(remote != null && remote.getAddress() != null
                    ? remote.getAddress().getHostAddress()
                    : "unknown");
        };
    }

    /**
     * User-ID-based key resolver for authenticated routes (e.g. /api/trips/**).
     * Falls back to "anonymous" — effectively unreachable on authenticated() routes
     * because the SecurityWebFilterChain redirects unauthenticated requests to the
     * ProblemDetailAuthEntryPoint before the RequestRateLimiter runs.
     * switchIfEmpty satisfies C30-P1 (non-empty Mono guarantee on public/unauthenticated paths).
     */
    @Bean
    public KeyResolver userIdKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(a -> a != null && a.isAuthenticated()
                        && a.getPrincipal() instanceof UserContext)
                .map(a -> ((UserContext) a.getPrincipal()).userId())
                .switchIfEmpty(Mono.just("anonymous"));
    }
}
