package com.bizgrow.invitation;

import com.bizgrow.audit.AuditAction;
import com.bizgrow.audit.AuditService;
import com.bizgrow.common.config.AppProperties;
import com.bizgrow.common.exception.BadRequestException;
import com.bizgrow.common.exception.ConflictException;
import com.bizgrow.common.exception.ForbiddenException;
import com.bizgrow.common.exception.NotFoundException;
import com.bizgrow.common.util.TokenHasher;
import com.bizgrow.invitation.delivery.InvitationDelivery;
import com.bizgrow.invitation.dto.CreateInvitationRequest;
import com.bizgrow.invitation.dto.InvitationResponse;
import com.bizgrow.membership.AccessControlService;
import com.bizgrow.membership.MembershipRole;
import com.bizgrow.membership.MembershipService;
import com.bizgrow.membership.OrganizationMembership;
import com.bizgrow.membership.Permission;
import com.bizgrow.organization.Organization;
import com.bizgrow.organization.OrganizationReadService;
import com.bizgrow.user.User;
import com.bizgrow.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Invitation lifecycle: issue (token hashed at rest, 7-day expiry) → list →
 * accept (single-use, e-mail-bound, transactional) → revoke.
 *
 * <p>Security invariants:
 * <ul>
 *   <li><b>Raw token exists only transiently:</b> generated here with 256 bits of
 *       SecureRandom entropy, hashed immediately, handed to the delivery adapter
 *       (the one legitimate transport use) and — ONLY when the dev-only flag is on
 *       — echoed in the API response. Production never returns it.</li>
 *   <li><b>Binding:</b> acceptance requires the authenticated user's e-mail to equal
 *       {@code invited_email} (case-insensitive) — a leaked link cannot be redeemed
 *       by a different account.</li>
 *   <li><b>Single-use:</b> accepted/revoked/expired invitations are rejected with
 *       specific messages; acceptance + membership creation commit together.</li>
 *   <li><b>Rank rule:</b> an inviter cannot offer a role above their own
 *       (only owners may invite owners).</li>
 *   <li>Already-active members hit a clean conflict instead of a duplicate-key 500.</li>
 * </ul></p>
 */
