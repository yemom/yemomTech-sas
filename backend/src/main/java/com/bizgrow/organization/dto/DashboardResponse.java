package com.YemomBizGrow.organization.dto;

import com.YemomBizGrow.website.dto.WebsiteResponse;

import java.util.List;

/**
 * GET /api/v1/organizations/{id}/dashboard — everything the first-release
 * workspace overview shows, assembled from REAL persisted data only.
 *
 * <p>No traffic, leads, revenue, SEO score or AI recommendations appear here:
 * those modules do not exist yet, and inventing numbers would train users to
 * distrust the product. {@code onboarding.websiteRegistered} drives the
 * progress checklist instead.</p>
 *
 * @param organization    org profile (includes member/website counts)
 * @param recentWebsites  newest ACTIVE websites (max 5) for the "recent activity" card
 * @param onboarding      onboarding progress derived from real data
 */
public record DashboardResponse(
        OrganizationResponse organization,
        List<WebsiteResponse> recentWebsites,
        OnboardingProgress onboarding) {

    /**
     * @param websiteRegistered whether the org registered at least one website
     * @param hasAdditionalMember whether anyone besides the owner has joined
     */
    public record OnboardingProgress(boolean websiteRegistered, boolean hasAdditionalMember) {
    }
}
