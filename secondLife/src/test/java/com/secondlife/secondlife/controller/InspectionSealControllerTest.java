package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.dto.request.InspectionSealScanRequest;
import com.secondlife.secondlife.dto.response.InspectionSealLogResponse;
import com.secondlife.secondlife.dto.response.InspectionSealVerificationResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.InspectionEscrowService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
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
class InspectionSealControllerTest {

    @Mock
    private InspectionService inspectionService;

    @Mock
    private InspectionPricingService pricingService;

    @Mock
    private InspectionEscrowService escrowService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        InspectionController controller = new InspectionController(
                inspectionService,
                pricingService,
                escrowService,
                currentUserProvider
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/inspection/seal/{tamperSealId}: Xác thực tem niêm phong hợp lệ")
    void testGetSealVerification() throws Exception {
        String sealId = "SL-SEAL-20261014-9988";
        InspectionSealVerificationResponse mockResponse = InspectionSealVerificationResponse.builder()
                .tamperSealId(sealId)
                .inspectionOrderId(UUID.randomUUID())
                .postId(UUID.randomUUID())
                .productTitle("iPhone 13 Pro Max")
                .conditionScore(92)
                .conditionGrade("A")
                .inspectionStatus("PASSED")
                .inspectionCenterName("Trung tâm kiểm định SecondLife")
                .inspectorName("Nguyễn Văn A")
                .inspectedAt(Instant.now())
                .currentSealStatus("INTACT")
                .isOfficialSecondLifeSeal(true)
                .scanHistory(List.of())
                .build();

        when(inspectionService.getSealVerification(sealId)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/inspection/seal/" + sealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tamperSealId").value(sealId))
                .andExpect(jsonPath("$.data.currentSealStatus").value("INTACT"))
                .andExpect(jsonPath("$.data.officialSecondLifeSeal").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/inspection/seal/scan: Ghi nhận lịch sử quét mã tem QR")
    void testRecordSealScan() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(actorId);

        InspectionSealScanRequest request = new InspectionSealScanRequest();
        request.setTamperSealId("SL-SEAL-20261014-9988");
        request.setScanStage("CARRIER_HANDOVER");
        request.setSealCondition("INTACT");
        request.setLocationText("Hub Cầu Giấy");
        request.setNote("Tem nguyên vẹn, shipper nhận hàng");

        InspectionSealLogResponse mockLog = InspectionSealLogResponse.builder()
                .id(UUID.randomUUID())
                .tamperSealId(request.getTamperSealId())
                .scannedBy(actorId)
                .scannedByName("Shipper B")
                .scanStage("CARRIER_HANDOVER")
                .sealCondition("INTACT")
                .locationText("Hub Cầu Giấy")
                .note("Tem nguyên vẹn, shipper nhận hàng")
                .scannedAt(Instant.now())
                .build();

        when(inspectionService.recordSealScan(eq(actorId), any())).thenReturn(mockLog);

        mockMvc.perform(post("/api/v1/inspection/seal/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tamperSealId").value("SL-SEAL-20261014-9988"))
                .andExpect(jsonPath("$.data.scanStage").value("CARRIER_HANDOVER"))
                .andExpect(jsonPath("$.data.sealCondition").value("INTACT"));
    }
}
