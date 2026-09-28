package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.request.PostSubmitRequest;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.dto.response.PostFinalizeResponse;
import com.secondlife.secondlife.dto.response.PostSubmitResponse;
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

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<com.secondlife.secondlife.entity.Post>> getPublicPosts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID itemId,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(postService.getPublicPosts(categoryId, itemId, pageable));
    }

    @PostMapping("/init")
    @PreAuthorize("hasAuthority('POST_CREATE')")
    public ResponseEntity<PostInitResponse> initPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @ModelAttribute PostInitRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(postService.initPost(userId, request));
    }

    @PostMapping("/finalize-chat/{sessionId}")
    @PreAuthorize("hasAuthority('POST_CREATE')")
    public ResponseEntity<PostFinalizeResponse> finalizeChat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID sessionId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        PostFinalizeResponse response = postService.finalizeChatAndDescription(userId, sessionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/submit/{postId}")
    @PreAuthorize("hasAuthority('POST_CREATE')")
    public ResponseEntity<PostSubmitResponse> submitPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID postId,
            @RequestBody @jakarta.validation.Valid PostSubmitRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        PostSubmitResponse response = postService.submitPost(userId, postId, request);
        return ResponseEntity.ok(response);
    }
}
