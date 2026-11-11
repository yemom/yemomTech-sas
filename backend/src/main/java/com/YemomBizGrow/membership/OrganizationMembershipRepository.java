package com.YemomBizGrow.membership;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByOrganizationIdAndUserIdAndMembershipStatus(UUID organizationId, UUID userId,
                                                               MembershipStatus status);

    long countByOrganizationIdAndRoleAndMembershipStatus(UUID organizationId, MembershipRole role,
                                                         MembershipStatus status);

    long countByOrganizationIdAndMembershipStatus(UUID organizationId, MembershipStatus status);

    Page<OrganizationMembership> findAllByOrganizationIdAndMembershipStatus(UUID organizationId,
                                                                            MembershipStatus status,
                                                                            Pageable pageable);

    /**
     * Organizations a user belongs to (tenant-safe listing source): filtered to
     * ACTIVE memberships and joined against organizations in the service layer.
     */
    List<OrganizationMembership> findByUserIdAndMembershipStatus(UUID userId, MembershipStatus status);

    /** True when the user has at least one active membership anywhere (drives /auth/me). */
    boolean existsByUserIdAndMembershipStatus(UUID userId, MembershipStatus status);
}
