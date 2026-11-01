package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.config.ShippingCatalogProperties;
import com.secondlife.secondlife.dto.response.GeocodedAddressResponse;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingCatalogService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShippingGeocodingControllerTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ReverseGeocodingService service;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config {
        @Bean ReverseGeocodingService geocoding() { return mock(ReverseGeocodingService.class); }
        @Bean ShippingController controller(ReverseGeocodingService geocoding) {
            return new ShippingController(mock(ShippingCatalogService.class), mock(ShippingQuoteService.class),
                    mock(CurrentUserProvider.class), geocoding);
        }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(errors -> errors.authenticationEntryPoint(
                            (request, response, error) -> response.sendError(401))).build();
        }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        service = context.getBean(ReverseGeocodingService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void authenticatedBuyerReceivesWrappedCoordinates() throws Exception {
        when(service.geocode("12 Cầu Giấy")).thenReturn(new GeocodedAddressResponse("12 Cầu Giấy",
                new BigDecimal("21.0333"), new BigDecimal("105.7937"),
                ShippingCatalogProperties.City.HANOI, "VN", "place-1"));
        mvc.perform(get("/api/v1/shipping/geocode").param("address", " 12 Cầu Giấy ")
                        .with(user("buyer").roles("BUYER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.latitude").value(21.0333))
                .andExpect(jsonPath("$.data.city").value("HANOI"));
        verify(service).geocode("12 Cầu Giấy");
    }

    @Test
    void anonymousRequestCannotCallProvider() throws Exception {
        mvc.perform(get("/api/v1/shipping/geocode").param("address", "12 Cầu Giấy"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void invalidQueryReturns400BeforeCallingService() throws Exception {
        mvc.perform(get("/api/v1/shipping/geocode").with(user("buyer"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/shipping/geocode").param("address", "   ").with(user("buyer")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/shipping/geocode").param("address", "x".repeat(501)).with(user("buyer")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
