package com.YemomBizGrow.marketing;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MarketingCampaignRepository extends JpaRepository<MarketingCampaign, UUID> {

    Page<MarketingCampaign> findByOrganizationId(UUID organizationId, Pageable pageable);

    Page<MarketingCampaign> findByOrganizationIdAndStatus(UUID organizationId, CampaignStatus status, Pageable pageable);

    Optional<MarketingCampaign> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<MarketingCampaign> findByOrganizationIdAndUtmCampaign(UUID organizationId, String utmCampaign);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, CampaignStatus status);
}
