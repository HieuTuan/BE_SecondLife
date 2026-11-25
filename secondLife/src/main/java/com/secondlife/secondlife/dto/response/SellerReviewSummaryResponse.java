package com.secondlife.secondlife.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@Builder
public class SellerReviewSummaryResponse {
    private UUID sellerId;
    private double averageRating;
    private long totalReviews;
    private Map<Integer, Long> ratingCounts;
}
