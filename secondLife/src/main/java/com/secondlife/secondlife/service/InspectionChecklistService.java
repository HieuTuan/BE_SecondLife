package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionChecklistTemplateRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;

import java.util.List;
import java.util.UUID;

public interface InspectionChecklistService {
    List<InspectionChecklistResponse> getAllTemplates();
    InspectionChecklistResponse getById(UUID templateId);
    InspectionChecklistResponse getByCategoryId(UUID categoryId);
    InspectionChecklistResponse createTemplate(UUID creatorId, InspectionChecklistTemplateRequest request);
    InspectionChecklistResponse updateTemplate(UUID templateId, InspectionChecklistTemplateRequest request);
    void deleteTemplate(UUID templateId);
}
