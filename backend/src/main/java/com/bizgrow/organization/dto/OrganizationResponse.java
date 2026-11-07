package com.YemomBizGrow.organization.dto;

import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * The organization as clients see it: profile + Ethiopia-friendly locale
 * settings + live counts used by the workspace dashboard.
 *
 * @param memberCount ACTIVE memberships (shown on dashboard/settings/members)
 * @param websiteCount ACTIVE registered websites (shown on dashboard/websites)
 */
public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String industry,
        String websiteUrl,
        String countryCode,
        String currencyCode,
        String timezone,
        OrganizationStatus status,
        UUID createdBy,
        long memberCount,
        long websiteCount,
        Instant createdAt,
        Instant updatedAt) {

    public static OrganizationResponse of(Organization organization, long memberCount, long websiteCount) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getDescription(),
                organization.getIndustry(),
                organization.getWebsiteUrl(),
                organization.getCountryCode(),
                organization.getCurrencyCode(),
                organization.getTimezone(),
                organization.getStatus(),
                organization.getCreatedBy(),
                memberCount,
                websiteCount,
                organization.getCreatedAt(),
                organization.getUpdatedAt());
    }
}
