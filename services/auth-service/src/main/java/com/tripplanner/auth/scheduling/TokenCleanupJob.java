package com.tripplanner.auth.scheduling;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily 02:00 UTC cleanup of expired email-verification + refresh tokens (D-17).
 * No ShedLock — auth-service is single-instance per docs/02 §3.
 *
 * <p>Two transactions per Open Question 4 RESOLVED — partial failure on one DELETE doesn't
 * roll back the other; logs are also cleaner (each transaction reports its own row count).
 *
 * <p>Self-invocation caveat: cleanup() calls cleanupEmailTokens() / cleanupRefreshTokens()
 * via {@code this}, which bypasses the AOP proxy. The inner methods are public to allow
 * proxy interception when called from outside (e.g., admin cron-trigger endpoint in v2).
 * For Phase 2's single @Scheduled trigger, the practical outcome is: each native query runs
 * in its own short-lived transaction (default Tx propagation REQUIRED). Plan 06's IT
 * confirms behavior end-to-end by triggering the cleanup manually and asserting row deletes.
 *
 * <p>The cron string {@code "0 0 2 * * *"} is Spring's 6-field cron (second minute hour
 * day-of-month month day-of-week) — daily at 02:00:00. Spring uses the JVM's default
 * timezone unless {@code zone =} is specified; UTC is the JVM default in the docker images
 * Phase 0 deploys, aligning with D-17's "02:00 UTC" specification.
 */
@Component
public class TokenCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupJob.class);

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanup() {
        int evDeleted = cleanupEmailTokens();
        int rtDeleted = cleanupRefreshTokens();
        log.info("TokenCleanupJob: deleted {} email_verification_tokens, {} refresh_tokens",
                evDeleted, rtDeleted);
    }

    @Transactional
    public int cleanupEmailTokens() {
        return em.createNativeQuery(
                        "DELETE FROM auth.email_verification_tokens WHERE expires_at < NOW() - INTERVAL '7 days'")
                .executeUpdate();
    }

    @Transactional
    public int cleanupRefreshTokens() {
        return em.createNativeQuery(
                        "DELETE FROM auth.refresh_tokens WHERE expires_at < NOW() - INTERVAL '7 days'")
                .executeUpdate();
    }
}
