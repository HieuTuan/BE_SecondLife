package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.PostInitRequest;
import com.secondlife.secondlife.dto.PostInitResponse;
import com.secondlife.secondlife.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/init")
    public ResponseEntity<PostInitResponse> initPost(
            @RequestAttribute("userId") UUID userId,
            @RequestBody PostInitRequest request) {
        return ResponseEntity.ok(postService.initPost(userId, request));
    }

    @PostMapping("/finalize-chat/{sessionId}")
    public ResponseEntity<String> finalizeChat(
            @RequestAttribute("userId") UUID userId,
            @PathVariable UUID sessionId) {
        String description = postService.finalizeChatAndDescription(userId, sessionId);
        return ResponseEntity.ok(description);
    }

    @PostMapping("/submit/{postId}")
    public ResponseEntity<String> submitPost(
            @RequestAttribute("userId") UUID userId,
            @PathVariable UUID postId) {
        postService.submitPost(userId, postId);
        return ResponseEntity.ok("Post submitted successfully");
    }
}
