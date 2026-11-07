package com.bizgrow.membership;

import com.bizgrow.common.exception.ForbiddenException;
import com.bizgrow.common.exception.NotFoundException;
import com.bizgrow.organization.Organization;
import com.bizgrow.organization.OrganizationReadService;
import com.bizgrow.organization.OrganizationStatus;
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
 *       message; non-members never reach that check, so existence stays hidden).
 *       This matches {@code listMine}, which already filters ARCHIVED rows out.</li>
 *   <li><b>Members lacking a permission get 403</b> (they already know the org
 *       exists; hiding that would just confuse legitimate users).</li>
 * </ul>
 * The membership row is re-read from the database per operation — a revoked role
 * takes effect immediately, regardless of what any token or cached UI state says.</p>
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
     *
     * @throws NotFoundException  not a member of this organization, or the
     *                            organization has been archived (soft-deleted)
     * @throws ForbiddenException member without the required permission
     */
    public OrganizationMembership requirePermission(UUID userId, UUID organizationId, Permission permission) {
        // Membership first: a non-member gets the generic 404 even for an archived
        // org, so probing cannot distinguish "archived" from "never existed".
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
}
