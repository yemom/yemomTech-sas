package com.YemomBizGrow.crm.dto;

import com.YemomBizGrow.crm.LeadSource;
import com.YemomBizGrow.crm.LeadStage;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateLeadRequest(
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

        LeadSource source,

        LeadStage stage,

        UUID assignedUserId,

        @DecimalMin(value = "0.0", inclusive = true, message = "Estimated value must be non-negative")
        BigDecimal estimatedValueEtb,

        String notes
) {}
