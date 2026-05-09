// Source: 02-06-PLAN.md Task 6.2(a); ROADMAP SC#1 / SC#3 / SC#4 (full happy-path E2E).
//
// End-to-end happy-path covering all 5 endpoints in sequence:
//   1. POST /api/auth/signup       -> 201 + userId; verification email sent via async @EventListener.
//   2. GET  /api/auth/verify       -> 302 to {frontend.base-url}/verify?status=success.
//   3. POST /api/auth/login        -> 200 + accessToken + Set-Cookie refresh_token.
//   4. POST /api/auth/refresh      -> 200 + new accessToken + new Set-Cookie (rotation).
//   5. POST /api/auth/logout       -> 204 + Max-Age=0 cookie clear.
//   6. POST /api/auth/refresh (B)  -> 401 auth.refresh_invalid (logout revoked the cookie).
//
// Verification email retrieval: pulls messages from the in-process GreenMail extension and parses
// the 64-hex token from "...token=<token>\n" inside the body. The send is @Async, so we briefly
// await receipt before extracting (Thread.sleep + bounded retry; documented as a known timing-flake risk).
package com.tripplanner.auth.api;

import com.tripplanner.auth.support.AuthIntegrationTestBase;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends AuthIntegrationTestBase {

    @Autowired MockMvc mvc;

    @Test
    void signup_verify_login_refresh_logout_full_flow() throws Exception {
        String email = "happy@example.com";
        String password = "correctpassword";

        // 1. Signup -> 201 + userId. The async EmailVerificationSender publishes via @EventListener.
        mvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists());

        // GreenMail receives the message (asynchronously) — extract the 64-hex token from the body.
        String token = extractTokenFromGreenMail(email);
        assertThat(token).hasSize(64);

        // 2. Verify -> 302 redirect to ${app.frontend.base-url}/verify?status=success.
        mvc.perform(get("/api/auth/verify").param("token", token))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", Matchers.endsWith("/verify?status=success")));

        // 3. Login -> 200 + JWT + Set-Cookie refresh_token.
        MvcResult login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().path("refresh_token", "/api/auth"))
                .andReturn();
        String refreshA = login.getResponse().getCookie("refresh_token").getValue();
        String accessToken = com.jayway.jsonpath.JsonPath.read(
                login.getResponse().getContentAsString(), "$.accessToken");
        assertThat(refreshA).isNotBlank();
        assertThat(accessToken).isNotBlank();

        // 4. Refresh -> 200 + new JWT + new cookie (rotated).
        MvcResult refresh = mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(cookie().exists("refresh_token"))
                .andReturn();
        String refreshB = refresh.getResponse().getCookie("refresh_token").getValue();
        assertThat(refreshB)
                .as("rotation must mint a new cookie value")
                .isNotEqualTo(refreshA);

        // 5. Logout with cookie B + bearer JWT -> 204 + Max-Age=0 cookie clear (D-12 / D-11).
        mvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(new Cookie("refresh_token", refreshB)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        // 6. Post-logout refresh attempt with cookie B -> 401 auth.refresh_invalid.
        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refresh_token", refreshB)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.refresh_invalid"))
                .andExpect(jsonPath("$.properties.code").doesNotExist());
    }

    /**
     * Pulls the verification token from GreenMail's received-messages buffer. Polls briefly because
     * the @Async send may complete after the controller returns 201.
     */
    private String extractTokenFromGreenMail(String to) throws Exception {
        // Wait up to ~3s for the async send to land.
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
