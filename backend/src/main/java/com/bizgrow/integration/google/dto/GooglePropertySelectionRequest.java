package com.YemomBizGrow.integration.google.dto;

import jakarta.validation.constraints.Size;

public record GooglePropertySelectionRequest(
        @Size(max = 128, message = "GA4 Property ID cannot exceed 128 characters")
        String ga4PropertyId,

        @Size(max = 2048, message = "Search Console Site URL cannot exceed 2048 characters")
        String gscSiteUrl
) {}
