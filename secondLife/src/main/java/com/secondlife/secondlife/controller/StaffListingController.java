package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.ListingReviewRejectRequest;
import com.secondlife.secondlife.dto.response.ListingDraftResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.ListingReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff/listings")
@RequiredArgsConstructor
@PreAuthorize("(hasRole('STAFF') and hasAuthority('STAFF_LISTING_REVIEW')) or (hasRole('ADMIN') and hasAuthority('POST_REVIEW'))")
public class StaffListingController {
    private final ListingReviewService reviews;
    private final CurrentUserProvider currentUser;
    @GetMapping
    public ApiResponse<Page<ListingDraftResponse>> queue(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new com.secondlife.secondlife.exception.BadRequestException("Invalid page or size");
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
