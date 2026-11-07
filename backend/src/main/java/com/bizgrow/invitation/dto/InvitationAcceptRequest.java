package com.bizgrow.invitation.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /api/v1/invitations/accept — presents the one-time token from the invite link. */
public record InvitationAcceptRequest(
        @NotBlank(message = "Invitation token is required.")
        String token) {
}
