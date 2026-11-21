package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.HighValueInspectionPolicyService;
import com.secondlife.secondlife.service.ListingAccessService;
import com.secondlife.secondlife.service.ListingCreditService;
import com.secondlife.secondlife.service.ListingPublicationService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service("listingPublicationService")
public class ListingPublicationServiceImpl implements ListingPublicationService {
    private final ListingCreditService credits;
    private final ListingAccessService access;
    private final PostRepository posts;
    private final InspectionOrderRepository inspections;
    private final HighValueInspectionPolicyService policies;
    private final long publishWindowSeconds;
    private final int publishLimit;

    public ListingPublicationServiceImpl(ListingCreditService credits, ListingAccessService access, PostRepository posts,
                                     InspectionOrderRepository inspections,
                                     HighValueInspectionPolicyService policies,
                                     @Value("${app.listing.publish-window-seconds}") long publishWindowSeconds,
                                     @Value("${app.listing.publish-limit}") int publishLimit) {
        if (publishWindowSeconds <= 0 || publishLimit <= 0)
            throw new IllegalArgumentException("Listing publish window and limit must be positive");
        this.credits = credits;
        this.access = access;
        this.posts = posts;
        this.inspections = inspections;
        this.policies = policies;
        this.publishWindowSeconds = publishWindowSeconds;
        this.publishLimit = publishLimit;
    }

    public void requirePublishAvailable(UUID userId) {
        if (posts.countByUser_IdAndPublishedAtAfter(userId, Instant.now().minusSeconds(publishWindowSeconds)) >= publishLimit)
            throw new ConflictException("Publish rate limit reached; maximum " + publishLimit
                    + " accepted posts per " + publishWindowSeconds + " seconds");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void snapshotPolicy(Post post) {
        var policy = policies.get();
        post.setInspectionThresholdSnapshot(policy.thresholdPrice());
        post.setInspectionPolicyVersion(policy.version());
        post.setMandatoryInspection(post.getPrice().compareTo(policy.thresholdPrice()) > 0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void accept(Post post) {
        access.requireVerifiedSeller(post.getUser().getId());
        post.setRejectionReason(null);
        if (post.getInspectionPolicyVersion() == null) snapshotPolicy(post);
        if (Boolean.TRUE.equals(post.getMandatoryInspection())) {
            if (inspections.findActiveByPostId(post.getId()).isEmpty()) {
                InspectionOrder order = new InspectionOrder();
                order.setPost(post);
                order.setSeller(post.getUser());
                order.setInspectionScope(InspectionScope.PRE_LISTING);
                order.setFeePayer(InspectionFeePayer.SELLER_PAYS);
                order.setBuyerFeePercent(0);
                order.setOrderType(null);
                order.setStatus("PENDING");
                inspections.save(order);
            }
            post.setStatus("PENDING_INSPECTION");
        } else activate(post);
        posts.save(post);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void activate(Post post) {
        preparePublication(post);
        publish(post);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markReadyToPublish(Post post) {
        post.setStatus("READY_TO_PUBLISH");
        posts.save(post);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markReadyAfterInspectionReturn(UUID postId) {
        Post post = posts.findByIdForUpdate(postId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài đăng"));
        if ("PENDING_INSPECTION".equals(post.getStatus())) {
            markReadyToPublish(post);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Post publishInspectedPost(UUID sellerId, UUID postId) {
        Post post = access.owned(sellerId, postId, true);
        if ("ACTIVE".equals(post.getStatus()) && Boolean.TRUE.equals(post.getMandatoryInspection())) {
            return post;
        }
        if (!"READY_TO_PUBLISH".equals(post.getStatus())) {
            throw new ConflictException("Bài đăng chưa sẵn sàng để đăng bán");
        }
        activate(post);
        return post;
    }

    private void preparePublication(Post post) {
        if (post.getMandatoryInspection() == null) snapshotPolicy(post);
        if (Boolean.TRUE.equals(post.getMandatoryInspection())
                && !inspections.existsByPostIdAndInspectionScopeAndStatusIn(post.getId(), InspectionScope.PRE_LISTING,
                        List.of("PASSED", "COMPLETED"))) {
            throw new ConflictException("Bài giá trị cao cần hoàn tất kiểm định đạt trước khi hiển thị");
        }
        access.requireVerifiedSeller(post.getUser().getId());
        if (!post.isListingCreditCharged()) {
            requirePublishAvailable(post.getUser().getId());
            credits.consume(post.getUser().getId(), CreditType.LISTING, "LISTING_PUBLISH:" + post.getId());
            post.setListingCreditCharged(true);
        }
    }

    private void publish(Post post) {
        post.setStatus("ACTIVE");
        if (post.getPublishedAt() == null) post.setPublishedAt(Instant.now());
        post.setRejectionReason(null);
        posts.save(post);
    }
}
