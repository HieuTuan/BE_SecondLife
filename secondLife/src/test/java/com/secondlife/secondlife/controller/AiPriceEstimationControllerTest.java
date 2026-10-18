package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.dto.request.AiDirectPriceEstimateRequest;
import com.secondlife.secondlife.service.DirectPriceEstimationService;
import com.secondlife.secondlife.dto.response.AiDirectPriceEstimateResponse;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AiPriceEstimationControllerTest {

    @Mock
    private DirectPriceEstimationService estimationService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AiPriceEstimationController controller = new AiPriceEstimationController(estimationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/ai/estimate-price: Định giá AI thành công")
    void testEstimatePrice() throws Exception {
        AiDirectPriceEstimateRequest request = new AiDirectPriceEstimateRequest(
                "MacBook Pro M1 2020 16GB 512GB",
                "Laptop",
                "Apple",
                "MacBook Pro M1",
                "LIKE_NEW",
                "Máy dùng kỹ còn bảo hành",
                List.of("https://example.com/macbook.jpg")
        );

        AiDirectPriceEstimateResponse suggestion = new AiDirectPriceEstimateResponse(
                new BigDecimal("18000000"),
                new BigDecimal("21000000"),
                new BigDecimal("19500000"),
                "3-5 ngày",
                "gemini-2.5-flash"
        );

        when(estimationService.estimatePrice(any())).thenReturn(suggestion);

        mockMvc.perform(post("/api/v1/ai/estimate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fairPriceMin").value(18000000))
                .andExpect(jsonPath("$.data.fairPriceMax").value(21000000))
                .andExpect(jsonPath("$.data.suggestedPrice").value(19500000))
                .andExpect(jsonPath("$.data.modelVersion").value("gemini-2.5-flash"));
    }

    @Test
    @DisplayName("POST /api/ai/estimate-price: Hỗ trợ alias endpoint cũ tương thích FE")
    void testEstimatePriceAlias() throws Exception {
        AiDirectPriceEstimateRequest request = new AiDirectPriceEstimateRequest(
                "Xe máy Honda Wave Alpha",
                null,
                null,
                null,
                null,
                null,
                null
        );

        AiDirectPriceEstimateResponse suggestion = new AiDirectPriceEstimateResponse(
                new BigDecimal("12000000"),
                new BigDecimal("14000000"),
                new BigDecimal("13000000"),
                "1-2 tuần",
                "gemini-2.5-flash"
        );

        when(estimationService.estimatePrice(any())).thenReturn(suggestion);

        mockMvc.perform(post("/api/ai/estimate-price")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.suggestedPrice").value(13000000));
    }
}
