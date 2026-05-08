// Source: docs/02-architecture.md §6.3, 00-RESEARCH.md lines 1037-1083, 00-CONTEXT.md D-04.
// Copies traceId/spanId/requestId into MDC after request begins so JSON logs emitted by
// libs/observability's logback-spring-base.xml carry trace context.
//
// userId is left empty in Phase 0 — Phase 1's JwtCommonFilter will populate it.
// Pitfall 7 (Convention C7): do NOT register Spring's HTTP observation filter manually
// — it is auto-configured by Spring Boot 3.2+ via WebHttpHandlerBuilder.
package com.tripplanner.observability;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class MdcEnrichmentFilter extends OncePerRequestFilter {

    private final Tracer tracer;

    public MdcEnrichmentFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        var span = tracer.currentSpan();
        if (span != null) {
            MDC.put("traceId", span.context().traceId());
            MDC.put("spanId", span.context().spanId());
        }
        var requestId = req.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put("requestId", requestId);
        // userId populated by Phase 1's JwtCommonFilter; empty in Phase 0.
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.clear();
        }
    }
}
