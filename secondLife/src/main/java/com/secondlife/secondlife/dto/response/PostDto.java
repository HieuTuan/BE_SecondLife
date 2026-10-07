package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.entity.Post;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PostDto {
    private UUID id;
    private UUID categoryId;
    private UUID itemId;
    private String title;
    private String description;
    private java.util.List<String> imageUrls;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private BigDecimal price;
    private String itemCondition;
    private String aiDescription;
    private BigDecimal aiSuggestedPrice;
    private String rejectionReason;
    private Boolean listingCreditCharged;
    private SellerSummary user;

    @Data
    @Builder
    public static class SellerSummary {
        private UUID id;
        private String email;
        private String fullName;
        private String avatarUrl;
    }

    public static PostDto fromEntity(Post post) {
        SellerSummary seller = null;
        if (post.getUser() != null) {
            seller = SellerSummary.builder()
                    .id(post.getUser().getId())
                    .email(post.getUser().getEmail())
                    .fullName(post.getUser().getProfile() != null ? post.getUser().getProfile().getFullName() : null)
                    .avatarUrl(post.getUser().getProfile() != null ? post.getUser().getProfile().getAvatarUrl() : null)
                    .build();
        }
        return PostDto.builder()
                .id(post.getId())
                .categoryId(post.getCategoryId())
                .itemId(post.getItemId())
                .title(post.getTitle())
                .description(post.getDescription())
                .imageUrls(post.getImageUrls())
                .status(post.getStatus())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .price(post.getPrice())
                .itemCondition(post.getItemCondition())
                .aiDescription(post.getAiDescription())
                .aiSuggestedPrice(post.getAiSuggestedPrice())
                .rejectionReason(post.getRejectionReason())
                .listingCreditCharged(post.isListingCreditCharged())
                .user(seller)
                .build();
    }
}
