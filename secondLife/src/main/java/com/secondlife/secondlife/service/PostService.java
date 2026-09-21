package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.PostInitRequest;
import com.secondlife.secondlife.dto.PostInitResponse;

import java.util.UUID;

public interface PostService {
    PostInitResponse initPost(UUID userId, PostInitRequest request);
}
