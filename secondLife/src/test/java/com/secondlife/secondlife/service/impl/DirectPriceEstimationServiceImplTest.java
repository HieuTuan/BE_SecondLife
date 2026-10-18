package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AiDirectPriceEstimateRequest;
import com.secondlife.secondlife.dto.response.AiDirectPriceEstimateResponse;
import com.secondlife.secondlife.integration.ai.AiPriceProvider;
import com.secondlife.secondlife.mapper.AiPriceEstimationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectPriceEstimationServiceImplTest {

    @Mock
    private AiPriceProvider priceProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiPriceEstimationMapper mapper = MapperTestSupport.mapper(AiPriceEstimationMapper.class);
    private final AiPriceProvider.PriceSuggestion suggestion = new AiPriceProvider.PriceSuggestion(
            new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("150"), "3 days", "test-model");

    @Test
    void forwardsImagesAndTheFullSnapshotAndPreservesResponseFields() {
        AiDirectPriceEstimateRequest request = new AiDirectPriceEstimateRequest(
                "Phone",
                "Mobile",
                "Brand",
                "Model",
                "LIKE_NEW",
                "Good condition",
                List.of("https://example.com/phone.jpg")
        );
        when(priceProvider.estimate(anyString(), eq(request.imageUrls()))).thenReturn(suggestion);
        DirectPriceEstimationServiceImpl service = new DirectPriceEstimationServiceImpl(priceProvider, objectMapper, mapper);

        AiDirectPriceEstimateResponse response = service.estimatePrice(request);

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(priceProvider).estimate(snapshot.capture(), eq(request.imageUrls()));
        verify(priceProvider, never()).estimate(anyString());
        JsonNode json = objectMapper.readTree(snapshot.getValue());
        assertEquals(request.title(), json.path("title").asText());
        assertEquals(request.category(), json.path("category").asText());
        assertEquals(request.brand(), json.path("brand").asText());
        assertEquals(request.model(), json.path("model").asText());
        assertEquals(request.itemCondition(), json.path("itemCondition").asText());
        assertEquals(request.description(), json.path("description").asText());
        assertEquals(request.imageUrls().getFirst(), json.path("imageUrls").get(0).asText());
        assertEquals(suggestion.min(), response.fairPriceMin());
        assertEquals(suggestion.max(), response.fairPriceMax());
        assertEquals(suggestion.suggested(), response.suggestedPrice());
        assertEquals(suggestion.expectedSellTime(), response.expectedSellTime());
        assertEquals(suggestion.modelVersion(), response.modelVersion());
    }

    @Test
    void usesTextEstimationWhenImagesAreEmptyAndOmitsAbsentOptionalFields() {
        AiDirectPriceEstimateRequest request = new AiDirectPriceEstimateRequest(
                "Bike",
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
        when(priceProvider.estimate(anyString())).thenReturn(suggestion);
        DirectPriceEstimationServiceImpl service = new DirectPriceEstimationServiceImpl(priceProvider, objectMapper, mapper);

        service.estimatePrice(request);

        ArgumentCaptor<String> snapshot = ArgumentCaptor.forClass(String.class);
        verify(priceProvider).estimate(snapshot.capture());
        verify(priceProvider, never()).estimate(anyString(), any());
        JsonNode json = objectMapper.readTree(snapshot.getValue());
        assertEquals("Bike", json.path("title").asText());
        assertEquals(2, json.size());
    }

    @Test
    void preservesTheTitleAndDescriptionFallbackWhenSnapshotSerializationFails() {
        AiDirectPriceEstimateRequest request = new AiDirectPriceEstimateRequest(
                "Bike",
                null,
                null,
                null,
                null,
                "Used daily",
                null
        );
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new IllegalStateException("cannot serialize"));
        when(priceProvider.estimate("Bike Used daily")).thenReturn(suggestion);
        DirectPriceEstimationServiceImpl service = new DirectPriceEstimationServiceImpl(priceProvider, failingMapper, mapper);

        service.estimatePrice(request);

        verify(priceProvider).estimate("Bike Used daily");
    }
}
