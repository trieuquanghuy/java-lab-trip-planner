// Source: 02-PATTERNS.md §AuthProperties (verbatim) + revision iteration 1 correction —
//         prefix is `app` (NOT `app.auth`) so all four nested groups bind:
//         app.auth.cookie.* / app.frontend.* / app.mail.* / app.verification.*.
//         02-CONTEXT.md D-02 (frontend base URL), D-03 (mail from), D-12 (cookie secure profile-toggle).
//
// Bound from `application.yml` "app:" tree. @EnableConfigurationProperties(AuthProperties.class)
// lives on AuthServiceApplication (mirrors libs/jwt-common JwtAutoConfiguration:23).
package com.tripplanner.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public class AuthProperties {

    private final Auth auth = new Auth();
    private final Frontend frontend = new Frontend();
    private final Mail mail = new Mail();
    private final Verification verification = new Verification();

    public Auth getAuth() { return auth; }
    public Frontend getFrontend() { return frontend; }
    public Mail getMail() { return mail; }
    public Verification getVerification() { return verification; }

    public static class Auth {
        private final Cookie cookie = new Cookie();
        public Cookie getCookie() { return cookie; }
        public static class Cookie {
            private boolean secure;
            public boolean isSecure() { return secure; }
            public void setSecure(boolean s) { this.secure = s; }
        }
    }

    public static class Frontend {
        private String baseUrl = "http://localhost:5173";
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String u) { this.baseUrl = u; }
    }

    public static class Mail {
        private String from = "no-reply@tripplanner.local";
        public String getFrom() { return from; }
        public void setFrom(String f) { this.from = f; }
    }

    public static class Verification {
        private String linkBase = "http://localhost:8180/api/auth/verify?token=";
        public String getLinkBase() { return linkBase; }
        public void setLinkBase(String l) { this.linkBase = l; }
    }
}
