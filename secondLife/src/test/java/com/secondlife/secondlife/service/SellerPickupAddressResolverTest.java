package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SellerPickupAddressRequest;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.service.shipping.SellerPickupAddressResolver;
import com.secondlife.secondlife.service.shipping.ShippingProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SellerPickupAddressResolverTest {
    private final com.secondlife.secondlife.service.shipping.ReverseGeocodingService geocoding = mock(com.secondlife.secondlife.service.shipping.ReverseGeocodingService.class);
    private final SellerPickupAddressResolver resolver = new SellerPickupAddressResolver(geocoding);

    @Test void lalamoveNormalizesCoordinatesWithoutCallingGhnCatalogues() {

        when(geocoding.shipping(any())).thenReturn(new ShippingAddress("Seller","0901234567","123 Street, HCMC",new BigDecimal("10.7756"),new BigDecimal("106.7019")));
        var result = resolver.resolve(pickup("10.775600", "106.701900"));
        assertEquals("Seller", result.name());
        assertEquals("0901234567", result.phone());
        assertEquals("123 Street, HCMC", result.address());
        assertEquals(new BigDecimal("10.7756"), result.latitude());
        assertEquals(new BigDecimal("106.7019"), result.longitude());
    }

    @Test void lalamoveRejectsMissingCoordinatesEvenWithGhnIdentifiers() {

        assertThrows(BadRequestException.class, () -> resolver.resolve(
                new SellerPickupAddressRequest("Seller", "0901234567", "123 Street", null, null)));
        assertThrows(BadRequestException.class, () -> resolver.resolve(pickup("10.7756", null)));
        assertThrows(BadRequestException.class, () -> resolver.resolve(pickup(null, "106.7019")));
    }

    @Test void lalamoveRejectsOutOfRangeCoordinates() {

        assertThrows(BadRequestException.class, () -> resolver.resolve(pickup("90.1", "106.7019")));
        assertThrows(BadRequestException.class, () -> resolver.resolve(pickup("10.7756", "-180.1")));
    }



    private static SellerPickupAddressRequest pickup(String latitude, String longitude) {
        return new SellerPickupAddressRequest(" Seller ", " 0901234567 ", " 123 Street, HCMC ", decimal(latitude), decimal(longitude));
    }

    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}
