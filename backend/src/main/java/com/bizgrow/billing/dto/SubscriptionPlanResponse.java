package com.YemomBizGrow.billing.dto;

import com.YemomBizGrow.billing.BillingInterval;
import com.YemomBizGrow.billing.SubscriptionPlan;

import java.math.BigDecimal;
import java.util.UUID;

public record SubscriptionPlanResponse(
        UUID id,
        String name,
        String slug,
        String description,
        BigDecimal priceEtb,
        BillingInterval billingInterval,
        int maxWebsites,
        int maxMembers,
        int maxLeads,
        int maxCampaigns,
        boolean hasAiInsights,
        boolean hasAgencyWorkspace,
        boolean isActive,
        int sortOrder
) {
    public static SubscriptionPlanResponse from(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getSlug(),
                plan.getDescription(),
                plan.getPriceEtb(),
                plan.getBillingInterval(),
                plan.getMaxWebsites(),
                plan.getMaxMembers(),
                plan.getMaxLeads(),
                plan.getMaxCampaigns(),
                plan.isHasAiInsights(),
                plan.isHasAgencyWorkspace(),
                plan.isActive(),
                plan.getSortOrder()
        );
    }
}
