package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SellerPickupAddressRequest;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ShippingProviderException;
import com.secondlife.secondlife.service.shipping.GhnPickupAddressResolver;
import com.secondlife.secondlife.service.shipping.ShippingProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GhnPickupAddressResolverTest {
    private final ShippingProvider provider = mock(ShippingProvider.class);
    private final GhnPickupAddressResolver resolver = new GhnPickupAddressResolver(provider);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SellerPickupAddressRequest request = new SellerPickupAddressRequest(
            " Seller ", "0901234567", " 123 Street ", 1000001, 1003646);

    @Test void storesCanonicalGhnNamesAndTrimsStreetAddress() {
        province(1);
        ward(1000001);
        var result = resolver.resolve(request);
        assertEquals("Hồ Chí Minh", result.provinceName());
        assertEquals("Phường Vũng Tàu", result.wardName());
        assertEquals("123 Street", result.address());
        assertTrue(result.newAddress());
        assertNull(result.districtId());
        verify(provider).wards(1000001);
    }

    @Test void unknownOrDisabledProvinceIsRejected() {
        when(provider.provinces()).thenReturn(mapper.readTree("[]"));
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
        province(2);
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
        verify(provider, never()).wards(anyInt());
    }

    @Test void unknownWardOrWardOfAnotherProvinceIsRejected() {
        province(1);
        when(provider.wards(1000001)).thenReturn(mapper.readTree("[]"));
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
        ward(1000002);
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
    }

    @Test void malformedCatalogueIsNotAcceptedAsAUserAddress() {
        when(provider.provinces()).thenReturn(mapper.readTree("{}"));
        assertThrows(ShippingProviderException.class, () -> resolver.resolve(request));
    }

    @Test void providerFailureIsPropagatedWithoutInventingAnAddress() {
        when(provider.provinces()).thenThrow(new ShippingProviderException("GHN is unavailable"));
        assertThrows(ShippingProviderException.class, () -> resolver.resolve(request));
        verify(provider, never()).wards(anyInt());
    }

    private void province(int status) {
        when(provider.provinces()).thenReturn(mapper.readTree("""
                [{"_id":1000001,"name":"Hồ Chí Minh","type":"province","status":%d}]
                """.formatted(status)));
    }
    private void ward(int parent) {
        when(provider.wards(1000001)).thenReturn(mapper.readTree("""
                [{"_id":1003646,"name":"Phường Vũng Tàu","type":"ward","status":1,"parent_id":%d}]
                """.formatted(parent)));
    }
}
