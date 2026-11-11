package com.YemomBizGrow.reporting;

import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.crm.LeadStage;
import com.YemomBizGrow.crm.LeadStatus;
import com.YemomBizGrow.integration.google.GoogleAnalyticsSnapshotRepository;
import com.YemomBizGrow.integration.google.GoogleSearchConsoleSnapshotRepository;
import com.YemomBizGrow.marketing.MarketingCampaignRepository;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.reporting.dto.ComprehensiveReportResponse;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.seo.SeoAuditRepository;
import com.YemomBizGrow.seo.SeoAuditStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private SeoAuditRepository seoAuditRepository;

    @Mock
    private GoogleAnalyticsSnapshotRepository gaRepository;

    @Mock
    private GoogleSearchConsoleSnapshotRepository gscRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private MarketingCampaignRepository campaignRepository;

    @Mock
    private AccessControlService accessControlService;

    private ReportService reportService;

    private final UUID actorId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reportService = new ReportService(
                organizationRepository,
                seoAuditRepository,
                gaRepository,
                gscRepository,
                leadRepository,
                campaignRepository,
                accessControlService
        );
    }

    @Test
    @DisplayName("Generates comprehensive cross-module report and CSV export")
    void generateReportAndCsv() {
        Organization org = new Organization();
        org.setId(orgId);
        org.setName("Yemom Consulting");

        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        SeoAudit audit = new SeoAudit();
        audit.setScore(88);
        audit.setPagesCrawled(14);
        audit.setCriticalIssuesCount(1);
        audit.setWarningIssuesCount(3);
        audit.setStatus(SeoAuditStatus.COMPLETED);
        when(seoAuditRepository.findFirstByOrganizationIdAndStatusOrderByCompletedAtDesc(eq(orgId), eq(SeoAuditStatus.COMPLETED)))
                .thenReturn(Optional.of(audit));

        GoogleAnalyticsSnapshotRepository.AnalyticsTotals gaTotals = new GoogleAnalyticsSnapshotRepository.AnalyticsTotals() {
            public long getTotalSessions() { return 1500; }
            public long getTotalUsers() { return 1200; }
            public long getTotalPageViews() { return 4500; }
            public double getAvgBounceRate() { return 0.42; }
        };
        when(gaRepository.aggregateTotals(eq(orgId), any(), any(), any())).thenReturn(gaTotals);

        GoogleSearchConsoleSnapshotRepository.SearchConsoleTotals gscTotals = new GoogleSearchConsoleSnapshotRepository.SearchConsoleTotals() {
            public long getTotalClicks() { return 350; }
            public long getTotalImpressions() { return 8000; }
            public double getAvgCtr() { return 0.043; }
            public double getAvgPosition() { return 12.5; }
        };
        when(gscRepository.aggregateTotals(eq(orgId), any(), any(), any())).thenReturn(gscTotals);

        Lead lead1 = new Lead();
        lead1.setId(UUID.randomUUID());
        lead1.setStage(LeadStage.WON);
        lead1.setEstimatedValueEtb(new BigDecimal("60000.00"));

        Lead lead2 = new Lead();
        lead2.setId(UUID.randomUUID());
        lead2.setStage(LeadStage.QUALIFIED);
        lead2.setEstimatedValueEtb(new BigDecimal("40000.00"));

        when(leadRepository.findByOrganizationIdAndStatus(orgId, LeadStatus.ACTIVE)).thenReturn(List.of(lead1, lead2));
        when(campaignRepository.findByOrganizationId(eq(orgId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        ComprehensiveReportResponse report = reportService.generateComprehensiveReport(actorId, orgId);

        assertThat(report.organizationName()).isEqualTo("Yemom Consulting");
        assertThat(report.seo().latestScore()).isEqualTo(88);
        assertThat(report.traffic().totalSessions()).isEqualTo(1500);
        assertThat(report.traffic().totalSearchImpressions()).isEqualTo(8000);
        assertThat(report.crm().totalLeads()).isEqualTo(2);
        assertThat(report.crm().wonLeads()).isEqualTo(1);
        assertThat(report.crm().conversionRatePct()).isEqualTo(50.0);
        assertThat(report.crm().wonRevenueEtb()).isEqualByComparingTo("60000.00");

        String csv = reportService.exportReportCsv(actorId, orgId);
        assertThat(csv).contains("YemomBizGrow SaaS Performance Report");
        assertThat(csv).contains("SEO,Health Score,88");
        assertThat(csv).contains("Traffic,30-Day Sessions,1500");
        assertThat(csv).contains("CRM,Won Revenue ETB,60000.00");
    }
}
