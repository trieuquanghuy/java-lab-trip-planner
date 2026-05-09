// Source: 02-06-PLAN.md Task 6.2(d); 02-CONTEXT.md D-15 mandatory security IT #5;
//         02-UI-SPEC.md §Server-Driven Copy Contract line 144 (`auth.refresh_invalid` -> "Session expired. Please log in again.").
//
// Security IT #5: signup -> verify -> login -> logout -> /refresh with logged-out cookie -> 401.
// Includes a DB-level assertion (security-critical): the refresh-token row's `revoked_at` IS NOT NULL
// after logout (D-11 idempotent revoke; the row is NOT deleted, just marked revoked, so the next
// rotate attempt fails the `current.getRevokedAt() != null` check in RefreshTokenService.rotate).
//
// NOTE: The detail string asserted here is verbatim from production AuthControllerAdvice.java:108-110
// AND from 02-UI-SPEC.md line 144 — "Session expired. Please log in again." The plan-doc draft
// in 02-06-PLAN.md Task 6.2(d) showed a different string ("Your session has expired...") which
// does not match the UI-SPEC source-of-truth; this test uses the production+UI-SPEC version
// (Rule 1: align with the actual production string under test).
package com.tripplanner.auth.security;

import com.tripplanner.auth.repository.RefreshTokenRepository;
import com.tripplanner.auth.service.HashUtil;
import com.tripplanner.auth.support.AuthIntegrationTestBase;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
class DeletedRefreshTokenCannotBeUsedIT extends AuthIntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired RefreshTokenRepository refreshRepo;

    @Test
    void logged_out_refresh_token_returns_401_and_db_row_is_revoked() throws Exception {
        String email = "deleted@example.com";

        // 1. Signup
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correctpassword\"}"))
                .andExpect(status().isCreated());

        // 2. Verify email (consume the GreenMail token)
        String token = extractToken(email);
        mvc.perform(get("/api/auth/verify").param("token", token))
                .andExpect(status().isFound());

        // 3. Login -> capture refresh cookie A.
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correctpassword\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String cookieA = login.getResponse().getCookie("refresh_token").getValue();
        assertThat(cookieA).isNotBlank();

        // 4. Logout (presenting cookie A) -> 204 (D-11 idempotent revoke).
        mvc.perform(post("/api/auth/logout")
                        .cookie(new Cookie("refresh_token", cookieA)))
                .andExpect(status().isNoContent());

        // 5. POST /api/auth/refresh with cookie A -> 401 + verbatim UI-SPEC detail.
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", cookieA)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("auth.refresh_invalid"))
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."))
                .andExpect(jsonPath("$.properties.code").doesNotExist());

        // 6. DB-level assertion (security-critical) — the row exists but revoked_at IS NOT NULL.
        String hashA = HashUtil.sha256Hex(cookieA);
        assertThat(refreshRepo.findById(hashA))
                .as("refresh-token row must persist after logout (D-11 idempotent — revoke, not delete)")
                .isPresent();
        assertThat(refreshRepo.findById(hashA).orElseThrow().getRevokedAt())
                .as("logout must set revoked_at on the chain-head row (D-11)")
                .isNotNull();
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
