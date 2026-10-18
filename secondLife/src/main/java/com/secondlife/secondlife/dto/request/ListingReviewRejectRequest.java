package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ListingReviewRejectRequest(@NotBlank
        @Size(max = 2000) String reason) { }
