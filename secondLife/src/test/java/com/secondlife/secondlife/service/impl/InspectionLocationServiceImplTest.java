package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.config.ShippingCatalogProperties;
import com.secondlife.secondlife.config.ShippingCatalogProperties.City;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.GeocodedAddressResponse;
import com.secondlife.secondlife.dto.response.InspectionCenterOptionResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InspectionLocationServiceImplTest {
    @Test
    void textOnlyPickupUsesGeocodedLocationAndRejectsDifferentCity() {
        var shipping = mock(ShippingQuoteService.class);
        var geocoding = mock(ReverseGeocodingService.class);
        var centers = mock(InspectionCenterProfileService.class);
        var properties = new ShippingCatalogProperties();
        properties.setAllowedCities(List.of(City.HANOI, City.HO_CHI_MINH));
        var catalog = new ShippingCatalogPolicy(properties, new ObjectMapper());

        var seller = new User();
        seller.setId(UUID.randomUUID());
        var product = new Post();
        product.setUser(seller);
        var hanoi = new InspectionCenterOptionResponse(UUID.randomUUID(), "Hà Nội", "0900000000",
                "Hòa Lạc, Hà Nội", new BigDecimal("21.0135"), new BigDecimal("105.5253"), true);
        var hcm = new InspectionCenterOptionResponse(UUID.randomUUID(), "TP.HCM", "0900000001",
                "Thành phố Hồ Chí Minh", new BigDecimal("10.7769"), new BigDecimal("106.7009"), true);
        when(centers.getAvailableCenters()).thenReturn(List.of(hcm, hanoi));

        String address = "12 Cầu Giấy, Hà Nội";
        when(shipping.pickup(seller.getId())).thenReturn(new ShippingAddress("Seller", "0912345678",
                address, null, null));
        when(geocoding.geocodeStreetAddress(address)).thenReturn(new GeocodedAddressResponse(address,
                new BigDecimal("21.0333"), new BigDecimal("105.7937"), City.HANOI, "VN", null));

        try (var validators = Validation.buildDefaultValidatorFactory()) {
            var service = new InspectionLocationServiceImpl(shipping, geocoding, centers, catalog,
                    validators.getValidator());
            var pickup = service.sellerPickup(product);
            assertEquals(new BigDecimal("21.0333"), pickup.latitude());
            assertEquals(hanoi.id(), service.selectHub(pickup, null).id());

            when(geocoding.geocodeStreetAddress(address)).thenReturn(new GeocodedAddressResponse(address,
                    hcm.latitude(), hcm.longitude(), City.HO_CHI_MINH, "VN", null));
            assertThrows(ConflictException.class, () -> service.sellerPickup(product));
        }
    }
}
