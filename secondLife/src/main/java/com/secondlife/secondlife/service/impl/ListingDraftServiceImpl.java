package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.AcceptListingDescriptionRequest;
import com.secondlife.secondlife.dto.request.ListingDraftRequest;
import com.secondlife.secondlife.dto.response.ListingDraftResponse;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.ListingDraftMapper;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.ListingAccessService;
import com.secondlife.secondlife.service.ListingDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service("listingDraftService")
@RequiredArgsConstructor
public class ListingDraftServiceImpl implements ListingDraftService {

    private final ListingAccessService access;
    private final PostRepository posts;
    private final AiChatSessionRepository sessions;
    private final ListingDraftMapper listingDraftMapper;

    @Transactional
    public void delete(UUID userId, UUID postId) {
        var post = access.owned(userId, postId, true);
        if (!"DRAFT".equals(post.getStatus()) || post.isListingCreditCharged() || post.getPublishedAt() != null)
            throw new ConflictException("Only an unpublished DRAFT post can be deleted");
        // Keep chat/valuation history for audit, but stop further chat on the removed draft.
        for (var session : sessions.findByPostIdAndUser_Id(postId, userId)) {
            session.setCompleted(true);
            sessions.save(session);
        }
        try {
            // JPA removes the gallery collection; immutable valuations and credit entries are independent.
            posts.delete(post);
            posts.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Draft is referenced by another workflow and cannot be deleted");
        }
    }

    @Transactional(readOnly = true)
    public ListingDraftResponse get(UUID userId, UUID postId) {
        return listingDraftMapper.toResponse(access.owned(userId, postId, false));
    }
    @Transactional
    public ListingDraftResponse update(UUID userId, UUID postId, ListingDraftRequest request) {
        var post = access.owned(userId, postId, true);
        access.requireDraft(post);
        if (!Objects.equals(post.getDescription(), request.description().trim())) post.setDescriptionAccepted(false);
        post.setTitle(request.title().trim()); post.setDescription(request.description().trim());
        post.setItemCondition(request.itemCondition()); post.setPrice(request.price());
        posts.save(post);
        return listingDraftMapper.toResponse(post);
    }

    @Transactional
    public ListingDraftResponse acceptDescription(UUID userId, UUID postId,
            AcceptListingDescriptionRequest request) {
        var post = access.owned(userId, postId, true);
        access.requireDraft(post);
        String description = request == null || request.description() == null ? post.getAiDescription() : request.description();
        if (description == null || description.isBlank() || description.length() > 10000)
            throw new BadRequestException("A non-empty product description is required");
        post.setDescription(description.trim());
        post.setDescriptionAccepted(true);
        posts.save(post);
        return listingDraftMapper.toResponse(post);
    }
}
