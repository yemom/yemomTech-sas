package com.YemomBizGrow.marketing.dto;

import com.YemomBizGrow.marketing.CampaignChannel;
import com.YemomBizGrow.marketing.CampaignObjective;
import com.YemomBizGrow.marketing.CampaignStatus;
import com.YemomBizGrow.marketing.MarketingCampaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CampaignResponse(
        UUID id,
        UUID organizationId,
        String name,
        String description,
        CampaignObjective objective,
        CampaignStatus status,
        CampaignChannel channel,
        BigDecimal budgetEtb,
        BigDecimal spentEtb,
        LocalDate startDate,
        LocalDate endDate,
        String utmSource,
        String utmMedium,
        String utmCampaign,
        String utmTerm,
        String utmContent,
        long attributedLeadsCount,
        BigDecimal pipelineValueEtb,
        Instant createdAt,
        Instant updatedAt
) {
    public static CampaignResponse from(MarketingCampaign campaign, long attributedLeadsCount, BigDecimal pipelineValueEtb) {
        return new CampaignResponse(
                campaign.getId(),
                campaign.getOrganizationId(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getObjective(),
                campaign.getStatus(),
                campaign.getChannel(),
                campaign.getBudgetEtb(),
                campaign.getSpentEtb(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.getUtmSource(),
                campaign.getUtmMedium(),
                campaign.getUtmCampaign(),
                campaign.getUtmTerm(),
                campaign.getUtmContent(),
                attributedLeadsCount,
                pipelineValueEtb != null ? pipelineValueEtb : BigDecimal.ZERO,
                campaign.getCreatedAt(),
                campaign.getUpdatedAt()
        );
    }
}
