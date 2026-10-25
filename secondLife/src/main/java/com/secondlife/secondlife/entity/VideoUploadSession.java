package com.secondlife.secondlife.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="video_upload_sessions")
@Getter @Setter @NoArgsConstructor
public class VideoUploadSession {
    @Id private UUID id;
    @Column(name="owner_id",nullable=false) private UUID ownerId;
    @Column(name="request_id",nullable=false) private UUID requestId;
    @Column(name="public_id",nullable=false,unique=true,length=200) private String publicId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name="signature_data",nullable=false,columnDefinition="jsonb") private String signatureData;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="video_url",length=2048) private String videoUrl;
    @Column(name="asset_id",unique=true,length=200) private String assetId;
    @Column(name="size_bytes") private Long bytes;
    @Column(length=10) private String format;
    private Double duration;
    @Column(name="provider_version") private Long providerVersion;
}
