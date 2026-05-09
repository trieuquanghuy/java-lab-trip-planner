package com.tripplanner.auth.api.dto;

/**
 * docs/04 §3 — 200 body is { accessToken, expiresIn, user }.
 * expiresIn = 900 (15-min JWT TTL per docs/05 §1, mirrored in Plan 02's JwtIssuer).
 */
public record LoginResponse(String accessToken, int expiresIn, UserResponse user) {}
