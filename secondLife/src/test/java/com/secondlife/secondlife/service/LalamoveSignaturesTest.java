package com.secondlife.secondlife.service;

import com.secondlife.secondlife.service.shipping.LalamoveSignatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LalamoveSignaturesTest {
    @Test void signsExactUtf8BodyWithRequiredCrLfSeparators() {
        String body = "{\"data\":{\"address\":\"Hồ Chí Minh\"}}";
        String signature = LalamoveSignatures.sign("sk_test_Lalamove", "1545880607433", "POST", "/v3/quotations", body);
        assertEquals("dac3f027c0593cb145cd8e62846edd310ee4f3738b83e386b713e4cd5a7bb3a4", signature);
        assertNotEquals(signature, LalamoveSignatures.sign("sk_test_Lalamove", "1545880607433", "POST", "/v3/quotations", body + " "));
    }

    @Test void verifiesOnlyMatchingLowercaseHexSignatures() {
        String expected = LalamoveSignatures.sign("secret", "123", "GET", "/v3/cities", "");
        assertTrue(LalamoveSignatures.verify(expected, expected));
        assertFalse(LalamoveSignatures.verify(expected, expected.toUpperCase()));
        assertFalse(LalamoveSignatures.verify(expected, expected.substring(1)));
        assertFalse(LalamoveSignatures.verify(expected, null));
    }
}
