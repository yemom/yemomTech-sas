package com.YemomBizGrow.ai.dto;

import com.YemomBizGrow.ai.RecommendationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateRecommendationStatusRequest(
        @NotNull(message = "Status is required")
        RecommendationStatus status
) {}
