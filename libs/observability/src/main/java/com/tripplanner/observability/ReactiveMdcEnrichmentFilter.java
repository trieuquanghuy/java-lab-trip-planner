// Source: 00-PATTERNS.md Bucket B (lines 306-312) — adapt the servlet MdcEnrichmentFilter
// to a WebFilter for the api-gateway (WebFlux).
//
// Phase 0 wiring uses doOnEach + doFinally as the minimal log-correlation pattern.
// WebFlux MDC propagation across thread switches is non-trivial; Phase 10 hardening may
// switch to contextWrite + Hooks.enableAutomaticContextPropagation() for full async-trace
// propagation. For Phase 0's /__health/<svc> route this is sufficient.
//
// userId is left empty in Phase 0 — Phase 1's gateway JWT filter will populate it.
// Pitfall 7 (Convention C7): do NOT register Spring's HTTP observation filter manually
// — it is auto-configured by Spring Boot 3.2+ via WebHttpHandlerBuilder.
package com.tripplanner.observability;

import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class ReactiveMdcEnrichmentFilter implements WebFilter {

    private final Tracer tracer;

    public ReactiveMdcEnrichmentFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        final String reqId = requestId;
        return chain.filter(exchange)
                .doOnEach(signal -> {
                    var span = tracer.currentSpan();
                    if (span != null) {
                        MDC.put("traceId", span.context().traceId());
                        MDC.put("spanId", span.context().spanId());
                    }
                    MDC.put("requestId", reqId);
                })
                .doFinally(signalType -> MDC.clear());
    }
}
