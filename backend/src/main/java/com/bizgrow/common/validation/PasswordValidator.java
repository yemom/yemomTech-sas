package com.YemomBizGrow.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Enforces the platform password policy:
 * 8-128 characters containing at least one uppercase, one lowercase,
 * one digit and one symbol.
 *
 * <p>{@code null} is treated as valid so that {@code @NotBlank} alone reports a
 * missing password (one clear field error instead of two competing ones).
 * The policy is intentionally simple and explainable; a breach-corpus check
 * (NIST-style) is listed as future hardening in the security documentation.</p>
 */
public class PasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 128;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSymbol = false;
        for (char c : value.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSymbol = true;
            }
        }
        return hasUpper && hasLower && hasDigit && hasSymbol;
    }
}
