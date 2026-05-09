// Source: 02-06-PLAN.md Task 6.2(e); 02-RESEARCH.md §Code Examples lines 1076-1150;
//         02-CONTEXT.md D-15 mandatory security IT #6; D-10 (chain replay walks BOTH directions);
//         02-RESEARCH.md §Pitfall 4 (BOTH-directions revoke gate).
//
// Security IT #6 — KEYSTONE Pitfall 4 regression gate.
//
// Scenario: signup -> verify -> login (cookie A) -> /refresh (rotates A->B; cookie B) -> /refresh
// AGAIN with cookie A (replay). Replay path in RefreshTokenService.rotate detects rotated_to != null
// and calls revokeChain — which MUST walk back to root then forward, marking ALL rows revoked.
//
// DB-level assertion: BOTH the chain root (A) AND the chain head (B) end up with revoked_at != null.
// A forward-only chain walk would leave A unrevoked; this test fingers that regression immediately.
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("security")
class RotatedRefreshTokenCannotBeReusedIT extends AuthIntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired RefreshTokenRepository refreshRepo;

    @Test
    void replayed_refresh_token_revokes_entire_chain() throws Exception {
        String email = "rot@example.com";

        // 1. Signup + verify
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correctpassword\"}"))
                .andExpect(status().isCreated());
        String token = extractToken(email);
        mvc.perform(get("/api/auth/verify").param("token", token))
                .andExpect(status().isFound());

        // 2. Login -> cookie A.
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correctpassword\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String cookieA = login.getResponse().getCookie("refresh_token").getValue();

        // 3. First refresh — A -> B (success).
        MvcResult r1 = mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", cookieA)))
                .andExpect(status().isOk())
                .andReturn();
        String cookieB = r1.getResponse().getCookie("refresh_token").getValue();
        assertThat(cookieB)
                .as("rotation must mint a NEW cookie value")
                .isNotEqualTo(cookieA);

        // 4. REPLAY — present cookie A AGAIN -> 401 auth.refresh_invalid + chain revocation.
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", cookieA)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.refresh_invalid"))
                .andExpect(jsonPath("$.detail").value("Session expired. Please log in again."))
                .andExpect(jsonPath("$.properties.code").doesNotExist());

        // 5. Pitfall 4 hard gate — cookie B (chain HEAD, NEW after replay) is now also revoked
        //    because revokeChain walked from A forward through B.
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", cookieB)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.refresh_invalid"));

        // 6. DB-level confirmation — BOTH chain rows have revoked_at != null.
        String hashA = HashUtil.sha256Hex(cookieA);
        String hashB = HashUtil.sha256Hex(cookieB);
        assertThat(refreshRepo.findById(hashA).orElseThrow().getRevokedAt())
                .as("ROOT (cookie A) must be revoked — Pitfall 4 BOTH-directions walk")
                .isNotNull();
        assertThat(refreshRepo.findById(hashB).orElseThrow().getRevokedAt())
                .as("HEAD (cookie B) must be revoked — Pitfall 4 BOTH-directions walk")
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
