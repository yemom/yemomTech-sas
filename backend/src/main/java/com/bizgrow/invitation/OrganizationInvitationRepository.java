package com.bizgrow.invitation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationInvitationRepository extends JpaRepository<OrganizationInvitation, UUID> {

    Optional<OrganizationInvitation> findByInvitationTokenHash(String tokenHash);

    Page<OrganizationInvitation> findAllByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<OrganizationInvitation> findByOrganizationIdAndInvitedEmailAndAcceptedAtIsNull(UUID organizationId, String invitedEmail);
}
