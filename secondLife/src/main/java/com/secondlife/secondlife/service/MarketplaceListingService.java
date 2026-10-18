package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface MarketplaceListingService {
    Page<MarketplaceListingResponse> list(UUID categoryId, UUID itemId, Pageable pageable);

    MarketplaceListingResponse get(UUID postId);

}
