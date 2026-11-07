package com.bizgrow.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/organizations payload. Country/currency/timezone are NOT accepted
 * here — an organization always starts with the platform defaults (ET/ETB/
 * Africa/Addis_Ababa); per-organization overrides arrive with a dedicated
 * settings endpoint once there is a real need (avoid speculative fields).
 *
 * @param name        required business/organization name (max 200)
 * @param industry    optional free-form industry label, normalized to UPPER_CASE by the service
 * @param description optional pitch/description (max 2000)
 * @param websiteUrl  optional existing company website (http/https, validated)
 */
public record CreateOrganizationRequest(
        @NotBlank(message = "Organization name is required.")
        @Size(max = 200, message = "Organization name must be at most 200 characters.")
        String name,

        @Size(max = 100, message = "Industry must be at most 100 characters.")
        String industry,

        @Size(max = 2000, message = "Description must be at most 2000 characters.")
        String description,

        @Size(max = 500, message = "Website URL must be at most 500 characters.")
        @Pattern(regexp = "(?i)^https?://.+", message = "Website URL must start with http:// or https://")
        String websiteUrl) {
}
