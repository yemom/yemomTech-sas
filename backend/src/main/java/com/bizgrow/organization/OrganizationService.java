package com.bizgrow.organization;

import com.bizgrow.audit.AuditAction;
import com.bizgrow.audit.AuditService;
import com.bizgrow.common.exception.BadRequestException;
import com.bizgrow.common.exception.ConflictException;
import com.bizgrow.common.exception.NotFoundException;
import com.bizgrow.common.util.SlugGenerator;
import com.bizgrow.common.util.WebsiteUrlNormalizer;
import com.bizgrow.membership.AccessControlService;
import com.bizgrow.membership.MembershipRole;
import com.bizgrow.membership.MembershipService;
import com.bizgrow.organization.dto.CreateOrganizationRequest;
import com.bizgrow.organization.dto.DashboardResponse;
import com.bizgrow.organization.dto.OrganizationResponse;
import com.bizgrow.organization.dto.UpdateOrganizationRequest;
import com.bizgrow.website.WebsiteService;
import com.bizgrow.website.dto.WebsiteResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Organization use cases: create (with owner membership atomically), list mine,
 * read, update, soft-delete (archive), and the workspace dashboard.
 *
 * <p>Tenant rules enforced here:
 * <ul>
 *   <li>Listing never scans the organizations table — it starts from the caller's
 *       ACTIVE memberships, so another tenant's row cannot appear even by id guess
 *       (reads additionally pass through {@link AccessControlService}, which turns
 *       non-membership into 404).</li>
 *   <li>Creation and the owner membership commit in ONE transaction: there is no
 *       state where an organization exists without its owner.</li>
 *   <li>Deletion is {@code ARCHIVED} (soft): business rows and audit history are
 *       retained; a documented future retention job may purge after the recovery
 *       window.</li>
 * </ul></p>
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
        // country/currency/timezone come from entity defaults: ET / ETB / Africa/Addis_Ababa

        Organization saved;
        try {
            saved = organizationRepository.save(organization);
        } catch (DataIntegrityViolationException e) {
            // Extremely unlikely slug race — surface as a conflict, not a 500.
            throw new ConflictException("Could not allocate a unique organization address. Please retry.");
        }

        // Same transaction: no organization can exist without its owner.
        membershipService.create(saved.getId(), actorId, MembershipRole.BUSINESS_OWNER);

        auditService.record(actorId, saved.getId(), AuditAction.ORGANIZATION_CREATED,
                "organization", saved.getId(), Map.of("name", saved.getName()));
        return toResponse(saved);
    }

    /* ---------------------------------- read ---------------------------------- */

    /** Only organizations the caller actively belongs to (id existence proves nothing). */
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
        accessControl.requirePermission(actorId, organizationId, com.bizgrow.membership.Permission.ORG_VIEW);
        return toResponse(organizationRead(organizationId));
    }

    /**
     * Workspace dashboard: organization profile + counts + newest websites,
     * all read live. One authorization gate, then internal reads.
     */
    public DashboardResponse dashboard(UUID actorId, UUID organizationId) {
        accessControl.requirePermission(actorId, organizationId, com.bizgrow.membership.Permission.ORG_VIEW);
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
        accessControl.requirePermission(actorId, organizationId, com.bizgrow.membership.Permission.ORG_UPDATE);
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
                // slug intentionally unchanged: external links must stay stable
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
            return toResponse(organization); // no-op PATCH still returns current state
        }

        Organization saved = organizationRepository.save(organization);
        auditService.record(actorId, organizationId, AuditAction.ORGANIZATION_UPDATED,
                "organization", organizationId, Map.of("fields", changed));
        return toResponse(saved);
    }

    /* --------------------------------- delete --------------------------------- */

    /** Soft delete: status -> ARCHIVED, hidden from listings, data retained. */
    @Transactional
    public void archive(UUID actorId, UUID organizationId) {
        accessControl.requirePermission(actorId, organizationId, com.bizgrow.membership.Permission.ORG_DELETE);
        Organization organization = organizationRead(organizationId);
        if (organization.getStatus() == OrganizationStatus.ARCHIVED) {
            return; // unreachable via HTTP (the gate 404s archived orgs first); kept as a safety net
        }
        organization.setStatus(OrganizationStatus.ARCHIVED);
        organizationRepository.save(organization);
        auditService.record(actorId, organizationId, AuditAction.ORGANIZATION_ARCHIVED,
                "organization", organizationId, null);
    }

    /* ------------------------------- help rules ------------------------------- */

    /**
     * Unique slug: base + random suffix on collision (5 attempts). Randomness
     * (not a per-row counter) means two concurrent creations rarely collide at all.
     */
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
