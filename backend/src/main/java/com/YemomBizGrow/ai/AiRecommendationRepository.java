package com.YemomBizGrow.ai;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, UUID> {

    List<AiRecommendation> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, RecommendationStatus status);

    Page<AiRecommendation> findByOrganizationIdOrderByCreatedAtDesc(
            UUID organizationId, Pageable pageable);

    Page<AiRecommendation> findByOrganizationIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, RecommendationStatus status, Pageable pageable);

    Optional<AiRecommendation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationIdAndStatus(UUID organizationId, RecommendationStatus status);
}
