package com.bizgrow.organization;

import com.bizgrow.common.security.CurrentUser;
import com.bizgrow.organization.dto.CreateOrganizationRequest;
import com.bizgrow.organization.dto.DashboardResponse;
import com.bizgrow.organization.dto.OrganizationResponse;
import com.bizgrow.organization.dto.UpdateOrganizationRequest;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant management endpoints. The actor identity always comes from the
 * authenticated principal (never from the path or body), and every tenant-scoped
 * call is authorized inside the service against the database membership row.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@Tag(name = "Organizations", description = "Tenant creation, profile, dashboard, lifecycle")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /** Any authenticated user may create an organization; they become its OWNER. */
    @PostMapping
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody CreateOrganizationRequest request) {
        OrganizationResponse created = organizationService.create(CurrentUser.require().id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Only organizations where the caller holds an ACTIVE membership. */
    @GetMapping
    public ResponseEntity<List<OrganizationResponse>> listMine() {
        return ResponseEntity.ok(organizationService.listMine(CurrentUser.require().id()));
    }

    @GetMapping("/{organizationId}")
    public ResponseEntity<OrganizationResponse> get(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(organizationService.get(CurrentUser.require().id(), organizationId));
    }

    @GetMapping("/{organizationId}/dashboard")
    public ResponseEntity<DashboardResponse> dashboard(@PathVariable UUID organizationId) {
        return ResponseEntity.ok(organizationService.dashboard(CurrentUser.require().id(), organizationId));
    }

    @PatchMapping("/{organizationId}")
    public ResponseEntity<OrganizationResponse> update(@PathVariable UUID organizationId,
                                                       @Valid @RequestBody UpdateOrganizationRequest request) {
        return ResponseEntity.ok(
                organizationService.update(CurrentUser.require().id(), organizationId, request));
    }

    /** Soft delete (ARCHIVED) — owner only, per the permission matrix. */
    @DeleteMapping("/{organizationId}")
    public ResponseEntity<Void> archive(@PathVariable UUID organizationId) {
        organizationService.archive(CurrentUser.require().id(), organizationId);
        return ResponseEntity.noContent().build();
    }
}
