package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.service.CatalogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CatalogServiceImpl implements CatalogService {
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final CategoryQuestionTemplateRepository templateRepository;

    public CatalogServiceImpl(CategoryRepository categoryRepository, ItemRepository itemRepository, CategoryQuestionTemplateRepository templateRepository) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
        this.templateRepository = templateRepository;
    }

    @Override
    @Transactional
    public Category createCategory(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public Category updateCategory(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return categoryRepository.save(category);
    }

    @Override
    @Transactional
    public void deleteCategory(UUID id) {
        categoryRepository.deleteById(id);
    }

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    @Transactional
    public Item createItem(ItemRequest request) {
        Item item = new Item();
        item.setName(request.getName());
        item.setCategory(categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> new RuntimeException("Category not found")));
        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public Item updateItem(UUID id, ItemRequest request) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new RuntimeException("Item not found"));
        item.setName(request.getName());
        if (request.getCategoryId() != null) {
            item.setCategory(categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> new RuntimeException("Category not found")));
        }
        return itemRepository.save(item);
    }

    @Override
    @Transactional
    public void deleteItem(UUID id) {
        itemRepository.deleteById(id);
    }

    @Override
    public List<Item> getItemsByCategory(UUID categoryId) {
        return itemRepository.findByCategoryId(categoryId);
    }

    @Override
    public List<Item> getAllItems() {
        return itemRepository.findAll();
    }

    @Override
    @Transactional
    public CategoryQuestionTemplate createTemplate(CategoryQuestionTemplateRequest request) {
        CategoryQuestionTemplate template = new CategoryQuestionTemplate();
        template.setCategoryId(request.getCategoryId());
        template.setItemId(request.getItemId());
        template.setTemplateText(request.getTemplateText());
        return templateRepository.save(template);
    }

    @Override
    @Transactional
    public CategoryQuestionTemplate updateTemplate(UUID id, CategoryQuestionTemplateRequest request) {
        CategoryQuestionTemplate template = templateRepository.findById(id).orElseThrow(() -> new RuntimeException("Template not found"));
        template.setCategoryId(request.getCategoryId());
        template.setItemId(request.getItemId());
        template.setTemplateText(request.getTemplateText());
        return templateRepository.save(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID id) {
        templateRepository.deleteById(id);
    }

    @Override
    public List<CategoryQuestionTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }
}
