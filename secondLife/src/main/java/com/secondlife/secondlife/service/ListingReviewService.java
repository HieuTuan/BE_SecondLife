package com.secondlife.secondlife.service;

import org.springframework.data.domain.Pageable;

import org.springframework.data.domain.Page;

import com.secondlife.secondlife.dto.response.ListingDraftResponse;
import java.util.UUID;

public interface ListingReviewService {
    Page<ListingDraftResponse> queue(Pageable pageable);

    ListingDraftResponse detail(UUID id);

    ListingDraftResponse approve(UUID id, UUID reviewerId);

    ListingDraftResponse reject(UUID id, UUID reviewerId, String reason);

}
