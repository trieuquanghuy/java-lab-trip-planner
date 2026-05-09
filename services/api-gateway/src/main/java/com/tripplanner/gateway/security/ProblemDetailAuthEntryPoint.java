// ServerAuthenticationEntryPoint emitting RFC 7807 application/problem+json on 401.
//
// Source: 01-RESEARCH.md Pattern 6 (lines 805-827); 01-CONTEXT.md D-07; docs/04-api-spec.md §6
//         (stable error codes auth.unauthorized / auth.token_expired / auth.invalid_token).
//
// T-01-06 (trace ID leakage): the response body MUST NOT include the trace identifier or
// stack details. The body carries only the ErrorCode + detail; ops can correlate via the
// response's traceparent header (auto-set by libs/observability + Spring Boot tracing auto-config).
package com.tripplanner.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import com.tripplanner.jwt.JwtAuthenticationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ProblemDetailAuthEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper mapper;

    public ProblemDetailAuthEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        var resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.UNAUTHORIZED);
        resp.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        // Distinguish token-expired from invalid/forged from plain missing-auth.
        // BadCredentialsException wraps JwtAuthenticationException from ReactiveJwtAuthenticationManager.
        ErrorCode code;
        String detail;
        if (ex.getCause() instanceof JwtAuthenticationException jae
                && jae.reason() == JwtAuthenticationException.Reason.EXPIRED) {
            code = ErrorCode.AUTH_TOKEN_EXPIRED;
            detail = "Token has expired";
        } else if (ex instanceof BadCredentialsException) {
            code = ErrorCode.AUTH_INVALID_TOKEN;
            detail = "Token is invalid";
        } else {
            code = ErrorCode.AUTH_UNAUTHORIZED;
            detail = "Authentication required";
        }

        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.UNAUTHORIZED, code, detail);
        try {
            byte[] bytes = mapper.writeValueAsBytes(pd);
            return resp.writeWith(Mono.just(resp.bufferFactory().wrap(bytes)));
        } catch (JsonProcessingException jpe) {
            return Mono.error(jpe);
        }
    }
}
