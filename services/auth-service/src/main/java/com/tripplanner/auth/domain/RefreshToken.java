package com.tripplanner.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for auth.refresh_tokens. See V4__create_refresh_tokens.sql.
 * `tokenHash` is the SHA-256 hex of the raw cookie value — the raw token never lives in the DB
 * (docs/05-auth-security.md §2; 02-CONTEXT.md D-10/D-13).
 *
 * @JdbcTypeCode(SqlTypes.CHAR) on the two CHAR(64) columns (Plan 02-06 Rule 1 fix): Postgres
 * reports CHAR(64) as bpchar (Types#CHAR), but Hibernate's default String mapping is Types.VARCHAR.
 * Without this annotation ddl-auto: validate fails on boot with a schema-validation error.
 */
@Entity
@Table(name = "refresh_tokens", schema = "auth")
public class RefreshToken {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64, columnDefinition = "char(64)")
    private String tokenHash;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "rotated_to", length = 64, columnDefinition = "char(64)")
    private String rotatedTo;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {}

    public RefreshToken(String tokenHash, UUID userId, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getTokenHash() { return tokenHash; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getRotatedTo() { return rotatedTo; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setRotatedTo(String h) { this.rotatedTo = h; }
    public void setRevokedAt(Instant t) { this.revokedAt = t; }
}
