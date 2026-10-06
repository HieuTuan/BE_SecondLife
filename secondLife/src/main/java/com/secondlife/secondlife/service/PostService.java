package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.request.PostSubmitRequest;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.dto.response.PostFinalizeResponse;
import com.secondlife.secondlife.dto.response.PostSubmitResponse;
import com.secondlife.secondlife.entity.Post;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface PostService {
    PostInitResponse initPost(UUID userId, PostInitRequest request);

    PostFinalizeResponse finalizeChatAndDescription(UUID userId, UUID sessionId);

    PostSubmitResponse submitPost(UUID userId, UUID postId, PostSubmitRequest request);

    void approvePost(UUID postId);

    void rejectPost(UUID postId, String reason);

    Page<com.secondlife.secondlife.dto.response.PostDto> getAdminPosts(String status, UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable);

    Page<com.secondlife.secondlife.dto.response.PostDto> getPublicPosts(UUID categoryId, UUID itemId, org.springframework.data.domain.Pageable pageable);

    com.secondlife.secondlife.dto.response.PostDto getPostDetail(UUID postId);

    Page<com.secondlife.secondlife.dto.response.PostDto> getMyPosts(UUID userId, org.springframework.data.domain.Pageable pageable);
}
