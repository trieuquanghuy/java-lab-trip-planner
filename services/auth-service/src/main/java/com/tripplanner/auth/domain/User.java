package com.tripplanner.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for auth.users. See V2__create_users.sql.
 * Fields match column types BYTE-FOR-BYTE — Hibernate ddl-auto: validate rejects drift on boot.
 */
@Entity
@Table(name = "users", schema = "auth")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}   // JPA

    public User(UUID id, String email, String passwordHash, boolean emailVerified) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.emailVerified = emailVerified;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEmailVerified(boolean v) { this.emailVerified = v; this.updatedAt = Instant.now(); }
    public void setPasswordHash(String h) { this.passwordHash = h; this.updatedAt = Instant.now(); }
}
