package com.bizgrow.website.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/organizations/{organizationId}/websites payload.
 *
 * <p>The service (not this annotation) performs full normalization: lowercasing,
 * scheme check, public-domain check, and per-organization uniqueness. The pattern
 * here only rejects obvious garbage early with a field error.</p>
 *
 * @param name display name for the website (max 200)
 * @param url  full URL; http/https only
 */
public record CreateWebsiteRequest(
        @NotBlank(message = "Website name is required.")
        @Size(max = 200, message = "Website name must be at most 200 characters.")
        String name,

        @NotBlank(message = "Website URL is required.")
        @Size(max = 2048, message = "URL must be at most 2048 characters.")
        @Pattern(regexp = "(?i)^https?://.+", message = "URL must start with http:// or https://")
        String url) {
}
