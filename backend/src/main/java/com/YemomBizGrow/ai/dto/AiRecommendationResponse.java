package com.YemomBizGrow.ai.dto;

import com.YemomBizGrow.ai.AiRecommendation;
import com.YemomBizGrow.ai.RecommendationCategory;
import com.YemomBizGrow.ai.RecommendationPriority;
import com.YemomBizGrow.ai.RecommendationStatus;

import java.time.Instant;
import java.util.UUID;

public record AiRecommendationResponse(
        UUID id,
        UUID organizationId,
        UUID websiteId,
        RecommendationCategory category,
        RecommendationPriority priority,
        String title,
        String problem,
        String evidence,
        String expectedBenefit,
        String actionRecommendation,
        RecommendationStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static AiRecommendationResponse from(AiRecommendation entity) {
        return new AiRecommendationResponse(
                entity.getId(),
                entity.getOrganizationId(),
                entity.getWebsiteId(),
                entity.getCategory(),
                entity.getPriority(),
                entity.getTitle(),
                entity.getProblem(),
                entity.getEvidence(),
                entity.getExpectedBenefit(),
                entity.getActionRecommendation(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
