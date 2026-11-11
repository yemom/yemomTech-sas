package com.YemomBizGrow.billing.dto;

import java.math.BigDecimal;

public record InitializePaymentResponse(
        String checkoutUrl,
        String txRef,
        BigDecimal amountEtb,
        String currency,
        String planName
) {
}
