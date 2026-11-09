package com.YemomBizGrow.agency;

import com.YemomBizGrow.agency.dto.AgencyClientSummaryResponse;
import com.YemomBizGrow.agency.dto.AgencyOverviewResponse;
import com.YemomBizGrow.agency.dto.LinkClientRequest;
import com.YemomBizGrow.agency.dto.UpdateClientRelationshipRequest;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
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
import static org.mockito.Mockito.verify;
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
    @DisplayName("Agency linking client sets status to PENDING by default")
    void linkClientStartsPending() {
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

        AgencyClientSummaryResponse response = agencyService.linkClient(
                actorId, agencyOrgId,
                new LinkClientRequest("ethio-real-estate", AgencyAccessLevel.FULL, "Partnership")
        );

        assertThat(response).isNotNull();
        assertThat(response.clientOrganizationId()).isEqualTo(clientOrgId);
        assertThat(response.status()).isEqualTo(AgencyRelationshipStatus.PENDING);
        // Metrics must be zero while PENDING
        assertThat(response.totalLeads()).isEqualTo(0);
        assertThat(response.totalPipelineValueEtb()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Client owner can approve a pending agency connection request")
    void clientCanApprovePendingRequest() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.PENDING);
        rel.setAccessLevel(AgencyAccessLevel.FULL);

        Organization client = new Organization();
        client.setId(clientOrgId);
        client.setName("Ethio Real Estate");
        client.setSlug("ethio-real-estate");

        when(relationshipRepository.findByIdAndClientOrganizationId(relId, clientOrgId))
                .thenReturn(Optional.of(rel));
        when(relationshipRepository.save(any(AgencyClientRelationship.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(organizationRepository.findById(clientOrgId)).thenReturn(Optional.of(client));

        AgencyClientSummaryResponse response = agencyService.approveClientRequest(actorId, clientOrgId, relId);

        assertThat(response.status()).isEqualTo(AgencyRelationshipStatus.ACTIVE);
        assertThat(rel.getStatus()).isEqualTo(AgencyRelationshipStatus.ACTIVE);
    }

    @Test
    @DisplayName("Agency cannot self-approve pending relationship to ACTIVE")
    void agencyCannotSelfApprove() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.PENDING);

        when(relationshipRepository.findByIdAndAgencyOrganizationId(relId, agencyOrgId))
                .thenReturn(Optional.of(rel));

        assertThatThrownBy(() -> agencyService.updateRelationship(
                actorId, agencyOrgId, relId,
                new UpdateClientRelationshipRequest(AgencyRelationshipStatus.ACTIVE, null, null)
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Agency cannot self-approve");
    }

    @Test
    @DisplayName("MARKETING_ONLY access level masks CRM confidential pipeline value and sales leads")
    void marketingOnlyAccessLevelMasksCrmData() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.ACTIVE);
        rel.setAccessLevel(AgencyAccessLevel.MARKETING_ONLY);

        Organization client = new Organization();
        client.setId(clientOrgId);
        client.setName("Client Org");

        when(relationshipRepository.findByAgencyOrganizationId(agencyOrgId))
                .thenReturn(List.of(rel));
        when(organizationRepository.findById(clientOrgId)).thenReturn(Optional.of(client));
        when(websiteRepository.countByOrganizationIdAndStatus(clientOrgId, WebsiteStatus.ACTIVE)).thenReturn(2L);
        when(campaignRepository.countByOrganizationIdAndStatus(clientOrgId, com.YemomBizGrow.marketing.CampaignStatus.ACTIVE)).thenReturn(3L);

        List<AgencyClientSummaryResponse> summaries = agencyService.listClients(actorId, agencyOrgId);

        assertThat(summaries).hasSize(1);
        AgencyClientSummaryResponse summary = summaries.get(0);
        // Marketing metrics are available
        assertThat(summary.websiteCount()).isEqualTo(2);
        assertThat(summary.activeCampaignsCount()).isEqualTo(3);
        // CRM confidential metrics MUST be masked
        assertThat(summary.totalLeads()).isEqualTo(0);
        assertThat(summary.totalPipelineValueEtb()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Client cannot link to itself")
    void linkClientSelfFails() {
        Organization self = new Organization();
        self.setId(agencyOrgId);
        self.setName("Self Agency");
        self.setSlug("self-agency");
        self.setStatus(OrganizationStatus.ACTIVE);

        when(organizationRepository.findBySlug("self-agency")).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> agencyService.linkClient(
                actorId, agencyOrgId,
                new LinkClientRequest("self-agency", AgencyAccessLevel.FULL, null)
        )).isInstanceOf(ConflictException.class)
          .hasMessageContaining("cannot link to itself");
    }
}
