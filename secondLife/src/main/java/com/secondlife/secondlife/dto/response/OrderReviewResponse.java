package com.secondlife.secondlife.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class OrderReviewResponse {
    private UUID id;
    private UUID orderId;
    private UUID buyerId;
    private String buyerName;
    private String buyerAvatar;
    private UUID sellerId;
    private String sellerName;
    private UUID postId;
    private String productTitle;
    private String productImage;
    private String conditionGrade;
    private Integer rating;
    private String comment;
    private List<String> tags;
    private List<String> photos;
    private boolean verifiedPurchase;
    private int editCount;
    private int maxEdits;
    private boolean canEdit;
    private boolean canDelete;
    private Long remainingEditSeconds;
    private Instant deadline;
    private Instant createdAt;
    private Instant updatedAt;
}
