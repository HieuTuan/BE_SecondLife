package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ListingPublicationService {
    private final ListingCreditService credits;
    private final ListingAccessService access;
    private final PostRepository posts;
    private final InspectionOrderRepository inspections;
    @Value("${app.inspection.high-value-threshold:5000000}") private BigDecimal highValueThreshold;
    @Value("${app.inspection.inspection-fee:200000}") private BigDecimal inspectionFee;
    @Value("${app.inspection.shipping-fee:50000}") private BigDecimal shippingFee;


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
            if (posts.countByUser_IdAndPublishedAtAfter(post.getUser().getId(), Instant.now().minusSeconds(600)) >= 5)
                throw new ConflictException("Publish rate limit reached; maximum 5 accepted posts per 10 minutes");
            credits.consume(post.getUser().getId(), CreditType.LISTING, "LISTING_PUBLISH:" + post.getId());
            post.setListingCreditCharged(true);
        }
        post.setStatus("ACTIVE");
        if (post.getPublishedAt() == null) post.setPublishedAt(Instant.now());
        post.setRejectionReason(null);
        posts.save(post);
    }
}
