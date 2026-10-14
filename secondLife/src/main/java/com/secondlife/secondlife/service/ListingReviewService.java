package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.ListingDraftResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.notification.NotificationType;
import com.secondlife.secondlife.repository.PostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class ListingReviewService {
    private final PostRepository posts;
    private final ListingAccessService access;
    private final ListingPublicationService publication;
    private final NotificationService notificationService;

    public ListingReviewService(PostRepository posts, ListingAccessService access, ListingPublicationService publication) {
        this(posts, access, publication, null);
    }

    @Autowired
    public ListingReviewService(PostRepository posts, ListingAccessService access, ListingPublicationService publication,
                                @Autowired(required = false) NotificationService notificationService) {
        this.posts = posts;
        this.access = access;
        this.publication = publication;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public Page<ListingDraftResponse> queue(Pageable pageable) {
        return posts.findByStatus("PENDING", pageable).map(ListingDraftResponse::from);
    }
    @Transactional(readOnly = true)
    public ListingDraftResponse detail(UUID id) {
        return ListingDraftResponse.from(posts.findById(id).orElseThrow(() -> new NotFoundException("Post not found")));
    }
    @Transactional
    public ListingDraftResponse approve(UUID id, UUID reviewerId) {
        Post post = lock(id);
        if ("ACTIVE".equals(post.getStatus()) || "PENDING_INSPECTION".equals(post.getStatus()))
            return ListingDraftResponse.from(post);
        if (!"PENDING".equals(post.getStatus())) throw new ConflictException("Post is not pending review");
        publication.accept(post);
        post.setReviewedBy(reviewerId); post.setReviewedAt(Instant.now());
        posts.save(post);
        if (notificationService != null && post.getUser() != null) {
            notificationService.notify(
                post.getUser().getId(),
                "Tin đăng đã được duyệt",
                "Tin đăng \"" + post.getTitle() + "\" của bạn đã được duyệt và đang hiển thị trên sàn.",
                NotificationType.LISTING,
                post.getId(),
                "POST",
                "/posts/" + post.getId()
            );
        }
        return ListingDraftResponse.from(post);
    }
    @Transactional
    public ListingDraftResponse reject(UUID id, UUID reviewerId, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 2000) throw new BadRequestException("A rejection reason is required");
        Post post = lock(id);
        if ("REJECTED".equals(post.getStatus()) && reason.trim().equals(post.getRejectionReason()))
            return ListingDraftResponse.from(post);
        if (!"PENDING".equals(post.getStatus())) throw new ConflictException("Post is not pending review");
        post.setStatus("REJECTED"); post.setRejectionReason(reason.trim());
        post.setReviewedBy(reviewerId); post.setReviewedAt(Instant.now()); posts.save(post);
        if (notificationService != null && post.getUser() != null) {
            notificationService.notify(
                post.getUser().getId(),
                "Tin đăng bị từ chối duyệt",
                "Tin đăng \"" + post.getTitle() + "\" bị từ chối duyệt. Lý do: " + reason.trim(),
                NotificationType.LISTING,
                post.getId(),
                "POST",
                "/seller/posts/" + post.getId() + "/edit"
            );
        }
        return ListingDraftResponse.from(post);
    }
    private Post lock(UUID id) {
        UUID owner = posts.findOwnerId(id).orElseThrow(() -> new NotFoundException("Post not found"));
        return access.owned(owner, id, true);
    }
}
