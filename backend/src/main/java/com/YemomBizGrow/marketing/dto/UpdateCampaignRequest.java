package com.YemomBizGrow.marketing.dto;

import com.YemomBizGrow.marketing.CampaignChannel;
import com.YemomBizGrow.marketing.CampaignObjective;
import com.YemomBizGrow.marketing.CampaignStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateCampaignRequest(
        @Size(max = 150, message = "Campaign name must not exceed 150 characters")
        String name,

        String description,

        CampaignObjective objective,

        CampaignStatus status,

        CampaignChannel channel,

        @DecimalMin(value = "0.0", inclusive = true, message = "Budget must be non-negative")
        BigDecimal budgetEtb,

        @DecimalMin(value = "0.0", inclusive = true, message = "Spent must be non-negative")
        BigDecimal spentEtb,

        LocalDate startDate,
        LocalDate endDate,

        @Size(max = 100)
        String utmSource,

        @Size(max = 100)
        String utmMedium,

        @Size(max = 100)
        String utmCampaign,

        @Size(max = 100)
        String utmTerm,

        @Size(max = 100)
        String utmContent
) {}
