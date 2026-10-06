package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.Item;

import java.util.List;
import java.util.UUID;

public interface CatalogService {
    Category createCategory(CategoryRequest request);
    Category updateCategory(UUID id, CategoryRequest request);
    void deleteCategory(UUID id);
    List<Category> getAllCategories();

    Item createItem(ItemRequest request);
    Item updateItem(UUID id, ItemRequest request);
    void deleteItem(UUID id);
    List<Item> getItemsByCategory(UUID categoryId);
    List<Item> getAllItems();

    CategoryQuestionTemplate createTemplate(CategoryQuestionTemplateRequest request);
    CategoryQuestionTemplate updateTemplate(UUID id, CategoryQuestionTemplateRequest request);
    void deleteTemplate(UUID id);
    List<CategoryQuestionTemplate> getAllTemplates();
}
