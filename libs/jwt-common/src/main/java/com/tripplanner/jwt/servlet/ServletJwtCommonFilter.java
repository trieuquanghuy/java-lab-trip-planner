// Source: 01-RESEARCH.md Pattern 4 (lines 549-642); 01-CONTEXT.md D-02 (defense-in-depth
//         re-validation downstream — Pitfall 1 keystone); 01-PATTERNS.md Bucket B lines 327-382.
//
// Convention C26-P1: every authenticated request to a downstream servlet service runs through
//   this filter — no exceptions, even before real endpoints exist. /__health and /actuator/health
//   are the only allowlisted paths.
// Convention C29-P1: this filter writes userId to MDC (servlet thread-local — no Reactor leak).
//   The reactive gateway intentionally does NOT write userId to MDC (Pitfall F sidestep).
// MDC hygiene (MdcEnrichmentFilter analog, lines 43-47): set userId on entry, clear in `finally`.
package com.tripplanner.jwt.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.contracts.UserContext;
import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import com.tripplanner.jwt.JwtAuthenticationException;
import com.tripplanner.jwt.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class ServletJwtCommonFilter extends OncePerRequestFilter {

    private static final List<String> WHITELIST = List.of(
        "/__health", "/actuator/health", "/actuator/info"
    );

    private final JwtVerifier verifier;
    private final ObjectMapper mapper;

    // Accept Spring Boot's auto-configured ObjectMapper so ProblemDetailJacksonMixin is registered.
    // The mixin flattens ProblemDetail extension properties (code, etc.) to the JSON root level,
    // enabling $.code assertions in tests. Using new ObjectMapper() nests them under "properties".
    public ServletJwtCommonFilter(JwtVerifier verifier, ObjectMapper mapper) {
        this.verifier = verifier;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();
        if (WHITELIST.stream().anyMatch(path::startsWith)) {
            chain.doFilter(req, resp);
            return;
        }

        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            writeProblem(resp, HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_UNAUTHORIZED,
                    "Authorization Bearer token required");
            return;
        }

        try {
            UserContext user = verifier.verify(header.substring("Bearer ".length()).trim());
            ServletAuthToken auth = new ServletAuthToken(user, AuthorityUtils.createAuthorityList("ROLE_USER"));
            auth.setAuthenticated(true);
            SecurityContextHolder.getContext().setAuthentication(auth);

            MDC.put("userId", user.userId());

            chain.doFilter(req, resp);
        } catch (JwtAuthenticationException ex) {
            ErrorCode code = ex.getMessage() != null && ex.getMessage().contains("expired")
                    ? ErrorCode.AUTH_TOKEN_EXPIRED
                    : ErrorCode.AUTH_INVALID_TOKEN;
            writeProblem(resp, HttpStatus.UNAUTHORIZED, code, ex.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
            MDC.remove("userId");
        }
    }

    private void writeProblem(HttpServletResponse resp, HttpStatus status, ErrorCode code, String detail)
            throws IOException {
        ProblemDetail pd = ProblemDetailFactory.of(status, code, detail);
        resp.setStatus(status.value());
        resp.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(resp.getOutputStream(), pd);
    }

    static class ServletAuthToken extends AbstractAuthenticationToken {
        private final UserContext principal;
        ServletAuthToken(UserContext p, Collection<? extends GrantedAuthority> auths) {
            super(auths);
            this.principal = p;
        }
        @Override public Object getCredentials() { return null; }
        @Override public Object getPrincipal()   { return principal; }
    }
}
