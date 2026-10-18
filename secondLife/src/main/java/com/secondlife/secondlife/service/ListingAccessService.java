package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.Post;
import java.util.UUID;

public interface ListingAccessService {
    void requireVerifiedSeller(UUID userId);

    Post owned(UUID userId, UUID postId, boolean lock);

    void requireDraft(Post post);

}
