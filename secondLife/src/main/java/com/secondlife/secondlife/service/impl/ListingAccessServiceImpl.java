package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.ListingAccessService;
import com.secondlife.secondlife.service.SellerReputationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("listingAccessService")
@RequiredArgsConstructor
public class ListingAccessServiceImpl implements ListingAccessService {
    private final UserRepository users;
    private final SellerReputationService reputation;
    private final PostRepository posts;
    private final SellerVerificationRepository verifications;

    public void requireVerifiedSeller(UUID userId) {
        reputation.requireSellingAllowed(userId);
        if (verifications.existsByUserIdAndStatus(userId, SellerVerificationStatus.APPROVED)) {
            return;
        }
        User user = users.findByIdWithAuthorities(userId).orElse(null);
        if (user != null && (user.hasRole("SELLER") || user.hasRole("ADMIN"))) {
            return;
        }
        throw new ConflictException("An approved seller verification is required before publish");
    }

    public Post owned(UUID userId, UUID postId, boolean lock) {
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
