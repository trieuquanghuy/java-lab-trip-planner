// Source: docs/04-api-spec.md §3 (5-endpoint contract — signup/verify/login/refresh/logout).
//         02-CONTEXT.md D-02 (verify 302 redirect), D-05 (X-Forwarded-For first token + getRemoteAddr fallback),
//         D-11 (logout idempotent), D-12 (cookie scope verbatim).
//         02-PATTERNS.md §AuthController + §AuthService.
//         02-UI-SPEC.md §Redirect Query-Param Contract (lowercase success/invalid/expired).
//
// Open Question 1 RESOLVED: NO /me endpoint. The SPA decodes email/ver from JWT payload directly.
//
// SecurityConfig (Plan 03) permitAll on /signup, /verify, /login, /refresh; /logout falls through to
// the JWT filter (authenticated()).
package com.tripplanner.auth.api;

import com.tripplanner.auth.api.dto.LoginRequest;
import com.tripplanner.auth.api.dto.LoginResponse;
import com.tripplanner.auth.api.dto.RefreshResponse;
import com.tripplanner.auth.api.dto.SignupRequest;
import com.tripplanner.auth.api.dto.SignupResponse;
import com.tripplanner.auth.api.dto.UserResponse;
import com.tripplanner.auth.config.AuthProperties;
import com.tripplanner.auth.domain.User;
import com.tripplanner.auth.service.AuthService;
import com.tripplanner.auth.service.EmailVerificationService;
import com.tripplanner.auth.service.RefreshTokenService;
import com.tripplanner.auth.service.exception.RefreshInvalidException;
import com.tripplanner.jwt.JwtIssuer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int ACCESS_TOKEN_TTL_SECONDS = 900;          // 15 min — docs/05 §1
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(7);   // docs/05 §1
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String REFRESH_COOKIE_PATH = "/api/auth";    // D-12 narrow scope

    private final AuthService authService;
    private final EmailVerificationService verificationService;
    private final RefreshTokenService refreshTokenService;
    private final JwtIssuer jwtIssuer;
    private final AuthProperties props;

    public AuthController(AuthService authService,
                          EmailVerificationService verificationService,
                          RefreshTokenService refreshTokenService,
                          JwtIssuer jwtIssuer,
                          AuthProperties props) {
        this.authService = authService;
        this.verificationService = verificationService;
        this.refreshTokenService = refreshTokenService;
        this.jwtIssuer = jwtIssuer;
        this.props = props;
    }

    /** D-24 / Open Q5 Option A — opaque 201 for new, existing-unverified, AND existing-verified. */
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req) {
        AuthService.SignupResult r = authService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignupResponse(r.userId()));
    }

    /** D-02: 302 redirect to ${app.frontend.base-url}/verify?status={success|invalid|expired}. */
    @GetMapping("/verify")
    public ResponseEntity<Void> verify(@RequestParam("token") String token) {
        String status = verificationService.consume(token);
        URI redirect = URI.create(props.getFrontend().getBaseUrl() + "/verify?status=" + status);
        return ResponseEntity.status(HttpStatus.FOUND).location(redirect).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest http) {
        String ip = resolveClientIp(http);
        AuthService.LoginResult r = authService.login(req, ip);

        ResponseCookie cookie = buildRefreshCookie(r.rawRefreshToken(), REFRESH_COOKIE_MAX_AGE);
        LoginResponse body = new LoginResponse(
                r.accessToken(), r.expiresInSeconds(), UserResponse.from(r.user()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    /**
     * Cookie -> RefreshTokenService.rotate (REPEATABLE_READ + SELECT FOR UPDATE) -> new JWT + new cookie.
     * Replay (already-rotated row) -> chain revoked + 401 auth.refresh_invalid.
     */
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) {
            // No cookie present -> 401 auth.refresh_invalid (matches the rotate-fail branch)
            throw new RefreshInvalidException();
        }
        RefreshTokenService.RotatedRefresh next = refreshTokenService.rotate(cookieValue);
        // Re-fetch User to get email + email_verified for the new access JWT (T-2-05-06 defense-in-depth:
        // a user deleted/banned in the 0..7d refresh window throws RefreshInvalidException -> 401).
        User user = authService.findUserByIdOrThrow(next.userId());
        String access = jwtIssuer.issueAccess(user.getId().toString(), user.getEmail(), user.isEmailVerified());

        ResponseCookie cookie = buildRefreshCookie(next.rawValue(), REFRESH_COOKIE_MAX_AGE);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new RefreshResponse(access, ACCESS_TOKEN_TTL_SECONDS));
    }

    /** D-11: idempotent. Cookie may be missing (bearer JWT valid but no cookie) — still return 204. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String cookieValue) {
        authService.logout(cookieValue);
        ResponseCookie cleared = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(props.getAuth().getCookie().isSecure())
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    /** D-12 cookie scope verbatim: HttpOnly; SameSite=Strict; Path=/api/auth; Secure=${profile-toggle}. */
    private ResponseCookie buildRefreshCookie(String rawValue, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, rawValue)
                .httpOnly(true)
                .secure(props.getAuth().getCookie().isSecure())   // false in dev/test, true in deployed
                .sameSite("Strict")                                // CSRF mitigation primary defense
                .path(REFRESH_COOKIE_PATH)                         // narrow scope: /refresh + /logout only
                .maxAge(maxAge)
                .build();
    }

    /**
     * D-05: gateway-injected X-Forwarded-For first token; fallback getRemoteAddr() for tests/direct hits.
     * The gateway is the only injector of X-Forwarded-For (Phase 1 D-05). Direct hits on auth-service:8081
     * are 401 by Phase 1 DirectServiceAccessWithoutGatewayReturns401IT — so a forged X-Forwarded-For
     * never reaches this code path in production.
     */
    private static String resolveClientIp(HttpServletRequest http) {
        String fwd = http.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma >= 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return http.getRemoteAddr();
    }
}
