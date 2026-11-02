package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.dto.response.ItemResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;

import java.util.List;
import java.util.UUID;

public interface CatalogManagementService {
    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(UUID id, CategoryRequest request);

    void deleteCategory(UUID id);

    List<CategoryResponse> getAllCategories();

    ItemResponse createItem(ItemRequest request);

    ItemResponse updateItem(UUID id, ItemRequest request);

    void deleteItem(UUID id);

    List<ItemResponse> getItemsByCategory(UUID categoryId);

    List<ItemResponse> getAllItems();

    CategoryQuestionTemplate createTemplate(CategoryQuestionTemplateRequest request);

    CategoryQuestionTemplate updateTemplate(UUID id, CategoryQuestionTemplateRequest request);

    void deleteTemplate(UUID id);

    List<CategoryQuestionTemplate> getAllTemplates();
}
