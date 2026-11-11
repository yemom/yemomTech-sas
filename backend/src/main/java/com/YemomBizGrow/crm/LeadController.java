package com.YemomBizGrow.crm;

import com.YemomBizGrow.common.response.PageResponse;
import com.YemomBizGrow.common.security.CurrentUser;
import com.YemomBizGrow.common.util.Pagination;
import com.YemomBizGrow.crm.dto.AddActivityRequest;
import com.YemomBizGrow.crm.dto.CreateLeadRequest;
import com.YemomBizGrow.crm.dto.LeadActivityResponse;
import com.YemomBizGrow.crm.dto.LeadResponse;
import com.YemomBizGrow.crm.dto.PipelineSummaryResponse;
import com.YemomBizGrow.crm.dto.TransitionStageRequest;
import com.YemomBizGrow.crm.dto.UpdateLeadRequest;
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

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/leads")
@Tag(name = "CRM Leads", description = "Multi-tenant CRM, lead capture and sales pipeline management")
public class LeadController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    public ResponseEntity<LeadResponse> createLead(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreateLeadRequest request) {
        UUID actorId = CurrentUser.require().id();
        LeadResponse created = leadService.createLead(actorId, organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<LeadResponse>> listLeads(
            @PathVariable UUID organizationId,
            @RequestParam(required = false) LeadStage stage,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID actorId = CurrentUser.require().id();
        PageResponse<LeadResponse> leads = leadService.listLeads(
                actorId, organizationId, stage, search, Pagination.of(page, size));
        return ResponseEntity.ok(leads);
    }

    @GetMapping("/pipeline-summary")
    public ResponseEntity<PipelineSummaryResponse> getPipelineSummary(
            @PathVariable UUID organizationId) {
        UUID actorId = CurrentUser.require().id();
        PipelineSummaryResponse summary = leadService.getPipelineSummary(actorId, organizationId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/{leadId}")
    public ResponseEntity<LeadResponse> getLead(
            @PathVariable UUID organizationId,
            @PathVariable UUID leadId) {
        UUID actorId = CurrentUser.require().id();
        LeadResponse lead = leadService.getLead(actorId, organizationId, leadId);
        return ResponseEntity.ok(lead);
    }

    @PatchMapping("/{leadId}")
    public ResponseEntity<LeadResponse> updateLead(
            @PathVariable UUID organizationId,
            @PathVariable UUID leadId,
            @Valid @RequestBody UpdateLeadRequest request) {
        UUID actorId = CurrentUser.require().id();
        LeadResponse updated = leadService.updateLead(actorId, organizationId, leadId, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{leadId}/stage")
    public ResponseEntity<LeadResponse> transitionStage(
            @PathVariable UUID organizationId,
            @PathVariable UUID leadId,
            @Valid @RequestBody TransitionStageRequest request) {
        UUID actorId = CurrentUser.require().id();
        LeadResponse updated = leadService.transitionStage(actorId, organizationId, leadId, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{leadId}")
    public ResponseEntity<Void> archiveLead(
            @PathVariable UUID organizationId,
            @PathVariable UUID leadId) {
        UUID actorId = CurrentUser.require().id();
        leadService.archiveLead(actorId, organizationId, leadId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{leadId}/activities")
    public ResponseEntity<PageResponse<LeadActivityResponse>> listActivities(
            @PathVariable UUID organizationId,
            @PathVariable UUID leadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID actorId = CurrentUser.require().id();
        PageResponse<LeadActivityResponse> activities = leadService.listActivities(
                actorId, organizationId, leadId, Pagination.of(page, size));
        return ResponseEntity.ok(activities);
    }

    @PostMapping("/{leadId}/activities")
    public ResponseEntity<LeadActivityResponse> addActivity(
            @PathVariable UUID organizationId,
            @PathVariable UUID leadId,
            @Valid @RequestBody AddActivityRequest request) {
        UUID actorId = CurrentUser.require().id();
        LeadActivityResponse activity = leadService.addActivity(actorId, organizationId, leadId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(activity);
    }
}
