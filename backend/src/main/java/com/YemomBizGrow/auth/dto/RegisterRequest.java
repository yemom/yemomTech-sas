package com.YemomBizGrow.auth.dto;

import com.YemomBizGrow.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/auth/register payload.
 *
 * <p>Validation is declarative here and re-checked against real state in the
 * service (duplicate e-mail), so the controller stays a thin translation layer.</p>
 *
 * @param firstName   required, max 100
 * @param lastName    required, max 100
 * @param email       required, syntactically valid; normalized before persistence
 * @param password    required + platform password policy (see {@link StrongPassword})
 * @param phoneNumber optional international format, e.g. +251934000000
 */
public record RegisterRequest(
        @NotBlank(message = "First name is required.")
        @Size(max = 100, message = "First name must be at most 100 characters.")
        String firstName,

        @NotBlank(message = "Last name is required.")
        @Size(max = 100, message = "Last name must be at most 100 characters.")
        String lastName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        @Size(max = 255, message = "Email must be at most 255 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @StrongPassword
        String password,

        @Size(max = 32, message = "Phone number must be at most 32 characters.")
        @Pattern(regexp = "^\\+?[0-9][0-9 ()-]{6,19}$",
                message = "Phone number must look like +251911234567.")
        String phoneNumber) {
}
