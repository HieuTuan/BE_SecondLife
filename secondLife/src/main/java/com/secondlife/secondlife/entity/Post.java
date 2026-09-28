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

    @Column(name = "ai_suggested_price", precision = 18, scale = 2)
    private BigDecimal aiSuggestedPrice; // Giá AI gợi ý

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
