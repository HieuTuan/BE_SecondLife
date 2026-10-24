package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.AiPriceEstimationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface AiValuationService {
    AiPriceEstimationResponse create(UUID userId, UUID postId, UUID requestId);

    AiPriceEstimationResponse latest(UUID userId, UUID postId);

    Page<AiPriceEstimationResponse> history(UUID userId, UUID postId, Pageable pageable);

}
