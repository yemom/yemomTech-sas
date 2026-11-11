package com.YemomBizGrow.seo;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.ConflictException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import com.YemomBizGrow.seo.dto.SeoAuditResponse;
import com.YemomBizGrow.seo.dto.SeoCrawledPageResponse;
import com.YemomBizGrow.seo.dto.SeoIssueResponse;
import com.YemomBizGrow.website.Website;
import com.YemomBizGrow.website.WebsiteRepository;
import com.YemomBizGrow.website.WebsiteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SeoAuditService {

    private final SeoAuditRepository auditRepository;
    private final SeoCrawledPageRepository crawledPageRepository;
    private final SeoIssueRepository issueRepository;
    private final WebsiteRepository websiteRepository;
    private final AccessControlService accessControlService;
    private final AuditService auditService;
    private final SeoAuditRunner auditRunner;

    public SeoAuditService(
            SeoAuditRepository auditRepository,
            SeoCrawledPageRepository crawledPageRepository,
            SeoIssueRepository issueRepository,
            WebsiteRepository websiteRepository,
            AccessControlService accessControlService,
            AuditService auditService,
            SeoAuditRunner auditRunner) {
        this.auditRepository = auditRepository;
        this.crawledPageRepository = crawledPageRepository;
        this.issueRepository = issueRepository;
        this.websiteRepository = websiteRepository;
        this.accessControlService = accessControlService;
        this.auditService = auditService;
        this.auditRunner = auditRunner;
    }

    @Transactional
    public SeoAuditResponse triggerAudit(UUID actorUserId, UUID organizationId, UUID websiteId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.SEO_MANAGE);

        Website website = websiteRepository.findByIdAndOrganizationId(websiteId, organizationId)
                .orElseThrow(() -> new NotFoundException("Website not found."));

        if (website.getStatus() != WebsiteStatus.ACTIVE) {
            throw new ConflictException("Cannot run SEO audit on an inactive website.");
        }

        // Prevent concurrent audits for the same website
        auditRepository.findFirstByOrganizationIdAndWebsiteIdAndStatusIn(
                organizationId,
                websiteId,
                List.of(SeoAuditStatus.PENDING, SeoAuditStatus.RUNNING)
        ).ifPresent(existing -> {
            throw new ConflictException("An SEO audit is already running or queued for this website.");
        });

        SeoAudit audit = new SeoAudit();
        audit.setOrganizationId(organizationId);
        audit.setWebsiteId(websiteId);
        audit.setStatus(SeoAuditStatus.PENDING);
        audit = auditRepository.save(audit);

        auditService.record(actorUserId, organizationId, AuditAction.SEO_AUDIT_TRIGGERED,
                "SeoAudit", audit.getId(),
                Map.of("websiteId", websiteId, "websiteUrl", website.getUrl()));

        auditRunner.executeAuditAsync(audit.getId(), website.getUrl());

        return SeoAuditResponse.from(audit);
    }

    @Transactional(readOnly = true)
    public SeoAuditResponse getAudit(UUID actorUserId, UUID organizationId, UUID websiteId, UUID auditId) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_VIEW);

        SeoAudit audit = auditRepository.findByIdAndOrganizationIdAndWebsiteId(auditId, organizationId, websiteId)
                .orElseThrow(() -> new NotFoundException("SEO audit not found."));

        return SeoAuditResponse.from(audit);
    }

    @Transactional(readOnly = true)
    public PageResponse<SeoAuditResponse> listAudits(UUID actorUserId, UUID organizationId, UUID websiteId, Pageable pageable) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_VIEW);

        // Ensure website belongs to org
        websiteRepository.findByIdAndOrganizationId(websiteId, organizationId)
                .orElseThrow(() -> new NotFoundException("Website not found."));

        Page<SeoAudit> page = auditRepository.findByOrganizationIdAndWebsiteIdOrderByCreatedAtDesc(organizationId, websiteId, pageable);
        return PageResponse.from(page.map(SeoAuditResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<SeoIssueResponse> listIssues(
            UUID actorUserId, UUID organizationId, UUID websiteId, UUID auditId,
            String severity, String category, Pageable pageable) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_VIEW);

        auditRepository.findByIdAndOrganizationIdAndWebsiteId(auditId, organizationId, websiteId)
                .orElseThrow(() -> new NotFoundException("SEO audit not found."));

        Page<SeoIssue> page;
        if (severity != null && !severity.isBlank()) {
            page = issueRepository.findByOrganizationIdAndAuditIdAndSeverity(organizationId, auditId, severity.toUpperCase(), pageable);
        } else if (category != null && !category.isBlank()) {
            page = issueRepository.findByOrganizationIdAndAuditIdAndCategory(organizationId, auditId, category.toUpperCase(), pageable);
        } else {
            page = issueRepository.findByOrganizationIdAndAuditIdOrderBySeverityAscCreatedAtAsc(organizationId, auditId, pageable);
        }

        return PageResponse.from(page.map(SeoIssueResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<SeoCrawledPageResponse> listCrawledPages(
            UUID actorUserId, UUID organizationId, UUID websiteId, UUID auditId, Pageable pageable) {
        accessControlService.requirePermission(actorUserId, organizationId, Permission.WEBSITE_VIEW);

        auditRepository.findByIdAndOrganizationIdAndWebsiteId(auditId, organizationId, websiteId)
                .orElseThrow(() -> new NotFoundException("SEO audit not found."));

        Page<SeoCrawledPage> page = crawledPageRepository.findByOrganizationIdAndAuditIdOrderByCreatedAtAsc(organizationId, auditId, pageable);
        return PageResponse.from(page.map(SeoCrawledPageResponse::from));
    }
}
