// Source: 00-CONTEXT.md D-05 — Phase 0 ships ONLY these 2 baseline codes.
// Phase 1+ extends this enum as new codes arise (auth.invalid_credentials,
// trip.not_found, validation.required_field, etc.). Adding more codes in
// Phase 0 is scope creep and contradicts D-05.
package com.tripplanner.errors;

public enum ErrorCode {
    AUTH_UNAUTHORIZED("auth.unauthorized"),
    AUTH_RATE_LIMITED("auth.rate_limited");

    private final String code;

    ErrorCode(String code) { this.code = code; }

    public String code() { return code; }
}
