package com.bizgrow.invitation.dto;

import com.bizgrow.membership.MembershipRole;

import java.util.UUID;

/**
 * Result of successfully redeeming an invitation: enough context for the
 * frontend to switch into the newly joined organization. The client then
 * re-fetches /auth/me and the dashboard for full state.
 */
public record InvitationAcceptResponse(
        UUID organizationId,
        String name,
        String slug,
        MembershipRole role) {
}
