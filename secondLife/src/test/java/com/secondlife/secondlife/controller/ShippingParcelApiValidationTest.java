package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.ShipmentService;
import com.secondlife.secondlife.service.ShipmentTimelineService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShippingParcelApiValidationTest {
    private final ShippingQuoteService quotes = mock(ShippingQuoteService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ShipmentController(
            mock(ShipmentService.class), mock(ShipmentTimelineService.class), quotes, mock(CurrentUserProvider.class)))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();

    @ParameterizedTest
    @CsvSource({"100000,220,120,100", "1000000,500,210,205", "5000000,600,220,220"})
    void savesHeavyAndLargeParcelWithoutAFixedCarrierLimit(int weight, int length, int width, int height) throws Exception {
        when(quotes.saveParcel(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        mvc.perform(put("/api/v1/posts/1c2dd5e3-36db-420b-b313-9ed606b017a9/shipping-package")
                .contentType("application/json").content(parcelJson(weight, length, width, height)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.weight").value(weight))
                .andExpect(jsonPath("$.data.length").value(length))
                .andExpect(jsonPath("$.data.width").value(width))
                .andExpect(jsonPath("$.data.height").value(height));
    }

    @ParameterizedTest
    @CsvSource({"0,20,15,10", "-1,20,15,10", "600,0,15,10", "600,-1,15,10",
            "600,20,0,10", "600,20,-1,10", "600,20,15,0", "600,20,15,-1"})
    void rejectsNonPositiveMeasurementsBeforeSaving(int weight, int length, int width, int height) throws Exception {
        mvc.perform(put("/api/v1/posts/1c2dd5e3-36db-420b-b313-9ed606b017a9/shipping-package")
                .contentType("application/json").content(parcelJson(weight, length, width, height)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isNotEmpty());
        verifyNoInteractions(quotes);
    }

    private static String parcelJson(int weight, int length, int width, int height) {
        return "{\"weight\":%d,\"length\":%d,\"width\":%d,\"height\":%d}"
                .formatted(weight, length, width, height);
    }
}