@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final Duration INVITATION_TTL = Duration.ofDays(7);

    private final OrganizationInvitationRepository invitationRepository;
    private final AccessControlService accessControl;
    private final MembershipService membershipService;
    private final OrganizationReadService organizationReadService;
    private final UserService userService;
    private final InvitationDelivery delivery;
    private final AppProperties properties;
    private final AuditService auditService;

    public InvitationService(OrganizationInvitationRepository invitationRepository,
                             AccessControlService accessControl,
                             MembershipService membershipService,
                             OrganizationReadService organizationReadService,
                             UserService userService,
                             InvitationDelivery delivery,
                             AppProperties properties,
                             AuditService auditService) {
        this.invitationRepository = invitationRepository;
        this.accessControl = accessControl;
        this.membershipService = membershipService;
        this.organizationReadService = organizationReadService;
        this.userService = userService;
        this.delivery = delivery;
        this.properties = properties;
        this.auditService = auditService;
    }

    /* --------------------------------- create --------------------------------- */

    @Transactional
    public InvitationResponse create(UUID actorId, UUID organizationId, CreateInvitationRequest request) {
        OrganizationMembership actor = accessControl.requirePermission(
                actorId, organizationId, Permission.INVITE_MANAGE);
        organizationReadService.requireActive(organizationId);

        if (!actor.getRole().isAtLeast(request.role())) {
            throw ForbiddenException.defaultDenied(); // cannot offer a role above one's own
        }

        String email = request.email().trim().toLowerCase(Locale.ROOT);

        boolean alreadyMember = userService.findByEmail(email)
                .map(existingUser -> membershipService.findActive(organizationId, existingUser.getId()).isPresent())
                .orElse(false);
        if (alreadyMember) {
            throw new ConflictException("This person is already a member of the organization.");
        }

        boolean alreadyPending = invitationRepository
                .findByOrganizationIdAndInvitedEmailAndAcceptedAtIsNull(organizationId, email).stream()
                .anyMatch(inv -> inv.isPending() && !inv.isExpired());
        if (alreadyPending) {
            throw new ConflictException("An invitation for this email is already pending.");
        }

        String rawToken = TokenHasher.newRawToken();
        OrganizationInvitation invitation = new OrganizationInvitation();
        invitation.setOrganizationId(organizationId);
        invitation.setInvitedEmail(email);
        invitation.setInvitedBy(actorId);
        invitation.setRole(request.role());
        invitation.setInvitationTokenHash(TokenHasher.sha256Hex(rawToken));
        invitation.setExpiresAt(Instant.now().plus(INVITATION_TTL));
        OrganizationInvitation saved = invitationRepository.save(invitation);

        Organization organization = organizationReadService.get(organizationId);
        String acceptUrl = properties.getSecurity().getInvitation().isExposeDevToken()
                ? "" // dev: token comes back in the response, no link needed
                : properties.getFrontend().getBaseUrl() + "/workspace/invitations";
        delivery.deliver(email, rawToken, organization.getName(), acceptUrl);

        auditService.record(actorId, organizationId, AuditAction.MEMBER_INVITED,
                "invitation", saved.getId(),
                Map.of("invitedEmail", email, "role", request.role().name()));

        InvitationResponse response = InvitationResponse.from(saved);
        if (properties.getSecurity().getInvitation().isExposeDevToken()) {
            return new InvitationResponse(response.id(), response.email(), response.role(), response.state(),
                    response.expiresAt(), response.acceptedAt(), response.revokedAt(), response.createdAt(),
                    rawToken);
        }
        return response;
    }

    /* ---------------------------------- list ---------------------------------- */

    public Page<InvitationResponse> list(UUID actorId, UUID organizationId, Pageable pageable) {
        accessControl.requirePermission(actorId, organizationId, Permission.INVITE_MANAGE);
        return invitationRepository.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable)
                .map(InvitationResponse::from);
    }

    /* --------------------------------- accept --------------------------------- */

    /**
     * Redeems an invitation for the CURRENTLY AUTHENTICATED user.
     * All-or-nothing: invitation consumed + membership created in one transaction.
     */
    @Transactional
    public com.bizgrow.invitation.dto.InvitationAcceptResponse accept(UUID acceptingUserId, String rawToken) {
        OrganizationInvitation invitation = invitationRepository
                .findByInvitationTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> NotFoundException.resource("Invitation"));

        if (invitation.getRevokedAt() != null) {
            throw new BadRequestException("This invitation has been revoked.");
        }
        if (invitation.getAcceptedAt() != null) {
            throw new BadRequestException("This invitation has already been used.");
        }
        if (invitation.isExpired()) {
            throw new BadRequestException("This invitation has expired. Please ask for a new one.");
        }

        User acceptingUser = userService.getById(acceptingUserId);
        if (!invitation.getInvitedEmail().equalsIgnoreCase(acceptingUser.getEmail())) {
            // Never confirm what the correct address was.
            throw new ForbiddenException("This invitation was sent to a different email address.");
        }

        organizationReadService.requireActive(invitation.getOrganizationId());

        if (membershipService.findActive(invitation.getOrganizationId(), acceptingUserId).isPresent()) {
            throw new ConflictException("You are already a member of this organization.");
        }

        // create-or-reactivate (handles re-invite after a past removal)
        membershipService.join(invitation.getOrganizationId(), acceptingUserId, invitation.getRole());

        invitation.setAcceptedAt(Instant.now());
        invitation.setAcceptedBy(acceptingUserId);
        invitationRepository.save(invitation);

        auditService.record(acceptingUserId, invitation.getOrganizationId(), AuditAction.INVITATION_ACCEPTED,
                "invitation", invitation.getId(),
                Map.of("role", invitation.getRole().name()));

        Organization joined = organizationReadService.get(invitation.getOrganizationId());
        return new com.bizgrow.invitation.dto.InvitationAcceptResponse(
                joined.getId(), joined.getName(), joined.getSlug(), invitation.getRole());
    }

    /* --------------------------------- revoke --------------------------------- */

    @Transactional
    public InvitationResponse revoke(UUID actorId, UUID organizationId, UUID invitationId) {
        accessControl.requirePermission(actorId, organizationId, Permission.INVITE_MANAGE);

        OrganizationInvitation invitation = invitationRepository.findById(invitationId)
                .filter(inv -> inv.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> NotFoundException.resource("Invitation"));

        if (invitation.getAcceptedAt() != null) {
            throw new BadRequestException("This invitation has already been accepted and cannot be revoked.");
        }
        if (invitation.getRevokedAt() != null) {
            return InvitationResponse.from(invitation); // idempotent
        }

        invitation.setRevokedAt(Instant.now());
        OrganizationInvitation saved = invitationRepository.save(invitation);

        auditService.record(actorId, organizationId, AuditAction.INVITATION_REVOKED,
                "invitation", saved.getId(),
                Map.of("invitedEmail", saved.getInvitedEmail()));

        return InvitationResponse.from(saved);
    }
}
