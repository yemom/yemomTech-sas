package com.YemomBizGrow.invitation.dto;

import com.YemomBizGrow.invitation.OrganizationInvitation;
import com.YemomBizGrow.membership.MembershipRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Invitation as listable metadata. The raw token is deliberately NOT part of
 * this shape — {@code devAcceptToken} is a separate, explicitly-gated field that
 * only exists when the dev profile enables {@code expose-dev-token}.
 *
 * @param state derived PENDING/ACCEPTED/REVOKED/EXPIRED view of the timestamps
 * @param devAcceptToken raw token for local development ONLY (never in production)
 */
public record InvitationResponse(
        UUID id,
        String email,
        MembershipRole role,
        String state,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        Instant createdAt,
        String devAcceptToken) {

    public static InvitationResponse from(OrganizationInvitation invitation) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getInvitedEmail(),
                invitation.getRole(),
                deriveState(invitation),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getRevokedAt(),
                invitation.getCreatedAt(),
                null);
    }

    public static String deriveState(OrganizationInvitation invitation) {
        if (invitation.getAcceptedAt() != null) {
            return "ACCEPTED";
        }
        if (invitation.getRevokedAt() != null) {
            return "REVOKED";
        }
        return invitation.isExpired() ? "EXPIRED" : "PENDING";
    }
}
