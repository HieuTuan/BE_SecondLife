package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.response.PostInitResponse;

import java.util.UUID;

public interface PostService {
    PostInitResponse initPost(UUID userId, PostInitRequest request);
    String finalizeChatAndDescription(UUID userId, UUID sessionId);
    void submitPost(UUID userId, UUID postId);
    void approvePost(UUID postId);
    void rejectPost(UUID postId, String reason);
    org.springframework.data.domain.Page<com.secondlife.secondlife.entity.Post> getAdminPosts(String status, UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<com.secondlife.secondlife.entity.Post> getPublicPosts(UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable);
}
