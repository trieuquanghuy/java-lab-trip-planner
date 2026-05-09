// Source: 02-06-PLAN.md Task 6.2(b); 02-CONTEXT.md D-14 BL-01 (no `new ObjectMapper()`); D-18 (field-name discrimination);
//         02-UI-SPEC.md §Server-Driven Copy Contract — verbatim detail strings (locked byte-for-byte).
//
// BL-01 contract test for auth-service: every error path renders code at JSON ROOT (`$.code`),
// NEVER nested under `$.properties.code`. Mirrors the gateway-side Pitfall-7 lesson.
//
// Verbatim UI-SPEC detail strings asserted here (each appears exactly once in production
// AuthControllerAdvice.java):
//   - auth.weak_password         -> "Password does not meet minimum requirements."
//   - auth.invalid_email         -> "Invalid email format."
//   - auth.invalid_credentials   -> "Email or password is incorrect."
//
// (auth.refresh_invalid + auth.email_not_verified + auth.rate_limited + auth.token_invalid +
//  auth.token_expired are asserted by the @Tag("security") ITs that exercise their happy-path triggers.)
package com.tripplanner.auth.api;

import com.tripplanner.auth.support.AuthIntegrationTestBase;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerAdviceIT extends AuthIntegrationTestBase {

    @Autowired MockMvc mvc;

    @Test
    void weak_password_returns_400_with_code_at_root_and_verbatim_detail() throws Exception {
        // password="short" (5 chars) fails @Size(min=8) on SignupRequest -> AUTH_WEAK_PASSWORD.
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"u@e.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("auth.weak_password"))
                .andExpect(jsonPath("$.detail").value("Password does not meet minimum requirements."))
                .andExpect(jsonPath("$.properties.code").doesNotExist());
    }

    @Test
    void invalid_email_returns_400_with_code_at_root_and_verbatim_detail() throws Exception {
        // email="not-an-email" fails @Email on SignupRequest -> AUTH_INVALID_EMAIL.
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"correctpassword\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("auth.invalid_email"))
                .andExpect(jsonPath("$.detail").value("Invalid email format."))
                .andExpect(jsonPath("$.properties.code").doesNotExist());
    }

    @Test
    void invalid_credentials_returns_400_with_code_at_root_and_verbatim_detail() throws Exception {
        // No prior signup — user-not-found path: dummy bcrypt + recordFailure + InvalidCredentialsException.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"never@registered.com\",\"password\":\"anything12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("auth.invalid_credentials"))
                .andExpect(jsonPath("$.detail").value("Email or password is incorrect."))
                .andExpect(jsonPath("$.properties.code").doesNotExist());
    }

    @Test
    void verify_with_unknown_token_redirects_to_invalid_status() throws Exception {
        // 302 redirect path takes precedence over RFC 7807 — verify D-02 / UI-SPEC §Redirect Query-Param.
        // EmailVerificationService.consume() returns "invalid" for unknown tokens.
        mvc.perform(get("/api/auth/verify").param("token", "0".repeat(64)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.endsWith("/verify?status=invalid")));
    }
}
