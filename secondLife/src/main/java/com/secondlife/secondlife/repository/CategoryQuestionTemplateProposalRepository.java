package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CategoryQuestionTemplateProposal;
import com.secondlife.secondlife.enums.TemplateProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryQuestionTemplateProposalRepository extends JpaRepository<CategoryQuestionTemplateProposal, UUID> {
    Optional<CategoryQuestionTemplateProposal> findByCategoryIdAndItemIdAndStatus(
            UUID categoryId, UUID itemId, TemplateProposalStatus status);

    Optional<CategoryQuestionTemplateProposal> findByCategoryIdAndItemIdIsNullAndStatus(
            UUID categoryId, TemplateProposalStatus status);

    List<CategoryQuestionTemplateProposal> findAllByOrderByUpdatedAtDesc();

    List<CategoryQuestionTemplateProposal> findByStatusOrderByUpdatedAtDesc(TemplateProposalStatus status);
}

