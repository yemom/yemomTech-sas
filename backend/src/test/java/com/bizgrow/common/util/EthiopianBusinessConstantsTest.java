package com.YemomBizGrow.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EthiopianBusinessConstantsTest {

    @Test
    @DisplayName("Calculates 15% VAT accurately")
    void testCalculateVat() {
        BigDecimal subtotal = new BigDecimal("1000.00");
        BigDecimal vat = EthiopianBusinessConstants.calculateVat(subtotal);
        assertThat(vat).isEqualByComparingTo(new BigDecimal("150.00"));

        BigDecimal total = EthiopianBusinessConstants.calculateTotalWithVat(subtotal);
        assertThat(total).isEqualByComparingTo(new BigDecimal("1150.00"));
    }

    @Test
    @DisplayName("Handles zero and null subtotal safely")
    void testVatNullAndZero() {
        assertThat(EthiopianBusinessConstants.calculateVat(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(EthiopianBusinessConstants.calculateTotalWithVat(null)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(EthiopianBusinessConstants.calculateVat(BigDecimal.ZERO)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Has comprehensive list of Ethiopian industries")
    void testIndustries() {
        assertThat(EthiopianBusinessConstants.INDUSTRIES)
                .isNotEmpty()
                .contains("Real Estate & Property", "Import & Export", "Education & Training", "Technology & Software");
    }
}
