package com.YemomBizGrow.agency.dto;

import java.math.BigDecimal;
import java.util.List;

public record AgencyOverviewResponse(
        long totalClients,
        long activeClients,
        long totalManagedWebsites,
        long totalAggregatedLeads,
        BigDecimal totalAggregatedPipelineEtb,
        double averageClientSeoScore,
        List<AgencyClientSummaryResponse> clients
) {}
