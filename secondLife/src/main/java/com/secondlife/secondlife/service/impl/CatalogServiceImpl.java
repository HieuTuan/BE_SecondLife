package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.dto.response.ItemResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogServiceImpl {
    private final CatalogService catalog;
    private final CategoryQuestionTemplateRepository templateRepository;

    public CategoryResponse createCategory(CategoryRequest request) { return catalog.createCategory(request); }
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) { return catalog.updateCategory(id, request); }
    public void deleteCategory(UUID id) { catalog.deleteCategory(id); }
    public List<CategoryResponse> getAllCategories() { return catalog.categories(); }
    public ItemResponse createItem(ItemRequest request) { return catalog.createItem(request); }
    public ItemResponse updateItem(UUID id, ItemRequest request) { return catalog.updateItem(id, request); }
    public void deleteItem(UUID id) { catalog.deleteItem(id); }
    public List<ItemResponse> getItemsByCategory(UUID categoryId) { return catalog.items(categoryId); }
    public List<ItemResponse> getAllItems() { return catalog.items(null); }

    @Transactional
    public CategoryQuestionTemplate createTemplate(CategoryQuestionTemplateRequest request) {
        CategoryQuestionTemplate template = new CategoryQuestionTemplate();
        template.setCategoryId(request.getCategoryId());
        template.setItemId(request.getItemId());
        template.setTemplateText(request.getTemplateText());
        return templateRepository.save(template);
    }

    @Transactional
    public CategoryQuestionTemplate updateTemplate(UUID id, CategoryQuestionTemplateRequest request) {
        CategoryQuestionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        template.setCategoryId(request.getCategoryId());
        template.setItemId(request.getItemId());
        template.setTemplateText(request.getTemplateText());
        return templateRepository.save(template);
    }

    @Transactional
    public void deleteTemplate(UUID id) { templateRepository.deleteById(id); }

    @Transactional(readOnly = true)
    public List<CategoryQuestionTemplate> getAllTemplates() { return templateRepository.findAll(); }
}
