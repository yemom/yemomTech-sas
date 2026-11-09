package com.YemomBizGrow.agency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgencyClientRelationshipRepository extends JpaRepository<AgencyClientRelationship, UUID> {

    List<AgencyClientRelationship> findByAgencyOrganizationId(UUID agencyOrganizationId);

    List<AgencyClientRelationship> findByClientOrganizationId(UUID clientOrganizationId);

    Optional<AgencyClientRelationship> findByIdAndAgencyOrganizationId(UUID id, UUID agencyOrganizationId);

    Optional<AgencyClientRelationship> findByAgencyOrganizationIdAndClientOrganizationId(UUID agencyOrganizationId, UUID clientOrganizationId);

    boolean existsByAgencyOrganizationIdAndClientOrganizationId(UUID agencyOrganizationId, UUID clientOrganizationId);
}
