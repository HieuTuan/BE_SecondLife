package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ListingDraftRequest;
import com.secondlife.secondlife.dto.response.ListingDraftResponse;
import com.secondlife.secondlife.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingDraftService {
    private final ListingAccessService access;
    private final PostRepository posts;

    @Transactional(readOnly = true)
    public ListingDraftResponse get(UUID userId, UUID postId) {
        return ListingDraftResponse.from(access.owned(userId, postId, false));
    }
    @Transactional
    public ListingDraftResponse update(UUID userId, UUID postId, ListingDraftRequest request) {
        var post = access.owned(userId, postId, true);
        access.requireDraft(post);
        if (!java.util.Objects.equals(post.getDescription(), request.description().trim())) post.setDescriptionAccepted(false);
        post.setTitle(request.title().trim()); post.setDescription(request.description().trim());
        post.setItemCondition(request.itemCondition()); post.setPrice(request.price());
        posts.save(post);
        return ListingDraftResponse.from(post);
    }

    @Transactional
    public ListingDraftResponse acceptDescription(UUID userId, UUID postId,
            com.secondlife.secondlife.dto.request.AcceptListingDescriptionRequest request) {
        var post = access.owned(userId, postId, true);
        access.requireDraft(post);
        String description = request == null || request.description() == null ? post.getAiDescription() : request.description();
        if (description == null || description.isBlank() || description.length() > 10000)
            throw new com.secondlife.secondlife.exception.BadRequestException("A non-empty product description is required");
        post.setDescription(description.trim());
        post.setDescriptionAccepted(true);
        posts.save(post);
        return ListingDraftResponse.from(post);
    }
}
