package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.SellerOnboardingService;
import com.secondlife.secondlife.service.shipping.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShippingContactApiValidationTest {
    @ParameterizedTest @ValueSource(strings={"0912345678","+84912345678","84912345678","02412345678"})
    void quoteAcceptsLocalAndInternationalVietnameseContacts(String phone) throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new ShippingController(mock(ShippingCatalogService.class),mock(ShippingQuoteService.class),
            mock(CurrentUserProvider.class),mock(ReverseGeocodingService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(post("/api/v1/shipping/quotes").contentType("application/json").content("""
            {"postId":"1c2dd5e3-36db-420b-b313-9ed606b017a9",
             "deliveryAddress":{"name":"Buyer","phone":"%s","latitude":21.0285,"longitude":105.8542}}
            """.formatted(phone))).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"123456789012345","2274799037","+6512345678"})
    void quoteRejectsInvalidVietnameseRecipientBeforeCallingTheShippingService(String phone) throws Exception {
        var quotes=mock(ShippingQuoteService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new ShippingController(mock(ShippingCatalogService.class),quotes,
            mock(CurrentUserProvider.class),mock(ReverseGeocodingService.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(post("/api/v1/shipping/quotes").contentType("application/json").content("""
            {"postId":"1c2dd5e3-36db-420b-b313-9ed606b017a9",
             "deliveryAddress":{"name":"Buyer","phone":"%s","latitude":21.0285,"longitude":105.8542}}
            """.formatted(phone)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("deliveryAddress.phone"));
        verifyNoInteractions(quotes);
    }

    @Test void onboardingRejectsInvalidPickupContactBeforeSavingTheProfile() throws Exception {
        var onboarding=mock(SellerOnboardingService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new SellerOnboardingController(onboarding,mock(CurrentUserProvider.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(put("/api/v1/seller-onboarding/me").contentType("application/json").content("""
            {"shopName":"Shop","email":"shop@example.test","phone":"0912345678",
             "pickupAddress":{"name":"Seller","phone":"123456789012345","latitude":21.0333,"longitude":105.7937}}
            """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("pickupAddress.phone"));
        verifyNoInteractions(onboarding);
    }
}
