// Source: docs/02-architecture.md §6.3, 00-RESEARCH.md lines 1037-1083, 00-CONTEXT.md D-04.
// Copies traceId/spanId/requestId/userId into MDC after request begins so JSON logs emitted by
// libs/observability's logback-spring-base.xml carry trace context.
//
// userId is read from X-User-Id header (injected by gateway's XUserIdInjectionGlobalFilter).
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
        var userId = req.getHeader("X-User-Id");
        if (userId != null && !userId.isBlank()) {
            MDC.put("userId", userId);
        }
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.clear();
        }
    }
}
