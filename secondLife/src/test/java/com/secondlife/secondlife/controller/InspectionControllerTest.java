package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.InspectionEscrowService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.InspectionService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InspectionControllerTest {

    @Mock
    private InspectionService inspectionService;

    @Mock
    private InspectionPricingService pricingService;

    @Mock
    private InspectionEscrowService escrowService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private InspectionController inspectionController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(inspectionController)
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver(),
                        new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/inspection/quote: Trả về báo giá 200 OK với đầy đủ 2 gói AT_HOME và AT_CENTER")
    void testGetQuote() throws Exception {
        UUID postId = UUID.randomUUID();
        InspectionQuoteRequest request = InspectionQuoteRequest.builder()
                .postId(postId)
                .build();

        InspectionQuoteResponse response = InspectionQuoteResponse.builder()
                .postId(postId)
                .postTitle("iPhone 13 Pro Max")
                .postPrice(BigDecimal.valueOf(15000000))
                .baseInspectionFee(BigDecimal.valueOf(100000))
                .travelDistanceKm(BigDecimal.valueOf(10.5))
                .travelFeeRoundTrip(BigDecimal.valueOf(65000))
                .atHomeTotalFee(BigDecimal.valueOf(165000))
                .shippingToCenterFee(BigDecimal.valueOf(50000))
                .returnShippingReserveFee(BigDecimal.valueOf(50000))
                .atCenterTotalFee(BigDecimal.valueOf(200000))
                .build();

        when(pricingService.quote(any(InspectionQuoteRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/inspection/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(postId.toString()))
                .andExpect(jsonPath("$.postTitle").value("iPhone 13 Pro Max"))
                .andExpect(jsonPath("$.baseInspectionFee").value(100000))
                .andExpect(jsonPath("$.atHomeTotalFee").value(165000))
                .andExpect(jsonPath("$.atCenterTotalFee").value(200000));
    }

    @Test
    @DisplayName("GET /api/v1/inspection/orders/{orderId}: Trả về chi tiết đơn kiểm định")
    void testGetOrderById() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        InspectionOrderResponse response = InspectionOrderResponse.builder()
                .id(orderId)
                .status(InspectionOrderStatus.WAITING_SELLER_APPROVAL.name())
                .inspectionFee(BigDecimal.valueOf(100000))
                .build();

        when(currentUserProvider.resolveUserId(any())).thenReturn(actorId);
        when(inspectionService.getOrderById(actorId, orderId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/inspection/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value(InspectionOrderStatus.WAITING_SELLER_APPROVAL.name()))
                .andExpect(jsonPath("$.inspectionFee").value(100000));
    }

    @Test
    @DisplayName("POST /api/v1/inspection/quote: Kiểm tra trả về cả cấu trúc lồng nhau atHomePackage và atCenterPackage")
    void testGetQuoteWithNestedPackages() throws Exception {
        UUID postId = UUID.randomUUID();
        InspectionQuoteRequest request = InspectionQuoteRequest.builder()
                .postId(postId)
                .build();

        InspectionQuoteResponse response = InspectionQuoteResponse.builder()
                .postId(postId)
                .postTitle("Tủ lạnh Samsung Inverter")
                .baseInspectionFee(BigDecimal.valueOf(100000))
                .atHomePackage(InspectionQuoteResponse.AtHomePackageDto.builder()
                        .distanceKm(BigDecimal.valueOf(35.0))
                        .travelFeeOneWay(BigDecimal.valueOf(117500))
                        .travelFeeRoundTrip(BigDecimal.valueOf(235000))
                        .totalFee(BigDecimal.valueOf(335000))
                        .suggestedSplit50(BigDecimal.valueOf(167500))
                        .build())
                .atCenterPackage(InspectionQuoteResponse.AtCenterPackageDto.builder()
                        .shippingToCenterFee(BigDecimal.valueOf(45000))
                        .returnReserveFee(BigDecimal.valueOf(45000))
                        .totalFee(BigDecimal.valueOf(190000))
                        .suggestedSplit50(BigDecimal.valueOf(95000))
                        .build())
                .build();

        when(pricingService.quote(any(InspectionQuoteRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/inspection/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFee").value(100000))
                .andExpect(jsonPath("$.atHomePackage.totalFee").value(335000))
                .andExpect(jsonPath("$.atHomePackage.suggestedSplit50").value(167500))
                .andExpect(jsonPath("$.atCenterPackage.totalFee").value(190000))
                .andExpect(jsonPath("$.atCenterPackage.suggestedSplit50").value(95000));
    }

    @Test
    @DisplayName("POST /api/v1/inspection/requests: Buyer gửi buyerConsentZinLoss boolean alias thành công")
    void testRequestInspectionWithZinConsentAlias() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();

        String payload = """
                {
                    "postId": "%s",
                    "orderType": "AT_HOME",
                    "feePayer": "SPLIT_50_50",
                    "buyerConsentZinLoss": true
                }
                """.formatted(postId);

        InspectionOrderResponse response = InspectionOrderResponse.builder()
                .id(UUID.randomUUID())
                .postId(postId)
                .status(InspectionOrderStatus.WAITING_SELLER_APPROVAL.name())
                .buyerConsentZin(true)
                .build();

        when(currentUserProvider.resolveUserId(any())).thenReturn(buyerId);
        when(inspectionService.requestInspection(eq(buyerId), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/inspection/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(InspectionOrderStatus.WAITING_SELLER_APPROVAL.name()))
                .andExpect(jsonPath("$.buyerConsentZin").value(true));
    }
}
