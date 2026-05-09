package com.tripplanner.auth.api.dto;

import com.tripplanner.auth.domain.User;

import java.util.UUID;

/**
 * Embedded in LoginResponse. NOT exposed via /me — Open Question 1 SKIP per RESEARCH
 * (the SPA already has email/ver in the JWT payload).
 *
 * The static factory keeps entity-to-DTO mapping in one place; saves duplication across
 * LoginResponse construction sites in Plan 05's AuthService.
 */
public record UserResponse(UUID id, String email, boolean emailVerified) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.isEmailVerified());
    }
}
