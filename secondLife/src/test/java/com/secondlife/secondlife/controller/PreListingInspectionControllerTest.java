package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PreListingInspectionControllerTest {
    PreListingInspectionService preListing = mock(PreListingInspectionService.class);
    HighValueInspectionPolicyService policy = mock(HighValueInspectionPolicyService.class);
    CurrentUserProvider currentUser = mock(CurrentUserProvider.class);
    MockMvc mvc;
    UUID sellerId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        when(currentUser.resolveUserId(any())).thenReturn(sellerId);
        mvc = MockMvcBuilders.standaloneSetup(
                        new PreListingInspectionController(policy, preListing, currentUser),
                        new AdminHighValueInspectionPolicyController(policy, currentUser))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void quoteAcceptsBothExistingInspectionMethodsAndReturnsTheStoredQuoteId() throws Exception {
        UUID post = UUID.randomUUID();
        UUID quote = UUID.randomUUID();
        for (var type : InspectionOrderType.values()) {
            when(preListing.quote(sellerId, post, new PreListingInspectionQuoteRequest(type, null)))
                    .thenReturn(new PreListingInspectionQuoteResponse(quote, Instant.now().plusSeconds(600), type, null));
            mvc.perform(post("/api/v1/inspection/posts/" + post + "/pre-listing/quote")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"orderType\":\"" + type + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.quoteId").value(quote.toString()))
                    .andExpect(jsonPath("$.data.orderType").value(type.name()));
        }
    }

    @Test
    void quoteRequiresMethodAndConfirmationRequiresExplicitConsent() throws Exception {
        String path = "/api/v1/inspection/posts/" + UUID.randomUUID() + "/pre-listing";
        mvc.perform(post(path + "/quote").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path + "/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quoteId\":\"" + UUID.randomUUID() + "\",\"sellerConsentZin\":false}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(preListing);
    }

    @Test
    void adminPolicyRejectsZeroAndReturnsThePersistedThreshold() throws Exception {
        mvc.perform(put("/api/v1/admin/inspection/high-value-policy")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdPrice\":0}"))
                .andExpect(status().isBadRequest());
        when(policy.update(sellerId, new HighValueInspectionPolicyRequest(new BigDecimal("8000000"))))
                .thenReturn(new HighValueInspectionPolicyResponse(new BigDecimal("8000000"), "VND", 2, Instant.now(), sellerId));
        mvc.perform(put("/api/v1/admin/inspection/high-value-policy")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdPrice\":8000000}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thresholdPrice").value(8000000))
                .andExpect(jsonPath("$.data.version").value(2));
    }
}
