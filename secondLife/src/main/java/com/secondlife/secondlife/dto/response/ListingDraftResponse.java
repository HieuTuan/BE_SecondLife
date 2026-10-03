package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.Post;
import java.math.BigDecimal;
import java.util.UUID;

public record ListingDraftResponse(UUID postId, UUID categoryId, UUID itemId, String title,
        String description, String itemCondition, String imageUrl, java.util.List<String> imageUrls, BigDecimal price,
        BigDecimal aiSuggestedPrice, String status, String rejectionReason, boolean listingCreditCharged,
        String aiDescription, boolean descriptionAccepted, String reviewReason, java.util.List<UUID> duplicateMatches) {
    public static ListingDraftResponse from(Post p) {
        return new ListingDraftResponse(p.getId(), p.getCategoryId(), p.getItemId(), p.getTitle(),
                p.getDescription(), p.getItemCondition(), p.getImageUrl(), p.getImageUrls(), p.getPrice(), p.getAiSuggestedPrice(),
                p.getStatus(), p.getRejectionReason(), p.isListingCreditCharged(), p.getAiDescription(),
                p.isDescriptionAccepted(), p.getReviewReason(), p.getDuplicateMatches());
    }
}
