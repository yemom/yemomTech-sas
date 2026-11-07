package com.YemomBizGrow.audit;

import com.YemomBizGrow.audit.dto.AuditLogResponse;
import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.common.util.Pagination;
import com.YemomBizGrow.membership.AccessControlService;
import com.YemomBizGrow.membership.Permission;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Audit log browsing for organizations.
 * Requires ORG_VIEW permission. Enforces tenant boundary through AccessControlService.
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/audit-logs")
@Tag(name = "Audit Logs", description = "Query organization-level audit trails")
public class AuditLogController {

    private final AuditService auditService;
    private final AccessControlService accessControl;

    public AuditLogController(AuditService auditService, AccessControlService accessControl) {
        this.auditService = auditService;
        this.accessControl = accessControl;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> list(
            @PathVariable UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        accessControl.requirePermission(CurrentUser.require().id(), organizationId, Permission.ORG_VIEW);
        return ResponseEntity.ok(auditService.listByOrganization(organizationId, Pagination.of(page, size)));
    }
}
