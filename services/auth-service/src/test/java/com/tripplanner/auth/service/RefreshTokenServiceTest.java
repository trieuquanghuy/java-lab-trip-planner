// Source: 02-06-PLAN.md Task 6.1(d); 02-CONTEXT.md D-10 (chain replay walks BOTH directions);
//         02-RESEARCH.md §Pitfall 4 (mid-chain replay must revoke ROOT — forward-only walk leaves it intact).
//
// Pure unit test (Mockito-only — no Spring, no DB) — fast (<200ms each). Stubs RefreshTokenRepository
// to drive the chain walk. The two scenarios cover:
//   1. Mid-chain replay: present `b` (root → b → c chain). revokeChain MUST walk back from b to a (root)
//      then forward marking a, b, c all revoked. Forward-only would leave `a` unrevoked.
//   2. Tail-replay: present `c` (the head). revokeChain walks back through b → a (root), then forward.
//      Pitfall 4 hard gate: ROOT (a) MUST end up revoked.
package com.tripplanner.auth.service;

import com.tripplanner.auth.domain.RefreshToken;
import com.tripplanner.auth.repository.RefreshTokenRepository;
import com.tripplanner.auth.service.exception.RefreshInvalidException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository repo;
    @InjectMocks RefreshTokenService svc;

    /** Helper: real SHA-256 hex of a raw cookie string — matches HashUtil.sha256Hex. */
    private static String h(String raw) {
        return HashUtil.sha256Hex(raw);
    }

    @Test
    void replay_revokes_entire_chain_walking_BOTH_directions() {
        // Build a 3-link chain: a -> b -> c (a is root, c is head).
        UUID userId = UUID.randomUUID();
        String hashA = h("raw_a");
        String hashB = h("raw_b");
        String hashC = h("raw_c");

        RefreshToken a = new RefreshToken(hashA, userId, Instant.now().plusSeconds(86400));
        RefreshToken b = new RefreshToken(hashB, userId, Instant.now().plusSeconds(86400));
        RefreshToken c = new RefreshToken(hashC, userId, Instant.now().plusSeconds(86400));
        a.setRotatedTo(hashB);
        b.setRotatedTo(hashC);
        // c.rotatedTo == null (c is the head)

        // Replay attacker presents B (already-rotated middle row) → rotate() detects replay and
        // calls revokeChain(b). The chain walk MUST walk back to root (a) then forward (a → b → c).
        when(repo.findByTokenHashForUpdate(hashB)).thenReturn(Optional.of(b));
        when(repo.findByRotatedTo(hashB)).thenReturn(Optional.of(a));
        when(repo.findByRotatedTo(hashA)).thenReturn(Optional.empty()); // root reached
        // Forward-walk from root: a.rotatedTo=hashB → fetch b; b.rotatedTo=hashC → fetch c; c.rotatedTo=null → stop.
        when(repo.findById(hashB)).thenReturn(Optional.of(b));
        when(repo.findById(hashC)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> svc.rotate("raw_b"))
                .isInstanceOf(RefreshInvalidException.class);

        // All three rows MUST have revoked_at != null (Pitfall 4 hard gate).
        assertThat(a.getRevokedAt())
                .as("ROOT (a) must be revoked — forward-only walk would miss this row")
                .isNotNull();
        assertThat(b.getRevokedAt()).isNotNull();
        assertThat(c.getRevokedAt()).isNotNull();
    }

    @Test
    void revoke_chain_walks_back_to_root_then_forward() {
        // Same shape but enter at the HEAD (c). Reverse-walk must reach a; forward-walk must mark all.
        UUID userId = UUID.randomUUID();
        String hashA = h("raw_a");
        String hashB = h("raw_b");
        String hashC = h("raw_c");
        String hashD = h("raw_d"); // c.rotatedTo points here, but row is gone (DB returns empty)

        RefreshToken a = new RefreshToken(hashA, userId, Instant.now().plusSeconds(86400));
        RefreshToken b = new RefreshToken(hashB, userId, Instant.now().plusSeconds(86400));
        RefreshToken c = new RefreshToken(hashC, userId, Instant.now().plusSeconds(86400));
        a.setRotatedTo(hashB);
        b.setRotatedTo(hashC);
        c.setRotatedTo(hashD); // c also rotated (so replay detected on c)

        when(repo.findByTokenHashForUpdate(hashC)).thenReturn(Optional.of(c));
        when(repo.findByRotatedTo(hashC)).thenReturn(Optional.of(b));
        when(repo.findByRotatedTo(hashB)).thenReturn(Optional.of(a));
        when(repo.findByRotatedTo(hashA)).thenReturn(Optional.empty()); // root reached
        // Forward-walk from a: a.rotatedTo=hashB → fetch b; b.rotatedTo=hashC → fetch c;
        // c.rotatedTo=hashD → fetch d (returns empty); cursor=null → stop.
        when(repo.findById(hashB)).thenReturn(Optional.of(b));
        when(repo.findById(hashC)).thenReturn(Optional.of(c));
        lenient().when(repo.findById(hashD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.rotate("raw_c"))
                .isInstanceOf(RefreshInvalidException.class);

        // Pitfall 4 hard gate — ROOT (a) MUST be revoked even though we entered at the tail.
        assertThat(a.getRevokedAt())
                .as("ROOT (a) must be revoked — Pitfall 4 BOTH-directions walk")
                .isNotNull();
        assertThat(b.getRevokedAt()).isNotNull();
        assertThat(c.getRevokedAt()).isNotNull();
    }
}
