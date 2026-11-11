package com.YemomBizGrow.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicLeadCaptureRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @Email(message = "Email must be a valid email address")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 50, message = "Phone number must not exceed 50 characters")
        String phoneNumber,

        @Size(max = 200, message = "Company name must not exceed 200 characters")
        String companyName,

        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        String notes,

        /**
         * Spam prevention honeypot field. Real users never see or fill this field.
         * Automated bots fill every input they encounter. If non-blank, request is dropped.
         */
        String honeypot
) {}
