package com.YemomBizGrow.reporting;

import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.crm.LeadStage;
import com.YemomBizGrow.crm.LeadStatus;
import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository;
import com.YemomBizGrow.integration.google.GoogleSearchConsoleSnapshotRepository;
import com.YemomBizGrow.marketing.CampaignStatus;
import com.YemomBizGrow.marketing.MarketingCampaign;
import com.YemomBizGrow.marketing.MarketingCampaignRepository;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.reporting.dto.ComprehensiveReportResponse;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.seo.SeoAuditRepository;
import com.YemomBizGrow.seo.SeoAuditStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReportService {

    private final OrganizationRepository organizationRepository;
    private final SeoAuditRepository seoAuditRepository;
    private final GoogleAnalyticsSnapshotRepository gaRepository;
    private final GoogleSearchConsoleSnapshotRepository gscRepository;
    private final LeadRepository leadRepository;
    private final MarketingCampaignRepository campaignRepository;
    private final AccessControlService accessControlService;

    public ReportService(
            OrganizationRepository organizationRepository,
            SeoAuditRepository seoAuditRepository,
            GoogleAnalyticsSnapshotRepository gaRepository,
            GoogleSearchConsoleSnapshotRepository gscRepository,
            LeadRepository leadRepository,
            MarketingCampaignRepository campaignRepository,
            AccessControlService accessControlService
    ) {
        this.organizationRepository = organizationRepository;
        this.seoAuditRepository = seoAuditRepository;
        this.gaRepository = gaRepository;
        this.gscRepository = gscRepository;
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.accessControlService = accessControlService;
    }

    @Transactional(readOnly = true)
    public ComprehensiveReportResponse generateComprehensiveReport(UUID actorUserId, UUID organizationId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ANALYTICS_VIEW);

        Organization org = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization not found"));

        // 1. SEO section
        Optional<SeoAudit> latestAudit = seoAuditRepository.findFirstByOrganizationIdAndStatusOrderByCompletedAtDesc(
                organizationId, SeoAuditStatus.COMPLETED);

        ComprehensiveReportResponse.SeoReportSection seoSection = new ComprehensiveReportResponse.SeoReportSection(
                latestAudit.map(SeoAudit::getScore).orElse(null),
                latestAudit.map(SeoAudit::getPagesCrawled).map(Integer::longValue).orElse(0L),
                latestAudit.map(SeoAudit::getCriticalIssuesCount).orElse(0),
                latestAudit.map(SeoAudit::getWarningIssuesCount).orElse(0),
                latestAudit.map(a -> a.getStatus().name()).orElse("NO_AUDITS")
        );

        // 2. Traffic section (last 30 days)
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(30);

        var gaTotals = gaRepository.aggregateTotals(organizationId, null, from, to);
        var gscTotals = gscRepository.aggregateTotals(organizationId, null, from, to);

        ComprehensiveReportResponse.TrafficReportSection trafficSection = new ComprehensiveReportResponse.TrafficReportSection(
                gaTotals.getTotalSessions(),
                gaTotals.getTotalPageViews(),
                Math.round(gaTotals.getAvgBounceRate() * 1000.0) / 10.0,
                gscTotals.getTotalImpressions(),
                gscTotals.getTotalClicks(),
                Math.round(gscTotals.getAvgCtr() * 1000.0) / 10.0
        );

        // 3. CRM section
        List<Lead> allLeads = leadRepository.findByOrganizationIdAndStatus(organizationId, LeadStatus.ACTIVE);
        long totalLeads = allLeads.size();
        long wonLeads = allLeads.stream().filter(l -> l.getStage() == LeadStage.WON).count();
        long lostLeads = allLeads.stream().filter(l -> l.getStage() == LeadStage.LOST).count();
        long activeLeads = totalLeads - wonLeads - lostLeads;

        double conversionRate = totalLeads > 0
                ? Math.round(((double) wonLeads / totalLeads) * 1000.0) / 10.0
                : 0.0;

        BigDecimal totalPipeline = allLeads.stream()
                .map(l -> l.getEstimatedValueEtb() != null ? l.getEstimatedValueEtb() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal wonRevenue = allLeads.stream()
                .filter(l -> l.getStage() == LeadStage.WON)
                .map(l -> l.getEstimatedValueEtb() != null ? l.getEstimatedValueEtb() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ComprehensiveReportResponse.CrmReportSection crmSection = new ComprehensiveReportResponse.CrmReportSection(
                totalLeads,
                activeLeads,
                wonLeads,
                lostLeads,
                conversionRate,
                totalPipeline,
                wonRevenue
        );

        // 4. Marketing section
        List<MarketingCampaign> campaigns = campaignRepository.findByOrganizationId(organizationId, Pageable.unpaged()).getContent();
        long totalCampaigns = campaigns.size();
        long activeCampaigns = campaigns.stream().filter(c -> c.getStatus() == CampaignStatus.ACTIVE).count();

        BigDecimal totalBudget = campaigns.stream()
                .map(c -> c.getBudgetEtb() != null ? c.getBudgetEtb() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSpent = campaigns.stream()
                .map(c -> c.getSpentEtb() != null ? c.getSpentEtb() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long attributedLeads = allLeads.stream().filter(l -> l.getCampaignId() != null).count();

        BigDecimal roas = (totalSpent.compareTo(BigDecimal.ZERO) > 0)
                ? wonRevenue.divide(totalSpent, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        ComprehensiveReportResponse.MarketingReportSection marketingSection = new ComprehensiveReportResponse.MarketingReportSection(
                totalCampaigns,
                activeCampaigns,
                totalBudget,
                totalSpent,
                attributedLeads,
                roas
        );

        return new ComprehensiveReportResponse(
                org.getId(),
                org.getName(),
                Instant.now(),
                seoSection,
                trafficSection,
                crmSection,
                marketingSection
        );
    }

    @Transactional(readOnly = true)
    public String exportReportCsv(UUID actorUserId, UUID organizationId) {
        ComprehensiveReportResponse report = generateComprehensiveReport(actorUserId, organizationId);

        StringBuilder sb = new StringBuilder();
        sb.append("BizGrow SaaS Performance Report\n");
        sb.append("Organization,").append(escapeCsv(report.organizationName())).append("\n");
        sb.append("Generated At,").append(report.generatedAt()).append("\n\n");

        sb.append("Module,Metric,Value\n");

        // SEO
        sb.append("SEO,Health Score,").append(report.seo().latestScore() != null ? report.seo().latestScore() : "N/A").append("\n");
        sb.append("SEO,Crawled Pages,").append(report.seo().crawledPagesCount()).append("\n");
        sb.append("SEO,Critical Issues,").append(report.seo().criticalIssuesCount()).append("\n");
        sb.append("SEO,Warning Issues,").append(report.seo().warningIssuesCount()).append("\n");

        // Traffic
        sb.append("Traffic,30-Day Sessions,").append(report.traffic().totalSessions()).append("\n");
        sb.append("Traffic,30-Day Pageviews,").append(report.traffic().totalPageviews()).append("\n");
        sb.append("Traffic,Bounce Rate %,").append(report.traffic().bounceRatePct()).append("\n");
        sb.append("Traffic,Search Impressions,").append(report.traffic().totalSearchImpressions()).append("\n");
        sb.append("Traffic,Search Clicks,").append(report.traffic().totalSearchClicks()).append("\n");
        sb.append("Traffic,Search CTR %,").append(report.traffic().averageCtrPct()).append("\n");

        // CRM
        sb.append("CRM,Total Leads,").append(report.crm().totalLeads()).append("\n");
        sb.append("CRM,Active Pipeline Leads,").append(report.crm().activeLeads()).append("\n");
        sb.append("CRM,Won Deals,").append(report.crm().wonLeads()).append("\n");
        sb.append("CRM,Lost Deals,").append(report.crm().lostLeads()).append("\n");
        sb.append("CRM,Conversion Rate %,").append(report.crm().conversionRatePct()).append("\n");
        sb.append("CRM,Pipeline Value ETB,").append(report.crm().totalPipelineValueEtb()).append("\n");
        sb.append("CRM,Won Revenue ETB,").append(report.crm().wonRevenueEtb()).append("\n");

        // Marketing
        sb.append("Marketing,Total Campaigns,").append(report.marketing().totalCampaigns()).append("\n");
        sb.append("Marketing,Active Campaigns,").append(report.marketing().activeCampaigns()).append("\n");
        sb.append("Marketing,Total Budget ETB,").append(report.marketing().totalBudgetEtb()).append("\n");
        sb.append("Marketing,Total Spent ETB,").append(report.marketing().totalSpentEtb()).append("\n");
        sb.append("Marketing,Attributed Leads,").append(report.marketing().attributedLeadsCount()).append("\n");
        sb.append("Marketing,Return on Ad Spend (ROAS),").append(report.marketing().returnOnAdSpend()).append("\n");

        return sb.toString();
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
