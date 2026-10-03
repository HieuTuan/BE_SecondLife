package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.AiPriceEstimationResponse;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiValuationService {
    private final ListingAccessService access;
    private final ListingCreditService credits;
    private final AiPriceEstimateRepository estimates;
    private final PostRepository posts;
    private final AiPriceProvider provider;
    private final ObjectMapper mapper;
    private final CategoryRepository categories;
    private final ItemRepository items;

    @Transactional
    public AiPriceEstimationResponse create(UUID userId, UUID postId, UUID requestId) {
        if (requestId == null) throw new BadRequestException("requestId is required");
        Post post = access.owned(userId, postId, true);
        String snapshot = snapshot(post);
        String fingerprint = ListingFingerprint.sha256(snapshot);
        var existing = estimates.findByListingIdAndRequestId(postId, requestId);
        if (existing.isPresent()) {
            if (!fingerprint.equals(existing.get().getInputFingerprint()))
                throw new ConflictException("requestId was already used with different product details; use a new requestId");
            return AiPriceEstimationResponse.from(existing.get());
        }
        access.requireDraft(post);
        if (!post.isDescriptionAccepted())
            throw new ConflictException("Accept the product description before valuation");
        if (post.getTitle() == null || post.getTitle().isBlank() || post.getDescription() == null || post.getDescription().isBlank())
            throw new BadRequestException("Save a product title and description before requesting valuation");
        credits.consume(userId, CreditType.VALUATION, "VALUATION:" + postId + ":" + requestId);
        var imageUrls = post.getImageUrls();
        var result = imageUrls.isEmpty() ? provider.estimate(snapshot) : provider.estimate(snapshot, imageUrls);
        AiPriceEstimate estimate = new AiPriceEstimate();
        estimate.setListingId(postId); estimate.setRequestId(requestId);
        estimate.setInputFingerprint(fingerprint); estimate.setInputSnapshot(snapshot);
        estimate.setModelVersion(result.modelVersion()); estimate.setFairPriceMin(result.min());
        estimate.setFairPriceMax(result.max()); estimate.setSuggestedPrice(result.suggested());
        estimate.setExpectedSellTime(result.expectedSellTime()); estimate.setCreatedAt(Instant.now());
        estimate = estimates.save(estimate);
        post.setAiSuggestedPrice(result.suggested());
        posts.save(post);
        return AiPriceEstimationResponse.from(estimate);
    }

    @Transactional(readOnly = true)
    public AiPriceEstimationResponse latest(UUID userId, UUID postId) {
        access.owned(userId, postId, false);
        return AiPriceEstimationResponse.from(estimates.findFirstByListingIdOrderByCreatedAtDescIdDesc(postId)
                .orElseThrow(() -> new NotFoundException("No AI valuation exists for this post")));
    }

    @Transactional(readOnly = true)
    public Page<AiPriceEstimationResponse> history(UUID userId, UUID postId, Pageable pageable) {
        access.owned(userId, postId, false);
        return estimates.findByListingIdOrderByCreatedAtDescIdDesc(postId, pageable).map(AiPriceEstimationResponse::from);
    }

    private String snapshot(Post p) {
        String category = p.getCategoryId() == null ? null : categories.findById(p.getCategoryId()).map(Category::getName).orElse(null);
        String item = p.getItemId() == null ? null : items.findById(p.getItemId()).map(Item::getName).orElse(null);
        return mapper.writeValueAsString(new ProductInput(p.getCategoryId(), p.getItemId(), category, item,
                p.getTitle(), p.getDescription(), p.getItemCondition(), p.getImageUrl(),
                p.getImages().isEmpty() ? java.util.List.of() : p.getImageUrls()));
    }
    private record ProductInput(UUID categoryId, UUID itemId, String category, String item,
                                String title, String description, String itemCondition, String imageUrl,
                                @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
                                java.util.List<String> imageUrls) {}
}
