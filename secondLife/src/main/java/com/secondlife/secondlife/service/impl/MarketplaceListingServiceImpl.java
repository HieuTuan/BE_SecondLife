package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.PostMapper;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.MarketplaceListingService;

import jakarta.persistence.criteria.JoinType;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("marketplaceListingService")
@Transactional(readOnly = true)
public class MarketplaceListingServiceImpl implements MarketplaceListingService {

    private final PostRepository posts;
    private final PostMapper postMapper;

    public MarketplaceListingServiceImpl(PostRepository posts, PostMapper postMapper) {
        this.posts = posts;
        this.postMapper = postMapper;
    }

    public Page<MarketplaceListingResponse> list(UUID categoryId, UUID itemId, Pageable pageable) {
        Specification<Post> filter = (root, query, cb) -> {
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("user", JoinType.LEFT).fetch("profile", JoinType.LEFT);
            }
            return cb.equal(root.get("status"), "ACTIVE");
        };
        if (categoryId != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("categoryId"), categoryId));
        if (itemId != null) filter = filter.and((root, query, cb) -> cb.equal(root.get("itemId"), itemId));
        Page<Post> page = posts.findAll(filter, pageable);
        if (!page.isEmpty()) {
            // Hydrate images for the managed page entities after database pagination.
            // Fetching a collection in the paged query would paginate in memory.
            posts.findAllWithImagesByIdIn(page.getContent().stream().map(Post::getId).toList());
        }
        return page.map(postMapper::toMarketplaceResponse);
    }

    public MarketplaceListingResponse get(UUID postId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new NotFoundException("Listing not found"));
        if (!"ACTIVE".equals(post.getStatus()) && !"RESERVED".equals(post.getStatus())) {
            throw new NotFoundException("Listing is not available for purchase");
        }
        return postMapper.toMarketplaceResponse(post);
    }

}
