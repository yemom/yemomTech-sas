package com.YemomBizGrow.reporting.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ComprehensiveReportResponse(
        UUID organizationId,
        String organizationName,
        Instant generatedAt,
        SeoReportSection seo,
        TrafficReportSection traffic,
        CrmReportSection crm,
        MarketingReportSection marketing
) {
    public record SeoReportSection(
            Integer latestScore,
            long crawledPagesCount,
            int criticalIssuesCount,
            int warningIssuesCount,
            String auditStatus
    ) {}

    public record TrafficReportSection(
            long totalSessions,
            long totalPageviews,
            double bounceRatePct,
            long totalSearchImpressions,
            long totalSearchClicks,
            double averageCtrPct
    ) {}

    public record CrmReportSection(
            long totalLeads,
            long activeLeads,
            long wonLeads,
            long lostLeads,
            double conversionRatePct,
            BigDecimal totalPipelineValueEtb,
            BigDecimal wonRevenueEtb
    ) {}

    public record MarketingReportSection(
            long totalCampaigns,
            long activeCampaigns,
            BigDecimal totalBudgetEtb,
            BigDecimal totalSpentEtb,
            long attributedLeadsCount,
            BigDecimal returnOnAdSpend
    ) {}
}
