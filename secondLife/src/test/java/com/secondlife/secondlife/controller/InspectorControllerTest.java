package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionOrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.InspectionChecklistService;
import com.secondlife.secondlife.service.InspectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InspectorControllerTest {

    @Mock
    private InspectionService inspectionService;

    @Mock
    private InspectionChecklistService checklistService;

    @Mock
    private InspectionOrderRepository orderRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private InspectorController inspectorController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());

        mockMvc = MockMvcBuilders.standaloneSetup(inspectorController)
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new org.springframework.data.web.PageableHandlerMethodArgumentResolver()
                )
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/inspector/orders: Trả về danh sách đơn kiểm định của KTV")
    void testGetMyAssignedOrders() throws Exception {
        UUID inspectorId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(inspectorId);

        InspectionOrderResponse order = InspectionOrderResponse.builder()
                .id(UUID.randomUUID())
                .status("DISPATCHED")
                .build();

        when(inspectionService.getMyOrders(inspectorId)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/v1/inspector/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("DISPATCHED"));
    }

    @Test
    @DisplayName("GET /api/v1/inspector/orders/{orderId}/checklist: Nạp biểu mẫu checklist theo category sản phẩm")
    void testGetChecklistForOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        Post post = new Post();
        post.setCategoryId(categoryId);

        InspectionOrder order = new InspectionOrder();
        order.setId(orderId);
        order.setPost(post);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        InspectionChecklistResponse checklist = InspectionChecklistResponse.builder()
                .title("Checklist Điện thoại di động")
                .categoryId(categoryId)
                .items(List.of(
                        InspectionChecklistItemResponse.builder()
                                .itemTitle("Màn hình cảm ứng")
                                .mandatory(true)
                                .build()
                ))
                .build();

        when(checklistService.getByCategoryId(categoryId)).thenReturn(checklist);

        mockMvc.perform(get("/api/v1/inspector/orders/" + orderId + "/checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Checklist Điện thoại di động"))
                .andExpect(jsonPath("$.items[0].itemTitle").value("Màn hình cảm ứng"));
    }

    @Test
    @DisplayName("POST /api/v1/inspector/orders/{orderId}/result: Inspector nộp kết quả PASSED kèm video proof")
    void testSubmitInspectionResult() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID inspectorId = UUID.randomUUID();

        when(currentUserProvider.resolveUserId(any())).thenReturn(inspectorId);

        InspectionResultRequest request = new InspectionResultRequest();
        request.setStatus("PASSED");
        request.setNote("Máy zin 100%, pin tốt");
        request.setVideoProofUrl("https://res.cloudinary.com/test/video.mp4");

        InspectionOrderResponse response = InspectionOrderResponse.builder()
                .id(orderId)
                .status("PASSED")
                .videoProofUrl("https://res.cloudinary.com/test/video.mp4")
                .build();

        when(inspectionService.submitResult(eq(orderId), eq(inspectorId), any(InspectionResultRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/inspector/orders/" + orderId + "/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.videoProofUrl").value("https://res.cloudinary.com/test/video.mp4"));
    }

    @Test
    @DisplayName("POST /api/v1/inspector/orders/{orderId}/submit-report: FE gọi submit-report alias kèm conditionScore và tamperSealId")
    void testSubmitInspectionReportAlias() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID inspectorId = UUID.randomUUID();

        when(currentUserProvider.resolveUserId(any())).thenReturn(inspectorId);

        InspectionResultRequest request = new InspectionResultRequest();
        request.setStatus("PASSED");
        request.setNote("Bo mạch nguyên bản");
        request.setConditionScore(95);
        request.setConditionGrade("Like New");
        request.setTamperSealId("SL-SEAL-8829102");
        request.setBarcode("SL-BC-998877");

        InspectionOrderResponse response = InspectionOrderResponse.builder()
                .id(orderId)
                .status("PASSED")
                .conditionScore(95)
                .conditionGrade("Like New")
                .tamperSealId("SL-SEAL-8829102")
                .barcode("SL-BC-998877")
                .build();

        when(inspectionService.submitResult(eq(orderId), eq(inspectorId), any(InspectionResultRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/inspector/orders/" + orderId + "/submit-report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.conditionScore").value(95))
                .andExpect(jsonPath("$.conditionGrade").value("Like New"))
                .andExpect(jsonPath("$.tamperSealId").value("SL-SEAL-8829102"))
                .andExpect(jsonPath("$.barcode").value("SL-BC-998877"));
    }

    @Test
    @DisplayName("POST /api/v1/inspector/orders/{orderId}/start: KTV bắt đầu kiểm định chuyển trạng thái sang INSPECTING")
    void testStartInspection() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID inspectorId = UUID.randomUUID();

        when(currentUserProvider.resolveUserId(any())).thenReturn(inspectorId);

        InspectionOrderResponse response = InspectionOrderResponse.builder()
                .id(orderId)
                .status("INSPECTING")
                .build();

        when(inspectionService.startInspection(eq(inspectorId), eq(orderId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/inspector/orders/" + orderId + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INSPECTING"));
    }

    @Test
    @DisplayName("GET /api/v1/inspector/stats: Lấy thống kê hiệu suất KPI và thu nhập thành công")
    void testGetMyStats() throws Exception {
        UUID inspectorId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(inspectorId);

        InspectorStatsResponse response = InspectorStatsResponse.builder()
                .inspectorId(inspectorId)
                .inspectorName("Trần Kỹ Thuật")
                .badgeCode("SL-TECH-089A")
                .skillLevel("Kỹ thuật viên Thẩm định Bậc 3")
                .completedJobs(15)
                .passedJobs(12)
                .failedJobs(3)
                .averageRating(4.9)
                .onTimeRate(98.5)
                .monthlyEarnings(new BigDecimal("3500000"))
                .laborFeeEarned(new BigDecimal("2800000"))
                .travelAllowanceEarned(new BigDecimal("700000"))
                .pendingSettlementAmount(new BigDecimal("500000"))
                .build();

        when(inspectionService.getInspectorStats(eq(inspectorId), eq(10), eq(2026))).thenReturn(response);

        mockMvc.perform(get("/api/v1/inspector/stats?month=10&year=2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badgeCode").value("SL-TECH-089A"))
                .andExpect(jsonPath("$.completedJobs").value(15))
                .andExpect(jsonPath("$.monthlyEarnings").value(3500000))
                .andExpect(jsonPath("$.averageRating").value(4.9));
    }

    @Test
    @DisplayName("GET /api/v1/inspector/payouts: Lấy danh sách lịch sử đối soát thù lao phân trang thành công")
    void testGetPayoutHistory() throws Exception {
        UUID inspectorId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(inspectorId);

        InspectorPayoutResponse payout = InspectorPayoutResponse.builder()
                .payoutId("PO-ABC12345")
                .orderId(UUID.randomUUID())
                .postTitle("Samsung Galaxy S23 Ultra")
                .orderType(InspectionOrderType.AT_HOME)
                .laborFee(new BigDecimal("150000"))
                .travelFee(new BigDecimal("30000"))
                .totalPayout(new BigDecimal("180000"))
                .status("SETTLED")
                .build();

        Page<InspectorPayoutResponse> page = new PageImpl<>(List.of(payout), PageRequest.of(0, 10), 1);
        when(inspectionService.getPayoutHistory(eq(inspectorId), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/inspector/payouts?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].payoutId").value("PO-ABC12345"))
                .andExpect(jsonPath("$.content[0].totalPayout").value(180000))
                .andExpect(jsonPath("$.content[0].status").value("SETTLED"));
    }
}
