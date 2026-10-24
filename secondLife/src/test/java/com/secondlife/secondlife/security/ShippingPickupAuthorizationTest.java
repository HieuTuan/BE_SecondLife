package com.secondlife.secondlife.security;

import com.secondlife.secondlife.controller.ShippingController;
import com.secondlife.secondlife.service.ReverseGeocodingService;
import com.secondlife.secondlife.service.ShippingCatalogService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(ShippingPickupAuthorizationTest.Config.class)
class ShippingPickupAuthorizationTest {
    private static final UUID USER_ID = UUID.randomUUID();
    @Autowired ShippingController controller;
    @Autowired ShippingQuoteService quotes;

    @org.junit.jupiter.api.BeforeEach void clearMockCalls() { clearInvocations(quotes); }

    @Test @WithMockUser(roles="BUYER")
    void buyerCanReadTheirMissingPickupAddress() {
        assertNull(controller.pickup(null).data());
        verify(quotes).pickup(USER_ID);
    }

    @Test @WithMockUser(roles="SELLER")
    void sellerCanStillReadTheirPickupAddress() {
        assertDoesNotThrow(() -> controller.pickup(null));
    }

    @Test @WithMockUser(roles="BUYER")
    void buyerCannotUseTheSellerPickupWriteEndpoint() {
        assertThrows(AccessDeniedException.class,() -> controller.savePickup(null,null));
    }

    @Test @WithMockUser(roles="STAFF")
    void unrelatedRoleCannotReadPickupAddress() {
        assertThrows(AccessDeniedException.class,() -> controller.pickup(null));
    }

    @Test @org.springframework.security.test.context.support.WithAnonymousUser
    void anonymousCallerCannotReadPickupAddress() {
        assertThrows(AccessDeniedException.class,() -> controller.pickup(null));
    }

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean ShippingQuoteService quotes() { return mock(ShippingQuoteService.class); }
        @Bean ShippingController controller(ShippingQuoteService quotes) {
            var current = mock(CurrentUserProvider.class);
            when(current.resolveUserId(null)).thenReturn(USER_ID);
            return new ShippingController(mock(ShippingCatalogService.class),quotes,current,mock(ReverseGeocodingService.class));
        }
    }
}