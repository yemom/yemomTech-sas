package com.bizgrow.invitation.dto;

import com.bizgrow.membership.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * POST /api/v1/organizations/{organizationId}/invitations payload.
 * The service verifies the actor may grant {@code role} (cannot invite above
 * one's own rank) in addition to the {@code INVITE_MANAGE} matrix check.
 */
public record CreateInvitationRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Enter a valid email address.")
        String email,

        @NotNull(message = "Role is required.")
        MembershipRole role) {
}
