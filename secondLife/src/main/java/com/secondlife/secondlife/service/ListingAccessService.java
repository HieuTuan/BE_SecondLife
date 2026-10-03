package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingAccessService {
    private final UserRepository users;
    private final PostRepository posts;
    private final com.secondlife.secondlife.repository.SellerVerificationRepository verifications;

    public void requireVerifiedSeller(UUID userId) {
        if (!verifications.existsByUserIdAndStatus(userId, com.secondlife.secondlife.enums.SellerVerificationStatus.APPROVED))
            throw new ConflictException("An approved seller verification is required before publish");
    }

    public Post owned(UUID userId, UUID postId, boolean lock) {
        if (lock) users.findByIdForRoleUpdate(userId).orElseThrow(() -> new NotFoundException("User not found"));
        Post post = (lock ? posts.findByIdForUpdate(postId) : posts.findById(postId))
                .orElseThrow(() -> new NotFoundException("Post not found"));
        if (post.getUser() == null || !userId.equals(post.getUser().getId()))
            throw new ForbiddenException("This post belongs to another user");
        return post;
    }

    public void requireDraft(Post post) {
        if (post.isListingCreditCharged()) throw new ConflictException("This post was already submitted; re-up is not supported");
        if (!"DRAFT".equals(post.getStatus()) && !"REJECTED".equals(post.getStatus()))
            throw new ConflictException("Only a draft or rejected post can be edited or valued");
    }
}
