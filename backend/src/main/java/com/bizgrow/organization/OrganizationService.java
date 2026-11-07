package com.YemomBizGrow.organization;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.ConflictException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.common.util.SlugGenerator;
import com.YemomBizGrow.common.util.WebsiteUrlNormalizer;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.MembershipRole;
import com.YemomBizGrow.membership.MembershipService;
import com.YemomBizGrow.organization.dto.CreateOrganizationRequest;
import com.YemomBizGrow.organization.dto.DashboardResponse;
import com.YemomBizGrow.organization.dto.OrganizationResponse;
import com.YemomBizGrow.organization.dto.UpdateOrganizationRequest;
import com.YemomBizGrow.website.WebsiteService;
import com.YemomBizGrow.website.dto.WebsiteResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Organization use cases: create (with owner membership atomically), list mine,
 * read, update, soft-delete (archive), restore, and the workspace dashboard.
 */
@Service
public class OrganizationService {

    private static final SecureRandom SLUG_RANDOM = new SecureRandom();

    private final OrganizationRepository organizationRepository;
    private final AccessControlService accessControl;
    private final MembershipService membershipService;
    private final WebsiteService websiteService;
    private final AuditService auditService;

    public OrganizationService(OrganizationRepository organizationRepository,
                               AccessControlService accessControl,
                               MembershipService membershipService,
                               WebsiteService websiteService,
                               AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.accessControl = accessControl;
        this.membershipService = membershipService;
        this.websiteService = websiteService;
        this.auditService = auditService;
    }

    /* --------------------------------- create --------------------------------- */

    @Transactional
    public OrganizationResponse create(UUID actorId, CreateOrganizationRequest request) {
        String name = request.name().trim();
        if (name.isEmpty()) {
            throw new BadRequestException("Organization name is required.");
        }

        Organization organization = new Organization();
        organization.setName(name);
        organization.setSlug(generateUniqueSlug(name));
        organization.setIndustry(normalizeIndustry(request.industry()));
        organization.setDescription(trimToNull(request.description()));
        organization.setWebsiteUrl(normalizeOptionalWebsiteUrl(request.websiteUrl()));
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setCreatedBy(actorId);

        Organization saved;
        try {
            saved = organizationRepository.save(organization);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Could not allocate a unique organization address. Please retry.");
        }

        membershipService.create(saved.getId(), actorId, MembershipRole.BUSINESS_OWNER);

        auditService.record(actorId, saved.getId(), AuditAction.ORGANIZATION_CREATED,
                "organization", saved.getId(), Map.of("name", saved.getName()));
        return toResponse(saved);
    }

    /* ---------------------------------- read ---------------------------------- */

    public List<OrganizationResponse> listMine(UUID actorId) {
        List<UUID> orgIds = membershipService.listActiveOrgIdsForUser(actorId);
        List<Organization> organizations = organizationReadFindMany(orgIds);
        return organizations.stream()
                .filter(o -> o.getStatus() == OrganizationStatus.ACTIVE)
                .map(this::toResponse)
                .sorted((a, b) -> a.createdAt().compareTo(b.createdAt()))
                .toList();
    }

    public OrganizationResponse get(UUID actorId, UUID organizationId) {
        accessControl.requirePermission(actorId, organizationId, com.YemomBizGrow.membership.Permission.ORG_VIEW);
        return toResponse(organizationRead(organizationId));
    }

    public DashboardResponse dashboard(UUID actorId, UUID organizationId) {
        accessControl.requirePermission(actorId, organizationId, com.YemomBizGrow.membership.Permission.ORG_VIEW);
        Organization organization = organizationRead(organizationId);

        long memberCount = membershipService.countActiveMembers(organizationId);
        long websiteCount = websiteService.countActive(organizationId);
        List<WebsiteResponse> recent = websiteService.listRecent(organizationId, 5);

        return new DashboardResponse(
                OrganizationResponse.of(organization, memberCount, websiteCount),
                recent,
                new DashboardResponse.OnboardingProgress(
                        websiteCount > 0,
                        memberCount > 1));
    }

    /* --------------------------------- update --------------------------------- */

