package com.YemomBizGrow.membership;

import com.YemomBizGrow.audit.AuditAction;
import com.YemomBizGrow.audit.AuditService;
import com.YemomBizGrow.common.exception.BadRequestException;
import com.YemomBizGrow.common.exception.ConflictException;
import com.YemomBizGrow.common.exception.ForbiddenException;
import com.YemomBizGrow.common.exception.NotFoundException;
import com.YemomBizGrow.membership.dto.MemberUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Membership data operations AND the member-management policy rules:
 * privilege escalation prevention, self-edit ban, and last-owner protection.
 *
 * <p>Why the rules live here (not the controller): they must hold for EVERY path
 * that can change a membership — the members API today, invitation acceptance
 * tomorrow, an import job later. Controllers add the coarse {@code MEMBER_MANAGE}
 * matrix check first; this service re-derives the actor's role from the database
 * and applies the fine-grained rules (defense in depth).</p>
 *
 * <p>Removals are status flips to REMOVED (rows are retained for audit), and only
 * ACTIVE memberships grant access.</p>
 */
@Service
public class MembershipService {

    private final OrganizationMembershipRepository repository;
    private final AuditService auditService;

    public MembershipService(OrganizationMembershipRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    /* ---------------------------------- reads --------------------------------- */

    public List<OrganizationMembership> listActiveForUser(UUID userId) {
        return repository.findByUserIdAndMembershipStatus(userId, MembershipStatus.ACTIVE);
    }

    public List<UUID> listActiveOrgIdsForUser(UUID userId) {
        return listActiveForUser(userId).stream()
                .map(OrganizationMembership::getOrganizationId)
                .toList();
    }

    public Optional<OrganizationMembership> findActive(UUID organizationId, UUID userId) {
        return repository.findByOrganizationIdAndUserId(organizationId, userId)
                .filter(m -> m.getMembershipStatus() == MembershipStatus.ACTIVE);
    }

    public long countActiveMembers(UUID organizationId) {
        return repository.countByOrganizationIdAndMembershipStatus(organizationId, MembershipStatus.ACTIVE);
    }

    public Page<OrganizationMembership> listActiveMembers(UUID organizationId, Pageable pageable) {
        return repository.findAllByOrganizationIdAndMembershipStatus(organizationId, MembershipStatus.ACTIVE, pageable);
    }

    public long countActiveOwners(UUID organizationId) {
        return repository.countByOrganizationIdAndRoleAndMembershipStatus(
                organizationId, MembershipRole.BUSINESS_OWNER, MembershipStatus.ACTIVE);
    }

    /* --------------------------------- writes --------------------------------- */

    /**
     * Strict membership creation (organization creation: the row cannot pre-exist).
     *
     * @throws DataIntegrityViolationException when the user is already a member —
     *         callers translate it into a friendly conflict
     */
    @Transactional
    public OrganizationMembership create(UUID organizationId, UUID userId, MembershipRole role) {
        OrganizationMembership membership = new OrganizationMembership();
        membership.setOrganizationId(organizationId);
        membership.setUserId(userId);
        membership.setRole(role);
        membership.setMembershipStatus(MembershipStatus.ACTIVE);
        membership.setJoinedAt(Instant.now());
        return repository.save(membership);
    }

    /**
     * Invitation acceptance path: create OR reactivate a previously
     * removed/suspended membership with the invited role. The unique constraint on
     * (organization_id, user_id) would reject a plain insert after a removal, so
     * re-joining is an explicit upsert.
     */
    @Transactional
    public OrganizationMembership join(UUID organizationId, UUID userId, MembershipRole role) {
        Optional<OrganizationMembership> existing = repository.findByOrganizationIdAndUserId(organizationId, userId);
        if (existing.isPresent()) {
            OrganizationMembership membership = existing.get();
            membership.setRole(role);
            membership.setMembershipStatus(MembershipStatus.ACTIVE);
            if (membership.getJoinedAt() == null) {
                membership.setJoinedAt(Instant.now());
            }
            return repository.save(membership);
        }
        return create(organizationId, userId, role);
    }

    /* ------------------- member management policy (rules) -------------------- */

    /**
     * Applies a role and/or status change to a member, enforcing:
     * <ol>
     *   <li><b>No self-edit:</b> the actor's own membership cannot be touched through
     *       this API — structurally blocking self-escalation. (Self-removal will get
     *       a dedicated "leave organization" flow.)</li>
     *   <li><b>No privilege escalation:</b> the actor's role must be at least the
     *       target's CURRENT role AND at least the PROSPECTIVE role — admins cannot
     *       touch owners, nobody can grant a rank above their own (only owners can
     *       make owners).</li>
     *   <li><b>Last-owner protection:</b> demoting, suspending or removing an owner
     *       is rejected when they are the only ACTIVE owner.</li>
     * </ol>
     * The coarse matrix check ({@code MEMBER_MANAGE}) was already done by the caller.
     */
    @Transactional
    public OrganizationMembership updateMember(UUID actorId, UUID organizationId, UUID memberId,
                                               MemberUpdateRequest request) {
        OrganizationMembership actor = requireActorMembership(actorId, organizationId);
        OrganizationMembership target = getInOrg(organizationId, memberId);

        if (target.getUserId().equals(actorId)) {
            throw new ForbiddenException("You cannot modify your own membership.");
        }

        MembershipRole prospectiveRole = request.role() != null ? request.role() : target.getRole();
        boolean outranksTarget = actor.getRole().isAtLeast(target.getRole());
        boolean mayGrantRole = actor.getRole().isAtLeast(prospectiveRole);
        if (!outranksTarget || !mayGrantRole) {
            throw ForbiddenException.defaultDenied();
        }

        if (request.status() == MembershipStatus.REMOVED) {
            throw new BadRequestException("Use the DELETE endpoint to remove a member.");
        }

        boolean changesRole = request.role() != null && request.role() != target.getRole();
        boolean changesStatus = request.status() != null && request.status() != target.getMembershipStatus();
        if (!changesRole && !changesStatus) {
            return target; // no-op PATCH still returns current state
        }

        boolean targetLosesOwner = target.getRole() == MembershipRole.BUSINESS_OWNER
                && (prospectiveRole != MembershipRole.BUSINESS_OWNER
                    || (request.status() != null && request.status() != MembershipStatus.ACTIVE));
        if (targetLosesOwner) {
            assertNotLastOwner(organizationId);
        }

        MembershipRole previousRole = target.getRole();
        MembershipStatus previousStatus = target.getMembershipStatus();

        if (changesRole) {
            target.setRole(request.role());
        }
        if (changesStatus) {
            target.setMembershipStatus(request.status());
        }
        OrganizationMembership saved = repository.save(target);

        if (changesRole) {
            auditService.record(actorId, organizationId, AuditAction.MEMBER_ROLE_CHANGED,
                    "membership", saved.getId(),
                    Map.of("targetUserId", saved.getUserId().toString(),
                           "previousRole", previousRole.name(),
                           "newRole", saved.getRole().name()));
        }
        if (changesStatus) {
            AuditAction action = saved.getMembershipStatus() == MembershipStatus.ACTIVE
                    ? AuditAction.MEMBER_REACTIVATED : AuditAction.MEMBER_SUSPENDED;
            auditService.record(actorId, organizationId, action,
                    "membership", saved.getId(),
                    Map.of("targetUserId", saved.getUserId().toString(),
                           "previousStatus", previousStatus.name(),
                           "newStatus", saved.getMembershipStatus().name()));
        }
        return saved;
    }

    /** Removal = status REMOVED (row retained). Same actor rules as updateMember. */
    @Transactional
    public OrganizationMembership removeMember(UUID actorId, UUID organizationId, UUID memberId) {
        OrganizationMembership actor = requireActorMembership(actorId, organizationId);
        OrganizationMembership target = getInOrg(organizationId, memberId);

        if (target.getUserId().equals(actorId)) {
            throw new ForbiddenException("You cannot remove your own membership.");
        }
        if (!actor.getRole().isAtLeast(target.getRole())) {
            throw ForbiddenException.defaultDenied();
        }

        if (target.getMembershipStatus() == MembershipStatus.REMOVED) {
            return target; // idempotent
        }
        if (target.getRole() == MembershipRole.BUSINESS_OWNER) {
            assertNotLastOwner(organizationId);
        }

        target.setMembershipStatus(MembershipStatus.REMOVED);
        OrganizationMembership saved = repository.save(target);

        auditService.record(actorId, organizationId, AuditAction.MEMBER_REMOVED,
                "membership", saved.getId(),
                Map.of("targetUserId", saved.getUserId().toString(),
                       "previousRole", saved.getRole().name()));
        return saved;
    }

    /** Loads a membership by id WITHIN an organization (a bare id is never trusted). */
    public OrganizationMembership getInOrg(UUID organizationId, UUID memberId) {
        return repository.findById(memberId)
                .filter(m -> m.getOrganizationId().equals(organizationId))
                .orElseThrow(() -> NotFoundException.resource("Member"));
    }

    /* ------------------------------ private rules ----------------------------- */

    private OrganizationMembership requireActorMembership(UUID actorId, UUID organizationId) {
        return findActive(organizationId, actorId)
                .orElseThrow(() -> NotFoundException.resource("Organization"));
    }

    private void assertNotLastOwner(UUID organizationId) {
        if (countActiveOwners(organizationId) <= 1) {
            throw new ConflictException("An organization must always keep at least one active owner.");
        }
    }
}
