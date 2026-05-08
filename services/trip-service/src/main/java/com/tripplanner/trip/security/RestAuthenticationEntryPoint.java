// Source: 01-RESEARCH.md Pattern 4 (lines 547-663) — defense-in-depth servlet wiring;
//         01-CONTEXT.md D-04 (downstream populates SecurityContextHolder; @AuthenticationPrincipal UserContext);
//         01-PATTERNS.md Bucket D (lines 771-801).
//
// Convention C26-P1: every authenticated downstream path MUST emit RFC 7807 ProblemDetail on auth failure.
// Convention C35-P1: header citation discipline.
//
// ServletJwtCommonFilter (libs/jwt-common, 01-02) writes its OWN 401 ProblemDetail when the Authorization
// header is missing or invalid — those responses bypass this entry point. This entry point handles the
// residual case: a path passes the filter (e.g. via the /__health allowlist or because the filter set the
// SecurityContext) but Spring Security's authorize-step then rejects it. For Phase 1 the residual case is
// narrow but the contract MUST be 7807-shaped to satisfy Convention C26-P1 / docs/04-api-spec.md §6.
package com.tripplanner.trip.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    // Spring Boot's auto-configured ObjectMapper has ProblemDetailJacksonMixin registered,
    // which flattens ProblemDetail extension properties (code, etc.) to the JSON root level.
    // Using new ObjectMapper() would nest them under "properties" — wrong for $.code assertions.
    private final ObjectMapper mapper;

    public RestAuthenticationEntryPoint(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetail pd = ProblemDetailFactory.of(
                HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED, "Authentication required");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), pd);
    }
}
