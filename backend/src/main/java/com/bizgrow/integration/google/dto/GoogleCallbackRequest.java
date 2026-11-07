package com.YemomBizGrow.integration.google.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GoogleCallbackRequest(
        @NotNull(message = "Website ID is required")
        UUID websiteId,

        @NotBlank(message = "Authorization code is required")
        String code,

        @NotBlank(message = "OAuth state is required")
        String state
) {}
