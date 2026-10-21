package com.secondlife.secondlife.security;

import com.secondlife.secondlife.controller.ShipmentController;
import com.secondlife.secondlife.controller.StaffReportController;
import com.secondlife.secondlife.dto.request.CreateShipmentRequest;
import com.secondlife.secondlife.dto.request.ReportRequests.Decision;
import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.Resolve;
import com.secondlife.secondlife.dto.request.ReportRequests.ResponsibleParty;
import com.secondlife.secondlife.service.ShipmentService;
import com.secondlife.secondlife.service.ShipmentTimelineService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import com.secondlife.secondlife.service.UserReportService;
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

@SpringJUnitConfig(OrderDisputeAuthorizationTest.Config.class)
class OrderDisputeAuthorizationTest {
    @Autowired StaffReportController reports;
    @Autowired ShipmentController shipments;

    @Test @WithMockUser(authorities = {"ROLE_STAFF", "STAFF_FRAUD_REVIEW"})
    void reportReviewerCannotApproveFinancialReturnWithoutDisputePermission() {
        assertThrows(AccessDeniedException.class, () -> reports.resolve(null, UUID.randomUUID(), approval()));
    }

    @Test @WithMockUser(authorities = {"ROLE_STAFF", "STAFF_FRAUD_REVIEW"})
    void existingReportReviewerCanStillDismiss() {
        assertDoesNotThrow(() -> reports.resolve(null, UUID.randomUUID(), new Resolve(Decision.DISMISSED, ResponsibleParty.NONE, "Rejected")));
    }

    @Test @WithMockUser(authorities = {"ROLE_STAFF", "STAFF_DISPUTE_REVIEW"})
    void financialPermissionCannotBypassReportReviewPermission() {
        assertThrows(AccessDeniedException.class, () -> reports.resolve(null, UUID.randomUUID(), approval()));
    }

    @Test @WithMockUser(authorities = {"ROLE_STAFF", "STAFF_DISPUTE_REVIEW", "STAFF_FRAUD_REVIEW"})
    void authorizedStaffCanApproveReturn() {
        assertDoesNotThrow(() -> reports.resolve(null, UUID.randomUUID(), approval()));
    }

    @Test @WithMockUser(roles = "STAFF")
    void staffRoleAloneCannotBookReturn() {
        assertThrows(AccessDeniedException.class, () -> shipments.create(null, UUID.randomUUID(), returned()));
    }

    @Test @WithMockUser(authorities = {"ROLE_STAFF", "STAFF_DISPUTE_REVIEW"})
    void authorizedOperationalStaffCanBookReturn() {
        assertDoesNotThrow(() -> shipments.create(null, UUID.randomUUID(), returned()));
    }

    @Test @WithMockUser(roles = "SELLER")
    void sellerOutboundAccessIsPreservedButBuyerReturnIsDenied() {
        var outbound = new CreateShipmentRequest(UUID.randomUUID(), "SELLER_TO_BUYER", null, null, null);
        assertDoesNotThrow(() -> shipments.create(null, UUID.randomUUID(), outbound));
        assertThrows(AccessDeniedException.class, () -> shipments.create(null, UUID.randomUUID(), returned()));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminStillRequiresAdministrativeAuthority() {
        assertThrows(AccessDeniedException.class, () -> reports.resolve(null, UUID.randomUUID(), approval()));
        assertThrows(AccessDeniedException.class, () -> shipments.create(null, UUID.randomUUID(), returned()));
    }

    private Resolve approval() {
        return new Resolve(Decision.CONFIRMED, ResponsibleParty.SELLER, "Approved return", ResolutionAction.RETURN_AND_REFUND);
    }
    private CreateShipmentRequest returned() {
        return new CreateShipmentRequest(UUID.randomUUID(), "BUYER_TO_SELLER", null, null, "Approved return", UUID.randomUUID());
    }

    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean CurrentUserProvider current() {
            var current = mock(CurrentUserProvider.class);
            when(current.resolveUserId(null)).thenReturn(UUID.randomUUID());
            return current;
        }
        @Bean StaffReportController reports(CurrentUserProvider current) {
            return new StaffReportController(mock(UserReportService.class), current);
        }
        @Bean ShipmentController shipments(CurrentUserProvider current) {
            return new ShipmentController(mock(ShipmentService.class), mock(ShipmentTimelineService.class), mock(ShippingQuoteService.class), current);
        }
    }
}
