package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.Post;
import java.util.UUID;

public interface ListingAccessService {
    void requireVerifiedSeller(UUID userId);

    Post owned(UUID userId, UUID postId, boolean lock);

    void requireDraft(Post post);

    static boolean canEditDraft(Post post) {
        return !post.isListingCreditCharged()
                && ("DRAFT".equals(post.getStatus()) || "REJECTED".equals(post.getStatus()));
    }

}
