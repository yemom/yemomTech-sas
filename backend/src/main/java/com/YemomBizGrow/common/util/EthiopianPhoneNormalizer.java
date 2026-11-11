package com.YemomBizGrow.common.util;

import java.util.regex.Pattern;

/**
 * Normalizes and validates Ethiopian telephone numbers.
 * Supported carriers:
 * - Ethio Telecom (mobile: 09..., fixed lines: 011...)
 * - Safaricom Ethiopia (mobile: 07...)
 * Canonical output: E.164 (+251XXXXXXXXX)
 */
public final class EthiopianPhoneNormalizer {

    private static final Pattern CLEAN_PATTERN = Pattern.compile("[\\s\\-\\(\\)\\.]+");
    private static final Pattern ETHIOPIAN_PHONE_REGEX = Pattern.compile("^\\+251[79]\\d{8}$");

    private EthiopianPhoneNormalizer() {
    }

    /**
     * Cleans and normalizes an Ethiopian phone number to canonical E.164 (+251XXXXXXXXX).
     * Returns null if input is null, blank, or invalid.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String cleaned = CLEAN_PATTERN.matcher(raw.trim()).replaceAll("");

        // Handle 09XXXXXXXX or 07XXXXXXXX (10 digits starting with 0)
        if (cleaned.startsWith("0") && cleaned.length() == 10) {
            cleaned = "+251" + cleaned.substring(1);
        }
        // Handle 251XXXXXXXXX (12 digits starting with 251)
        else if (cleaned.startsWith("251") && cleaned.length() == 12) {
            cleaned = "+" + cleaned;
        }
        // Handle 9XXXXXXXX or 7XXXXXXXX (9 digits without leading 0)
        else if ((cleaned.startsWith("9") || cleaned.startsWith("7")) && cleaned.length() == 9) {
            cleaned = "+251" + cleaned;
        }

        if (ETHIOPIAN_PHONE_REGEX.matcher(cleaned).matches()) {
            return cleaned;
        }

        return null;
    }

    /**
     * Checks whether the phone number is a valid Ethiopian mobile number.
     */
    public static boolean isValid(String raw) {
        return normalize(raw) != null;
    }

    /**
     * Formats canonical E.164 for display in Ethiopian local style: "0911 22 33 44" or "+251 911 22 33 44"
     */
    public static String formatLocal(String canonicalOrRaw) {
        String normalized = normalize(canonicalOrRaw);
        if (normalized == null) {
            return canonicalOrRaw;
        }
        // normalized is +251XXXXXXXXX (13 chars)
        // +251 9XX XX XX XX
        return normalized.substring(0, 4) + " " +
                normalized.substring(4, 7) + " " +
                normalized.substring(7, 9) + " " +
                normalized.substring(9, 11) + " " +
                normalized.substring(11);
    }
}
