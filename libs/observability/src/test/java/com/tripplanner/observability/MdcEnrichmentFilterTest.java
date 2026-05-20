package com.tripplanner.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MdcEnrichmentFilterTest {

    private Tracer tracer;
    private MdcEnrichmentFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        filter = new MdcEnrichmentFilter(tracer);
        chain = mock(FilterChain.class);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void populatesMdcWithTraceContext() throws Exception {
        Span span = mock(Span.class);
        TraceContext ctx = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(ctx);
        when(ctx.traceId()).thenReturn("abc123trace");
        when(ctx.spanId()).thenReturn("def456span");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isEqualTo("abc123trace");
            assertThat(MDC.get("spanId")).isEqualTo("def456span");
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
        // MDC cleared after filter
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void usesRequestIdHeaderWhenPresent() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("X-Request-Id", "custom-req-id");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("requestId")).isEqualTo("custom-req-id");
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void generatesRequestIdWhenHeaderMissing() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("requestId")).isNotNull().isNotBlank();
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);
    }

    @Test
    void generatesRequestIdWhenHeaderIsBlank() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("X-Request-Id", "   ");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("requestId")).isNotNull().doesNotContain("   ");
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);
    }

    @Test
    void populatesUserIdFromHeader() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("X-User-Id", "user-42");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("userId")).isEqualTo("user-42");
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);
    }

    @Test
    void noUserIdHeader_mdcDoesNotContainUserId() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("userId")).isNull();
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);
    }

    @Test
    void blankUserIdHeader_mdcDoesNotContainUserId() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("X-User-Id", "  ");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("userId")).isNull();
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);
    }

    @Test
    void mdcClearedEvenOnException() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("X-User-Id", "user-1");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doThrow(new RuntimeException("boom")).when(chain).doFilter(req, resp);

        try {
            filter.doFilterInternal(req, resp, chain);
        } catch (RuntimeException ignored) {
        }

        assertThat(MDC.get("userId")).isNull();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void noSpan_noTraceIdInMdc() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        doAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isNull();
            assertThat(MDC.get("spanId")).isNull();
            return null;
        }).when(chain).doFilter(req, resp);

        filter.doFilterInternal(req, resp, chain);
    }
}
