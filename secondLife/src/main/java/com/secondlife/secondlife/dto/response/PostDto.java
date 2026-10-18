package com.secondlife.secondlife.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record PostDto(
    UUID id,
    UUID categoryId,
    UUID itemId,
    String title,
    String description,
    List<String> imageUrls,
    String status,
    Instant createdAt,
    Instant updatedAt,
    BigDecimal price,
    String itemCondition,
    String aiDescription,
    BigDecimal aiSuggestedPrice,
    String rejectionReason,
    Boolean listingCreditCharged,
    Integer shippingWeight,
    Integer shippingLength,
    Integer shippingWidth,
    Integer shippingHeight,
    SellerSummary user
) {

    @Builder
    public record SellerSummary(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        String address
    ) {
    }
}
