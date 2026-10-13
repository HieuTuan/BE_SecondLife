package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.controller.admin.AdminInspectionController;
import com.secondlife.secondlife.dto.request.ReviewFeeProposalRequest;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.InspectionPricingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminInspectionControllerTest {

    @Mock
    private InspectionPricingService pricingService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AdminInspectionController adminInspectionController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminInspectionController)
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver(),
                        new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/admin/inspection/travel-tiers: Trả về danh sách 6 bậc thang")
    void testGetTravelTiers() throws Exception {
        InspectionTravelTierDto t1 = InspectionTravelTierDto.builder()
                .tierNumber(1)
                .name("0-5 km")
                .fromKm(BigDecimal.valueOf(0))
                .toKm(BigDecimal.valueOf(5))
                .pricePerKm(BigDecimal.valueOf(2000))
                .status("ACTIVE")
                .build();

        when(pricingService.getActiveTiers()).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/v1/admin/inspection/travel-tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].tierNumber").value(1))
                .andExpect(jsonPath("$.data[0].pricePerKm").value(2000));
    }

    @Test
    @DisplayName("POST /api/v1/admin/inspection/fee-proposals/{id}/review: Duyệt thành công proposal")
    void testReviewFeeProposal() throws Exception {
        UUID proposalId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        ReviewFeeProposalRequest request = new ReviewFeeProposalRequest(true, "Chấp thuận đề xuất biểu phí");

        InspectionFeeProposalResponse response = InspectionFeeProposalResponse.builder()
                .id(proposalId)
                .status(InspectionFeeProposalStatus.APPROVED)
                .baseInspectionFee(BigDecimal.valueOf(120000))
                .build();

        when(currentUserProvider.resolveAdminId(any())).thenReturn(adminId);
        when(pricingService.reviewProposal(eq(proposalId), eq(adminId), eq(true), anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/inspection/fee-proposals/" + proposalId + "/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(proposalId.toString()))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }
}
