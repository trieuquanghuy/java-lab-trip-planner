package com.tripplanner.auth.repository;

import com.tripplanner.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Phase 2 / 02-CONTEXT.md D-10 + D-13 + Pitfall 4 (revokeChain walks BOTH directions —
 * back via findByRotatedTo reverse-lookup, then forward via findById on rotated_to).
 *
 * findByTokenHashForUpdate emits SELECT ... FOR UPDATE. The CALLING service method MUST be
 * @Transactional(isolation = REPEATABLE_READ) per D-13 — Spring Data's @Lock requires an
 * active write transaction (Pitfall 2 / spring-data-jpa#2141).
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rt FROM RefreshToken rt WHERE rt.tokenHash = :hash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("hash") String hash);

    /** Reverse-lookup for chain-walk in revokeChain (Pitfall 4). The rt_rotated_to_idx supports this. */
    Optional<RefreshToken> findByRotatedTo(String tokenHash);
}
