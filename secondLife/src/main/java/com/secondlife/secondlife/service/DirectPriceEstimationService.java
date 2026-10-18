package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AiDirectPriceEstimateRequest;
import com.secondlife.secondlife.dto.response.AiDirectPriceEstimateResponse;

public interface DirectPriceEstimationService {
    AiDirectPriceEstimateResponse estimatePrice(AiDirectPriceEstimateRequest request);
}
