package com.bizgrow.membership;

import com.bizgrow.common.response.PageResponse;
import com.bizgrow.common.security.CurrentUser;
import com.bizgrow.common.util.Pagination;
import com.bizgrow.membership.dto.MemberResponse;
import com.bizgrow.membership.dto.MemberUpdateRequest;
import com.bizgrow.user.UserService;
import com.bizgrow.user.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Organization membership endpoints (paginated; all tenant-gated).
 *
 * <p>Coarse permission ({@code MEMBER_VIEW}/{@code MEMBER_MANAGE}) is checked here,
 * then the service applies the fine-grained rules (self-edit ban, escalation ban,
 * last-owner protection) against fresh database state.</p>
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/members")
@Tag(name = "Members", description = "Organization member list and management")
public class MembershipController {

    private final MembershipService membershipService;
    private final AccessControlService accessControl;
    private final UserService userService;

    public MembershipController(MembershipService membershipService,
                                AccessControlService accessControl,
                                UserService userService) {
        this.membershipService = membershipService;
        this.accessControl = accessControl;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<MemberResponse>> list(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID actorId = CurrentUser.require().id();
        accessControl.requirePermission(actorId, organizationId, Permission.MEMBER_VIEW);

        Page<OrganizationMembership> memberships =
                membershipService.listActiveMembers(organizationId, Pagination.of(page, size));

        Map<UUID, User> users = userService.findUsersByIds(
                memberships.getContent().stream().map(OrganizationMembership::getUserId).toList());

        Page<MemberResponse> mapped = memberships.map(m -> MemberResponse.of(m, users.get(m.getUserId())));
        return ResponseEntity.ok(PageResponse.from(mapped));
    }

    @PatchMapping("/{memberId}")
    public ResponseEntity<MemberResponse> update(@PathVariable UUID organizationId,
                                                 @PathVariable UUID memberId,
                                                 @Valid @RequestBody MemberUpdateRequest request) {
        UUID actorId = CurrentUser.require().id();
        accessControl.requirePermission(actorId, organizationId, Permission.MEMBER_MANAGE);

        OrganizationMembership updated = membershipService.updateMember(actorId, organizationId, memberId, request);
        User target = userService.findUsersByIds(java.util.List.of(updated.getUserId()))
                .getOrDefault(updated.getUserId(), null);
        return ResponseEntity.ok(MemberResponse.of(updated, target));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<Void> remove(@PathVariable UUID organizationId, @PathVariable UUID memberId) {
        UUID actorId = CurrentUser.require().id();
        accessControl.requirePermission(actorId, organizationId, Permission.MEMBER_MANAGE);
        membershipService.removeMember(actorId, organizationId, memberId);
        return ResponseEntity.noContent().build();
    }
}
