package com.YemomBizGrow.ai;

import com.YemomBizGrow.ai.dto.AiRecommendationResponse;
import com.YemomBizGrow.ai.dto.UpdateRecommendationStatusRequest;
import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/ai/recommendations")
@Tag(name = "AI Recommendations", description = "AI-powered growth, conversion, and SEO suggestions")
public class AiRecommendationController {

    private final AiService aiService;

    public AiRecommendationController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping
    @Operation(summary = "List AI recommendations for the organization")
    public PageResponse<AiRecommendationResponse> getRecommendations(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) RecommendationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID actorId = CurrentUser.require().id();
        return aiService.getRecommendations(actorId, organizationId, status, pageable);
    }

    @PostMapping("/generate")
    @Operation(summary = "Trigger on-demand generation of fresh AI recommendations")
    public List<AiRecommendationResponse> generate(
            @PathVariable UUID organizationId) {
        UUID actorId = CurrentUser.require().id();
        return aiService.generateRecommendations(actorId, organizationId);
    }

    @PatchMapping("/{recommendationId}/status")
    @Operation(summary = "Update recommendation status (OPEN, APPLIED, DISMISSED)")
    public AiRecommendationResponse updateStatus(
            @PathVariable UUID organizationId,
            @PathVariable UUID recommendationId,
            @Valid @RequestBody UpdateRecommendationStatusRequest request) {
        UUID actorId = CurrentUser.require().id();
        return aiService.updateStatus(actorId, organizationId, recommendationId, request.status());
    }
}
