package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.Post;
import java.util.UUID;

public interface ListingPublicationService {
    void requirePublishAvailable(UUID userId);

    void snapshotPolicy(Post post);

    void accept(Post post);

    void activate(Post post);

    void markReadyToPublish(Post post);

    void markReadyAfterInspectionReturn(UUID postId);

    Post publishInspectedPost(UUID sellerId, UUID postId);

}
