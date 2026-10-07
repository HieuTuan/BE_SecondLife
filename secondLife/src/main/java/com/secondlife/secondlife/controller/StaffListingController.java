package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.ListingReviewRejectRequest;
import com.secondlife.secondlife.dto.response.ListingDraftResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ListingReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/listings")
@PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_LISTING_REVIEW')) or (hasRole('ADMIN') and hasAuthority('POST_REVIEW'))")
public class StaffListingController {
    private final ListingReviewService reviews;
    private final CurrentUserProvider currentUser;
    private final int maxPageSize;

    public StaffListingController(ListingReviewService reviews, CurrentUserProvider currentUser,
            @Value("${app.pagination.max-size}") int maxPageSize) {
        this.reviews = reviews;
        this.currentUser = currentUser;
        this.maxPageSize = maxPageSize;
    }

    @GetMapping
    public ApiResponse<Page<ListingDraftResponse>> queue(@RequestParam(defaultValue = "${app.pagination.default-page}") int page,
            @RequestParam(defaultValue = "${app.pagination.default-size}") int size) {
        if (page < 0 || size < 1 || size > maxPageSize) throw new com.secondlife.secondlife.exception.BadRequestException("Invalid page or size");
        return ApiResponse.success(reviews.queue(PageRequest.of(page, size, Sort.by("createdAt").ascending())));
    }
    @GetMapping("/{postId}")
    public ApiResponse<ListingDraftResponse> detail(@PathVariable UUID postId) { return ApiResponse.success(reviews.detail(postId)); }
    @PostMapping("/{postId}/approve")
    public ApiResponse<ListingDraftResponse> approve(@PathVariable UUID postId, @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.success(reviews.approve(postId, currentUser.resolveUserId(user)));
    }
    @PostMapping("/{postId}/reject")
    public ApiResponse<ListingDraftResponse> reject(@PathVariable UUID postId, @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody ListingReviewRejectRequest request) {
        return ApiResponse.success(reviews.reject(postId, currentUser.resolveUserId(user), request.reason()));
    }
}
