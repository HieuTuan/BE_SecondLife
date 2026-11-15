package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.PostMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository.ListingInspection;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.MarketplaceListingService;

import jakarta.persistence.criteria.JoinType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("marketplaceListingService")
@Transactional(readOnly = true)
public class MarketplaceListingServiceImpl implements MarketplaceListingService {

    private final PostRepository posts;
    private final InspectionOrderRepository inspections;
    private final PostMapper postMapper;

    public MarketplaceListingServiceImpl(PostRepository posts, InspectionOrderRepository inspections, PostMapper postMapper) {
        this.posts = posts;
        this.inspections = inspections;
        this.postMapper = postMapper;
    }

    @Override
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
        if (page.isEmpty()) {
            return page.map(post -> toResponse(post, null));
        }
        var postIds = page.getContent().stream().map(Post::getId).toList();
        // Hydrate images in one query after database pagination.
        posts.findAllWithImagesByIdIn(postIds);
        var inspectionByPost = inspections.findLatestListingInspections(postIds).stream()
                .collect(Collectors.toMap(ListingInspection::getPostId, Function.identity()));
        return page.map(post -> toResponse(post, inspectionByPost.get(post.getId())));
    }

    @Override
    public MarketplaceListingResponse get(UUID postId) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new NotFoundException("Listing not found"));
        if (!"ACTIVE".equals(post.getStatus()) && !"RESERVED".equals(post.getStatus())) {
            throw new NotFoundException("Listing is not available for purchase");
        }
        var matches = inspections.findLatestListingInspections(List.of(postId));
        return toResponse(post, matches.isEmpty() ? null : matches.getFirst());
    }

    private MarketplaceListingResponse toResponse(Post post, ListingInspection inspection) {
        if (inspection == null) {
            return postMapper.toMarketplaceResponse(post, false, null, null);
        }
        return postMapper.toMarketplaceResponse(post, true, inspection.getTamperSealId(), inspection.getConditionGrade());
    }
}
