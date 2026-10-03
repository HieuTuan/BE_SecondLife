package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.ListingDraftResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingReviewService {
    private final PostRepository posts;
    private final ListingAccessService access;
    private final ListingPublicationService publication;

    @Transactional(readOnly = true)
    public Page<ListingDraftResponse> queue(Pageable pageable) {
        return posts.findByStatus("PENDING", pageable).map(ListingDraftResponse::from);
    }
    @Transactional(readOnly = true)
    public ListingDraftResponse detail(UUID id) {
        return ListingDraftResponse.from(posts.findById(id).orElseThrow(() -> new NotFoundException("Post not found")));
    }
    @Transactional
    public ListingDraftResponse approve(UUID id, UUID reviewerId) {
        Post post = lock(id);
        if ("ACTIVE".equals(post.getStatus()) || "PENDING_INSPECTION".equals(post.getStatus()))
            return ListingDraftResponse.from(post);
        if (!"PENDING".equals(post.getStatus())) throw new ConflictException("Post is not pending review");
        publication.accept(post);
        post.setReviewedBy(reviewerId); post.setReviewedAt(Instant.now());
        posts.save(post);
        return ListingDraftResponse.from(post);
    }
    @Transactional
    public ListingDraftResponse reject(UUID id, UUID reviewerId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 2000) throw new BadRequestException("A rejection reason is required");
        Post post = lock(id);
        if ("REJECTED".equals(post.getStatus()) && reason.trim().equals(post.getRejectionReason()))
            return ListingDraftResponse.from(post);
        if (!"PENDING".equals(post.getStatus())) throw new ConflictException("Post is not pending review");
        post.setStatus("REJECTED"); post.setRejectionReason(reason.trim());
        post.setReviewedBy(reviewerId); post.setReviewedAt(Instant.now()); posts.save(post);
        return ListingDraftResponse.from(post);
    }
    private Post lock(UUID id) {
        UUID owner = posts.findOwnerId(id).orElseThrow(() -> new NotFoundException("Post not found"));
        return access.owned(owner, id, true);
    }
}
