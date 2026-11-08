package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.ListingAccessService;
import com.secondlife.secondlife.service.ListingCreditService;
import com.secondlife.secondlife.service.ListingPublicationService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service("listingPublicationService")
public class ListingPublicationServiceImpl implements ListingPublicationService {
    private final ListingCreditService credits;
    private final ListingAccessService access;
    private final PostRepository posts;
    private final InspectionOrderRepository inspections;
    private final InspectionPricingService pricing;
    private final BigDecimal highValueThreshold;
    private final long publishWindowSeconds;
    private final int publishLimit;

    public ListingPublicationServiceImpl(ListingCreditService credits, ListingAccessService access, PostRepository posts,
                                     InspectionOrderRepository inspections,
                                     @Value("${app.inspection.high-value-threshold}") BigDecimal highValueThreshold,
                                     @Value("${app.listing.publish-window-seconds}") long publishWindowSeconds,
                                     @Value("${app.listing.publish-limit}") int publishLimit,
                                     InspectionPricingService pricing) {
        if (highValueThreshold.signum() < 0)
            throw new IllegalArgumentException("Inspection threshold must be non-negative");
        if (publishWindowSeconds <= 0 || publishLimit <= 0)
            throw new IllegalArgumentException("Listing publish window and limit must be positive");
        this.credits = credits;
        this.access = access;
        this.posts = posts;
        this.inspections = inspections;
        this.pricing = pricing;
        this.highValueThreshold = highValueThreshold;
        this.publishWindowSeconds = publishWindowSeconds;
        this.publishLimit = publishLimit;
    }

    public void requirePublishAvailable(UUID userId) {
        if (posts.countByUser_IdAndPublishedAtAfter(userId, Instant.now().minusSeconds(publishWindowSeconds)) >= publishLimit)
            throw new ConflictException("Publish rate limit reached; maximum " + publishLimit
                    + " accepted posts per " + publishWindowSeconds + " seconds");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accept(Post post) {
        access.requireVerifiedSeller(post.getUser().getId());
        post.setRejectionReason(null);
        if (post.getPrice().compareTo(highValueThreshold) > 0) {
            InspectionQuoteResponse quote = pricing.calculateQuote(InspectionQuoteRequest.builder().postId(post.getId()).build());
            InspectionOrder order = new InspectionOrder();
            order.setPost(post);
            order.setSeller(post.getUser());
            order.setInspectionScope(InspectionScope.PRE_LISTING);
            order.setFeePayer(InspectionFeePayer.SELLER_PAYS);
            order.setInspectionFee(quote.baseInspectionFee());
            order.setShippingFee(quote.shippingToCenterFee());
            order.setReturnShippingReserveFee(quote.returnShippingReserveFee());
            order.setSellerFeeShare(quote.atCenterTotalFee());
            pricing.applyConfiguration(order, quote);
            order.setStatus("PENDING");
            inspections.save(order);
            post.setStatus("PENDING_INSPECTION");
        } else activate(post);
        posts.save(post);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void activate(Post post) {
        access.requireVerifiedSeller(post.getUser().getId());
        if (!post.isListingCreditCharged()) {
            requirePublishAvailable(post.getUser().getId());
            credits.consume(post.getUser().getId(), CreditType.LISTING, "LISTING_PUBLISH:" + post.getId());
            post.setListingCreditCharged(true);
        }
        post.setStatus("ACTIVE");
        if (post.getPublishedAt() == null) post.setPublishedAt(Instant.now());
        post.setRejectionReason(null);
        posts.save(post);
    }
}
