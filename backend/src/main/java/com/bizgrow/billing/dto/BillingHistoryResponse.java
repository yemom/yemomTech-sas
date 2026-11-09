package com.YemomBizGrow.billing.dto;

import com.YemomBizGrow.billing.BillingEventType;
import com.YemomBizGrow.billing.BillingHistory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BillingHistoryResponse(
        UUID id,
        UUID organizationId,
        UUID transactionId,
        UUID subscriptionId,
        BillingEventType eventType,
        String description,
        BigDecimal amountEtb,
        Instant createdAt
) {
    public static BillingHistoryResponse from(BillingHistory h) {
        return new BillingHistoryResponse(
                h.getId(),
                h.getOrganizationId(),
                h.getTransactionId(),
                h.getSubscriptionId(),
                h.getEventType(),
                h.getDescription(),
                h.getAmountEtb(),
                h.getCreatedAt()
        );
    }
}
