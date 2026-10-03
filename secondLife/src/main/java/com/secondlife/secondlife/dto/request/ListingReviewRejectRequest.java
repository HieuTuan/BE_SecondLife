package com.secondlife.secondlife.dto.request;

public record ListingReviewRejectRequest(@jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 2000) String reason) { }
