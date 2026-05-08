// Source: docs/04-api-spec.md §6 error code catalog;
//         00-CONTEXT.md D-05 (Phase 0 baseline 2 codes);
//         01-CONTEXT.md (Phase 1 extends with 3 codes for invalid/expired tokens + 502).
//
// Phase 0 baseline: AUTH_UNAUTHORIZED, AUTH_RATE_LIMITED.
// Phase 1 additions: AUTH_INVALID_TOKEN (forged/malformed), AUTH_TOKEN_EXPIRED, BAD_GATEWAY (502).
package com.tripplanner.errors;

public enum ErrorCode {
    AUTH_UNAUTHORIZED("auth.unauthorized"),
    AUTH_RATE_LIMITED("auth.rate_limited"),
    AUTH_INVALID_TOKEN("auth.invalid_token"),
    AUTH_TOKEN_EXPIRED("auth.token_expired"),
    BAD_GATEWAY("gateway.bad_gateway");

    private final String code;

    ErrorCode(String code) { this.code = code; }

    public String code() { return code; }
}
