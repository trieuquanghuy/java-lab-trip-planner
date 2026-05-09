package com.tripplanner.auth.api.dto;

/**
 * docs/04 §3 — 200 body is { accessToken, expiresIn }.
 * SPA keeps the cached UserResponse from /login; refresh only rotates the access token.
 */
public record RefreshResponse(String accessToken, int expiresIn) {}
