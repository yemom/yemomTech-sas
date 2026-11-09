package com.YemomBizGrow.billing.dto;

import jakarta.validation.constraints.NotBlank;

public record InitializePaymentRequest(
        @NotBlank(message = "Plan slug is required")
        String planSlug,

        String returnUrl
) {
}
