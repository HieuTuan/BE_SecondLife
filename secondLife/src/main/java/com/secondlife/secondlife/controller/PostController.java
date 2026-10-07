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

    public PostController(PostService postService, CurrentUserProvider currentUserProvider) {
        this.postService = postService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/init")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ResponseEntity<PostInitResponse> initPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute PostInitRequest request) {
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

    @GetMapping
    public ResponseEntity<com.secondlife.secondlife.common.PageResponse<com.secondlife.secondlife.dto.response.PostDto>> getPublicPosts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID itemId,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(com.secondlife.secondlife.common.PageResponse.from(postService.getPublicPosts(categoryId, itemId, pageable)));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<com.secondlife.secondlife.dto.response.PostDto> getPostDetail(@PathVariable UUID postId) {
        return ResponseEntity.ok(postService.getPostDetail(postId));
    }

    @GetMapping("/my-posts")
    @PreAuthorize("hasAuthority('PROFILE_READ_SELF')")
    public ResponseEntity<com.secondlife.secondlife.common.PageResponse<com.secondlife.secondlife.dto.response.PostDto>> getMyPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            org.springframework.data.domain.Pageable pageable) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(com.secondlife.secondlife.common.PageResponse.from(postService.getMyPosts(userId, pageable)));
    }

    @PostMapping("/{postId}/ai-price-estimation")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ResponseEntity<com.secondlife.secondlife.dto.response.AiPriceEstimationResponse> estimatePrice(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID postId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        String requestId = body != null ? body.get("requestId") : null;
        return ResponseEntity.ok(postService.estimatePrice(userId, postId, requestId));
    }

    @GetMapping("/{postId}/ai-price-estimation")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ResponseEntity<com.secondlife.secondlife.dto.response.AiPriceEstimationResponse> getLatestPriceEstimate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID postId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(postService.estimatePrice(userId, postId, null));
    }

    @PutMapping("/{postId}/draft")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ResponseEntity<com.secondlife.secondlife.dto.response.PostDto> updateDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID postId,
            @RequestBody com.secondlife.secondlife.dto.request.UpdateDraftRequest request) {
        return ResponseEntity.ok(postService.updateDraft(userDetails.getId(), postId, request));
    }

    @PostMapping("/{postId}/accept-description")
    @PreAuthorize("hasAuthority('LISTING_CREATE_SELF')")
    public ResponseEntity<com.secondlife.secondlife.dto.response.PostDto> acceptDescription(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID postId,
            @RequestBody com.secondlife.secondlife.dto.request.AcceptDescriptionRequest request) {
        return ResponseEntity.ok(postService.acceptDescription(userDetails.getId(), postId, request));
    }
}
