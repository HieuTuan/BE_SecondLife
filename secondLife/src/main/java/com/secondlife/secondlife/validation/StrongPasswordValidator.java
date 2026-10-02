package com.secondlife.secondlife.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    // At least one digit, one lowercase, one uppercase, one special character, minimum 8 characters
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern LOWER_PATTERN = Pattern.compile(".*[a-z].*");
    private static final Pattern UPPER_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern SPECIAL_PATTERN = Pattern.compile(".*[^a-zA-Z0-9].*");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return false;
        }

        if (password.length() < 8) {
            return false;
        }

        return DIGIT_PATTERN.matcher(password).matches()
                && LOWER_PATTERN.matcher(password).matches()
                && UPPER_PATTERN.matcher(password).matches()
                && SPECIAL_PATTERN.matcher(password).matches();
    }
}
