package com.bizgrow.website.dto;

import com.bizgrow.website.Website;
import com.bizgrow.website.WebsiteStatus;

import java.time.Instant;
import java.util.UUID;

/** Safe projection of a registered website — entities are never serialized directly. */
public record WebsiteResponse(
        UUID id,
        UUID organizationId,
        String name,
        String domain,
        String url,
        WebsiteStatus status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static WebsiteResponse from(Website website) {
        return new WebsiteResponse(
                website.getId(),
                website.getOrganizationId(),
                website.getName(),
                website.getDomain(),
                website.getUrl(),
                website.getStatus(),
                website.getCreatedBy(),
                website.getCreatedAt(),
                website.getUpdatedAt());
    }
}
