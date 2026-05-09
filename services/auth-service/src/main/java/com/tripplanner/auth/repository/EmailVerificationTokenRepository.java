package com.tripplanner.auth.repository;

import com.tripplanner.auth.domain.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, String> {

    /** Re-signup branch (D-23) — invalidate any prior unconsumed tokens for this user. */
    @Modifying
    @Query("UPDATE EmailVerificationToken t SET t.consumedAt = :now " +
           "WHERE t.userId = :userId AND t.consumedAt IS NULL")
    int markAllUnconsumedAsConsumedFor(@Param("userId") UUID userId, @Param("now") Instant now);
}
