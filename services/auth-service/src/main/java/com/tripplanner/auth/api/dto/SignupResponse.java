package com.tripplanner.auth.api.dto;

import java.util.UUID;

/**
 * docs/04 §3 — 201 body is exactly { userId }.
 * Per D-24 (account-enumeration on /signup locked), this is returned for both new accounts
 * AND existing accounts (verified or unverified) — opaque envelope.
 */
public record SignupResponse(UUID userId) {}
