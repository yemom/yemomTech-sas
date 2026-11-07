package com.YemomBizGrow.seo.dto;

import com.YemomBizGrow.seo.SeoIssue;

import java.time.Instant;
import java.util.UUID;

public record SeoIssueResponse(
        UUID id,
        UUID auditId,
        UUID organizationId,
        UUID websiteId,
        String category,
        String severity,
        String title,
        String description,
        String affectedUrl,
        String evidence,
        String explanation,
        String recommendation,
        String status,
        Instant createdAt
) {
    public static SeoIssueResponse from(SeoIssue issue) {
        return new SeoIssueResponse(
                issue.getId(),
                issue.getAuditId(),
                issue.getOrganizationId(),
                issue.getWebsiteId(),
                issue.getCategory(),
                issue.getSeverity(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getAffectedUrl(),
                issue.getEvidence(),
                issue.getExplanation(),
                issue.getRecommendation(),
                issue.getStatus(),
                issue.getCreatedAt()
        );
    }
}
