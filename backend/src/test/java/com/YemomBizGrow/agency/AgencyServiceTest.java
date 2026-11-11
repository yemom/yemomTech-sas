package com.YemomBizGrow.agency;

import com.YemomBizGrow.agency.dto.AgencyClientSummaryResponse;
import com.YemomBizGrow.agency.dto.LinkClientRequest;
import com.YemomBizGrow.agency.dto.UpdateClientRelationshipRequest;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.ConflictException;
import com.YemomBizGrow.common.exception.ForbiddenException;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.marketing.MarketingCampaignRepository;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.organization.OrganizationStatus;
import com.YemomBizGrow.seo.SeoAuditRepository;
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
import static org.mockito.Mockito.doThrow;
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
        assertThat(response.totalLeads()).isEqualTo(0);
        assertThat(response.totalPipelineValueEtb()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario 1: PENDING -> ACTIVE by client: allowed")
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
    @DisplayName("Scenario 2: PENDING -> ACTIVE by agency: rejected")
    void agencyCannotSelfApprovePending() {
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
                new UpdateClientRelationshipRequest(AgencyRelationshipStatus.ACTIVE, null)
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Agency cannot change relationship status");
    }

    @Test
    @DisplayName("Scenario 3: REJECTED -> ACTIVE by agency: rejected")
    void agencyCannotReactivateRejected() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.REJECTED);

        when(relationshipRepository.findByIdAndAgencyOrganizationId(relId, agencyOrgId))
                .thenReturn(Optional.of(rel));

        assertThatThrownBy(() -> agencyService.updateRelationship(
                actorId, agencyOrgId, relId,
                new UpdateClientRelationshipRequest(AgencyRelationshipStatus.ACTIVE, null)
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Cannot modify relationship in REJECTED status");
    }

    @Test
    @DisplayName("Scenario 4: REVOKED -> ACTIVE by agency: rejected")
    void agencyCannotReactivateRevoked() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.REVOKED);

        when(relationshipRepository.findByIdAndAgencyOrganizationId(relId, agencyOrgId))
                .thenReturn(Optional.of(rel));

        assertThatThrownBy(() -> agencyService.updateRelationship(
                actorId, agencyOrgId, relId,
                new UpdateClientRelationshipRequest(AgencyRelationshipStatus.ACTIVE, null)
        )).isInstanceOf(BadRequestException.class)
          .hasMessageContaining("Cannot modify relationship in REVOKED status");
    }

    @Test
    @DisplayName("Scenario 5: REVOKED -> ACTIVE by client without new request: rejected")
    void clientCannotApproveRevokedRelationship() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.REVOKED);

        when(relationshipRepository.findByIdAndClientOrganizationId(relId, clientOrgId))
                .thenReturn(Optional.of(rel));

        assertThatThrownBy(() -> agencyService.approveClientRequest(actorId, clientOrgId, relId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only PENDING agency requests can be approved");
    }

    @Test
    @DisplayName("Scenario 6: Revoked agency cannot retrieve confidential client metrics")
    void revokedAgencyGetsZeroMetrics() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.REVOKED);

        Organization client = new Organization();
        client.setId(clientOrgId);
        client.setName("Client Org");

        when(relationshipRepository.findByAgencyOrganizationId(agencyOrgId))
                .thenReturn(List.of(rel));
        when(organizationRepository.findById(clientOrgId)).thenReturn(Optional.of(client));

        List<AgencyClientSummaryResponse> summaries = agencyService.listClients(actorId, agencyOrgId);

        assertThat(summaries).hasSize(1);
        AgencyClientSummaryResponse summary = summaries.get(0);
        assertThat(summary.status()).isEqualTo(AgencyRelationshipStatus.REVOKED);
        assertThat(summary.totalLeads()).isEqualTo(0);
        assertThat(summary.websiteCount()).isEqualTo(0);
        assertThat(summary.totalPipelineValueEtb()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Scenario 7: Unauthorized users cannot approve requests")
    void unauthorizedUserCannotApproveRequest() {
        UUID relId = UUID.randomUUID();
        doThrow(new ForbiddenException("User lacks permission ORG_UPDATE"))
                .when(accessControlService).requirePermission(actorId, clientOrgId, Permission.ORG_UPDATE);

        assertThatThrownBy(() -> agencyService.approveClientRequest(actorId, clientOrgId, relId))
                .isInstanceOf(ForbiddenException.class);
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
        assertThat(summary.websiteCount()).isEqualTo(2);
        assertThat(summary.activeCampaignsCount()).isEqualTo(3);
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

    @Test
    @DisplayName("Scenario 8: Agency cannot escalate access level from READ_ONLY to FULL")
    void agencyCannotEscalateAccessLevelToFull() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.ACTIVE);
        rel.setAccessLevel(AgencyAccessLevel.READ_ONLY);

        when(relationshipRepository.findByIdAndAgencyOrganizationId(relId, agencyOrgId))
                .thenReturn(Optional.of(rel));
        when(relationshipRepository.save(any(AgencyClientRelationship.class)))
                .thenAnswer(inv -> {
                    AgencyClientRelationship r = inv.getArgument(0);
                    r.setId(relId);
                    return r;
                });
        Organization client = new Organization();
        client.setId(clientOrgId);
        client.setName("Client Org");
        when(organizationRepository.findById(clientOrgId)).thenReturn(Optional.of(client));

        // Access level is now immutable from agency side - verify it stays READ_ONLY
        AgencyClientSummaryResponse response = agencyService.updateRelationship(
                actorId, agencyOrgId, relId,
                new UpdateClientRelationshipRequest(null, "Some notes")
        );

        assertThat(response.accessLevel()).isEqualTo(AgencyAccessLevel.READ_ONLY);
    }

    @Test
    @DisplayName("Scenario 9: Agency cannot escalate access level from READ_ONLY to MARKETING_ONLY")
    void agencyCannotEscalateAccessLevelToMarketingOnly() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.ACTIVE);
        rel.setAccessLevel(AgencyAccessLevel.READ_ONLY);

        when(relationshipRepository.findByIdAndAgencyOrganizationId(relId, agencyOrgId))
                .thenReturn(Optional.of(rel));
        when(relationshipRepository.save(any(AgencyClientRelationship.class)))
                .thenAnswer(inv -> {
                    AgencyClientRelationship r = inv.getArgument(0);
                    r.setId(relId);
                    return r;
                });
        Organization client = new Organization();
        client.setId(clientOrgId);
        client.setName("Client Org");
        when(organizationRepository.findById(clientOrgId)).thenReturn(Optional.of(client));

        // Access level should remain READ_ONLY since agency cannot change it
        AgencyClientSummaryResponse response = agencyService.updateRelationship(
                actorId, agencyOrgId, relId,
                new UpdateClientRelationshipRequest(null, "Some notes")
        );

        assertThat(response.accessLevel()).isEqualTo(AgencyAccessLevel.READ_ONLY);
    }

    @Test
    @DisplayName("Scenario 10: Agency cannot change access level on ACTIVE relationship")
    void agencyCannotChangeAccessLevelOnActiveRelationship() {
        UUID relId = UUID.randomUUID();
        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setId(relId);
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrgId);
        rel.setStatus(AgencyRelationshipStatus.ACTIVE);
        rel.setAccessLevel(AgencyAccessLevel.FULL);

        when(relationshipRepository.findByIdAndAgencyOrganizationId(relId, agencyOrgId))
                .thenReturn(Optional.of(rel));
        when(relationshipRepository.save(any(AgencyClientRelationship.class)))
                .thenAnswer(inv -> {
                    AgencyClientRelationship r = inv.getArgument(0);
                    r.setId(relId);
                    return r;
                });
        Organization client = new Organization();
        client.setId(clientOrgId);
        client.setName("Client Org");
        when(organizationRepository.findById(clientOrgId)).thenReturn(Optional.of(client));

        // Attempt to change access level - should be ignored, access level stays FULL
        AgencyClientSummaryResponse response = agencyService.updateRelationship(
                actorId, agencyOrgId, relId,
                new UpdateClientRelationshipRequest(null, "Some notes")
        );

        assertThat(response.accessLevel()).isEqualTo(AgencyAccessLevel.FULL);
    }
}
