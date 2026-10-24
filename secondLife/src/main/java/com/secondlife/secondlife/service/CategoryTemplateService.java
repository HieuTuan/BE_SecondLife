package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateResponse;
import java.util.List;
import java.util.UUID;

public interface CategoryTemplateService {
    CategoryQuestionTemplateResponse createTemplate(CategoryQuestionTemplateRequest request);
    CategoryQuestionTemplateResponse updateTemplate(UUID id, CategoryQuestionTemplateRequest request);
    void deleteTemplate(UUID id);
    List<CategoryQuestionTemplateResponse> getAllTemplates();
}
