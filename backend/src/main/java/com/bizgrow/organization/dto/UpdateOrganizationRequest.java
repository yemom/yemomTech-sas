package com.bizgrow.organization.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/v1/organizations/{id} payload — every field optional (true PATCH:
 * only non-null fields are applied). System-controlled fields (slug, status,
 * country/currency/timezone, createdBy) are absent by design so they can never
 * be mass-assigned by a client.
 *
 * @param name        new name (slug does NOT change — links stay stable)
 * @param industry    new industry label
 * @param description new description; empty string clears it
 * @param websiteUrl  new profile URL; empty string clears it
 */
public record UpdateOrganizationRequest(
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
