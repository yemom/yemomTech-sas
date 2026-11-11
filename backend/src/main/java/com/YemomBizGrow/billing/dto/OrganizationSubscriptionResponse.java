package com.YemomBizGrow.billing.dto;

import com.YemomBizGrow.billing.OrganizationSubscription;
import com.YemomBizGrow.billing.SubscriptionStatus;

import java.time.Instant;
import java.util.UUID;

public record OrganizationSubscriptionResponse(
        UUID id,
        UUID organizationId,
        SubscriptionPlanResponse plan,
        SubscriptionStatus status,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        Instant cancelledAt,
        boolean cancelAtPeriodEnd,
        Instant createdAt
) {
    public static OrganizationSubscriptionResponse from(OrganizationSubscription sub) {
        return new OrganizationSubscriptionResponse(
                sub.getId(),
                sub.getOrganizationId(),
                SubscriptionPlanResponse.from(sub.getPlan()),
                sub.getStatus(),
                sub.getCurrentPeriodStart(),
                sub.getCurrentPeriodEnd(),
                sub.getCancelledAt(),
                sub.isCancelAtPeriodEnd(),
                sub.getCreatedAt()
        );
    }
}
