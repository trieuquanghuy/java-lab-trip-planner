package com.tripplanner.auth.service.exception;

/** Per docs/04 §6 + UI-SPEC §Server-Driven Copy Contract — handler in AuthControllerAdvice maps to RFC 7807. */
public class RefreshInvalidException extends RuntimeException {
    public RefreshInvalidException() { super(); }
}
