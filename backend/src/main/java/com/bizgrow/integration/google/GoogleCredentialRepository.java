package com.YemomBizGrow.integration.google;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GoogleCredentialRepository extends JpaRepository<GoogleCredential, UUID> {

    Optional<GoogleCredential> findByOrganizationIdAndWebsiteId(UUID organizationId, UUID websiteId);

    boolean existsByOrganizationIdAndWebsiteId(UUID organizationId, UUID websiteId);

    void deleteByOrganizationIdAndWebsiteId(UUID organizationId, UUID websiteId);
}
