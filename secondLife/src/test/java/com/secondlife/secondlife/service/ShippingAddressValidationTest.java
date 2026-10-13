package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SellerPickupAddressRequest;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ShippingAddressValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void close() { factory.close(); }

    @Test void coordinateOnlyJsonDoesNotRequireGhnNamesCodesOrAddressMode() {
        var address = JsonMapper.builder().build().readValue("""
                {"name":"Seller","phone":"0901234567","address":"123 Street, HCMC",
                 "latitude":10.7756,"longitude":106.7019}
                """, ShippingAddress.class);
        assertTrue(validator.validate(address).isEmpty());
        assertEquals(new BigDecimal("10.7756"), address.latitude());
        assertEquals(new BigDecimal("106.7019"), address.longitude());
    }

    @Test void sellerPickupAcceptsCoordinatesWithoutGhnIdentifiers() {
        var address = pickup("10.7756", "106.7019");
        assertTrue(validator.validate(address).isEmpty());
    }



    @Test void jsonContainsCoordinatesWithoutValidationHelperProperties() {
        var mapper = JsonMapper.builder().build();
        var shippingJson = mapper.readTree(mapper.writeValueAsString(shipping("10.7756", "106.7019")));
        assertEquals(new BigDecimal("10.7756"), shippingJson.path("latitude").decimalValue());
        assertEquals(new BigDecimal("106.7019"), shippingJson.path("longitude").decimalValue());
        assertFalse(shippingJson.has("coordinatePairValid"));
        assertFalse(shippingJson.has("administrativeNamesValid"));
        assertFalse(shippingJson.has("districtNameValid"));
        assertFalse(shippingJson.has("legacyCodesValid"));
        var pickupJson = mapper.readTree(mapper.writeValueAsString(pickup("10.7756", "106.7019")));
        assertEquals(new BigDecimal("10.7756"), pickupJson.path("latitude").decimalValue());
        assertEquals(new BigDecimal("106.7019"), pickupJson.path("longitude").decimalValue());
        assertFalse(pickupJson.has("coordinatePairValid"));
        assertFalse(pickupJson.has("locationValid"));
    }

    @Test void coordinatesMustBeProvidedAsAPair() {
        assertFalse(validator.validate(shipping("10.7756", null)).isEmpty());
        assertFalse(validator.validate(shipping(null, "106.7019")).isEmpty());
        assertFalse(validator.validate(pickup("10.7756", null)).isEmpty());
        assertFalse(validator.validate(pickup(null, "106.7019")).isEmpty());
    }

    @Test void coordinatesRejectValuesOutsideGeographicalRanges() {
        for (String latitude : new String[]{"-90.0001", "90.0001"}) {
            assertFalse(validator.validate(shipping(latitude, "106.7019")).isEmpty());
            assertFalse(validator.validate(pickup(latitude, "106.7019")).isEmpty());
        }
        for (String longitude : new String[]{"-180.0001", "180.0001"}) {
            assertFalse(validator.validate(shipping("10.7756", longitude)).isEmpty());
            assertFalse(validator.validate(pickup("10.7756", longitude)).isEmpty());
        }
        assertTrue(validator.validate(shipping("-90", "-180")).isEmpty());
        assertTrue(validator.validate(pickup("90", "180")).isEmpty());
    }

    @Test void legacyGhnJsonStillReadsWithoutCoordinates() {
        var address = JsonMapper.builder().build().readValue("""
                {"name":"Seller","phone":"0901234567","address":"123 Street",
                 "provinceName":"HCMC","districtName":"District 1","wardName":"Ward 1",
                 "districtId":1442,"wardCode":"20308","newAddress":false}
                """, ShippingAddress.class);
        assertNull(address.latitude());
        assertNull(address.longitude());
        assertFalse(validator.validate(address).isEmpty());
    }



    private static ShippingAddress shipping(String latitude, String longitude) {
        return new ShippingAddress("Seller", "0901234567", "123 Street, HCMC", decimal(latitude), decimal(longitude));
    }

    private static SellerPickupAddressRequest pickup(String latitude, String longitude) {
        return new SellerPickupAddressRequest("Seller", "0901234567", "123 Street, HCMC", decimal(latitude), decimal(longitude));
    }

    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}
