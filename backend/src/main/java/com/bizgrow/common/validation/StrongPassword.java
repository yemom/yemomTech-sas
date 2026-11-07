package com.YemomBizGrow.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Password policy annotation — the policy lives in one place
 * ({@link PasswordValidator}) and is applied by Bean Validation like any
 * built-in constraint, so every endpoint that accepts a password enforces it
 * automatically and reports it through the standard fieldErrors envelope.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
public @interface StrongPassword {

    String message() default "Password must be at least 8 characters and contain an uppercase letter, "
            + "a lowercase letter, a digit and a symbol.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
