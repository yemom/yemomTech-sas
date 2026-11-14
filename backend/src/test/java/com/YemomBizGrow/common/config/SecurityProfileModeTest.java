package com.YemomBizGrow.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards the production security boundary.
 *
 * <p>The regression these tests exist for: a deployment running {@code prod,dev}
 * used to be treated as lenient because the check asked "is <em>any</em> dev-ish
 * profile active?". A production deployment must never inherit development-grade
 * secrets, and a mixed profile set is a deployment error, not a mode.</p>
 */
class SecurityProfileModeTest {

    @ParameterizedTest
    @DisplayName("production profiles are always strict")
    @ValueSource(strings = {"prod", "production", "PROD", "Production"})
    void productionProfilesAreStrict(String profile) {
        assertThat(SecurityProfileMode.resolve(profile)).isEqualTo(SecurityProfileMode.STRICT);
    }

    @ParameterizedTest
    @DisplayName("development-only profiles may relax validation")
    @ValueSource(strings = {"dev", "test", "local", "DEV", "Local"})
    void developmentOnlyProfilesAreLenient(String profile) {
        assertThat(SecurityProfileMode.resolve(profile)).isEqualTo(SecurityProfileMode.LENIENT);
    }

    @Test
    @DisplayName("several development profiles together stay lenient")
    void multipleDevelopmentProfilesAreLenient() {
        assertThat(SecurityProfileMode.resolve("dev", "test"))
                .isEqualTo(SecurityProfileMode.LENIENT);
        assertThat(SecurityProfileMode.resolve("dev", "local"))
                .isEqualTo(SecurityProfileMode.LENIENT);
    }

    @ParameterizedTest
    @DisplayName("mixing a production profile with a development profile is rejected")
    @CsvSource({
            "prod, dev",
            "prod, test",
            "prod, local",
            "production, dev",
            "production, test",
            "production, local",
            "dev, prod",
            "test, production",
            "local, prod"
    })
    void mixedProductionAndDevelopmentProfilesAreRejected(String first, String second) {
        assertThatThrownBy(() -> SecurityProfileMode.resolve(first, second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mixed Spring profiles");
    }

    @Test
    @DisplayName("unknown or empty profile sets are strict, never lenient")
    void unknownProfilesAreStrict() {
        assertThat(SecurityProfileMode.resolve()).isEqualTo(SecurityProfileMode.STRICT);
        assertThat(SecurityProfileMode.resolve("")).isEqualTo(SecurityProfileMode.STRICT);
        assertThat(SecurityProfileMode.resolve("staging")).isEqualTo(SecurityProfileMode.STRICT);
        assertThat(SecurityProfileMode.resolve((String[]) null)).isEqualTo(SecurityProfileMode.STRICT);
    }

    @Test
    @DisplayName("isLenient mirrors the resolved mode")
    void isLenientMirrorsMode() {
        assertThat(SecurityProfileMode.LENIENT.isLenient()).isTrue();
        assertThat(SecurityProfileMode.STRICT.isLenient()).isFalse();
    }
}
