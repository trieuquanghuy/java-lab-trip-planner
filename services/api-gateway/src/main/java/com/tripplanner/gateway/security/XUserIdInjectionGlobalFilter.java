// GlobalFilter that strips client-supplied X-User-Id / X-User-Email and re-injects them from the
// validated JWT principal — Pitfall 1 keystone (T-01-04).
//
// Source: 01-RESEARCH.md Pattern 3 (lines 471-545); 01-CONTEXT.md D-02 (Pitfall 1 keystone),
//         D-18 (X-Request-Id propagation); 01-PATTERNS.md Bucket C lines 603-632.
//
// Filter ordering (Pitfall 1 keystone — read carefully):
//   * Spring Security AuthenticationWebFilter is at SecurityWebFiltersOrder.AUTHENTICATION = -200.
//   * libs/observability ReactiveMdcEnrichmentFilter is at Ordered.HIGHEST_PRECEDENCE + 100.
//   * This filter MUST run AFTER Spring Security (so SecurityContext is populated for authed routes)
//     and BEFORE NettyRoutingFilter (which is Ordered.LOWEST_PRECEDENCE) so the mutated headers
//     reach the downstream service.
//   * Order = -100 satisfies both constraints.
//
// Convention C29-P1 (Pitfall F sidestep): this filter MUST NOT write userId to the diagnostic
// context. The Reactor-Netty event loop reuses threads across requests; diagnostic writes leak.
// Downstream servlet filter writes userId on its per-request thread instead — that is the
// source of truth for log attribution.
package com.tripplanner.gateway.security;

import com.tripplanner.contracts.UserContext;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Runs AFTER Spring Security AuthenticationWebFilter so the SecurityContext is populated.
 * Strips any client-supplied X-User-Id / X-User-Email and replaces them with values
 * derived from the validated JWT principal. Pitfall 1 keystone.
 *
 * Order: AUTHENTICATION (Spring Security) is at -200 in SecurityWebFiltersOrder; we run
 * after Security (-200) but before the routing filter (NettyRoutingFilter is at Ordered.LOWEST).
 * Order = -100 satisfies both constraints (after -200, before LOWEST_PRECEDENCE).
 */
@Component
public class XUserIdInjectionGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated()
                        && auth.getPrincipal() instanceof UserContext)
                .map(auth -> (UserContext) auth.getPrincipal())
                .flatMap(user -> {
                    // Authenticated branch: strip client headers, inject from validated JWT principal.
                    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
                    if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
                    final String reqId = requestId;
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(h -> {
                                // STRIP first, then inject — order matters per Pitfall 1 (T-01-04).
                                h.remove("X-User-Id");
                                h.remove("X-User-Email");
                                h.set("X-User-Id", user.userId());
                                if (user.email() != null) h.set("X-User-Email", user.email());
                                h.set("X-Request-Id", reqId);
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                // No SecurityContext (public route) — still strip any client-supplied X-User-Id
                // so a public route can never spoof identity onto a downstream call later.
                .switchIfEmpty(Mono.defer(() -> {
                    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
                    if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
                    final String reqId = requestId;
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(h -> {
                                // STRIP only — no inject on public routes (prevents X-User-Id spoofing).
                                h.remove("X-User-Id");
                                h.remove("X-User-Email");
                                h.set("X-Request-Id", reqId);
                            })
                            .build();
                    return chain.filter(exchange.mutate().request(mutated).build());
                }));
    }

    /** Run AFTER Spring Security (which is at SecurityWebFiltersOrder.AUTHENTICATION = -200). */
    @Override
    public int getOrder() { return -100; }
}
