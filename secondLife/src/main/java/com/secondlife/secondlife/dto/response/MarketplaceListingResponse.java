package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record MarketplaceListingResponse(UUID id, UUID postId, UUID sellerId, String sellerName, String sellerAvatar, String sellerAddress, UUID categoryId, UUID itemId,
        String title, String brand, String description, String itemCondition, String imageUrl, List<String> imageUrls,
        BigDecimal price, String status, Instant publishedAt,
        Integer shippingWeight, Integer shippingLength, Integer shippingWidth, Integer shippingHeight,
        Boolean isInspected, String tamperSealId, String conditionGrade, String inspectionMethod) {}
