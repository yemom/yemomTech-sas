package com.YemomBizGrow.billing.dto;

import com.YemomBizGrow.billing.PaymentProvider;
import com.YemomBizGrow.billing.PaymentStatus;
import com.YemomBizGrow.billing.PaymentTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentTransactionResponse(
        UUID id,
        UUID organizationId,
        String planName,
        String txRef,
        String chapaTransactionId,
        BigDecimal amountEtb,
        String currency,
        PaymentStatus status,
        PaymentProvider provider,
        Instant verifiedAt,
        String failureReason,
        Instant createdAt
) {
    public static PaymentTransactionResponse from(PaymentTransaction tx) {
        return new PaymentTransactionResponse(
                tx.getId(),
                tx.getOrganizationId(),
                tx.getPlan() != null ? tx.getPlan().getName() : "Unknown",
                tx.getTxRef(),
                tx.getChapaTransactionId(),
                tx.getAmountEtb(),
                tx.getCurrency(),
                tx.getStatus(),
                tx.getProvider(),
                tx.getVerifiedAt(),
                tx.getFailureReason(),
                tx.getCreatedAt()
        );
    }
}
