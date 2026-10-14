package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_device_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_device_tokens_token", columnNames = {"token"})
})
@Getter
@Setter
@NoArgsConstructor
public class UserDeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token", nullable = false, length = 500)
    private String token;

    @Column(name = "device_type", length = 20)
    private String deviceType = "ANDROID"; // ANDROID, IOS, WEB

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt = Instant.now();

    public UserDeviceToken(User user, String token, String deviceType) {
        this.user = user;
        this.token = token;
        this.deviceType = deviceType != null ? deviceType.toUpperCase() : "ANDROID";
        this.createdAt = Instant.now();
        this.lastUsedAt = Instant.now();
    }
}
