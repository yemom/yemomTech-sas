package com.YemomBizGrow.common.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PasswordValidatorTest {

    private final PasswordValidator validator = new PasswordValidator();
    private final ConstraintValidatorContext context = mock(ConstraintValidatorContext.class);

    @ParameterizedTest
    @DisplayName("policy-compliant passwords are accepted")
    @ValueSource(strings = {
            "Passw0rd!",
            "Ethiopia2026#",
            "aB3$efgh",
            "AAAAAAAA1a!"
    })
    void acceptsStrongPasswords(String candidate) {
        assertThat(validator.isValid(candidate, context)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("weak passwords are rejected")
    @ValueSource(strings = {
            "alllower1!",
            "ALLUPPER1!",
            "NoDigits!!",
            "NoSymbols1",
            "1234567890",
            "Password1"     // no symbol
    })
    void rejectsWeakPasswords(String candidate) {
        assertThat(validator.isValid(candidate, context)).isFalse();
    }

    @ParameterizedTest
    @DisplayName("length bounds are enforced (8..128)")
    @ValueSource(strings = {"Ab1$def", "Ab1$" + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void enforcesLengthBounds(String candidate) {
        assertThat(validator.isValid(candidate, context)).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @DisplayName("null is valid so @NotBlank alone reports missing passwords")
    void nullIsValid(String candidate) {
        assertThat(validator.isValid(candidate, context)).isTrue();
    }

    @Test
    @DisplayName("unicode symbols count as symbols but letters must include case")
    void unicodeHandling() {
        assertThat(validator.isValid("Ab1§defg", context)).isTrue();
        assertThat(validator.isValid("Ab§defg", context)).isFalse();  // no digit
    }
}
