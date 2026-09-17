package com.secondlife.secondlife.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AdminPass@123",
            "Valid#Password1",
            "Secure$99Test",
            "P@ssw0rd!"
    })
    void validPasswords_ShouldReturnTrue(String password) {
        assertTrue(validator.isValid(password, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Short1!",         // < 8 chars
            "nouppercase1@abc", // no uppercase
            "NOLOWERCASE1@ABC", // no lowercase
            "NoNumber!@#xyz",   // no digit
            "NoSpecial123456",  // no special char
            ""                  // empty
    })
    void invalidPasswords_ShouldReturnFalse(String password) {
        assertFalse(validator.isValid(password, null));
    }

    @Test
    void nullPassword_ShouldReturnFalse() {
        assertFalse(validator.isValid(null, null));
    }
}
