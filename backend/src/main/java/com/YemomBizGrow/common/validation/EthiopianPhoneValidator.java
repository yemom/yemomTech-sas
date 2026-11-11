package com.YemomBizGrow.common.validation;

import com.YemomBizGrow.common.util.EthiopianPhoneNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EthiopianPhoneValidator implements ConstraintValidator<EthiopianPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // Optional validation; use @NotBlank if required
        }
        return EthiopianPhoneNormalizer.isValid(value);
    }
}
