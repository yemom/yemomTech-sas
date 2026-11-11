package com.YemomBizGrow.integration.google;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GoogleCredentialRepository extends JpaRepository<GoogleCredential, UUID> {

    Optional<GoogleCredential> findByOrganizationIdAndWebsiteId(UUID organizationId, UUID websiteId);

    boolean existsByOrganizationIdAndWebsiteId(UUID organizationId, UUID websiteId);

    void deleteByOrganizationIdAndWebsiteId(UUID organizationId, UUID websiteId);

    @Modifying
    @Query("UPDATE GoogleCredential c SET c.oauthState = NULL, c.oauthStateExpiresAt = NULL " +
           "WHERE c.organizationId = :orgId AND c.websiteId = :websiteId " +
           "AND c.oauthState = :state AND c.oauthStateExpiresAt > :now")
    int consumeOauthState(@Param("orgId") UUID orgId,
                          @Param("websiteId") UUID websiteId,
                          @Param("state") String state,
                          @Param("now") Instant now);
}
