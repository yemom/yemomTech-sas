package com.YemomBizGrow.seo;

import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.common.util.Pagination;
import com.YemomBizGrow.seo.dto.SeoAuditResponse;
import com.YemomBizGrow.seo.dto.SeoCrawledPageResponse;
import com.YemomBizGrow.seo.dto.SeoIssueResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/websites/{websiteId}/audits")
@Tag(name = "SEO Audits", description = "Trigger and inspect website SEO audits, crawled pages, and issues")
public class SeoAuditController {

    private final SeoAuditService auditService;

    public SeoAuditController(SeoAuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<SeoAuditResponse> triggerAudit(
            @PathVariable UUID organizationId,
            @PathVariable UUID websiteId) {
        UUID actorId = CurrentUser.require().id();
        SeoAuditResponse response = auditService.triggerAudit(actorId, organizationId, websiteId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<SeoAuditResponse>> listAudits(
            @PathVariable UUID organizationId,
            @PathVariable UUID websiteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID actorId = CurrentUser.require().id();
        PageResponse<SeoAuditResponse> response = auditService.listAudits(actorId, organizationId, websiteId, Pagination.of(page, size));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{auditId}")
    public ResponseEntity<SeoAuditResponse> getAudit(
            @PathVariable UUID organizationId,
            @PathVariable UUID websiteId,
            @PathVariable UUID auditId) {
        UUID actorId = CurrentUser.require().id();
        SeoAuditResponse response = auditService.getAudit(actorId, organizationId, websiteId, auditId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{auditId}/issues")
    public ResponseEntity<PageResponse<SeoIssueResponse>> listIssues(
            @PathVariable UUID organizationId,
            @PathVariable UUID websiteId,
            @PathVariable UUID auditId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID actorId = CurrentUser.require().id();
        PageResponse<SeoIssueResponse> response = auditService.listIssues(
                actorId, organizationId, websiteId, auditId, severity, category, Pagination.of(page, size));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{auditId}/pages")
    public ResponseEntity<PageResponse<SeoCrawledPageResponse>> listCrawledPages(
            @PathVariable UUID organizationId,
            @PathVariable UUID websiteId,
            @PathVariable UUID auditId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        UUID actorId = CurrentUser.require().id();
        PageResponse<SeoCrawledPageResponse> response = auditService.listCrawledPages(
                actorId, organizationId, websiteId, auditId, Pagination.of(page, size));
        return ResponseEntity.ok(response);
    }
}
