package com.YemomBizGrow.common.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Classifies the active Spring profiles into exactly one security mode.
 *
 * <p>Why this exists: {@link StartupValidator} relaxes a handful of checks
 * (placeholder JWT secret, publicly known AES key, raw invitation tokens) for
 * local development. A naive "is <em>any</em> dev/test profile active" test
 * made a <strong>mixed {@code prod,dev} deployment silently lenient</strong> —
 * a deployment that looks production-grade on paper but boots with
 * development-grade secrets. The security boundary must be decided by the
 * <em>set</em> of active profiles, never by their union of relaxed signals.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Production profile ({@code prod} / {@code production}) active
 *       &rarr; {@link #STRICT}. Production is always strict, even if other
 *       profiles are also active.</li>
 *   <li>Only development profiles ({@code dev} / {@code test} / {@code local})
 *       active &rarr; {@link #LENIENT}.</li>
 *   <li>Production <em>and</em> development profile active together &rarr;
 *       rejected outright. This is a deployment mistake, not something to
 *       paper over at runtime.</li>
 *   <li>Anything else (unknown or no profile) &rarr; {@link #STRICT}.</li>
 * </ul>
 */
public enum SecurityProfileMode {

    /** Local development/test: relaxed checks are permitted. */
    LENIENT,

    /** Production (or unrecognised profile): every check is enforced. */
    STRICT;

    /** Profile names that mean "this is a production deployment". */
    public static final Set<String> PRODUCTION_PROFILES = Set.of("prod", "production");

    /** Profile names that mean "local development or automated test". */
    public static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "test", "local");

    /**
     * Resolve the security mode for the given active profiles.
     *
     * @param profiles the active Spring profile names (case-insensitive, may be empty)
     * @return {@link #STRICT} or {@link #LENIENT}
     * @throws IllegalStateException when production and development profiles are mixed
     */
    public static SecurityProfileMode resolve(String... profiles) {
        Set<String> normalized = normalize(profiles);

        boolean production = normalized.stream().anyMatch(PRODUCTION_PROFILES::contains);
        boolean development = normalized.stream().anyMatch(DEVELOPMENT_PROFILES::contains);

        if (production && development) {
            throw new IllegalStateException(
                    "Mixed Spring profiles are not allowed: " + normalized
                            + " combines a production profile (" + String.join(", ", productionProfiles(normalized))
                            + ") with a development/test profile (" + String.join(", ", developmentProfiles(normalized))
                            + "). Activate exactly one environment — production deployments must never inherit "
                            + "development profile defaults.");
        }

        if (production) {
            return STRICT;
        }
        if (development) {
            return LENIENT;
        }
        // Unknown or empty profile set: stay strict. A profile we do not
        // understand must never unlock relaxed validation.
        return STRICT;
    }

    /** True when relaxed checks are permitted. */
    public boolean isLenient() {
        return this == LENIENT;
    }

    private static Set<String> normalize(String... profiles) {
        if (profiles == null || profiles.length == 0) {
            return Set.of();
        }
        return Arrays.stream(profiles)
                .filter(p -> p != null && !p.isBlank())
                .map(p -> p.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> productionProfiles(Set<String> normalized) {
        return normalized.stream().filter(PRODUCTION_PROFILES::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> developmentProfiles(Set<String> normalized) {
        return normalized.stream().filter(DEVELOPMENT_PROFILES::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
