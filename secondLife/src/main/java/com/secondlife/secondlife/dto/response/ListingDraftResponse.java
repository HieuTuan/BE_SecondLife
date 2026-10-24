package com.secondlife.secondlife.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ListingDraftResponse(UUID postId, UUID categoryId, UUID itemId, String title,
        String description, String itemCondition, String imageUrl, List<String> imageUrls, BigDecimal price,
        BigDecimal aiSuggestedPrice, String status, String rejectionReason, boolean listingCreditCharged,
        String aiDescription, boolean descriptionAccepted, String reviewReason, List<UUID> duplicateMatches) {

}
