package com.YemomBizGrow.integration.google;

import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.integration.google.dto.AnalyticsOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/analytics")
@Tag(name = "Analytics", description = "Performance metrics and search analytics for organization websites")
public class AnalyticsController {

    private final GoogleIntegrationService googleIntegrationService;

    public AnalyticsController(GoogleIntegrationService googleIntegrationService) {
        this.googleIntegrationService = googleIntegrationService;
    }

    @GetMapping("/overview")
    @Operation(summary = "Get aggregated analytics and search console performance overview")
    public AnalyticsOverviewResponse getOverview(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) UUID websiteId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID actorId = CurrentUser.require().id();
        return googleIntegrationService.getOverview(
                actorId,
                organizationId,
                websiteId,
                startDate,
                endDate);
    }
}
