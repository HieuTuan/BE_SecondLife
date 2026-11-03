package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.response.TopupPackagePurchaseResponse;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CreditService;
import com.secondlife.secondlife.service.TopupPackagePurchaseService;
import com.secondlife.secondlife.service.TopupPackageQueryService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TopupPurchaseControllerTest {
    private static final UUID USER = UUID.randomUUID();
    private static final UUID PACKAGE = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TopupPackagePurchaseService service;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config {
        @Bean TopupPackagePurchaseService purchases() { return mock(TopupPackagePurchaseService.class); }
        @Bean TopupController controller(TopupPackagePurchaseService purchases) {
            return new TopupController(mock(CreditService.class), mock(TopupPackageQueryService.class),
                    new CurrentUserProvider(), purchases);
        }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .exceptionHandling(errors -> errors.authenticationEntryPoint(
                            (request, response, error) -> response.sendError(401))).build();
        }
    }

    @BeforeAll
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(Config.class);
        context.refresh();
        service = context.getBean(TopupPackagePurchaseService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @BeforeEach void resetService() { reset(service); }
    @AfterAll void close() { context.close(); }

    private String body() {
        return "{\"requestId\":\"" + REQUEST + "\",\"packageVersion\":0,\"expectedPrice\":59000}";
    }

    private TopupPackagePurchaseResponse receipt() {
        return new TopupPackagePurchaseResponse(UUID.randomUUID(), REQUEST, PACKAGE, 0,
                "Basic Combo", null, 2, 10, 2, new BigDecimal("59000"), "VND",
                new BigDecimal("64000"), new BigDecimal("7.81"), new BigDecimal("20000"),
                new BigDecimal("2000"), new BigDecimal("2000"), new BigDecimal("100000"),
                new BigDecimal("41000"), 2, 10, 2, Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void anonymousAndMissingAuthorityCannotPurchase() throws Exception {
        mvc.perform(post("/api/v1/topup/purchase/" + PACKAGE).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/topup/purchase/" + PACKAGE).with(csrf()).with(user("seller").roles("SELLER"))
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void purchaseUsesAuthenticatedOwnerAndReturnsReceiptWrapper() throws Exception {
        var details = mock(CustomUserDetails.class);
        when(details.getId()).thenReturn(USER);
        when(details.getUsername()).thenReturn("seller");
        doReturn(List.of(new SimpleGrantedAuthority(
                "CREDIT_PURCHASE_SELF"))).when(details).getAuthorities();
        var receipt = receipt();
        when(service.purchase(eq(USER), eq(PACKAGE), any())).thenReturn(receipt);
        mvc.perform(post("/api/v1/topup/purchase/" + PACKAGE).with(csrf()).with(user(details))
                        .contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(receipt.id().toString()))
                .andExpect(jsonPath("$.data.walletBalanceAfter").value(41000));
        verify(service).purchase(eq(USER), eq(PACKAGE), any());
        when(service.purchase(eq(USER), eq(PACKAGE), any())).thenThrow(new ConflictException("Gói đã thay đổi"));
        mvc.perform(post("/api/v1/topup/purchase/" + PACKAGE).with(csrf()).with(user(details))
                .contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isConflict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "{\"requestId\":null,\"packageVersion\":0,\"expectedPrice\":59000}",
            "{\"requestId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"packageVersion\":-1,\"expectedPrice\":59000}",
            "{\"requestId\":\"3fa85f64-5717-4562-b3fc-2c963f66afa6\",\"packageVersion\":0,\"expectedPrice\":999}"})
    void invalidPurchaseNeverCallsService(String body) throws Exception {
        mvc.perform(post("/api/v1/topup/purchase/" + PACKAGE).with(csrf())
                        .with(user("seller").authorities(new SimpleGrantedAuthority(
                                "CREDIT_PURCHASE_SELF"))).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void historyAndDetailUseCurrentOwnerAndReadPermission() throws Exception {
        var details = mock(CustomUserDetails.class);
        when(details.getId()).thenReturn(USER);
        when(details.getUsername()).thenReturn("seller");
        doReturn(List.of(new SimpleGrantedAuthority(
                "CREDIT_READ_SELF"))).when(details).getAuthorities();
        var receipt = receipt();
        when(service.getMyPurchases(USER, 0, 20)).thenReturn(new PageResponse<>(List.of(receipt), 0, 20, 1, 1, true));
        when(service.getMyPurchase(USER, receipt.id())).thenReturn(receipt);
        mvc.perform(get("/api/v1/topup/purchases").with(user(details)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].id").value(receipt.id().toString()));
        mvc.perform(get("/api/v1/topup/purchases/" + receipt.id()).with(user(details)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.packageName").value("Basic Combo"));
        mvc.perform(get("/api/v1/topup/purchases").with(user("other").roles("SELLER")))
                .andExpect(status().isForbidden());
    }
}
