package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_chat_sessions")
@Getter
@Setter
@NoArgsConstructor
public class AiChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "session_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "post_id")
    private UUID postId; // Link to the draft post being created

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "message_count", nullable = false)
    private int messageCount = 0; // Each post allows up to 5 messages

    @Column(name = "is_completed", nullable = false)
    private boolean isCompleted = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
