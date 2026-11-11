package com.YemomBizGrow.marketing;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.crm.Lead;
import com.YemomBizGrow.crm.LeadRepository;
import com.YemomBizGrow.crm.LeadStage;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.marketing.dto.CampaignPerformanceResponse;
import com.YemomBizGrow.marketing.dto.CampaignResponse;
import com.YemomBizGrow.marketing.dto.CreateCampaignRequest;
import com.YemomBizGrow.marketing.dto.UpdateCampaignRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MarketingCampaignService {

    private static final Logger log = LoggerFactory.getLogger(MarketingCampaignService.class);

    private final MarketingCampaignRepository campaignRepository;
    private final LeadRepository leadRepository;
    private final AccessControlService accessControlService;
    private final AuditService auditService;

    public MarketingCampaignService(
            MarketingCampaignRepository campaignRepository,
            LeadRepository leadRepository,
            AccessControlService accessControlService,
            AuditService auditService
    ) {
        this.campaignRepository = campaignRepository;
        this.leadRepository = leadRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
    }

    @Transactional
    public CampaignResponse createCampaign(UUID actorUserId, UUID organizationId, CreateCampaignRequest request) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_MANAGE);

        MarketingCampaign campaign = new MarketingCampaign();
        campaign.setOrganizationId(organizationId);
        campaign.setName(request.name().trim());
        campaign.setDescription(request.description());
        campaign.setObjective(request.objective());
        campaign.setStatus(request.status() != null ? request.status() : CampaignStatus.ACTIVE);
        campaign.setChannel(request.channel());
        campaign.setBudgetEtb(request.budgetEtb() != null ? request.budgetEtb() : BigDecimal.ZERO);
        campaign.setSpentEtb(request.spentEtb() != null ? request.spentEtb() : BigDecimal.ZERO);
        campaign.setStartDate(request.startDate());
        campaign.setEndDate(request.endDate());
        campaign.setUtmSource(request.utmSource());
        campaign.setUtmMedium(request.utmMedium());
        campaign.setUtmCampaign(request.utmCampaign() != null ? request.utmCampaign().trim().toLowerCase() : null);
        campaign.setUtmTerm(request.utmTerm());
        campaign.setUtmContent(request.utmContent());

        MarketingCampaign saved = campaignRepository.save(campaign);

        auditService.record(organizationId, actorUserId, AuditAction.CAMPAIGN_CREATED, "MarketingCampaign", saved.getId(),
                Map.of("name", saved.getName(), "channel", saved.getChannel().name()));

        log.info("Campaign '{}' ({}) created for organization {}", saved.getName(), saved.getId(), organizationId);
        return CampaignResponse.from(saved, 0, BigDecimal.ZERO);
    }

    @Transactional
    public CampaignResponse updateCampaign(UUID actorUserId, UUID organizationId, UUID campaignId, UpdateCampaignRequest request) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_MANAGE);

        MarketingCampaign campaign = campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> new NotFoundException("Campaign not found with ID: " + campaignId));

        if (request.name() != null && !request.name().isBlank()) {
            campaign.setName(request.name().trim());
        }
        if (request.description() != null) {
            campaign.setDescription(request.description());
        }
        if (request.objective() != null) {
            campaign.setObjective(request.objective());
        }
        if (request.status() != null) {
            campaign.setStatus(request.status());
        }
        if (request.channel() != null) {
            campaign.setChannel(request.channel());
        }
        if (request.budgetEtb() != null) {
            campaign.setBudgetEtb(request.budgetEtb());
        }
        if (request.spentEtb() != null) {
            campaign.setSpentEtb(request.spentEtb());
        }
        if (request.startDate() != null) {
            campaign.setStartDate(request.startDate());
        }
        if (request.endDate() != null) {
            campaign.setEndDate(request.endDate());
        }
        if (request.utmSource() != null) {
            campaign.setUtmSource(request.utmSource());
        }
        if (request.utmMedium() != null) {
            campaign.setUtmMedium(request.utmMedium());
        }
        if (request.utmCampaign() != null) {
            campaign.setUtmCampaign(request.utmCampaign().trim().toLowerCase());
        }
        if (request.utmTerm() != null) {
            campaign.setUtmTerm(request.utmTerm());
        }
        if (request.utmContent() != null) {
            campaign.setUtmContent(request.utmContent());
        }

        MarketingCampaign saved = campaignRepository.save(campaign);
        auditService.record(organizationId, actorUserId, AuditAction.CAMPAIGN_UPDATED, "MarketingCampaign", saved.getId(),
                Map.of("status", saved.getStatus().name()));

        return toResponseWithAttribution(saved);
    }

    @Transactional(readOnly = true)
    public CampaignResponse getCampaign(UUID actorUserId, UUID organizationId, UUID campaignId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ORG_VIEW);

        MarketingCampaign campaign = campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> new NotFoundException("Campaign not found with ID: " + campaignId));

        return toResponseWithAttribution(campaign);
    }

    @Transactional(readOnly = true)
    public PageResponse<CampaignResponse> listCampaigns(UUID actorUserId, UUID organizationId, CampaignStatus status, Pageable pageable) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ORG_VIEW);

        Page<MarketingCampaign> page = (status != null)
                ? campaignRepository.findByOrganizationIdAndStatus(organizationId, status, pageable)
                : campaignRepository.findByOrganizationId(organizationId, pageable);

        Page<CampaignResponse> responsePage = page.map(this::toResponseWithAttribution);
        return PageResponse.from(responsePage);
    }

    @Transactional
    public void deleteCampaign(UUID actorUserId, UUID organizationId, UUID campaignId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_MANAGE);

        MarketingCampaign campaign = campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> new NotFoundException("Campaign not found with ID: " + campaignId));

        campaignRepository.delete(campaign);
        auditService.record(organizationId, actorUserId, AuditAction.CAMPAIGN_DELETED, "MarketingCampaign", campaignId,
                Map.of("name", campaign.getName()));

        log.info("Campaign '{}' deleted for org {}", campaignId, organizationId);
    }

    @Transactional(readOnly = true)
    public CampaignPerformanceResponse getCampaignPerformance(UUID actorUserId, UUID organizationId, UUID campaignId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.ANALYTICS_VIEW);

        MarketingCampaign campaign = campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> new NotFoundException("Campaign not found with ID: " + campaignId));

        List<Lead> leads = leadRepository.findByOrganizationIdAndCampaignId(organizationId, campaignId);
        long totalLeads = leads.size();
        long wonLeads = leads.stream().filter(l -> l.getStage() == LeadStage.WON).count();

        BigDecimal totalPipeline = leads.stream()
                .map(l -> l.getEstimatedValueEtb() != null ? l.getEstimatedValueEtb() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal wonPipeline = leads.stream()
                .filter(l -> l.getStage() == LeadStage.WON)
                .map(l -> l.getEstimatedValueEtb() != null ? l.getEstimatedValueEtb() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal spent = campaign.getSpentEtb() != null && campaign.getSpentEtb().compareTo(BigDecimal.ZERO) > 0
                ? campaign.getSpentEtb()
                : BigDecimal.ZERO;

        BigDecimal costPerLead = (totalLeads > 0 && spent.compareTo(BigDecimal.ZERO) > 0)
                ? spent.divide(BigDecimal.valueOf(totalLeads), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal roas = (spent.compareTo(BigDecimal.ZERO) > 0)
                ? wonPipeline.divide(spent, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new CampaignPerformanceResponse(
                campaign.getId(),
                campaign.getName(),
                campaign.getBudgetEtb(),
                spent,
                totalLeads,
                wonLeads,
                wonPipeline,
                totalPipeline,
                costPerLead,
                roas
        );
    }

    private CampaignResponse toResponseWithAttribution(MarketingCampaign campaign) {
        List<Lead> leads = leadRepository.findByOrganizationIdAndCampaignId(campaign.getOrganizationId(), campaign.getId());
        long count = leads.size();
        BigDecimal pipelineValue = leads.stream()
                .map(l -> l.getEstimatedValueEtb() != null ? l.getEstimatedValueEtb() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CampaignResponse.from(campaign, count, pipelineValue);
    }
}
