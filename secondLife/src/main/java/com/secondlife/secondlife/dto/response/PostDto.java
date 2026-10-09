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
        private String address;
    }

    public static PostDto fromEntity(Post post) {
        SellerSummary seller = null;
        if (post.getUser() != null) {
            String address = null;
            if (post.getUser().getProfile() != null) {
                var profile = post.getUser().getProfile();
                java.util.List<String> addressParts = new java.util.ArrayList<>();
                if (profile.getStreetAddress() != null && !profile.getStreetAddress().isBlank()) addressParts.add(profile.getStreetAddress());
                if (profile.getWard() != null && !profile.getWard().isBlank()) addressParts.add(profile.getWard());
                if (profile.getDistrict() != null && !profile.getDistrict().isBlank()) addressParts.add(profile.getDistrict());
                if (profile.getProvince() != null && !profile.getProvince().isBlank()) addressParts.add(profile.getProvince());
                if (!addressParts.isEmpty()) address = String.join(", ", addressParts);
            }
            seller = SellerSummary.builder()
                    .id(post.getUser().getId())
                    .email(post.getUser().getEmail())
                    .fullName(post.getUser().getProfile() != null ? post.getUser().getProfile().getFullName() : null)
                    .avatarUrl(post.getUser().getProfile() != null ? post.getUser().getProfile().getAvatarUrl() : null)
                    .address(address)
                    .build();
        }
        return PostDto.builder()
                .id(post.getId())
                .categoryId(post.getCategoryId())
                .itemId(post.getItemId())
                .title(post.getTitle())
                .description(post.getDescription())
                .imageUrls(post.getImageUrls() != null ? new java.util.ArrayList<>(post.getImageUrls()) : null)
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
