package com.bizgrow.website;

import com.bizgrow.common.response.PageResponse;
import com.bizgrow.common.security.CurrentUser;
import com.bizgrow.common.util.Pagination;
import com.bizgrow.website.dto.CreateWebsiteRequest;
import com.bizgrow.website.dto.UpdateWebsiteRequest;
import com.bizgrow.website.dto.WebsiteResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Website registration endpoints (first vertical slice).
 * All routes are organization-scoped; the service enforces membership,
 * permission, active-organization, validation and per-tenant uniqueness.
 * Registration performs no outbound HTTP request (no SSRF surface).
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/websites")
@Tag(name = "Websites", description = "Register and manage business websites")
public class WebsiteController {

    private final WebsiteService websiteService;

    public WebsiteController(WebsiteService websiteService) {
        this.websiteService = websiteService;
    }

    @PostMapping
    public ResponseEntity<WebsiteResponse> create(@PathVariable UUID organizationId,
                                                  @Valid @RequestBody CreateWebsiteRequest request) {
        WebsiteResponse created = websiteService.create(CurrentUser.require().id(), organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<WebsiteResponse>> list(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = websiteService.list(CurrentUser.require().id(), organizationId, Pagination.of(page, size));
        return ResponseEntity.ok(PageResponse.from(result));
    }

    @GetMapping("/{websiteId}")
    public ResponseEntity<WebsiteResponse> get(@PathVariable UUID organizationId,
                                               @PathVariable UUID websiteId) {
        return ResponseEntity.ok(websiteService.get(CurrentUser.require().id(), organizationId, websiteId));
    }

    @PatchMapping("/{websiteId}")
    public ResponseEntity<WebsiteResponse> update(@PathVariable UUID organizationId,
                                                  @PathVariable UUID websiteId,
                                                  @Valid @RequestBody UpdateWebsiteRequest request) {
        return ResponseEntity.ok(
                websiteService.update(CurrentUser.require().id(), organizationId, websiteId, request));
    }

    /** Soft deactivation: row retained, status flips to INACTIVE. Idempotent. */
    @DeleteMapping("/{websiteId}")
    public ResponseEntity<WebsiteResponse> deactivate(@PathVariable UUID organizationId,
                                                      @PathVariable UUID websiteId) {
        return ResponseEntity.ok(websiteService.deactivate(CurrentUser.require().id(), organizationId, websiteId));
    }
}
