package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AcceptListingDescriptionRequest;
import com.secondlife.secondlife.dto.request.ListingDraftRequest;
import com.secondlife.secondlife.dto.response.ListingDraftResponse;

import java.util.UUID;

public interface ListingDraftService {
    void delete(UUID userId, UUID postId);

    ListingDraftResponse get(UUID userId, UUID postId);

    ListingDraftResponse update(UUID userId, UUID postId, ListingDraftRequest request);

    ListingDraftResponse acceptDescription(UUID userId, UUID postId, AcceptListingDescriptionRequest request);

}
