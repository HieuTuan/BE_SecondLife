package com.secondlife.secondlife.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "report_submissions")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportSubmission {
    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "author_role", nullable = false, updatable = false, length = 10)
    private String authorRole;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Column(nullable = false, updatable = false, length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence_urls", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String evidenceUrls;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ReportSubmission(UUID id, UUID reportId, UUID authorId, String authorRole, UUID requestId,
            String description, String evidenceUrls, Instant createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.authorId = authorId;
        this.authorRole = authorRole;
        this.requestId = requestId;
        this.description = description;
        this.evidenceUrls = evidenceUrls;
        this.createdAt = createdAt;
    }
}
