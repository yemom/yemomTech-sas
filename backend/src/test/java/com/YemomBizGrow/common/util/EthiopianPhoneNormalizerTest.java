package com.YemomBizGrow.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class EthiopianPhoneNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "0911223344",
            "+251911223344",
            "251911223344",
            "911223344",
            "09 11 22 33 44",
            "09-11-22-33-44",
            "+251 911 22 33 44",
            "(+251) 911-223344"
    })
    @DisplayName("Normalizes various Ethio Telecom mobile formats to canonical +251911223344")
    void testNormalizeEthioTelecom(String input) {
        assertThat(EthiopianPhoneNormalizer.normalize(input)).isEqualTo("+251911223344");
        assertThat(EthiopianPhoneNormalizer.isValid(input)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0712345678",
            "+251712345678",
            "251712345678",
            "712345678",
            "07 12 34 56 78",
            "+251 712 34 56 78"
    })
    @DisplayName("Normalizes Safaricom Ethiopia mobile formats to canonical +251712345678")
    void testNormalizeSafaricom(String input) {
        assertThat(EthiopianPhoneNormalizer.normalize(input)).isEqualTo("+251712345678");
        assertThat(EthiopianPhoneNormalizer.isValid(input)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0811223344",     // invalid prefix
            "09112233",       // too short
            "091122334455",   // too long
            "+14155552671",   // US number
            "+447911123456",  // UK number
            "abc0911223344",  // letters
            ""
    })
    @DisplayName("Rejects non-Ethiopian or malformed phone numbers")
    void testRejectInvalid(String input) {
        assertThat(EthiopianPhoneNormalizer.normalize(input)).isNull();
        assertThat(EthiopianPhoneNormalizer.isValid(input)).isFalse();
    }

    @Test
    @DisplayName("formatLocal produces readable Ethiopian grouped representation")
    void testFormatLocal() {
        String formatted = EthiopianPhoneNormalizer.formatLocal("0911223344");
        assertThat(formatted).isEqualTo("+251 911 22 33 44");
    }
}
