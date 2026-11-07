package com.YemomBizGrow.invitation;

import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.common.util.Pagination;
import com.YemomBizGrow.invitation.dto.CreateInvitationRequest;
import com.YemomBizGrow.invitation.dto.InvitationAcceptRequest;
import com.YemomBizGrow.invitation.dto.InvitationAcceptResponse;
import com.YemomBizGrow.invitation.dto.InvitationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Invitation endpoints. Note the split:
 * <ul>
 *   <li>Organization-scoped routes under {@code /organizations/{id}/invitations}
 *       require {@code INVITE_MANAGE} (owner/admin).</li>
 *   <li>{@code /invitations/accept} is a global route — the token itself carries
 *       the organization binding, and acceptance is restricted to the authenticated
 *       user whose e-mail matches the invitation.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Invitations", description = "Invite members to an organization")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/organizations/{organizationId}/invitations")
    public ResponseEntity<InvitationResponse> create(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateInvitationRequest request) {
        InvitationResponse created = invitationService.create(CurrentUser.require().id(), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/organizations/{organizationId}/invitations")
    public ResponseEntity<PageResponse<InvitationResponse>> list(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = invitationService.list(CurrentUser.require().id(), organizationId, Pagination.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @PostMapping("/organizations/{organizationId}/invitations/{invitationId}/revoke")
    public ResponseEntity<InvitationResponse> revoke(@PathVariable UUID organizationId,
                                                     @PathVariable UUID invitationId) {
        return ResponseEntity.ok(
                invitationService.revoke(CurrentUser.require().id(), organizationId, invitationId));
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<InvitationAcceptResponse> accept(
            @Valid @RequestBody InvitationAcceptRequest request) {
        InvitationAcceptResponse joined =
                invitationService.accept(CurrentUser.require().id(), request.token());
        return ResponseEntity.ok(joined);
    }
}
