package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.request.PostSubmitRequest;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.dto.response.PostFinalizeResponse;

import java.util.UUID;

public interface PostService {
    PostInitResponse initPost(UUID userId, PostInitRequest request);
    PostFinalizeResponse finalizeChatAndDescription(UUID userId, UUID sessionId);
    void submitPost(UUID userId, UUID postId, PostSubmitRequest request);
    void approvePost(UUID postId);
    void rejectPost(UUID postId, String reason);
    org.springframework.data.domain.Page<com.secondlife.secondlife.entity.Post> getAdminPosts(String status, UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<com.secondlife.secondlife.entity.Post> getPublicPosts(UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable);
}
