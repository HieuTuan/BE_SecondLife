package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionFeeProposalRepository extends JpaRepository<InspectionFeeProposal, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InspectionFeeProposal p where p.id = :id")
    Optional<InspectionFeeProposal> findByIdForUpdate(@Param("id") UUID id);

    Optional<InspectionFeeProposal> findFirstByProposedBy_IdAndComplexityLevelAndStatusOrderByReviewedAtDescIdDesc(
            UUID centerId, InspectionComplexityLevel level, InspectionFeeProposalStatus status);

    @EntityGraph(attributePaths = {"category", "proposedBy", "reviewedBy"})
    @Query("""
            select p from InspectionFeeProposal p
            where p.proposedBy.id = :authorId and (:status is null or p.status = :status)
            """)
    Page<InspectionFeeProposal> findByAuthor(@Param("authorId") UUID authorId,
                                           @Param("status") InspectionFeeProposalStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "proposedBy", "reviewedBy"})
    @Query("""
            select p from InspectionFeeProposal p
            where p.proposedBy.id=:centerId and p.complexityLevel is not null and p.status='APPROVED'
              and not exists (select q.id from InspectionFeeProposal q
                  where q.proposedBy.id=p.proposedBy.id and q.complexityLevel=p.complexityLevel
                    and q.status='APPROVED' and (q.reviewedAt>p.reviewedAt or (q.reviewedAt=p.reviewedAt and q.id>p.id)))
            order by p.complexityLevel
            """)
    List<InspectionFeeProposal> findCurrentLevelFees(@Param("centerId") UUID centerId);

    @EntityGraph(attributePaths = {"proposedBy"})
    @Query("""
            select p from InspectionFeeProposal p
            where p.proposedBy.id in :centerIds and p.complexityLevel in :levels
              and p.status in ('APPROVED','PENDING_APPROVAL')
              and not exists (select q.id from InspectionFeeProposal q
                  where q.proposedBy.id=p.proposedBy.id and q.complexityLevel=p.complexityLevel and q.status=p.status
                    and ((p.status='APPROVED' and (q.reviewedAt>p.reviewedAt or (q.reviewedAt=p.reviewedAt and q.id>p.id)))
                      or (p.status='PENDING_APPROVAL' and (q.createdAt>p.createdAt or (q.createdAt=p.createdAt and q.id>p.id)))))
            """)
    List<InspectionFeeProposal> findLatestLevelFeesForCenters(@Param("centerIds") Collection<UUID> centerIds,
                                                            @Param("levels") Collection<InspectionComplexityLevel> levels);

    @EntityGraph(attributePaths = {"category", "proposedBy", "reviewedBy"})
    Page<InspectionFeeProposal> findByStatusOrderByCreatedAtDesc(InspectionFeeProposalStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"category", "proposedBy", "reviewedBy"})
    Page<InspectionFeeProposal> findAll(Pageable pageable);

    Optional<InspectionFeeProposal> findFirstByCategory_IdAndStatusOrderByReviewedAtDesc(UUID categoryId, InspectionFeeProposalStatus status);

    Optional<InspectionFeeProposal> findFirstByStatusOrderByReviewedAtDesc(InspectionFeeProposalStatus status);
}
