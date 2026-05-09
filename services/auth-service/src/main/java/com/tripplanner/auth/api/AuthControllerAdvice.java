// Source: 02-RESEARCH.md §Code Examples lines 903-1008.
//         02-UI-SPEC.md §Server-Driven Copy Contract — the 9 detail strings are LOCKED here byte-for-byte.
//         02-CONTEXT.md D-14 (BL-01 — Spring's auto-configured ObjectMapper handles serialization),
//                       D-18 (Bean Validation field-name discrimination).
//
// Pitfall 7 / BL-01: NEVER `new ObjectMapper()` — Spring's auto-configured ObjectMapper has
//   ProblemDetailJacksonMixin that flattens code/etc to JSON root ($.code, NOT $.properties.code).
//   Returning a ProblemDetail from an @ExceptionHandler lets Spring's HandlerAdapter serialize via
//   the auto-configured bean. This advice never constructs an ObjectMapper.
//
// The 9 verbatim UI-SPEC detail strings (Plan 06's AuthControllerAdviceIT will assert each verbatim):
//   auth.invalid_email          400 -> "Invalid email format."
//   auth.weak_password          400 -> "Password does not meet minimum requirements."
//   auth.invalid_credentials    400 -> "Email or password is incorrect."
//   auth.email_not_verified     403 -> "Please verify your email before logging in."
//   auth.token_invalid          400 -> "This verification link is invalid."
//   auth.token_expired          400 -> "This verification link has expired."
//   auth.refresh_invalid        401 -> "Session expired. Please log in again."
//   auth.rate_limited           429 -> "Too many attempts. Please try again later."
//   validation.failed           400 -> "Request validation failed."
package com.tripplanner.auth.api;

import com.tripplanner.auth.service.exception.EmailAlreadyRegisteredException;
import com.tripplanner.auth.service.exception.EmailNotVerifiedException;
import com.tripplanner.auth.service.exception.InvalidCredentialsException;
import com.tripplanner.auth.service.exception.LoginRateLimitedException;
import com.tripplanner.auth.service.exception.RefreshInvalidException;
import com.tripplanner.auth.service.exception.TokenExpiredException;
import com.tripplanner.auth.service.exception.TokenInvalidException;
import com.tripplanner.errors.ErrorCode;
import com.tripplanner.errors.ProblemDetailFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Set;
import java.util.stream.Collectors;

@ControllerAdvice
public class AuthControllerAdvice extends ResponseEntityExceptionHandler {

    // --- Bean Validation (D-18) — discriminate on field name to map to UI-SPEC strings ---
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest req) {

        Set<String> failedFields = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .collect(Collectors.toSet());

        ErrorCode code;
        String detail;
        if (failedFields.contains("password")) {
            code = ErrorCode.AUTH_WEAK_PASSWORD;
            detail = "Password does not meet minimum requirements.";   // UI-SPEC §Server-Driven Copy
        } else if (failedFields.contains("email")) {
            code = ErrorCode.AUTH_INVALID_EMAIL;
            detail = "Invalid email format.";                          // UI-SPEC §Server-Driven Copy
        } else {
            code = ErrorCode.VALIDATION_FAILED;
            detail = "Request validation failed.";                     // UI-SPEC §Server-Driven Copy (fallback)
        }
        ProblemDetail pd = ProblemDetailFactory.of(HttpStatus.BAD_REQUEST, code, detail);
        HttpHeaders out = new HttpHeaders();
        out.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, out, HttpStatus.BAD_REQUEST);
    }

    // --- Custom service exceptions — UI-SPEC detail strings VERBATIM (byte-for-byte) ---

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ProblemDetail> onInvalidCreds(InvalidCredentialsException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_INVALID_CREDENTIALS,
                "Email or password is incorrect.");
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<ProblemDetail> onUnverified(EmailNotVerifiedException ex) {
        return body(HttpStatus.FORBIDDEN, ErrorCode.AUTH_EMAIL_NOT_VERIFIED,
                "Please verify your email before logging in.");
    }

    @ExceptionHandler(TokenInvalidException.class)
    public ResponseEntity<ProblemDetail> onTokenInvalid(TokenInvalidException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_TOKEN_INVALID,
                "This verification link is invalid.");
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ProblemDetail> onTokenExpired(TokenExpiredException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_TOKEN_EXPIRED,
                "This verification link has expired.");
    }

    @ExceptionHandler(RefreshInvalidException.class)
    public ResponseEntity<ProblemDetail> onRefreshInvalid(RefreshInvalidException ex) {
        return body(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REFRESH_INVALID,
                "Session expired. Please log in again.");
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ProblemDetail> onRateLimited(LoginRateLimitedException ex) {
        return body(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.AUTH_RATE_LIMITED,
                "Too many attempts. Please try again later.");
    }

    /**
     * EmailAlreadyRegisteredException is in the catalog (ErrorCode entry exists) but is
     * NEVER thrown from /signup per D-24 / Open Q5 Option A — UI-SPEC §Server-Driven Copy Contract
     * marks it "NOT EMITTED on /api/auth/signup". This handler exists so any FUTURE admin endpoint
     * that decides to surface registration can route through the same advice without retroactive change.
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ProblemDetail> onAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        return body(HttpStatus.CONFLICT, ErrorCode.AUTH_EMAIL_ALREADY_REGISTERED,
                "An account with this email already exists.");
    }

    private ResponseEntity<ProblemDetail> body(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetailFactory.of(status, code, detail);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(pd, headers, status);
    }
}
