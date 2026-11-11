package com.YemomBizGrow.marketing.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CampaignPerformanceResponse(
        UUID campaignId,
        String campaignName,
        BigDecimal budgetEtb,
        BigDecimal spentEtb,
        long totalAttributedLeads,
        long wonLeads,
        BigDecimal wonPipelineValueEtb,
        BigDecimal totalPipelineValueEtb,
        BigDecimal costPerLeadEtb,
        BigDecimal returnOnAdSpendRatio
) {}
