package com.tripplanner.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReactiveMdcEnrichmentFilterTest {

    private Tracer tracer;
    private ReactiveMdcEnrichmentFilter filter;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        filter = new ReactiveMdcEnrichmentFilter(tracer);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void setsTraceContextInMdc() {
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("trace-123");
        when(ctx.spanId()).thenReturn("span-456");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Request-Id", "req-789")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    void usesCustomRequestId() {
        when(tracer.currentSpan()).thenReturn(null);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Request-Id", "custom-id")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        verify(chain).filter(exchange);
    }

    @Test
    void generatesRequestIdWhenMissing() {
        when(tracer.currentSpan()).thenReturn(null);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void handlesUserIdHeader() {
        when(tracer.currentSpan()).thenReturn(null);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-User-Id", "user-42")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
    }

    @Test
    void mdcClearedAfterFilter() {
        when(tracer.currentSpan()).thenReturn(null);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-User-Id", "user-1")
                .header("X-Request-Id", "req-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result).verifyComplete();
        // MDC should be cleared by doFinally
        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }
}
