// Source: 02-RESEARCH.md §Pattern 2 lines 405-420 (consume returns "success"|"invalid"|"expired");
//         02-CONTEXT.md D-02 (verify endpoint flow), D-23 (re-signup invalidates prior).
//         UI-SPEC §Redirect Query-Param Contract — exactly these 3 lowercase string values.
package com.tripplanner.auth.service;

import com.tripplanner.auth.domain.EmailVerificationToken;
import com.tripplanner.auth.domain.User;
import com.tripplanner.auth.repository.EmailVerificationTokenRepository;
import com.tripplanner.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class EmailVerificationService {

    private static final Duration TTL = Duration.ofHours(24);          // docs/05 §7
    private static final SecureRandom RNG = new SecureRandom();

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepo, UserRepository userRepo) {
        this.tokenRepo = tokenRepo;
        this.userRepo = userRepo;
    }

    /** Mint a 32-byte hex token (CHAR(64)) for the given user. Caller is already inside @Transactional. */
    public String mintFor(User user) {
        String tok = randomHex(32);
        tokenRepo.save(new EmailVerificationToken(tok, user.getId(), Instant.now().plus(TTL)));
        return tok;
    }

    /**
     * Single-use idempotent consume (D-02).
     * Per docs/05 §9.1 / Pitfall 3: unknown AND consumed both return "invalid" (no enumeration leak).
     * Returns one of: "success" | "invalid" | "expired" — verbatim UI-SPEC §Redirect Query-Param values.
     */
    @Transactional
    public String consume(String token) {
        Optional<EmailVerificationToken> opt = tokenRepo.findById(token);
        if (opt.isEmpty()) {
            return "invalid";
        }
        EmailVerificationToken t = opt.get();
        if (t.getConsumedAt() != null) {
            return "invalid";   // already consumed — same code as unknown per §9.1
        }
        if (t.getExpiresAt().isBefore(Instant.now())) {
            return "expired";
        }
        t.setConsumedAt(Instant.now());
        userRepo.findById(t.getUserId()).ifPresent(u -> u.setEmailVerified(true));
        return "success";
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }
}
