package com.YemomBizGrow.agency;

import com.YemomBizGrow.agency.dto.AgencyClientSummaryResponse;
import com.YemomBizGrow.agency.dto.AgencyOverviewResponse;
import com.YemomBizGrow.agency.dto.LinkClientRequest;
import com.YemomBizGrow.agency.dto.UpdateClientRelationshipRequest;
import com.YemomBizGrow.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/agency")
public class AgencyController {

    private final AgencyService agencyService;

    public AgencyController(AgencyService agencyService) {
        this.agencyService = agencyService;
    }

    @PostMapping("/clients")
    public ResponseEntity<AgencyClientSummaryResponse> linkClient(
            @PathVariable UUID organizationId,
            @Valid @RequestBody LinkClientRequest request
    ) {
        UUID actorUserId = CurrentUser.require().id();
        AgencyClientSummaryResponse response = agencyService.linkClient(actorUserId, organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/clients")
    public ResponseEntity<List<AgencyClientSummaryResponse>> listClients(
            @PathVariable UUID organizationId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        List<AgencyClientSummaryResponse> response = agencyService.listClients(actorUserId, organizationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overview")
    public ResponseEntity<AgencyOverviewResponse> getOverview(
            @PathVariable UUID organizationId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        AgencyOverviewResponse response = agencyService.getAgencyOverview(actorUserId, organizationId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/clients/{relationshipId}")
    public ResponseEntity<AgencyClientSummaryResponse> updateRelationship(
            @PathVariable UUID organizationId,
            @PathVariable UUID relationshipId,
            @Valid @RequestBody UpdateClientRelationshipRequest request
    ) {
        UUID actorUserId = CurrentUser.require().id();
        AgencyClientSummaryResponse response = agencyService.updateRelationship(actorUserId, organizationId, relationshipId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/clients/{relationshipId}")
    public ResponseEntity<Void> revokeRelationship(
            @PathVariable UUID organizationId,
            @PathVariable UUID relationshipId
    ) {
        UUID actorUserId = CurrentUser.require().id();
        agencyService.revokeRelationship(actorUserId, organizationId, relationshipId);
        return ResponseEntity.noContent().build();
    }
}
