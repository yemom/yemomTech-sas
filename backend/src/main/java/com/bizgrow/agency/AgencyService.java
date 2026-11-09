package com.YemomBizGrow.agency;

import com.YemomBizGrow.agency.dto.AgencyClientSummaryResponse;
import com.YemomBizGrow.agency.dto.AgencyOverviewResponse;
import com.YemomBizGrow.agency.dto.LinkClientRequest;
import com.YemomBizGrow.agency.dto.UpdateClientRelationshipRequest;
import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
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

    /**
     * Agency requests to link a client organization.
     * Starts in PENDING status until approved by client organization owner/admin.
     */
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
            throw new ConflictException("A relationship already exists between your agency and this client organization");
        }

        AgencyClientRelationship rel = new AgencyClientRelationship();
        rel.setAgencyOrganizationId(agencyOrgId);
        rel.setClientOrganizationId(clientOrg.getId());
        rel.setStatus(AgencyRelationshipStatus.PENDING); // Security: Must be approved by client!
        rel.setAccessLevel(request.accessLevel() != null ? request.accessLevel() : AgencyAccessLevel.FULL);
        rel.setNotes(request.notes());

        AgencyClientRelationship saved = relationshipRepository.save(rel);

        auditService.record(actorUserId, agencyOrgId, AuditAction.AGENCY_CLIENT_LINKED, "AgencyClientRelationship", saved.getId(),
                Map.of("clientOrgId", clientOrg.getId().toString(), "clientSlug", clientOrg.getSlug(), "status", "PENDING"));

        log.info("Agency org {} requested link to client org {} ({}) - pending client approval", agencyOrgId, clientOrg.getId(), clientOrg.getName());
        return buildClientSummary(saved, clientOrg);
    }

    /**
     * Client organization approves an incoming agency connection request.
     */
    @Transactional
    public AgencyClientSummaryResponse approveClientRequest(UUID actorUserId, UUID clientOrgId, UUID relationshipId) {
        accessControlService.requirePermission(actorUserId, clientOrgId, Permission.ORG_UPDATE);

        AgencyClientRelationship rel = relationshipRepository.findByIdAndClientOrganizationId(relationshipId, clientOrgId)
                .orElseThrow(() -> new NotFoundException("Agency request not found with ID: " + relationshipId));

        if (rel.getStatus() != AgencyRelationshipStatus.PENDING) {
            throw new ConflictException("Only PENDING agency requests can be approved. Current status: " + rel.getStatus());
        }

        rel.setStatus(AgencyRelationshipStatus.ACTIVE);
        AgencyClientRelationship saved = relationshipRepository.save(rel);

        auditService.record(actorUserId, clientOrgId, AuditAction.AGENCY_REQUEST_APPROVED, "AgencyClientRelationship", saved.getId(),
                Map.of("agencyOrgId", rel.getAgencyOrganizationId().toString()));

        Organization clientOrg = organizationRepository.findById(clientOrgId)
                .orElseThrow(() -> new NotFoundException("Client organization not found"));

        log.info("Client org {} APPROVED agency connection request {}", clientOrgId, relationshipId);
        return buildClientSummary(saved, clientOrg);
    }

    /**
     * Client organization rejects an incoming agency connection request.
     */
    @Transactional
    public void rejectClientRequest(UUID actorUserId, UUID clientOrgId, UUID relationshipId) {
        accessControlService.requirePermission(actorUserId, clientOrgId, Permission.ORG_UPDATE);

        AgencyClientRelationship rel = relationshipRepository.findByIdAndClientOrganizationId(relationshipId, clientOrgId)
                .orElseThrow(() -> new NotFoundException("Agency request not found with ID: " + relationshipId));

        rel.setStatus(AgencyRelationshipStatus.REJECTED);
        relationshipRepository.save(rel);

        auditService.record(actorUserId, clientOrgId, AuditAction.AGENCY_REQUEST_REJECTED, "AgencyClientRelationship", relationshipId,
                Map.of("agencyOrgId", rel.getAgencyOrganizationId().toString()));

        log.info("Client org {} REJECTED agency connection request {}", clientOrgId, relationshipId);
    }

    /**
     * Client organization revokes an active agency relationship.
     */
    @Transactional
    public void revokeByClient(UUID actorUserId, UUID clientOrgId, UUID relationshipId) {
        accessControlService.requirePermission(actorUserId, clientOrgId, Permission.ORG_UPDATE);

        AgencyClientRelationship rel = relationshipRepository.findByIdAndClientOrganizationId(relationshipId, clientOrgId)
                .orElseThrow(() -> new NotFoundException("Agency relationship not found with ID: " + relationshipId));

        rel.setStatus(AgencyRelationshipStatus.REVOKED);
        relationshipRepository.save(rel);

        auditService.record(actorUserId, clientOrgId, AuditAction.AGENCY_CLIENT_REVOKED, "AgencyClientRelationship", relationshipId,
                Map.of("agencyOrgId", rel.getAgencyOrganizationId().toString(), "revokedBy", "CLIENT"));

        log.info("Client org {} REVOKED agency access for relationship {}", clientOrgId, relationshipId);
    }

    /**
     * Lists incoming agency requests and active agency relationships for a client organization.
     */
    @Transactional(readOnly = true)
    public List<AgencyClientSummaryResponse> listClientAgencyRequests(UUID actorUserId, UUID clientOrgId) {
        accessControlService.requirePermission(actorUserId, clientOrgId, Permission.ORG_VIEW);

        List<AgencyClientRelationship> relationships = relationshipRepository.findByClientOrganizationId(clientOrgId);
        Organization clientOrg = organizationRepository.findById(clientOrgId)
                .orElseThrow(() -> new NotFoundException("Client organization not found"));

        return relationships.stream()
                .map(rel -> buildClientSummary(rel, clientOrg))
                .toList();
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
        List<AgencyClientSummaryResponse> activeList = clients.stream()
                .filter(c -> c.status() == AgencyRelationshipStatus.ACTIVE)
                .toList();

        long activeClients = activeList.size();
        long totalWebsites = activeList.stream().mapToLong(AgencyClientSummaryResponse::websiteCount).sum();
        long totalLeads = activeList.stream().mapToLong(AgencyClientSummaryResponse::totalLeads).sum();

        BigDecimal totalPipeline = activeList.stream()
                .map(AgencyClientSummaryResponse::totalPipelineValueEtb)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        double avgSeoScore = activeList.stream()
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

        // Security: Agency cannot approve its own relationship to ACTIVE!
        if (request.status() != null) {
            if (request.status() == AgencyRelationshipStatus.ACTIVE && rel.getStatus() == AgencyRelationshipStatus.PENDING) {
                throw new BadRequestException("Agency cannot self-approve pending relationships. Approval must be granted by the client organization.");
            }
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

        auditService.record(actorUserId, agencyOrgId, AuditAction.AGENCY_CLIENT_REVOKED, "AgencyClientRelationship", relationshipId,
                Map.of("clientOrgId", rel.getClientOrganizationId().toString(), "revokedBy", "AGENCY"));

        log.info("Agency org {} revoked client org {}", agencyOrgId, rel.getClientOrganizationId());
    }

    /**
     * Builds client summary, enforcing access levels and status isolation:
     * - If not ACTIVE: No confidential or operational metrics returned.
     * - If MARKETING_ONLY: CRM sales leads and pipeline values are hidden (returns 0).
     */
    private AgencyClientSummaryResponse buildClientSummary(AgencyClientRelationship rel, Organization clientOrg) {
        UUID clientId = clientOrg.getId();

        // If relationship is not active (PENDING, REJECTED, REVOKED), zero out all operational metrics
        if (rel.getStatus() != AgencyRelationshipStatus.ACTIVE) {
            return new AgencyClientSummaryResponse(
                    rel.getId(),
                    clientOrg.getId(),
                    clientOrg.getName(),
                    clientOrg.getSlug(),
                    clientOrg.getIndustry(),
                    rel.getStatus(),
                    rel.getAccessLevel(),
                    0,
                    null,
                    0,
                    BigDecimal.ZERO,
                    0,
                    rel.getCreatedAt()
            );
        }

        long websiteCount = websiteRepository.countByOrganizationIdAndStatus(clientId, WebsiteStatus.ACTIVE);
        Optional<SeoAudit> latestAudit = seoAuditRepository.findFirstByOrganizationIdAndStatusOrderByCompletedAtDesc(clientId, SeoAuditStatus.COMPLETED);
        Integer latestScore = latestAudit.map(SeoAudit::getScore).orElse(null);
        long activeCampaigns = campaignRepository.countByOrganizationIdAndStatus(clientId, CampaignStatus.ACTIVE);

        // Security check for MARKETING_ONLY access level: Hide CRM confidential revenue data!
        long leadCount = 0;
        BigDecimal totalPipeline = BigDecimal.ZERO;

        if (rel.getAccessLevel() != AgencyAccessLevel.MARKETING_ONLY) {
            List<Lead> leads = leadRepository.findByOrganizationIdAndStatus(clientId, LeadStatus.ACTIVE);
            leadCount = leads.size();
            totalPipeline = leads.stream()
                    .map(l -> l.getEstimatedValueEtb() != null ? l.getEstimatedValueEtb() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

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