    @Transactional
    public OrganizationResponse update(UUID actorId, UUID organizationId, UpdateOrganizationRequest request) {
        accessControl.requirePermission(actorId, organizationId, com.YemomBizGrow.membership.Permission.ORG_UPDATE);
        Organization organization = organizationRead(organizationId);

        List<String> changed = new ArrayList<>();
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new BadRequestException("Organization name cannot be empty.");
            }
            if (!name.equals(organization.getName())) {
                organization.setName(name);
                changed.add("name");
            }
        }
        if (request.industry() != null) {
            String industry = normalizeIndustry(request.industry());
            if (!java.util.Objects.equals(industry, organization.getIndustry())) {
                organization.setIndustry(industry);
                changed.add("industry");
            }
        }
        if (request.description() != null) {
            String description = trimToNull(request.description());
            if (!java.util.Objects.equals(description, organization.getDescription())) {
                organization.setDescription(description);
                changed.add("description");
            }
        }
        if (request.websiteUrl() != null) {
            String url = normalizeOptionalWebsiteUrl(request.websiteUrl());
            if (!java.util.Objects.equals(url, organization.getWebsiteUrl())) {
                organization.setWebsiteUrl(url);
                changed.add("websiteUrl");
            }
        }

        if (changed.isEmpty()) {
            return toResponse(organization);
        }

        Organization saved = organizationRepository.save(organization);
        auditService.record(actorId, organizationId, AuditAction.ORGANIZATION_UPDATED,
                "organization", organizationId, Map.of("fields", changed));
        return toResponse(saved);
    }

    /* --------------------------------- delete / restore --------------------------------- */

    @Transactional
    public void archive(UUID actorId, UUID organizationId) {
        accessControl.requirePermission(actorId, organizationId, com.YemomBizGrow.membership.Permission.ORG_DELETE);
        Organization organization = organizationRead(organizationId);
        if (organization.getStatus() == OrganizationStatus.ARCHIVED) {
            return;
        }
        organization.setStatus(OrganizationStatus.ARCHIVED);
        organizationRepository.save(organization);
        auditService.record(actorId, organizationId, AuditAction.ORGANIZATION_ARCHIVED,
                "organization", organizationId, null);
    }

    @Transactional
    public OrganizationResponse restore(UUID actorId, UUID organizationId) {
        accessControl.requireRestorePermission(actorId, organizationId);
        Organization organization = organizationRead(organizationId);
        if (organization.getStatus() != OrganizationStatus.ARCHIVED) {
            return toResponse(organization);
        }
        organization.setStatus(OrganizationStatus.ACTIVE);
        Organization saved = organizationRepository.save(organization);
        auditService.record(actorId, organizationId, AuditAction.ORGANIZATION_RESTORED,
                "organization", organizationId, null);
        return toResponse(saved);
    }

    /* ------------------------------- help rules ------------------------------- */

    private String generateUniqueSlug(String name) {
        String base = SlugGenerator.slugify(name);
        String candidate = base;
        for (int attempt = 0; attempt < 5; attempt++) {
            if (!organizationRepository.existsBySlug(candidate)) {
                return candidate;
            }
            candidate = base + "-" + Integer.toHexString(SLUG_RANDOM.nextInt(0x10000)).toLowerCase(Locale.ROOT);
        }
        throw new ConflictException("Could not allocate a unique organization address. Please retry.");
    }

    private String normalizeIndustry(String industry) {
        if (industry == null || industry.isBlank()) {
            return null;
        }
        return industry.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_");
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeOptionalWebsiteUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return WebsiteUrlNormalizer.normalize(url).url();
    }

    private Organization organizationRead(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> NotFoundException.resource("Organization"));
    }

    private List<Organization> organizationReadFindMany(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return organizationRepository.findAllById(ids);
    }

    /* ------------------------------ response mapping --------------------------- */

    private OrganizationResponse toResponse(Organization organization) {
        long memberCount = membershipService.countActiveMembers(organization.getId());
        long websiteCount = websiteService.countActive(organization.getId());
        return OrganizationResponse.of(organization, memberCount, websiteCount);
    }
}
