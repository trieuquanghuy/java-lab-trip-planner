package com.tripplanner.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for auth.email_verification_tokens. See V3__create_email_verification_tokens.sql.
 * `token` is the raw 64-char hex (CHAR(64) PK) — verification link reveals it; consumed_at marks single-use.
 */
@Entity
@Table(name = "email_verification_tokens", schema = "auth")
public class EmailVerificationToken {

    @Id
    @Column(name = "token", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String token;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected EmailVerificationToken() {}

    public EmailVerificationToken(String token, UUID userId, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getToken() { return token; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setConsumedAt(Instant t) { this.consumedAt = t; }
}
