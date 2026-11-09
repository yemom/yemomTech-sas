package com.YemomBizGrow.agency;

import com.YemomBizGrow.agency.dto.AgencyClientSummaryResponse;
import com.YemomBizGrow.agency.dto.AgencyOverviewResponse;
import com.YemomBizGrow.agency.dto.LinkClientRequest;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.ConflictException;
import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.crm.LeadStatus;
import com.YemomBizGrow.marketing.MarketingCampaignRepository;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.organization.OrganizationStatus;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.seo.SeoAuditRepository;
import com.YemomBizGrow.seo.SeoAuditStatus;
import com.YemomBizGrow.website.WebsiteRepository;
import com.YemomBizGrow.website.WebsiteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgencyServiceTest {

    @Mock
    private AgencyClientRelationshipRepository relationshipRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private WebsiteRepository websiteRepository;

    @Mock
    private SeoAuditRepository seoAuditRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private MarketingCampaignRepository campaignRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private AuditService auditService;

    private AgencyService agencyService;

    private final UUID actorId = UUID.randomUUID();
    private final UUID agencyOrgId = UUID.randomUUID();
    private final UUID clientOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        agencyService = new AgencyService(
                relationshipRepository,
                organizationRepository,
                websiteRepository,
                seoAuditRepository,
                leadRepository,
                campaignRepository,
                accessControlService,
                auditService
        );
    }

    @Test
    @DisplayName("Successfully links a client organization to an agency")
    void linkClientSuccess() {
        Organization client = new Organization();
        client.setId(clientOrgId);
        client.setName("Ethio Real Estate");
        client.setSlug("ethio-real-estate");
        client.setStatus(OrganizationStatus.ACTIVE);
        client.setIndustry("REAL_ESTATE");

        when(organizationRepository.findBySlug("ethio-real-estate")).thenReturn(Optional.of(client));
        when(relationshipRepository.existsByAgencyOrganizationIdAndClientOrganizationId(agencyOrgId, clientOrgId))
                .thenReturn(false);

        when(relationshipRepository.save(any(AgencyClientRelationship.class))).thenAnswer(inv -> {
            AgencyClientRelationship r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        when(websiteRepository.countByOrganizationIdAndStatus(clientOrgId, WebsiteStatus.ACTIVE)).thenReturn(2L);
        SeoAudit audit = new SeoAudit();
        audit.setScore(85);
        when(seoAuditRepository.findFirstByOrganizationIdAndStatusOrderByCompletedAtDesc(eq(clientOrgId), eq(SeoAuditStatus.COMPLETED)))
                .thenReturn(Optional.of(audit));
        when(leadRepository.findByOrganizationIdAndStatus(clientOrgId, LeadStatus.ACTIVE)).thenReturn(List.of());

        LinkClientRequest request = new LinkClientRequest("ethio-real-estate", AgencyAccessLevel.FULL, "Primary client");
        AgencyClientSummaryResponse response = agencyService.linkClient(actorId, agencyOrgId, request);

        assertThat(response.clientName()).isEqualTo("Ethio Real Estate");
        assertThat(response.websiteCount()).isEqualTo(2);
        assertThat(response.latestSeoScore()).isEqualTo(85);
        assertThat(response.status()).isEqualTo(AgencyRelationshipStatus.ACTIVE);
    }

    @Test
    @DisplayName("Prevents linking an organization to itself")
    void preventLinkingSelf() {
        Organization client = new Organization();
        client.setId(agencyOrgId);
        client.setName("Self Agency");
        client.setSlug("self-agency");
        client.setStatus(OrganizationStatus.ACTIVE);

        when(organizationRepository.findBySlug("self-agency")).thenReturn(Optional.of(client));

        LinkClientRequest request = new LinkClientRequest("self-agency", AgencyAccessLevel.FULL, null);
        assertThatThrownBy(() -> agencyService.linkClient(actorId, agencyOrgId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot link to itself");
    }

    @Test
    @DisplayName("Aggregates agency overview metrics across multiple managed clients")
    void getAgencyOverviewMetrics() {
        Organization client1 = new Organization();
        client1.setId(clientOrgId);
        client1.setName("Client One");
        client1.setSlug("client-one");

        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(UUID.randomUUID());
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.ACTIVE);

        when(relationshipRepository.findByAgencyOrganizationId(agencyOrgId)).thenReturn(List.of(rel));
        when(organizationRepository.findById(clientOrgId)).thenReturn(Optional.of(client1));
        when(websiteRepository.countByOrganizationIdAndStatus(clientOrgId, WebsiteStatus.ACTIVE)).thenReturn(3L);

        SeoAudit audit = new SeoAudit();
        audit.setScore(90);
        when(seoAuditRepository.findFirstByOrganizationIdAndStatusOrderByCompletedAtDesc(eq(clientOrgId), eq(SeoAuditStatus.COMPLETED)))
                .thenReturn(Optional.of(audit));

        Lead lead = new Lead();
        lead.setId(UUID.randomUUID());
        lead.setEstimatedValueEtb(new BigDecimal("150000.00"));
        when(leadRepository.findByOrganizationIdAndStatus(clientOrgId, LeadStatus.ACTIVE)).thenReturn(List.of(lead));

        AgencyOverviewResponse overview = agencyService.getAgencyOverview(actorId, agencyOrgId);

        assertThat(overview.totalClients()).isEqualTo(1);
        assertThat(overview.activeClients()).isEqualTo(1);
        assertThat(overview.totalManagedWebsites()).isEqualTo(3);
        assertThat(overview.totalAggregatedLeads()).isEqualTo(1);
        assertThat(overview.totalAggregatedPipelineEtb()).isEqualByComparingTo("150000.00");
        assertThat(overview.averageClientSeoScore()).isEqualTo(90.0);
    }
}
