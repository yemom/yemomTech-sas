package com.YemomBizGrow.integration.google;

import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.integration.google.dto.GoogleCallbackRequest;
import com.YemomBizGrow.integration.google.dto.GoogleConnectUrlResponse;
import com.YemomBizGrow.integration.google.dto.GoogleConnectionStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/integrations/google")
@Tag(name = "Google Integration", description = "OAuth connection and data sync for Google Analytics 4 & Search Console")
public class GoogleIntegrationController {

    private final GoogleIntegrationService googleIntegrationService;

    public GoogleIntegrationController(GoogleIntegrationService googleIntegrationService) {
        this.googleIntegrationService = googleIntegrationService;
    }

    @GetMapping("/status")
    @Operation(summary = "Check Google connection status for a website")
    public GoogleConnectionStatusResponse getStatus(
            @PathVariable UUID organizationId,
            @RequestParam UUID websiteId) {
        UUID actorId = CurrentUser.require().id();
        return googleIntegrationService.getConnectionStatus(actorId, organizationId, websiteId);
    }

    @GetMapping("/connect")
    @Operation(summary = "Generate Google OAuth2 authorization URL")
    public GoogleConnectUrlResponse connect(
            @PathVariable UUID organizationId,
            @RequestParam UUID websiteId) {
        UUID actorId = CurrentUser.require().id();
        return googleIntegrationService.generateConnectUrl(actorId, organizationId, websiteId);
    }

    @PostMapping("/callback")
    @Operation(summary = "Complete Google OAuth2 authorization flow")
    public GoogleConnectionStatusResponse callback(
            @PathVariable UUID organizationId,
            @Valid @RequestBody GoogleCallbackRequest request) {
        UUID actorId = CurrentUser.require().id();
        return googleIntegrationService.handleCallback(actorId, organizationId, request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Disconnect Google account for a website")
    public void disconnect(
            @PathVariable UUID organizationId,
            @RequestParam UUID websiteId) {
        UUID actorId = CurrentUser.require().id();
        googleIntegrationService.disconnect(actorId, organizationId, websiteId);
    }

    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Trigger on-demand sync of GA4 and Search Console metrics")
    public void sync(
            @PathVariable UUID organizationId,
            @RequestParam UUID websiteId) {
        UUID actorId = CurrentUser.require().id();
        googleIntegrationService.syncAnalytics(actorId, organizationId, websiteId);
    }
}
