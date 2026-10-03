package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts/{postId}")
@RequiredArgsConstructor
public class ListingValuationController {
    private final AiValuationService valuations;
    private final ListingDraftService drafts;
    private final CurrentUserProvider currentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ApiResponse<ListingDraftResponse> get(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID postId) {
        return ApiResponse.success(drafts.get(currentUser.resolveUserId(user), postId));
    }
    @PutMapping("/draft")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ApiResponse<ListingDraftResponse> update(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID postId, @Valid @RequestBody ListingDraftRequest request) {
        return ApiResponse.success(drafts.update(currentUser.resolveUserId(user), postId, request));
    }
    @PostMapping("/ai-price-estimation")
    @PreAuthorize("hasAuthority('LISTING_VALUATION_SELF')")
    public ResponseEntity<ApiResponse<AiPriceEstimationResponse>> estimate(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID postId, @Valid @RequestBody AiPriceEstimationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(valuations.create(currentUser.resolveUserId(user), postId, request.requestId())));
    }

    @PostMapping("/accept-description")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ApiResponse<ListingDraftResponse> acceptDescription(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID postId, @Valid @RequestBody(required = false) AcceptListingDescriptionRequest request) {
        return ApiResponse.success(drafts.acceptDescription(currentUser.resolveUserId(user), postId, request));
    }
    @GetMapping("/ai-price-estimation")
    @PreAuthorize("hasAuthority('LISTING_VALUATION_SELF')")
    public ApiResponse<AiPriceEstimationResponse> latest(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID postId) {
        return ApiResponse.success(valuations.latest(currentUser.resolveUserId(user), postId));
    }
    @GetMapping("/ai-price-estimation/history")
    @PreAuthorize("hasAuthority('LISTING_VALUATION_SELF')")
    public ApiResponse<Page<AiPriceEstimationResponse>> history(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID postId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new com.secondlife.secondlife.exception.BadRequestException("page >= 0 and size between 1 and 100 are required");
        return ApiResponse.success(valuations.history(currentUser.resolveUserId(user), postId, PageRequest.of(page, size)));
    }
}
