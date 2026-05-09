// 429 → RFC 7807 problem+json envelope.
//
// Source: 01-RESEARCH.md Pattern 6 (lines 832-862); 01-CONTEXT.md D-07.
//
// Spring Cloud Gateway's RequestRateLimiter writes 429 with empty body. This decorator
// intercepts writeWith and setComplete; when status == 429, replaces the body with
// ProblemDetailFactory.of(TOO_MANY_REQUESTS, AUTH_RATE_LIMITED, ...). docs/04-api-spec.md §6
// expects code "auth.rate_limited".
//
// Rule 1 fix: RequestRateLimiterGatewayFilterFactory calls setComplete() (empty body path)
// rather than writeWith() when rejecting a request. Override setComplete() to detect 429 and
// write the ProblemDetail body so Content-Type application/problem+json is always present.
//
// T-01-06 (trace ID leakage): the body does NOT include the trace identifier; only ErrorCode + detail.
package com.tripplanner.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import org.reactivestreams.Publisher;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Decorates the response to translate any HTTP 429 (Too Many Requests) status
 * written by Spring Cloud Gateway's RequestRateLimiter into a proper RFC 7807
 * application/problem+json body with ErrorCode.AUTH_RATE_LIMITED.
 *
 * Ordered at -2 so it runs before NettyWriteResponseFilter (which is at -1)
 * and thus wraps the response before it is actually flushed to the client.
 */
@Component
@Order(-2)
public class RateLimitProblemDetailFilter implements WebFilter {

    private final ObjectMapper mapper;

    public RateLimitProblemDetailFilter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            private final AtomicBoolean written = new AtomicBoolean(false);

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                // Plan 02-07 Rule 1 fix: when a downstream service (e.g. auth-service's
                // LoginRateLimiter at the IP+email leg, D-05/D-08) emits its own 429 with
                // a verbatim UI-SPEC ProblemDetail body via writeWith, pass it through
                // unchanged. Without this guard, this filter overwrote the downstream's
                // "Too many attempts. Please try again later." with the gateway-internal
                // "Rate limit exceeded for this route" string, breaking the UI-SPEC
                // §Server-Driven Copy Contract for auth.rate_limited (Phase 2 SC#5).
                //
                // Spring Cloud Gateway's own RequestRateLimiter does NOT call writeWith
                // for rejected requests — it uses setComplete() (empty-body path)
                // overridden below — so the original Phase 1 D-07 contract (gateway's
                // empty-body 429s gain a problem+json envelope) is preserved.
                return super.writeWith(body);
            }

            /**
             * RequestRateLimiterGatewayFilterFactory calls setComplete() (empty-body path)
             * rather than writeWith() when rejecting a request. We intercept here to ensure
             * the 429 response always has a proper RFC 7807 body and Content-Type header.
             */
            @Override
            public Mono<Void> setComplete() {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    return writeProblemDetail();
                }
                return super.setComplete();
            }

            private Mono<Void> writeProblemDetail() {
                if (!written.compareAndSet(false, true)) {
                    return Mono.empty();
                }
                // Echo X-Request-Id from the request to the response (WR-02).
                String reqId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
                if (reqId != null) {
                    getHeaders().set("X-Request-Id", reqId);
                }
                getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                ProblemDetail pd = ProblemDetailFactory.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        ErrorCode.AUTH_RATE_LIMITED,
                        "Rate limit exceeded for this route");
                try {
                    byte[] bytes = mapper.writeValueAsBytes(pd);
                    getHeaders().setContentLength(bytes.length);
                    return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
                } catch (JsonProcessingException ex) {
                    return Mono.error(ex);
                }
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }
}
