package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.request.PostSubmitRequest;
import com.secondlife.secondlife.dto.response.PostFinalizeResponse;
import com.secondlife.secondlife.dto.response.PostSubmitResponse;
import jakarta.validation.Valid;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.PostService;
import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import com.secondlife.secondlife.service.MarketplaceListingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;
    private final CurrentUserProvider currentUserProvider;
    private final MarketplaceListingService marketplaceListingService;

    public PostController(PostService postService, CurrentUserProvider currentUserProvider, MarketplaceListingService marketplaceListingService) {
        this.postService = postService;
        this.currentUserProvider = currentUserProvider;
        this.marketplaceListingService = marketplaceListingService;
    }

    @GetMapping
    public ApiResponse<Page<MarketplaceListingResponse>> listPosts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        return ApiResponse.success(marketplaceListingService.list(categoryId, itemId,
                PageRequest.of(safePage, safeSize, Sort.by("publishedAt").descending())));
    }

    @PostMapping(value = "/init", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ResponseEntity<PostInitResponse> initPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute PostInitRequest request) {

        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(postService.initPost(userId, request));
    }

    @PostMapping("/finalize-chat/{sessionId}")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ResponseEntity<PostFinalizeResponse> finalizeChat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID sessionId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(postService.finalizeChatAndDescription(userId, sessionId));
    }

    @PostMapping("/submit/{postId}")
    @PreAuthorize("hasAuthority('LISTING_PUBLISH_SELF')")
    public ResponseEntity<PostSubmitResponse> submitPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID postId,
            @Valid @RequestBody PostSubmitRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(postService.submitPost(userId, postId, request));
    }

    @GetMapping("/my-posts")
    @PreAuthorize("hasAuthority('PROFILE_READ_SELF')")
    public ResponseEntity<org.springframework.data.domain.Page<com.secondlife.secondlife.dto.response.PostDto>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            org.springframework.data.domain.Pageable pageable) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(postService.getMyPosts(userId, pageable));
    }
}
