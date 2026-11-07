package com.bizgrow.membership.dto;

import com.bizgrow.membership.MembershipRole;
import com.bizgrow.membership.MembershipStatus;

/**
 * PATCH /api/v1/organizations/{organizationId}/members/{memberId}.
 * Optional role and/or membership status. REMOVED is rejected here (DELETE is
 * the removal endpoint). Escalation/last-owner rules are enforced in the service.
 */
public record MemberUpdateRequest(MembershipRole role, MembershipStatus status) {
}
