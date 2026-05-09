// Source: 02-06-PLAN.md Task 6.2(f); 02-CONTEXT.md D-15 mandatory security IT #8;
//         D-06 (5 attempts/15min - 6th trips); D-07 (success clears counter);
//         02-UI-SPEC.md §Server-Driven Copy Contract — verbatim auth.rate_limited detail.
//
// Security IT #8: 5 wrong-password attempts return 400; the 6th trips the rate limiter and returns 429.
// The off-by-one between `>= 5` (correct, D-06) and `> 5` (wrong) is locked here by the
// status assertions: 5 successive 400s, then the 6th MUST be 429.
//
// LoginRateLimiter wires to the @ServiceConnection-injected Redis container; Lua INCR+EXPIRE
// atomicity is unit-tested separately in LoginRateLimiterTest.
package com.tripplanner.auth.security;

import com.tripplanner.auth.support.AuthIntegrationTestBase;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
class LoginRateLimitFailedAttemptsTriggersIT extends AuthIntegrationTestBase {

    @Autowired MockMvc mvc;

    @Test
    void sixth_failed_login_from_same_ip_email_within_15min_returns_429() throws Exception {
        String email = "rl@example.com";
        // Setup — signup + verify so the user EXISTS (login hits real bcrypt path, not user-not-found dummy).
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correctpassword\"}"))
                .andExpect(status().isCreated());
        String token = extractToken(email);
        mvc.perform(get("/api/auth/verify").param("token", token))
                .andExpect(status().isFound());

        // 5 wrong-password attempts: each hits exceeded()=false (count 0..4), bcrypt fails, recordFailure
        // increments to 1..5. All return 400 auth.invalid_credentials.
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", "203.0.113.10")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"WRONGPASS\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("auth.invalid_credentials"));
        }

        // 6th attempt — exceeded() reads count=5, trips BEFORE bcrypt verify -> 429.
        mvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"WRONGPASS\"}"))
                .andExpect(status().is(429))
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("auth.rate_limited"))
                .andExpect(jsonPath("$.detail").value("Too many attempts. Please try again later."))
                .andExpect(jsonPath("$.properties.code").doesNotExist());
    }

    private String extractToken(String to) throws Exception {
        for (int attempt = 0; attempt < 30; attempt++) {
            for (MimeMessage m : greenMail.getReceivedMessages()) {
                if (m.getAllRecipients()[0].toString().equals(to)) {
                    String body = (String) m.getContent();
                    int idx = body.indexOf("token=");
                    if (idx >= 0 && idx + 6 + 64 <= body.length()) {
                        return body.substring(idx + 6, idx + 6 + 64);
                    }
                }
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Verification email not received for " + to + " within 3s");
    }
}
