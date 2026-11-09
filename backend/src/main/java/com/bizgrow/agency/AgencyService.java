package com.YemomBizGrow.agency;

import com.YemomBizGrow.agency.dto.AgencyClientSummaryResponse;
import com.YemomBizGrow.agency.dto.AgencyOverviewResponse;
import com.YemomBizGrow.agency.dto.LinkClientRequest;
import com.YemomBizGrow.agency.dto.UpdateClientRelationshipRequest;
import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.ConflictException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.crm.LeadStatus;
import com.YemomBizGrow.marketing.CampaignStatus;
import com.YemomBizGrow.marketing.MarketingCampaignRepository;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationRepository;
import com.YemomBizGrow.organization.OrganizationStatus;
import com.YemomBizGrow.seo.SeoAudit;
import com.YemomBizGrow.seo.SeoAuditRepository;
import com.YemomBizGrow.seo.SeoAuditStatus;
import com.YemomBizGrow.website.WebsiteRepository;
import com.YemomBizGrow.website.WebsiteStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgencyService {

    private static final Logger log = LoggerFactory.getLogger(AgencyService.class);

    private final AgencyClientRelationshipRepository relationshipRepository;
    private final OrganizationRepository organizationRepository;
    private final WebsiteRepository websiteRepository;
    private final SeoAuditRepository seoAuditRepository;
    private final LeadRepository leadRepository;
    private final MarketingCampaignRepository campaignRepository;
    private final AccessControlService accessControlService;
    private final AuditService auditService;

    public AgencyService(
            AgencyClientRelationshipRepository relationshipRepository,
            OrganizationRepository organizationRepository,
            WebsiteRepository websiteRepository,
            SeoAuditRepository seoAuditRepository,
            LeadRepository leadRepository,
            MarketingCampaignRepository campaignRepository,
            AccessControlService accessControlService,
            AuditService auditService
    ) {
        this.relationshipRepository = relationshipRepository;
        this.organizationRepository = organizationRepository;
        this.websiteRepository = websiteRepository;
        this.seoAuditRepository = seoAuditRepository;
        this.leadRepository = leadRepository;
        this.campaignRepository = campaignRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
    }

    @Transactional
    public AgencyClientSummaryResponse linkClient(UUID actorUserId, UUID agencyOrgId, LinkClientRequest request) {
        accessControlService.requirePermission(actorUserId, agencyOrgId, Permission.ORG_UPDATE);

        String slug = request.clientOrganizationSlug().trim().toLowerCase();
        Organization clientOrg = organizationRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Client organization not found with slug: " + slug));

        if (clientOrg.getId().equals(agencyOrgId)) {
            throw new ConflictException("An organization cannot link to itself as a client");
        }

        if (clientOrg.getStatus() != OrganizationStatus.ACTIVE) {
            throw new ConflictException("Client organization is not active");
        }

        if (relationshipRepository.existsByAgencyOrganizationIdAndClientOrganizationId(agencyOrgId, clientOrg.getId())) {
            throw new ConflictException("This client organization is already linked to your agency");
        }

        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrg.getId());
        rel.setStatus(AgencyRelationshipStatus.ACTIVE);
        rel.setAccessLevel(request.accessLevel() != null ? request.accessLevel() : AgencyAccessLevel.FULL);
        rel.setNotes(request.notes());

        AgencyClientRelationship saved = relationshipRepository.save(rel);

        auditService.record(agencyOrgId, actorUserId, AuditAction.AGENCY_CLIENT_LINKED, "AgencyClientRelationship", saved.getId(),
                Map.of("clientOrgId", clientOrg.getId().toString(), "clientSlug", clientOrg.getSlug()));

        log.info("Agency org {} linked client org {} ({})", agencyOrgId, clientOrg.getId(), clientOrg.getName());
        return buildClientSummary(saved, clientOrg);
    }

    @Transactional(readOnly = true)
    public List<AgencyClientSummaryResponse> listClients(UUID actorUserId, UUID agencyOrgId) {
        accessControlService.requirePermission(actorUserId, agencyOrgId, Permission.ORG_VIEW);

        List<AgencyClientRelationship> relationships = relationshipRepository.findByAgencyOrganizationId(agencyOrgId);
        List<AgencyClientSummaryResponse> result = new ArrayList<>();

        for (AgencyClientRelationship rel : relationships) {
            organizationRepository.findById(rel.getClientOrganizationId()).ifPresent(clientOrg -> {
                result.add(buildClientSummary(rel, clientOrg));
            });
        }
        return result;
    }

    @Transactional(readOnly = true)
    public AgencyOverviewResponse getAgencyOverview(UUID actorUserId, UUID agencyOrgId) {
        accessControlService.requirePermission(actorUserId, agencyOrgId, Permission.ORG_VIEW);

        List<AgencyClientSummaryResponse> clients = listClients(actorUserId, agencyOrgId);

        long totalClients = clients.size();
        long activeClients = clients.stream().filter(c -> c.status() == AgencyRelationshipStatus.ACTIVE).count();
        long totalWebsites = clients.stream().mapToLong(AgencyClientSummaryResponse::websiteCount).sum();
        long totalLeads = clients.stream().mapToLong(AgencyClientSummaryResponse::totalLeads).sum();

        BigDecimal totalPipeline = clients.stream()
                .map(AgencyClientSummaryResponse::totalPipelineValueEtb)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double avgSeoScore = clients.stream()
                .filter(c -> c.latestSeoScore() != null)
                .mapToInt(AgencyClientSummaryResponse::latestSeoScore)
                .average()
                .orElse(0.0);

        return new AgencyOverviewResponse(
                totalClients,
                activeClients,
                totalWebsites,
                totalLeads,
                totalPipeline,
                Math.round(avgSeoScore * 10.0) / 10.0,
                clients
        );
    }

    @Transactional
    public AgencyClientSummaryResponse updateRelationship(
            UUID actorUserId,
            UUID agencyOrgId,
            UUID relationshipId,
            UpdateClientRelationshipRequest request
    ) {
        accessControlService.requirePermission(actorUserId, agencyOrgId, Permission.ORG_UPDATE);

        AgencyClientRelationship rel = relationshipRepository.findByIdAndAgencyOrganizationId(relationshipId, agencyOrgId)
                .orElseThrow(() -> new NotFoundException("Client relationship not found with ID: " + relationshipId));

        if (request.status() != null) {
            rel.setStatus(request.status());
        }
        if (request.accessLevel() != null) {
            rel.setAccessLevel(request.accessLevel());
        }
        if (request.notes() != null) {
            rel.setNotes(request.notes());
        }

        AgencyClientRelationship saved = relationshipRepository.save(rel);
        Organization clientOrg = organizationRepository.findById(saved.getClientOrganizationId())
                .orElseThrow(() -> new NotFoundException("Client organization not found"));

        return buildClientSummary(saved, clientOrg);
    }

    @Transactional
    public void revokeRelationship(UUID actorUserId, UUID agencyOrgId, UUID relationshipId) {
        accessControlService.requirePermission(actorUserId, agencyOrgId, Permission.ORG_UPDATE);

        AgencyClientRelationship rel = relationshipRepository.findByIdAndAgencyOrganizationId(relationshipId, agencyOrgId)
                .orElseThrow(() -> new NotFoundException("Client relationship not found with ID: " + relationshipId));

        rel.setStatus(AgencyRelationshipStatus.REVOKED);
        relationshipRepository.save(rel);

        auditService.record(agencyOrgId, actorUserId, AuditAction.AGENCY_CLIENT_REVOKED, "AgencyClientRelationship", relationshipId,
                Map.of("clientOrgId", rel.getClientOrganizationId().toString()));

        log.info("Agency org {} revoked client org {}", agencyOrgId, rel.getClientOrganizationId());
    }

    private AgencyClientSummaryResponse buildClientSummary(AgencyClientRelationship rel, Organization clientOrg) {
        UUID clientId = clientOrg.getId();

        long websiteCount = websiteRepository.countByOrganizationIdAndStatus(clientId, WebsiteStatus.ACTIVE);

        Optional<SeoAudit> latestAudit = seoAuditRepository.findFirstByOrganizationIdAndStatusOrderByCompletedAtDesc(clientId, SeoAuditStatus.COMPLETED);
        Integer latestScore = latestAudit.map(SeoAudit::getScore).orElse(null);

        List<Lead> leads = leadRepository.findByOrganizationIdAndStatus(clientId, LeadStatus.ACTIVE);
        long leadCount = leads.size();
        BigDecimal totalPipeline = leads.stream()
                .map(l -> l.getEstimatedValueEtb() != null ? l.getEstimatedValueEtb() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long activeCampaigns = campaignRepository.countByOrganizationIdAndStatus(clientId, CampaignStatus.ACTIVE);

        return new AgencyClientSummaryResponse(
                rel.getId(),
                clientOrg.getId(),
                clientOrg.getName(),
                clientOrg.getSlug(),
                clientOrg.getIndustry(),
                rel.getStatus(),
                rel.getAccessLevel(),
                websiteCount,
                latestScore,
                leadCount,
                totalPipeline,
                activeCampaigns,
                rel.getCreatedAt()
        );
    }
}
