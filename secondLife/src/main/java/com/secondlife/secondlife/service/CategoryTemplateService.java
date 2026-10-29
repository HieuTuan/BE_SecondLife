package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ApproveTemplateProposalRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.RejectTemplateProposalRequest;
import com.secondlife.secondlife.dto.request.UpdateTemplateProposalRequest;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateProposalResponse;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateResponse;
import com.secondlife.secondlife.dto.response.ResolvedQuestionTemplateResponse;
import com.secondlife.secondlife.enums.TemplateProposalStatus;

import java.util.List;
import java.util.UUID;

public interface CategoryTemplateService {
    CategoryQuestionTemplateResponse createTemplate(CategoryQuestionTemplateRequest request);

    CategoryQuestionTemplateResponse updateTemplate(UUID id, CategoryQuestionTemplateRequest request);

    void deleteTemplate(UUID id);

    List<CategoryQuestionTemplateResponse> getAllTemplates();

    ResolvedQuestionTemplateResponse resolveOrGenerateForListing(UUID categoryId, UUID itemId, UUID sellerId);

    List<CategoryQuestionTemplateProposalResponse> getProposals(TemplateProposalStatus status);

    CategoryQuestionTemplateProposalResponse updateProposal(UUID proposalId, UpdateTemplateProposalRequest request);

    CategoryQuestionTemplateResponse approveProposal(
            UUID proposalId,
            UUID adminId,
            ApproveTemplateProposalRequest request
    );

    CategoryQuestionTemplateProposalResponse rejectProposal(
            UUID proposalId,
            UUID adminId,
            RejectTemplateProposalRequest request
    );
}
