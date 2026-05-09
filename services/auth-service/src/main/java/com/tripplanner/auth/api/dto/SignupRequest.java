package com.tripplanner.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Per docs/04 §3 + 02-CONTEXT.md D-18.
 * Field names are "email" and "password" — AuthControllerAdvice (Plan 05) discriminates on these
 * exact names to map @Email failure to auth.invalid_email and @Size(min=8) to auth.weak_password.
 */
public record SignupRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 200) String password
) {}
