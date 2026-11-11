package com.YemomBizGrow.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Produces URL-safe slugs for organization addresses (/workspace is keyed by id,
 * but slugs are the human-facing stable identifier and appear in future links).
 */
public final class SlugGenerator {

    private static final int MAX_SLUG_LENGTH = 60;

    private SlugGenerator() {
    }

    /**
     * "Example Digital Agency" -> "example-digital-agency".
     * Returns {@code "organization"} when the input has no usable characters;
     * callers resolve uniqueness by appending a short random suffix.
     */
    public static String slugify(String input) {
        if (input == null) {
            return "organization";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .replaceAll("-{2,}", "-");
        if (normalized.isEmpty()) {
            return "organization";
        }
        return normalized.length() > MAX_SLUG_LENGTH
                ? normalized.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "")
                : normalized;
    }
}
