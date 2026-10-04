package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class ListingPublicationService {
    private final ListingCreditService credits;
    private final ListingAccessService access;
    private final PostRepository posts;
    private final InspectionOrderRepository inspections;
    private final BigDecimal highValueThreshold;
    private final BigDecimal inspectionFee;
    private final BigDecimal shippingFee;
    private final long publishWindowSeconds;
    private final int publishLimit;

    public ListingPublicationService(ListingCreditService credits, ListingAccessService access, PostRepository posts,
                                     InspectionOrderRepository inspections,
                                     @Value("${app.inspection.high-value-threshold}") BigDecimal highValueThreshold,
                                     @Value("${app.inspection.inspection-fee}") BigDecimal inspectionFee,
                                     @Value("${app.inspection.shipping-fee}") BigDecimal shippingFee,
                                     @Value("${app.listing.publish-window-seconds}") long publishWindowSeconds,
                                     @Value("${app.listing.publish-limit}") int publishLimit) {
        if (highValueThreshold.signum() < 0 || inspectionFee.signum() < 0 || shippingFee.signum() < 0)
            throw new IllegalArgumentException("Inspection threshold and fees must be non-negative");
        if (publishWindowSeconds <= 0 || publishLimit <= 0)
            throw new IllegalArgumentException("Listing publish window and limit must be positive");
        this.credits = credits;
        this.access = access;
        this.posts = posts;
        this.inspections = inspections;
        this.highValueThreshold = highValueThreshold;
        this.inspectionFee = inspectionFee;
        this.shippingFee = shippingFee;
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
            InspectionOrder order = new InspectionOrder();
            order.setPost(post); order.setInspectionFee(inspectionFee); order.setShippingFee(shippingFee);
            order.setStatus("PENDING"); inspections.save(order);
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
