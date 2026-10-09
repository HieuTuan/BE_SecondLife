package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MarketplaceListingResponse(UUID id, UUID postId, UUID sellerId, UUID categoryId, UUID itemId,
        String title, String description, String itemCondition, String imageUrl, List<String> imageUrls,
        BigDecimal price, String status, Instant publishedAt) {}
