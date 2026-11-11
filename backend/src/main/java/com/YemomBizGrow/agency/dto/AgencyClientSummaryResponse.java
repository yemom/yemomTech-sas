package com.YemomBizGrow.agency.dto;

import com.YemomBizGrow.agency.AgencyAccessLevel;
import com.YemomBizGrow.agency.AgencyRelationshipStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AgencyClientSummaryResponse(
        UUID relationshipId,
        UUID clientOrganizationId,
        String clientName,
        String clientSlug,
        String clientIndustry,
        AgencyRelationshipStatus status,
        AgencyAccessLevel accessLevel,
        long websiteCount,
        Integer latestSeoScore,
        long totalLeads,
        BigDecimal totalPipelineValueEtb,
        long activeCampaignsCount,
        Instant linkedAt
) {}
