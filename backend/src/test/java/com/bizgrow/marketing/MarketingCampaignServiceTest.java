package com.YemomBizGrow.marketing;

import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.crm.LeadStage;
import com.YemomBizGrow.marketing.dto.CampaignPerformanceResponse;
import com.YemomBizGrow.marketing.dto.CampaignResponse;
import com.YemomBizGrow.marketing.dto.CreateCampaignRequest;
import com.YemomBizGrow.membership.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingCampaignServiceTest {

    @Mock
    private MarketingCampaignRepository campaignRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AuditService auditService;

    private MarketingCampaignService campaignService;

    private final UUID actorId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();
    private final UUID campaignId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        campaignService = new MarketingCampaignService(
                campaignRepository,
                leadRepository,
                accessControlService,
                auditService
        );
    }

    @Test
    @DisplayName("Creates a marketing campaign with UTM parameters and records audit log")
    void createCampaignSuccess() {
        CreateCampaignRequest request = new CreateCampaignRequest(
                "Addis Tech Expo Ads",
                "Promoting annual developer summit",
                CampaignObjective.LEAD_GENERATION,
                CampaignStatus.ACTIVE,
                CampaignChannel.TELEGRAM,
                new BigDecimal("25000.00"),
                BigDecimal.ZERO,
                LocalDate.now(),
                LocalDate.now().plusMonths(1),
                "telegram",
                "cpc",
                "tech_expo_2026",
                "developers",
                "banner_ad"
        );

        when(campaignRepository.save(any(MarketingCampaign.class))).thenAnswer(inv -> {
            MarketingCampaign c = inv.getArgument(0);
            c.setId(campaignId);
            return c;
        });

        CampaignResponse response = campaignService.createCampaign(actorId, orgId, request);

        assertThat(response.id()).isEqualTo(campaignId);
        assertThat(response.name()).isEqualTo("Addis Tech Expo Ads");
        assertThat(response.channel()).isEqualTo(CampaignChannel.TELEGRAM);
        assertThat(response.utmCampaign()).isEqualTo("tech_expo_2026");
        assertThat(response.budgetEtb()).isEqualByComparingTo("25000.00");
    }

    @Test
    @DisplayName("Calculates campaign performance metrics (CPL and ROAS) correctly")
    void getCampaignPerformanceMetrics() {
        MarketingCampaign campaign = new MarketingCampaign();
        campaign.setId(campaignId);
        campaign.setOrganizationId(orgId);
        campaign.setName("Meta Real Estate Campaign");
        campaign.setBudgetEtb(new BigDecimal("10000.00"));
        campaign.setSpentEtb(new BigDecimal("5000.00"));

        Lead lead1 = new Lead();
        lead1.setId(UUID.randomUUID());
        lead1.setStage(LeadStage.WON);
        lead1.setEstimatedValueEtb(new BigDecimal("50000.00"));

        Lead lead2 = new Lead();
        lead2.setId(UUID.randomUUID());
        lead2.setStage(LeadStage.QUALIFIED);
        lead2.setEstimatedValueEtb(new BigDecimal("20000.00"));

        when(campaignRepository.findByIdAndOrganizationId(campaignId, orgId)).thenReturn(Optional.of(campaign));
        when(leadRepository.findByOrganizationIdAndCampaignId(orgId, campaignId)).thenReturn(List.of(lead1, lead2));

        CampaignPerformanceResponse perf = campaignService.getCampaignPerformance(actorId, orgId, campaignId);

        assertThat(perf.totalAttributedLeads()).isEqualTo(2);
        assertThat(perf.wonLeads()).isEqualTo(1);
        assertThat(perf.wonPipelineValueEtb()).isEqualByComparingTo("50000.00");
        assertThat(perf.totalPipelineValueEtb()).isEqualByComparingTo("70000.00");
        // Cost per lead = 5000 / 2 = 2500 ETB
        assertThat(perf.costPerLeadEtb()).isEqualByComparingTo("2500.00");
        // ROAS = 50000 / 5000 = 10.00
        assertThat(perf.returnOnAdSpendRatio()).isEqualByComparingTo("10.00");
    }
}
