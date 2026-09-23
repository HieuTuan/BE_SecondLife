package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.PostService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<PostInitResponse> initPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody PostInitRequest request) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(postService.initPost(userId, request));
    }

    @PostMapping("/finalize-chat/{sessionId}")
    public ResponseEntity<String> finalizeChat(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID sessionId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        String description = postService.finalizeChatAndDescription(userId, sessionId);
        return ResponseEntity.ok(description);
    }

    @PostMapping("/submit/{postId}")
    public ResponseEntity<String> submitPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID postId) {
        UUID userId = currentUserProvider.resolveUserId(userDetails);
        postService.submitPost(userId, postId);
        return ResponseEntity.ok("Post submitted successfully");
    }
}
