package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketplaceListingService {
    private final PostRepository posts;

    public Page<MarketplaceListingResponse> list(UUID categoryId, UUID itemId, Pageable pageable) {
        Specification<Post> filter = (root, query, cb) -> cb.equal(root.get("status"), "ACTIVE");
        if (categoryId != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        if (itemId != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        return posts.findAll(filter, pageable).map(this::response);
    }

    public MarketplaceListingResponse get(UUID postId) {
        return response(posts.findByIdAndStatus(postId, "ACTIVE")
                .orElseThrow(() -> new NotFoundException("Listing is not available for purchase")));
    }

    private MarketplaceListingResponse response(Post post) {
        return new MarketplaceListingResponse(post.getId(), post.getUser().getId(), post.getCategoryId(),
                post.getItemId(), post.getTitle(), post.getDescription(), post.getItemCondition(),
                post.getImageUrl(), post.getImageUrls(), post.getPrice(), post.getStatus(), post.getPublishedAt());
    }
}
