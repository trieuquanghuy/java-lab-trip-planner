// Source: 02-RESEARCH.md §Pattern 4 lines 469-534;
//         02-CONTEXT.md D-10 (chain replay revocation walks BOTH directions),
//         D-13 (REPEATABLE_READ + SELECT FOR UPDATE), D-21 (Tx boundaries).
//         02-PATTERNS.md §RefreshTokenService — verbatim source.
//
// Pitfall 2: @Lock(PESSIMISTIC_WRITE) requires an active write transaction. @Transactional(REPEATABLE_READ)
//   is mandatory on rotate().
// Pitfall 4: revokeChain MUST walk back to root via findByRotatedTo reverse-lookup BEFORE walking
//   forward. Otherwise mid-chain replays leave the root token unrevoked.
package com.tripplanner.auth.service;

import com.tripplanner.auth.domain.RefreshToken;
import com.tripplanner.auth.repository.RefreshTokenRepository;
import com.tripplanner.auth.service.exception.RefreshInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final Duration TTL = Duration.ofDays(7);            // docs/05 §1
    private static final SecureRandom RNG = new SecureRandom();

    private final RefreshTokenRepository repo;

    public RefreshTokenService(RefreshTokenRepository repo) {
        this.repo = repo;
    }

    /** Mint a new refresh chain root (called from AuthService.login post-credential-verify). */
    @Transactional
    public RefreshTokenIssued create(UUID userId) {
        String raw = randomHex(32);
        String hash = HashUtil.sha256Hex(raw);
        Instant expires = Instant.now().plus(TTL);
        repo.save(new RefreshToken(hash, userId, expires));
        return new RefreshTokenIssued(hash, raw, expires);
    }

    /**
     * Rotate (D-13): row-lock current, mint new, link via rotated_to. Replay → revoke chain.
     *
     * Plan 02-06 Rule 1 fix: noRollbackFor = RefreshInvalidException.class. Without this attribute
     * the replay branch's revokeChain() mutations would be rolled back when this method throws
     * RefreshInvalidException — leaving Pitfall 4's chain revocation unpersisted (the integration
     * test RotatedRefreshTokenCannotBeReusedIT caught this regression). The exception still
     * propagates to the controller (200 -> 401 mapping is unchanged).
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, noRollbackFor = RefreshInvalidException.class)
    public RotatedRefresh rotate(String rawCookieValue) {
        String hash = HashUtil.sha256Hex(rawCookieValue);
        RefreshToken current = repo.findByTokenHashForUpdate(hash)
                .orElseThrow(RefreshInvalidException::new);

        if (current.getRevokedAt() != null) throw new RefreshInvalidException();
        if (current.getExpiresAt().isBefore(Instant.now())) throw new RefreshInvalidException();
        if (current.getRotatedTo() != null) {
            // REPLAY DETECTED — walk chain BOTH directions, revoke all (D-10 / Pitfall 4)
            revokeChain(current);
            log.warn("Refresh-token replay detected userId={} chainHead={}",
                    current.getUserId(), current.getTokenHash().substring(0, 8));
            throw new RefreshInvalidException();
        }

        // Successful rotation: cap exp at original-issue + 7d (docs/05 §2 sliding cap)
        Instant nextExp = current.getCreatedAt().plus(TTL);
        if (nextExp.isBefore(Instant.now().plus(Duration.ofMinutes(1)))) {
            // Original chain too old to extend — force re-login
            throw new RefreshInvalidException();
        }
        String nextRaw = randomHex(32);
        String nextHash = HashUtil.sha256Hex(nextRaw);
        repo.save(new RefreshToken(nextHash, current.getUserId(), nextExp));
        current.setRotatedTo(nextHash);
        // current is managed (loaded under tx) — JPA flushes on commit
        return new RotatedRefresh(current.getUserId(), nextRaw, nextExp);
    }

    /** D-11: logout revokes chain head. Idempotent — silent no-op if cookie unknown. */
    @Transactional
    public void revokeChainHead(String rawCookieValue) {
        if (rawCookieValue == null || rawCookieValue.isBlank()) return;
        String hash = HashUtil.sha256Hex(rawCookieValue);
        repo.findById(hash).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) rt.setRevokedAt(Instant.now());
        });
    }

    /**
     * Pitfall 4 / D-10: walk BOTH directions. Back to root via findByRotatedTo reverse-lookup,
     * then forward from root, marking every row revoked.
     */
    private void revokeChain(RefreshToken pivot) {
        // 1) Walk BACKWARD to root
        RefreshToken cursor = pivot;
        while (true) {
            Optional<RefreshToken> prior = repo.findByRotatedTo(cursor.getTokenHash());
            if (prior.isEmpty()) break;
            cursor = prior.get();
        }
        RefreshToken root = cursor;

        // 2) Walk FORWARD from root, revoking each row
        cursor = root;
        Instant now = Instant.now();
        while (cursor != null) {
            cursor.setRevokedAt(now);
            cursor = cursor.getRotatedTo() == null
                    ? null
                    : repo.findById(cursor.getRotatedTo()).orElse(null);
        }
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buf) sb.append(String.format("%02x", b & 0xFF));
        return sb.toString();
    }

    /** Result of mintFor / create — caller embeds rawValue in a Set-Cookie. */
    public record RefreshTokenIssued(String tokenHash, String rawValue, Instant expiresAt) {}
    /** Result of rotate — caller mints a new access JWT for the user, embeds rawValue in Set-Cookie. */
    public record RotatedRefresh(UUID userId, String rawValue, Instant expiresAt) {}
}
