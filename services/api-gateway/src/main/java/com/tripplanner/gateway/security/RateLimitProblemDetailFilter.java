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

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
                    return writeProblemDetail();
                }
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
                getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
                ProblemDetail pd = ProblemDetailFactory.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        ErrorCode.AUTH_RATE_LIMITED,
                        "Rate limit exceeded for this route");
                try {
                    byte[] bytes = mapper.writeValueAsBytes(pd);
                    return super.writeWith(Mono.just(bufferFactory().wrap(bytes)));
                } catch (JsonProcessingException ex) {
                    return Mono.error(ex);
                }
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }
}
