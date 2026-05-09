// Source: 02-RESEARCH.md §Pattern 1 (signup) + §Pattern 3 (login).
//         02-PATTERNS.md §AuthService — verbatim for signup + login + logout bodies.
//         02-CONTEXT.md D-09 (email lowercase normalize EVERYWHERE), D-21 (Tx boundaries),
//         D-23 (re-signup branch), D-24 (Open Q5 Option A — opaque /signup).
//
// Open Question 5 RESOLVED Option A: existing-verified accounts also return opaque 201
//   (NEVER throw EmailAlreadyRegisteredException on /signup — preserves §9.1 enumeration policy).
//
// Pitfall §timing-attack: login flow MUST equalize latency on these branches:
//   - user not found
//   - wrong password
//   - email not verified
//   The dummy-bcrypt verify on user-not-found path keeps timing constant with the wrong-password path
//   (~250ms on bcrypt cost 12). Verified-check happens AFTER bcrypt so unverified vs wrong-password
//   share the same compute cost.
package com.tripplanner.auth.service;

import com.tripplanner.auth.api.dto.LoginRequest;
import com.tripplanner.auth.api.dto.SignupRequest;
import com.tripplanner.auth.domain.User;
import com.tripplanner.auth.email.VerificationEmailRequestedEvent;
import com.tripplanner.auth.repository.EmailVerificationTokenRepository;
import com.tripplanner.auth.repository.UserRepository;
import com.tripplanner.auth.service.exception.EmailNotVerifiedException;
import com.tripplanner.auth.service.exception.InvalidCredentialsException;
import com.tripplanner.auth.service.exception.LoginRateLimitedException;
import com.tripplanner.auth.service.exception.RefreshInvalidException;
import com.tripplanner.jwt.JwtIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * Bcrypt-12 hash of the empty string. Used as the right-hand side of a dummy
     * {@link PasswordEncoder#matches(CharSequence, String)} call on the user-not-found
     * branch of {@link #login} so the request takes the full ~250ms verify cost regardless
     * of whether the supplied email exists. Independently regenerable via
     * {@code new BCryptPasswordEncoder(12).encode("")}; the value here was generated once
     * at implementation time and frozen so the timing-defense doesn't depend on a per-request
     * encode (which would itself leak timing).
     */
    private static final String DUMMY_BCRYPT12_HASH =
            "$2a$12$WzpEMRHVmEpoF4zgbWiLoOoTHqdaJJa/svHYM/l9Aaq4aD2HfDb1y";

    /** Access-token TTL in seconds — matches docs/05 §1 (15 minutes). */
    private static final int ACCESS_TOKEN_TTL_SECONDS = 900;

    private final UserRepository userRepo;
    private final EmailVerificationTokenRepository verifTokenRepo;
    private final EmailVerificationService verificationService;
    private final RefreshTokenService refreshTokenService;
    private final LoginRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final ApplicationEventPublisher events;

    public AuthService(UserRepository userRepo,
                       EmailVerificationTokenRepository verifTokenRepo,
                       EmailVerificationService verificationService,
                       RefreshTokenService refreshTokenService,
                       LoginRateLimiter rateLimiter,
                       PasswordEncoder passwordEncoder,
                       JwtIssuer jwtIssuer,
                       ApplicationEventPublisher events) {
        this.userRepo = userRepo;
        this.verifTokenRepo = verifTokenRepo;
        this.verificationService = verificationService;
        this.refreshTokenService = refreshTokenService;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.events = events;
    }

    /**
     * D-21 REQUIRED. D-09 normalize email ONCE at write time.
     * D-23 re-signup branch + D-24 / Open Q5 Option A (verified-existing also returns opaque 201).
     */
    @Transactional
    public SignupResult signup(SignupRequest req) {
        String emailLower = normalize(req.email());
        Optional<User> existing = userRepo.findByEmail(emailLower);

        if (existing.isPresent()) {
            User u = existing.get();
            if (u.isEmailVerified()) {
                // D-24 / Open Q5 Option A — opaque 201, no email re-sent (§9.1 enumeration policy).
                log.info("Signup against existing verified account userId={} (opaque 201, no email)", u.getId());
                return new SignupResult(u.getId(), false);
            }
            // D-23: invalidate prior unconsumed tokens, mint fresh, send email.
            verifTokenRepo.markAllUnconsumedAsConsumedFor(u.getId(), Instant.now());
            String tok = verificationService.mintFor(u);
            events.publishEvent(new VerificationEmailRequestedEvent(u.getEmail(), u.getId(), tok));
            log.info("Re-signup of unverified account userId={} (D-23 fresh verification email)", u.getId());
            return new SignupResult(u.getId(), true);
        }

        // True new account
        String hash = passwordEncoder.encode(req.password());      // bcrypt cost 12 (D-19)
        User u = new User(UUID.randomUUID(), emailLower, hash, false);
        userRepo.save(u);
        String tok = verificationService.mintFor(u);
        events.publishEvent(new VerificationEmailRequestedEvent(u.getEmail(), u.getId(), tok));
        log.info("Signup created userId={} (verification email queued)", u.getId());
        return new SignupResult(u.getId(), true);
    }

    /**
     * D-21 REQUIRED. D-05/D-07 — rate-limit BEFORE bcrypt; success clears the counter.
     * Pitfall §timing — verified-account check AFTER bcrypt so unverified vs wrong-password share latency.
     */
    @Transactional
    public LoginResult login(LoginRequest req, String clientIp) {
        String emailLower = normalize(req.email());

        // D-05: rate-limit BEFORE bcrypt verify — saves ~250ms of CPU on guaranteed-rejected requests.
        if (rateLimiter.exceeded(clientIp, emailLower)) {
            throw new LoginRateLimitedException();
        }

        Optional<User> opt = userRepo.findByEmail(emailLower);
        if (opt.isEmpty()) {
            // Timing-attack defense: dummy bcrypt-12 verify keeps latency identical to wrong-password path.
            // Without this, attacker can distinguish "unknown email" (~1ms) from "known + wrong pw" (~250ms).
            passwordEncoder.matches("dummy", DUMMY_BCRYPT12_HASH);
            rateLimiter.recordFailure(clientIp, emailLower);
            throw new InvalidCredentialsException();
        }
        User u = opt.get();

        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            rateLimiter.recordFailure(clientIp, emailLower);
            throw new InvalidCredentialsException();
        }

        if (!u.isEmailVerified()) {
            // Verified-check AFTER bcrypt — same latency as wrong-password path (timing defense).
            throw new EmailNotVerifiedException();
        }

        rateLimiter.clear(clientIp, emailLower);   // D-07: success clears the counter
        String access = jwtIssuer.issueAccess(u.getId().toString(), u.getEmail(), true);
        RefreshTokenService.RefreshTokenIssued rt = refreshTokenService.create(u.getId());
        return new LoginResult(access, ACCESS_TOKEN_TTL_SECONDS, rt.rawValue(), u);
    }

    /** D-21 REQUIRED. D-11: idempotent — bearer JWT valid but cookie missing still returns successfully. */
    @Transactional
    public void logout(String rawCookieValue) {
        refreshTokenService.revokeChainHead(rawCookieValue);
    }

    /**
     * Used by AuthController.refresh after RefreshTokenService.rotate to re-fetch the User
     * for the new access JWT (need email + email_verified for the claims).
     *
     * If the row is gone (user deleted/banned in the 0..7d refresh window), throws
     * RefreshInvalidException -> 401 (defense-in-depth per T-2-05-06).
     */
    @Transactional(readOnly = true)
    public User findUserByIdOrThrow(UUID userId) {
        return userRepo.findById(userId).orElseThrow(RefreshInvalidException::new);
    }

    /** D-09 single normalization helper used at signup-write AND login-lookup. */
    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Returned to controller; emailSent=false signals existing-verified branch (D-24 / Open Q5 Option A). */
    public record SignupResult(UUID userId, boolean emailSent) {}

    /** Returned to controller; controller wraps rawRefreshToken in a Set-Cookie header. */
    public record LoginResult(String accessToken, int expiresInSeconds, String rawRefreshToken, User user) {}
}
