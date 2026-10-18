package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateResponse;
import com.secondlife.secondlife.mapper.CategoryQuestionTemplateMapper;
import com.secondlife.secondlife.service.CatalogManagementService;
import com.secondlife.secondlife.service.CategoryTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryTemplateServiceImpl implements CategoryTemplateService {

    private final CatalogManagementService catalogService;
    private final CategoryQuestionTemplateMapper mapper;

    @Override
    @Transactional
    public CategoryQuestionTemplateResponse createTemplate(CategoryQuestionTemplateRequest request) {
        return mapper.toResponse(catalogService.createTemplate(request));
    }

    @Override
    @Transactional
    public CategoryQuestionTemplateResponse updateTemplate(UUID id, CategoryQuestionTemplateRequest request) {
        return mapper.toResponse(catalogService.updateTemplate(id, request));
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID id) {
        catalogService.deleteTemplate(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryQuestionTemplateResponse> getAllTemplates() {
        return catalogService.getAllTemplates().stream().map(mapper::toResponse).toList();
    }
}
