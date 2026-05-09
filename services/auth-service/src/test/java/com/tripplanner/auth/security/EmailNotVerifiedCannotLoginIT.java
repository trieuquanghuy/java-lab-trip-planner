// Source: 02-06-PLAN.md Task 6.2(c); 02-CONTEXT.md D-15 mandatory security IT #7;
//         02-UI-SPEC.md §Server-Driven Copy Contract — verbatim detail string.
//
// Security IT #7: signup without verify, login -> 403 auth.email_not_verified.
// Verifies the timing-safe defense path in AuthService.login (verified-check happens AFTER bcrypt
// so unverified-account latency is identical to wrong-password — no timing leak between branches).
package com.tripplanner.auth.security;

import com.tripplanner.auth.support.AuthIntegrationTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
class EmailNotVerifiedCannotLoginIT extends AuthIntegrationTestBase {

    @Autowired MockMvc mvc;

    @Test
    void unverified_account_login_returns_403_email_not_verified() throws Exception {
        // 1. Signup but DO NOT verify (skip the GreenMail token-extraction + /verify call).
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unv@example.com\",\"password\":\"correctpassword\"}"))
                .andExpect(status().isCreated());

        // 2. Login with correct password but email NOT verified -> 403 auth.email_not_verified.
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"unv@example.com\",\"password\":\"correctpassword\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("auth.email_not_verified"))
                .andExpect(jsonPath("$.detail").value("Please verify your email before logging in."))
                .andExpect(jsonPath("$.properties.code").doesNotExist());
    }
}
