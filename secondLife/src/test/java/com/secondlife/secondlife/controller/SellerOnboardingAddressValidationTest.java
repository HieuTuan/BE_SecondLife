package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.SellerOnboardingRequest;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.SellerOnboardingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SellerOnboardingAddressValidationTest {
    @Test
    void coordinatesOnlyPickupIsAcceptedWithoutClientAddressText() throws Exception {
        var service = mock(SellerOnboardingService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SellerOnboardingController(service, mock(CurrentUserProvider.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(put("/api/v1/seller-onboarding/me").contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Shop A","email":"shop@example.test","phone":"0901234567",
                 "pickupAddress":{"name":"Seller","phone":"0901234567","latitude":21.0333,"longitude":105.7937}}
                """)) .andExpect(status().isOk());
    }
    @Test
    void provinceAndWardIdentifiersCannotReplaceRequiredCoordinates() throws Exception {
        var service = mock(SellerOnboardingService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SellerOnboardingController(service, mock(CurrentUserProvider.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(put("/api/v1/seller-onboarding/me").contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Shop A","email":"shop@example.test","phone":"0901234567",
                 "pickupAddress":{"name":"Seller","phone":"0901234567","address":"123 Street",
                 "provinceId":1000001,"wardId":1003646}}
                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test
    void coordinatePickupWithoutGhnIdentifiersReachesOnboarding() throws Exception {
        var service = mock(SellerOnboardingService.class);
        var current = mock(CurrentUserProvider.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SellerOnboardingController(service, current))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(put("/api/v1/seller-onboarding/me").contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Shop A","email":"shop@example.test","phone":"0901234567",
                 "pickupAddress":{"name":"Seller","phone":"0901234567","address":"123 Street, HCMC",
                 "latitude":10.7756,"longitude":106.7019}}
                """))
                .andExpect(status().isOk());
        var request = ArgumentCaptor.forClass(SellerOnboardingRequest.class);
        verify(service).save(isNull(), request.capture());
        assertEquals(new BigDecimal("10.7756"), request.getValue().pickupAddress().latitude());
        assertEquals(new BigDecimal("106.7019"), request.getValue().pickupAddress().longitude());
    }

    @Test
    void freeTextProvinceAndWardWithoutGhnIdentifiersAreRejected() throws Exception {
        var service = mock(SellerOnboardingService.class);
        var current = mock(CurrentUserProvider.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SellerOnboardingController(service, current))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(put("/api/v1/seller-onboarding/me").contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Shop A","email":"shop@example.test","phone":"0901234567",
                 "pickupAddress":{"name":"Seller","phone":"0901234567","address":"123 Street",
                 "provinceName":"Made up province","wardName":"Made up ward","newAddress":true}}
                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
