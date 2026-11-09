package com.YemomBizGrow.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Common constants, standard categories, and tax rates for Ethiopian businesses.
 */
public final class EthiopianBusinessConstants {

    private EthiopianBusinessConstants() {
    }

    public static final String CURRENCY_CODE = "ETB";
    public static final String CURRENCY_SYMBOL = "Br";

    /**
     * Standard Ethiopian Value Added Tax (VAT) rate: 15%.
     */
    public static final BigDecimal VAT_RATE = new BigDecimal("0.15");

    /**
     * Standard business industries in Ethiopia for categorization and marketing insights.
     */
    public static final List<String> INDUSTRIES = List.of(
            "Real Estate & Property",
            "Import & Export",
            "Education & Training",
            "Technology & Software",
            "Healthcare & Pharmaceuticals",
            "Hospitality & Tourism",
            "Construction & Architecture",
            "Retail & Wholesale",
            "Manufacturing & Industry",
            "Professional & Consulting Services",
            "Agriculture & Agro-processing",
            "Financial Services & Microfinance"
    );

    /**
     * Calculates 15% VAT for an amount in ETB.
     */
    public static BigDecimal calculateVat(BigDecimal subtotalEtb) {
        if (subtotalEtb == null) return BigDecimal.ZERO;
        return subtotalEtb.multiply(VAT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculates total price including 15% VAT.
     */
    public static BigDecimal calculateTotalWithVat(BigDecimal subtotalEtb) {
        if (subtotalEtb == null) return BigDecimal.ZERO;
        return subtotalEtb.add(calculateVat(subtotalEtb));
    }
}
