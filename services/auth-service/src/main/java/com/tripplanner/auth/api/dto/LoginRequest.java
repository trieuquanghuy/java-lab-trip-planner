package com.tripplanner.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Per docs/04 §3 + 02-CONTEXT.md D-18.
 * Field names match SignupRequest so AuthControllerAdvice (Plan 05) can use a single
 * field-discrimination function across signup + login.
 */
public record LoginRequest(
        @Email @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 200) String password
) {}
