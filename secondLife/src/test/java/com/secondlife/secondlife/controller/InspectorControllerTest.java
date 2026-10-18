package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistItemResponse;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.InspectionOrderChecklistService;
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
import tools.jackson.databind.ObjectMapper;

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
class InspectorControllerTest {

    @Mock
    private InspectionService inspectionService;

    @Mock
    private InspectionOrderChecklistService orderChecklistService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private InspectorController inspectorController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inspectorController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
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

        when(orderChecklistService.getForOrder(orderId)).thenReturn(checklist);

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

        InspectionResultRequest request = new InspectionResultRequest(
                "PASSED",
                "Máy zin 100%, pin tốt",
                "https://res.cloudinary.com/test/video.mp4",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

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

        InspectionResultRequest request = new InspectionResultRequest(
                "PASSED",
                "Bo mạch nguyên bản",
                null,
                null,
                null,
                null,
                95,
                "Like New",
                "SL-SEAL-8829102",
                "SL-BC-998877",
                null
        );

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
}
