package com.YemomBizGrow.membership.dto;

import com.YemomBizGrow.membership.MembershipRole;
import com.YemomBizGrow.membership.MembershipStatus;
import com.YemomBizGrow.membership.OrganizationMembership;
import com.YemomBizGrow.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Member list item: membership fields + the member's basic identity (needed to
 * render the members table). Assembled from two tables by the controller/service
 * layer; entities themselves are never serialized.
 */
public record MemberResponse(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        String email,
        MembershipRole role,
        MembershipStatus status,
        Instant joinedAt) {

    public static MemberResponse of(OrganizationMembership membership, User user) {
        return new MemberResponse(
                membership.getId(),
                membership.getUserId(),
                user != null ? user.getFirstName() : "",
                user != null ? user.getLastName() : "",
                user != null ? user.getEmail() : "",
                membership.getRole(),
                membership.getMembershipStatus(),
                membership.getJoinedAt());
    }
}
