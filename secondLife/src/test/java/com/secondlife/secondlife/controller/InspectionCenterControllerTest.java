package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.InspectionFeeProposalRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InspectionCenterControllerTest {

    @Mock
    private InspectionService inspectionService;

    @Mock
    private InspectionChecklistService checklistService;

    @Mock
    private InspectionPricingService pricingService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private InspectionCenterController centerController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(centerController)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/inspector-center/orders: Quản lý trung tâm xem danh sách đơn kiểm định")
    void testGetAllOrders() throws Exception {
        InspectionOrderResponse order = InspectionOrderResponse.builder()
                .id(UUID.randomUUID())
                .status("CONFIRMED_PENDING_DISPATCH")
                .build();

        when(inspectionService.getAllOrders(any(), any()))
                .thenReturn(new PageImpl<>(List.of(order)));

        mockMvc.perform(get("/api/v1/inspector-center/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].status").value("CONFIRMED_PENDING_DISPATCH"));
    }

    @Test
    @DisplayName("POST /api/v1/inspector-center/orders/{id}/assign: Phân công KTV cho đơn")
    void testAssignInspector() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID inspectorId = UUID.randomUUID();

        InspectionOrderResponse response = InspectionOrderResponse.builder()
                .id(orderId)
                .inspectorId(inspectorId)
                .status("DISPATCHED")
                .build();

        when(inspectionService.assignInspector(orderId, inspectorId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/inspector-center/orders/" + orderId + "/assign")
                        .param("inspectorId", inspectorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPATCHED"))
                .andExpect(jsonPath("$.inspectorId").value(inspectorId.toString()));
    }

    @Test
    @DisplayName("GET /api/v1/inspector-center/checklists: Lấy danh sách checklist templates")
    void testGetAllChecklists() throws Exception {
        InspectionChecklistResponse item = InspectionChecklistResponse.builder()
                .title("Checklist Đồ gia dụng")
                .build();

        when(checklistService.getAllTemplates()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/inspector-center/checklists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Checklist Đồ gia dụng"));
    }

    @Test
    @DisplayName("POST /api/v1/inspector-center/travel-tiers/propose: Gửi đề xuất biểu phí mới")
    void testProposeFee() throws Exception {
        UUID managerId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(managerId);

        InspectionFeeProposalRequest request = new InspectionFeeProposalRequest();
        request.setBaseInspectionFee(BigDecimal.valueOf(120000));
        request.setTiers(List.of());

        InspectionFeeProposalResponse response = InspectionFeeProposalResponse.builder()
                .id(UUID.randomUUID())
                .status(InspectionFeeProposalStatus.PENDING_APPROVAL)
                .baseInspectionFee(BigDecimal.valueOf(120000))
                .build();

        when(pricingService.proposeFee(eq(managerId), any(InspectionFeeProposalRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/inspector-center/travel-tiers/propose")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.baseInspectionFee").value(120000));
    }
}
