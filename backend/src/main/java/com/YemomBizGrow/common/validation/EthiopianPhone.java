package com.YemomBizGrow.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string is a valid Ethiopian mobile phone number
 * (Ethio Telecom 09... / +2519... or Safaricom Ethiopia 07... / +2517...).
 * Null or empty strings are considered valid (use @NotBlank if required).
 */
@Documented
@Constraint(validatedBy = EthiopianPhoneValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface EthiopianPhone {

    String message() default "Invalid Ethiopian phone number. Must be a valid Ethio Telecom (09/2519) or Safaricom Ethiopia (07/2517) number.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
