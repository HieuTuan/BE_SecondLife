package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InspectionFeeProposalRepository extends JpaRepository<InspectionFeeProposal, UUID> {

    Page<InspectionFeeProposal> findByStatusOrderByCreatedAtDesc(InspectionFeeProposalStatus status, Pageable pageable);

    Optional<InspectionFeeProposal> findFirstByCategory_IdAndStatusOrderByReviewedAtDesc(UUID categoryId, InspectionFeeProposalStatus status);

    Optional<InspectionFeeProposal> findFirstByStatusOrderByReviewedAtDesc(InspectionFeeProposalStatus status);
}
