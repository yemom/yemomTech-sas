package com.YemomBizGrow.membership;

import com.YemomBizGrow.common.exception.ForbiddenException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.organization.Organization;
import com.YemomBizGrow.organization.OrganizationReadService;
import com.YemomBizGrow.organization.OrganizationStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The tenant authorization gate — every organization-scoped operation passes
 * through this class before touching tenant data.
 *
 * <p>Three deliberate information-hiding behaviors:
 * <ul>
 *   <li><b>Non-membership looks identical to non-existence.</b> A user without an
 *       ACTIVE membership in organization X gets 404 "Organization not found."
 *       whether or not X exists — an attacker cannot enumerate other tenants'
 *       organization ids.</li>
 *   <li><b>Archived organizations leave the workspace.</b> Once soft-deleted,
 *       every org-scoped endpoint answers 404 (members get an explanatory
 *       message; non-members never reach that check, so existence stays hidden).</li>
 *   <li><b>Members lacking a permission get 403.</b></li>
 * </ul>
 * The membership row is re-read from the database per operation.</p>
 */
@Service
public class AccessControlService {

    private final MembershipService membershipService;
    private final OrganizationReadService organizationReadService;

    public AccessControlService(MembershipService membershipService,
                                OrganizationReadService organizationReadService) {
        this.membershipService = membershipService;
        this.organizationReadService = organizationReadService;
    }

    /** @throws NotFoundException when not an active member (existence-hidden) */
    public OrganizationMembership requireActiveMembership(UUID userId, UUID organizationId) {
        return membershipService.findActive(organizationId, userId)
                .orElseThrow(() -> NotFoundException.resource("Organization"));
    }

    /**
     * The standard tenant gate: ACTIVE membership + live (non-archived)
     * organization + permission from the matrix.
     */
    public OrganizationMembership requirePermission(UUID userId, UUID organizationId, Permission permission) {
        OrganizationMembership membership = requireActiveMembership(userId, organizationId);
        Organization organization = organizationReadService.get(organizationId);
        if (organization.getStatus() == OrganizationStatus.ARCHIVED) {
            throw new NotFoundException("This organization has been archived.");
        }
        if (!permission.allowedFor(membership.getRole())) {
            throw ForbiddenException.defaultDenied();
        }
        return membership;
    }

    /**
     * Specialized gate for restoring an archived organization.
     * Only an active BUSINESS_OWNER can restore an archived organization.
     */
    public OrganizationMembership requireRestorePermission(UUID userId, UUID organizationId) {
        OrganizationMembership membership = requireActiveMembership(userId, organizationId);
        if (membership.getRole() != MembershipRole.BUSINESS_OWNER) {
            throw ForbiddenException.defaultDenied();
        }
        return membership;
    }
}
