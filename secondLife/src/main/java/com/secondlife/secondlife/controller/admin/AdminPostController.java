package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/posts")
public class AdminPostController {

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/{postId}/approve")
    public ResponseEntity<String> approvePost(@PathVariable UUID postId) {
        postService.approvePost(postId);
        return ResponseEntity.ok("Post approved successfully");
    }

    @PostMapping("/{postId}/reject")
    public ResponseEntity<String> rejectPost(
            @PathVariable UUID postId,
            @RequestParam(required = false) String reason) {
        postService.rejectPost(postId, reason);
        return ResponseEntity.ok("Post rejected successfully");
    }
}
