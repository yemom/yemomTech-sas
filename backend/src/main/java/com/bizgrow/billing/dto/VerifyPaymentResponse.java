package com.YemomBizGrow.billing.dto;

import com.YemomBizGrow.billing.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record VerifyPaymentResponse(
        String txRef,
        PaymentStatus status,
        BigDecimal amountEtb,
        String currency,
        String planName,
        Instant verifiedAt,
        String message
) {
}
