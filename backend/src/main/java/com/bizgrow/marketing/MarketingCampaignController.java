package com.YemomBizGrow.marketing;

import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.marketing.dto.CampaignPerformanceResponse;
import com.YemomBizGrow.marketing.dto.CampaignResponse;
import com.YemomBizGrow.marketing.dto.CreateCampaignRequest;
import com.YemomBizGrow.marketing.dto.UpdateCampaignRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/campaigns")
public class MarketingCampaignController {

    private final MarketingCampaignService campaignService;

    public MarketingCampaignController(MarketingCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public ResponseEntity<CampaignResponse> createCampaign(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateCampaignRequest request
    ) {
        UUID actorUserId = CurrentUser.require().id();
        CampaignResponse response = campaignService.createCampaign(actorUserId, organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<CampaignResponse>> listCampaigns(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) CampaignStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID actorUserId = CurrentUser.require().id();
        PageResponse<CampaignResponse> response = campaignService.listCampaigns(actorUserId, organizationId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> getCampaign(
            @PathVariable UUID organizationId,
            @PathVariable UUID campaignId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        CampaignResponse response = campaignService.getCampaign(actorUserId, organizationId, campaignId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{campaignId}")
    public ResponseEntity<CampaignResponse> updateCampaign(
            @PathVariable UUID organizationId,
            @PathVariable UUID campaignId,
            @Valid @RequestBody UpdateCampaignRequest request
    ) {
        UUID actorUserId = CurrentUser.require().id();
        CampaignResponse response = campaignService.updateCampaign(actorUserId, organizationId, campaignId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{campaignId}")
    public ResponseEntity<Void> deleteCampaign(
            @PathVariable UUID organizationId,
            @PathVariable UUID campaignId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        campaignService.deleteCampaign(actorUserId, organizationId, campaignId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{campaignId}/performance")
    public ResponseEntity<CampaignPerformanceResponse> getCampaignPerformance(
            @PathVariable UUID organizationId,
            @PathVariable UUID campaignId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        CampaignPerformanceResponse response = campaignService.getCampaignPerformance(actorUserId, organizationId, campaignId);
        return ResponseEntity.ok(response);
    }
}
