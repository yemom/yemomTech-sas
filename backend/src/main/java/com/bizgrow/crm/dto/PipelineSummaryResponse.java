package com.YemomBizGrow.crm.dto;

import com.YemomBizGrow.crm.LeadStage;

import java.math.BigDecimal;
import java.util.Map;

public record PipelineSummaryResponse(
        Map<LeadStage, StageMetrics> stages,
        long totalLeads,
        BigDecimal totalPipelineValueEtb
) {
    public record StageMetrics(long count, BigDecimal totalValue) {}
}
