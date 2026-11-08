package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionChecklistSearchRequest;
import com.secondlife.secondlife.dto.request.InspectionChecklistTemplateRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.enums.InspectionChecklistStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InspectionChecklistService {
    List<InspectionChecklistResponse> getAllTemplates();
    List<InspectionChecklistResponse> getTemplatesForCenter(UUID actorId);
    InspectionChecklistResponse getById(UUID actorId, UUID templateId);
    InspectionChecklistResponse getByCategoryId(UUID categoryId);
    InspectionChecklistResponse createTemplate(UUID creatorId, InspectionChecklistTemplateRequest request);
    InspectionChecklistResponse updateTemplate(UUID actorId, UUID templateId, InspectionChecklistTemplateRequest request);
    void deleteTemplate(UUID actorId, UUID templateId);
    Page<InspectionChecklistResponse> getProposals(UUID actorId, InspectionChecklistStatus status, Pageable pageable);
    Page<InspectionChecklistResponse> searchProposals(UUID actorId, InspectionChecklistSearchRequest filter, Pageable pageable);
    InspectionChecklistResponse review(UUID reviewerId, UUID templateId, boolean approved, String reason);
}
