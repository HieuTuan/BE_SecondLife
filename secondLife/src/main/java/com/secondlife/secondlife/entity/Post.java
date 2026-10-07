package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post {
    private Integer shippingWeight;
    private Integer shippingLength;
    private Integer shippingWidth;
    private Integer shippingHeight;

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "item_id")
    private UUID itemId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url")
    private String imageUrl; // the original image uploaded by the user

    @ElementCollection
    @CollectionTable(name = "post_images", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "image_position")
    private java.util.List<PostImage> images = new java.util.ArrayList<>();

    public java.util.List<String> getImageUrls() {
        if (!images.isEmpty()) return images.stream().map(PostImage::getImageUrl).toList();
        return imageUrl == null ? java.util.List.of() : java.util.List.of(imageUrl);
    }

    public java.util.Set<String> getImageFingerprints() {
        var fingerprints = new java.util.HashSet<String>();
        if (imageFingerprint != null) fingerprints.add(imageFingerprint);
        for (PostImage image : images) if (image.getImageFingerprint() != null) fingerprints.add(image.getImageFingerprint());
        return fingerprints;
    }

    @Column(name = "status", nullable = false)
    private String status = "DRAFT"; // DRAFT, PENDING, ACTIVE, REJECTED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "price", precision = 18, scale = 2)
    private BigDecimal price; // Giá người dùng chốt

    @Column(name = "item_condition", length = 50)
    private String itemCondition; // Tình trạng (Mới, cũ, xước xát...) do AI quét

    @Column(name = "ai_description", columnDefinition = "TEXT")
    private String aiDescription; // Mô tả do AI gen ra lúc bấm kết thúc

    @Column(name = "description_accepted", nullable = false)
    private boolean descriptionAccepted;
    @Column(name = "review_reason", columnDefinition = "TEXT")
    private String reviewReason;
    @Column(name = "duplicate_post_ids", columnDefinition = "TEXT")
    private String duplicatePostIds;
    @Column(name = "reviewed_by")
    private UUID reviewedBy;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public java.util.List<UUID> getDuplicateMatches() {
        return duplicatePostIds == null || duplicatePostIds.isBlank() ? java.util.List.of()
                : java.util.Arrays.stream(duplicatePostIds.split(",")).map(UUID::fromString).toList();
    }

    @Column(name = "ai_suggested_price", precision = 18, scale = 2)
    private BigDecimal aiSuggestedPrice; // Giá AI gợi ý

    @Column(name = "listing_credit_charged", nullable = false)
    private boolean listingCreditCharged;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "image_fingerprint", length = 64)
    private String imageFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_chat_session_id")
    private AiChatSession aiChatSession; // Tham chiếu đến phiên chat AI

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private CategoryQuestionTemplate template; // Tham chiếu đến template đã dùng

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; // Lý do Admin từ chối

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
