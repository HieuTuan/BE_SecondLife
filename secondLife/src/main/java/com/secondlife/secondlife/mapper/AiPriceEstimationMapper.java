package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.AiDirectPriceEstimateResponse;
import com.secondlife.secondlife.dto.response.AiPriceEstimationResponse;
import com.secondlife.secondlife.entity.AiPriceEstimate;
import com.secondlife.secondlife.integration.ai.AiPriceProvider;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class AiPriceEstimationMapper {
    private final Delegate delegate;

    public AiPriceEstimationMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public AiDirectPriceEstimateResponse toResponse(AiPriceProvider.PriceSuggestion suggestion) {
        return delegate.toResponse(suggestion);
    }

    public AiPriceEstimationResponse toEstimateResponse(AiPriceEstimate estimate) {
        return delegate.toEstimateResponse(estimate);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "fairPriceMin", source = "min")
        @Mapping(target = "fairPriceMax", source = "max")
        @Mapping(target = "suggestedPrice", source = "suggested")
        AiDirectPriceEstimateResponse toResponse(AiPriceProvider.PriceSuggestion suggestion);

        @Mapping(target = "estimateId", source = "id")
        AiPriceEstimationResponse toEstimateResponse(AiPriceEstimate estimate);
    }
}
