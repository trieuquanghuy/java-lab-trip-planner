// Source: 01-RESEARCH.md "Recommended Project Structure" line 228; 01-PATTERNS.md Bucket B
//         (no Phase 0 analog — first custom exception in repo).
//
// Extends Spring Security's AuthenticationException so ReactiveAuthenticationManager can
// wrap it in BadCredentialsException and the servlet filter chain treats it as auth failure
// (not a generic Exception that would surface as 500).
package com.tripplanner.jwt;

import org.springframework.security.core.AuthenticationException;

public class JwtAuthenticationException extends AuthenticationException {

    public JwtAuthenticationException(String message) {
        super(message);
    }

    public JwtAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
