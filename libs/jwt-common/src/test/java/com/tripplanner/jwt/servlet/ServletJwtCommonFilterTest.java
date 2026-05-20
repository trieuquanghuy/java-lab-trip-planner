package com.tripplanner.jwt.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanner.contracts.UserContext;
import com.tripplanner.jwt.JwtAuthenticationException;
import com.tripplanner.jwt.JwtFixtures;
import com.tripplanner.jwt.JwtVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ServletJwtCommonFilterTest {

    private JwtVerifier verifier;
    private ObjectMapper mapper;
    private ServletJwtCommonFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        verifier = new JwtVerifier(JwtFixtures.TEST_SECRET);
        mapper = new ObjectMapper();
        filter = new ServletJwtCommonFilter(verifier, mapper);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void whitelistedPath_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void whitelistedPath_apiAuthSignup_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/signup");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void whitelistedPath_apiAuthLogin_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void whitelistedPath_apiSearch_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/search/cities");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void whitelistedPath_apiDestinations_passesThrough() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/destinations/123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void noAuthorizationHeader_returns401() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentType()).isEqualTo("application/problem+json");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void authorizationHeaderWithoutBearer_returns401() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("Authorization", "Basic abc123");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void authorizationHeaderTooShort_returns401() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("Authorization", "Bear");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validToken_setsSecurityContextAndCallsChain() throws ServletException, IOException {
        String token = JwtFixtures.mintValid("user-1", "user@example.com");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
        // SecurityContext is cleared in finally block
    }

    @Test
    void expiredToken_returns401WithExpiredCode() throws ServletException, IOException {
        String token = JwtFixtures.mintExpired("user-1", "user@example.com");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        String body = resp.getContentAsString();
        assertThat(body).contains("auth.token_expired");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void invalidToken_returns401WithInvalidCode() throws ServletException, IOException {
        String token = JwtFixtures.mintWrongSig("user-1", "user@example.com");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        String body = resp.getContentAsString();
        assertThat(body).contains("auth.invalid_token");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void malformedToken_returns401() throws ServletException, IOException {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("Authorization", "Bearer not.a.real.jwt");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void securityContextClearedAfterRequest() throws ServletException, IOException {
        String token = JwtFixtures.mintValid("user-1", "user@example.com");
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/trips");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilterInternal(req, resp, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldNotFilterAsyncDispatch_returnsTrue() {
        assertThat(filter.shouldNotFilterAsyncDispatch()).isTrue();
    }
}
