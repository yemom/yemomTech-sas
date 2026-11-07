package com.bizgrow.website;

import com.bizgrow.audit.AuditAction;
import com.bizgrow.audit.AuditService;
import com.bizgrow.common.exception.ConflictException;
import com.bizgrow.common.exception.NotFoundException;
import com.bizgrow.common.util.WebsiteUrlNormalizer;
import com.bizgrow.membership.AccessControlService;
import com.bizgrow.membership.Permission;
import com.bizgrow.organization.OrganizationReadService;
import com.bizgrow.website.dto.CreateWebsiteRequest;
import com.bizgrow.website.dto.UpdateWebsiteRequest;
import com.bizgrow.website.dto.WebsiteResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Website registration use cases — the data half of the first vertical slice.
 *
 * <p>Security-critical properties:
 * <ul>
 *   <li><b>No outbound requests, ever.</b> Registration only validates and stores
 *       the URL ({@link WebsiteUrlNormalizer}) — the classic SSRF vector (fetching
 *       user-supplied URLs) is structurally absent from this slice. When SEO
 *       crawling is introduced it must re-run this normalizer PLUS DNS/private-IP
 *       checks per request.</li>
 *   <li><b>Every read/write is organization-scoped in the query itself</b>
 *       ({@code findByIdAndOrganizationId}): a website id from another tenant
 *       matches nothing and returns 404 — existence is not disclosed.</li>
 *   <li><b>Permission + active-organization gates</b> run before any mutation.</li>
 * </ul></p>
 */
@Service
public class WebsiteService {

    private final WebsiteRepository websiteRepository;
    private final AccessControlService accessControl;
    private final OrganizationReadService organizationReadService;
    private final AuditService auditService;

    public WebsiteService(WebsiteRepository websiteRepository,
                          AccessControlService accessControl,
                          OrganizationReadService organizationReadService,
                          AuditService auditService) {
        this.websiteRepository = websiteRepository;
        this.accessControl = accessControl;
        this.organizationReadService = organizationReadService;
        this.auditService = auditService;
    }

    /* --------------------------------- create --------------------------------- */

    @Transactional
    public WebsiteResponse create(UUID actorId, UUID organizationId, CreateWebsiteRequest request) {
        accessControl.requirePermission(actorId, organizationId, Permission.WEBSITE_MANAGE);
        organizationReadService.requireActive(organizationId);

        WebsiteUrlNormalizer.NormalizedUrl normalized = WebsiteUrlNormalizer.normalize(request.url());

        if (websiteRepository.existsByOrganizationIdAndDomain(organizationId, normalized.domain())) {
            throw new ConflictException("A website with the domain '" + normalized.domain()
                    + "' is already registered in this organization.");
        }

        Website website = new Website();
        website.setOrganizationId(organizationId);
        website.setName(request.name().trim());
        website.setDomain(normalized.domain());
        website.setUrl(normalized.url());
        website.setStatus(WebsiteStatus.ACTIVE);
        website.setCreatedBy(actorId);

        Website saved;
        try {
            saved = websiteRepository.save(website);
        } catch (DataIntegrityViolationException e) {
            // Unique (organization_id, domain) race — same message as the pre-check.
            throw new ConflictException("A website with the domain '" + normalized.domain()
                    + "' is already registered in this organization.");
        }

        auditService.record(actorId, organizationId, AuditAction.WEBSITE_CREATED,
                "website", saved.getId(), Map.of("domain", saved.getDomain()));
        return WebsiteResponse.from(saved);
    }

    /* --------------------------------- read ----------------------------------- */

    public Page<WebsiteResponse> list(UUID actorId, UUID organizationId, Pageable pageable) {
        accessControl.requirePermission(actorId, organizationId, Permission.WEBSITE_VIEW);
        return websiteRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable)
                .map(WebsiteResponse::from);
    }

    public WebsiteResponse get(UUID actorId, UUID organizationId, UUID websiteId) {
        accessControl.requirePermission(actorId, organizationId, Permission.WEBSITE_VIEW);
        return WebsiteResponse.from(findScoped(organizationId, websiteId));
    }

    /** Dashboard feed: newest ACTIVE websites, bounded (no pagination needed). */
    public List<WebsiteResponse> listRecent(UUID organizationId, int limit) {
        return websiteRepository
                .findTop5ByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .limit(Math.max(1, limit))
                .map(WebsiteResponse::from)
                .toList();
    }

    public long countActive(UUID organizationId) {
        return websiteRepository.countByOrganizationIdAndStatus(organizationId, WebsiteStatus.ACTIVE);
    }

    /* --------------------------------- update --------------------------------- */

    @Transactional
    public WebsiteResponse update(UUID actorId, UUID organizationId, UUID websiteId, UpdateWebsiteRequest request) {
        accessControl.requirePermission(actorId, organizationId, Permission.WEBSITE_MANAGE);
        organizationReadService.requireActive(organizationId);
        Website website = findScoped(organizationId, websiteId);

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new com.bizgrow.common.exception.BadRequestException("Website name cannot be empty.");
            }
            website.setName(name);
        }

        if (request.url() != null) {
            WebsiteUrlNormalizer.NormalizedUrl normalized = WebsiteUrlNormalizer.normalize(request.url());
            if (!normalized.domain().equals(website.getDomain())) {
                if (websiteRepository.existsByOrganizationIdAndDomain(organizationId, normalized.domain())) {
                    throw new ConflictException("A website with the domain '" + normalized.domain()
                            + "' is already registered in this organization.");
                }
                website.setDomain(normalized.domain());
            }
            website.setUrl(normalized.url());
        }

        Website saved = websiteRepository.save(website);
        auditService.record(actorId, organizationId, AuditAction.WEBSITE_UPDATED,
                "website", saved.getId(), Map.of("domain", saved.getDomain()));
        return WebsiteResponse.from(saved);
    }

    /* ------------------------------ deactivate -------------------------------- */

    /** Soft deactivation (DELETE): row kept, hidden from ACTIVE counts. Idempotent. */
    @Transactional
    public WebsiteResponse deactivate(UUID actorId, UUID organizationId, UUID websiteId) {
        accessControl.requirePermission(actorId, organizationId, Permission.WEBSITE_MANAGE);
        Website website = findScoped(organizationId, websiteId);
        if (website.getStatus() == WebsiteStatus.INACTIVE) {
            return WebsiteResponse.from(website);
        }
        website.setStatus(WebsiteStatus.INACTIVE);
        Website saved = websiteRepository.save(website);
        auditService.record(actorId, organizationId, AuditAction.WEBSITE_DEACTIVATED,
                "website", saved.getId(), Map.of("domain", saved.getDomain()));
        return WebsiteResponse.from(saved);
    }

    /* ------------------------------- internals -------------------------------- */

    private Website findScoped(UUID organizationId, UUID websiteId) {
        return websiteRepository.findByIdAndOrganizationId(websiteId, organizationId)
                .orElseThrow(() -> NotFoundException.resource("Website"));
    }
}
