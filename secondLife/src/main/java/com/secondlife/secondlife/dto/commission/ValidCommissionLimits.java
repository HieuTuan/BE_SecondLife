package com.secondlife.secondlife.dto.commission;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidCommissionLimits.Validator.class)
public @interface ValidCommissionLimits {
    String message() default "maxCommission must be greater than or equal to minCommission";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidCommissionLimits, CommissionRuleRequest> {
        @Override public boolean isValid(CommissionRuleRequest value, ConstraintValidatorContext context) {
            if (value == null || value.minCommission() == null || value.maxCommission() == null
                    || value.maxCommission().compareTo(value.minCommission()) >= 0) return true;
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("maxCommission").addConstraintViolation();
            return false;
        }
    }
}
