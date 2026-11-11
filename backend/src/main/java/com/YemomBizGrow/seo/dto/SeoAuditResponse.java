package com.YemomBizGrow.seo.dto;

import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.seo.SeoAuditStatus;

import java.time.Instant;
import java.util.UUID;

public record SeoAuditResponse(
        UUID id,
        UUID organizationId,
        UUID websiteId,
        SeoAuditStatus status,
        Integer score,
        Integer technicalScore,
        Integer onPageScore,
        Integer contentScore,
        Integer accessibilityScore,
        int pagesCrawled,
        int criticalIssuesCount,
        int warningIssuesCount,
        int infoIssuesCount,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt
) {
    public static SeoAuditResponse from(SeoAudit audit) {
        return new SeoAuditResponse(
                audit.getId(),
                audit.getOrganizationId(),
                audit.getWebsiteId(),
                audit.getStatus(),
                audit.getScore(),
                audit.getTechnicalScore(),
                audit.getOnPageScore(),
                audit.getContentScore(),
                audit.getAccessibilityScore(),
                audit.getPagesCrawled(),
                audit.getCriticalIssuesCount(),
                audit.getWarningIssuesCount(),
                audit.getInfoIssuesCount(),
                audit.getErrorMessage(),
                audit.getStartedAt(),
                audit.getCompletedAt(),
                audit.getCreatedAt()
        );
    }
}
