package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.SellerOnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SellerOnboardingAddressValidationTest {
    @Test
    void freeTextProvinceAndWardWithoutGhnIdentifiersAreRejected() throws Exception {
        var service = mock(SellerOnboardingService.class);
        var current = mock(CurrentUserProvider.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SellerOnboardingController(service, current))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(put("/api/seller-onboarding/me").contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Shop A","email":"shop@example.test","phone":"0901234567",
                 "pickupAddress":{"name":"Seller","phone":"0901234567","address":"123 Street",
                 "provinceName":"Made up province","wardName":"Made up ward","newAddress":true}}
                """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
