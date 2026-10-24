package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AcceptDescriptionRequest;
import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.request.PostSubmitRequest;
import com.secondlife.secondlife.dto.request.UpdateDraftRequest;
import com.secondlife.secondlife.dto.response.AiPriceEstimationResponse;
import com.secondlife.secondlife.dto.response.PostDto;
import com.secondlife.secondlife.dto.response.PostFinalizeResponse;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.dto.response.PostSubmitResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PostService {
    PostInitResponse initPost(UUID userId, PostInitRequest request);

    PostFinalizeResponse finalizeChatAndDescription(UUID userId, UUID sessionId);
    PostFinalizeResponse finalizeChatAndDescription(UUID userId, UUID sessionId, String message);
    PostFinalizeResponse regenerateChatAndDescription(UUID userId, UUID sessionId);
    PostFinalizeResponse regenerateChatAndDescription(UUID userId, UUID sessionId, String message);

    PostSubmitResponse submitPost(UUID userId, UUID postId, PostSubmitRequest request);

    String suggestTitle(UUID userId, UUID postId, String context);

    AiPriceEstimationResponse estimatePrice(UUID userId, UUID postId, String requestId);

    PostDto updateDraft(UUID userId, UUID postId, UpdateDraftRequest request);

    PostDto acceptDescription(UUID userId, UUID postId, AcceptDescriptionRequest request);

    void approvePost(UUID postId);

    void rejectPost(UUID postId, String reason);

    Page<PostDto> getAdminPosts(String status, UUID categoryId, UUID itemId, Pageable pageable);

    Page<PostDto> getPublicPosts(UUID categoryId, UUID itemId, Pageable pageable);

    PostDto getPostDetail(UUID postId);

    Page<PostDto> getMyPosts(UUID userId, Pageable pageable);
}
