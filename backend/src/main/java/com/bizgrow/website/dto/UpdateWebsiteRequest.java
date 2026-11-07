package com.bizgrow.website.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/organizations/{organizationId}/websites/{websiteId}.
 * Both fields optional; a provided URL is re-normalized and re-checked for
 * per-organization uniqueness. Status changes go through DELETE (deactivate).
 */
public record UpdateWebsiteRequest(
        @Size(max = 200, message = "Website name must be at most 200 characters.")
        String name,

        @Size(max = 2048, message = "URL must be at most 2048 characters.")
        @Pattern(regexp = "(?i)^https?://.+", message = "URL must start with http:// or https://")
        String url) {
}
