package com.bizgrow.auth.dto;

import com.bizgrow.membership.MembershipRole;
import com.bizgrow.organization.OrganizationStatus;
import com.bizgrow.user.dto.UserResponse;

import java.util.List;
import java.util.UUID;

/**
 * GET /api/v1/auth/me — the frontend's session bootstrap payload: who I am plus
 * every organization I can switch to, with my role in each.
 *
 * <p>Roles come from the database (live), not from the JWT — which is exactly why
 * the UI can trust them for display while the backend independently re-checks
 * permissions on every call.</p>
 */
public record MeResponse(UserResponse user, List<MembershipBrief> memberships) {

    public record MembershipBrief(
            UUID organizationId,
            String name,
            String slug,
            OrganizationStatus status,
            MembershipRole role) {
    }
}
