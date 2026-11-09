package com.YemomBizGrow.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationSubscriptionRepository extends JpaRepository<OrganizationSubscription, UUID> {

    Optional<OrganizationSubscription> findByOrganizationId(UUID organizationId);
}
