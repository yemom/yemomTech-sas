package com.bizgrow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * POST /api/v1/auth/login payload.
 * Error messages returned for failures are deliberately generic (see AuthService).
 */
public record LoginRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        String email,

        @NotBlank(message = "Password is required.")
        String password) {
}
