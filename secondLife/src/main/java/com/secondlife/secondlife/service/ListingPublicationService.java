package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.Post;
import java.util.UUID;

public interface ListingPublicationService {
    void requirePublishAvailable(UUID userId);

    void accept(Post post);

    void activate(Post post);

}
