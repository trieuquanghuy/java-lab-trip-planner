// Source: docs/04-api-spec.md §6 error code catalog;
//         00-CONTEXT.md D-05 (Phase 0 baseline 2 codes);
//         01-CONTEXT.md (Phase 1 extends with 3 codes for invalid/expired tokens + 502).
//
// Phase 0 baseline: AUTH_UNAUTHORIZED, AUTH_RATE_LIMITED.
// Phase 1 additions: AUTH_INVALID_TOKEN (forged/malformed), AUTH_TOKEN_EXPIRED, BAD_GATEWAY (502).
// Phase 2 additions (02-CONTEXT.md D-15 / D-18): 8 auth.* + validation codes consumed by
//         AuthControllerAdvice — see .planning/phases/02-auth-service/02-RESEARCH.md lines 1010-1025.
package com.tripplanner.errors;

public enum ErrorCode {
    AUTH_UNAUTHORIZED("auth.unauthorized"),
    AUTH_RATE_LIMITED("auth.rate_limited"),
    AUTH_INVALID_TOKEN("auth.invalid_token"),
    AUTH_TOKEN_EXPIRED("auth.token_expired"),
    BAD_GATEWAY("gateway.bad_gateway"),
    AUTH_EMAIL_ALREADY_REGISTERED("auth.email_already_registered"),
    AUTH_INVALID_CREDENTIALS("auth.invalid_credentials"),
    AUTH_EMAIL_NOT_VERIFIED("auth.email_not_verified"),
    AUTH_TOKEN_INVALID("auth.token_invalid"),
    AUTH_REFRESH_INVALID("auth.refresh_invalid"),
    AUTH_WEAK_PASSWORD("auth.weak_password"),
    AUTH_INVALID_EMAIL("auth.invalid_email"),
    VALIDATION_FAILED("validation.failed");

    private final String code;

    ErrorCode(String code) { this.code = code; }

    public String code() { return code; }
}
