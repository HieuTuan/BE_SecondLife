package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.ReportSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReportSubmissionRepository extends JpaRepository<ReportSubmission, UUID> {
    Optional<ReportSubmission> findByReportIdAndAuthorIdAndRequestId(UUID reportId, UUID authorId, UUID requestId);

    Page<ReportSubmission> findByReportIdOrderByCreatedAtAscIdAsc(UUID reportId, Pageable pageable);

    Page<ReportSubmission> findByReportIdAndAuthorIdOrderByCreatedAtAscIdAsc(UUID reportId, UUID authorId, Pageable pageable);
}
