package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostImage {
    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;
    @Column(name = "image_fingerprint", length = 64)
    private String imageFingerprint;
    @Column(name = "perceptual_fingerprint", columnDefinition = "TEXT")
    private String perceptualFingerprint;

    public PostImage(String imageUrl, String imageFingerprint) {
        this.imageUrl = imageUrl;
        this.imageFingerprint = imageFingerprint;
    }
}
