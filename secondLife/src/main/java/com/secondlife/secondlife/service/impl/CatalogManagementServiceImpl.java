package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.dto.response.ItemResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.CatalogManagementService;
import com.secondlife.secondlife.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogManagementServiceImpl implements CatalogManagementService {
    private final CatalogService catalog;
    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;
    private final CategoryQuestionTemplateRepository templateRepository;
    private final PostRepository postRepository;

    @Override
    public CategoryResponse createCategory(CategoryRequest request) {
        return catalog.createCategory(request);
    }

    @Override
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        return catalog.updateCategory(id, request);
    }

    @Override
    public void deleteCategory(UUID id) {
        catalog.deleteCategory(id);
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return catalog.categories();
    }

    @Override
    public ItemResponse createItem(ItemRequest request) {
        return catalog.createItem(request);
    }

    @Override
    public ItemResponse updateItem(UUID id, ItemRequest request) {
        return catalog.updateItem(id, request);
    }

    @Override
    public void deleteItem(UUID id) {
        catalog.deleteItem(id);
    }

    @Override
    public List<ItemResponse> getItemsByCategory(UUID categoryId) {
        return catalog.items(categoryId);
    }

    @Override
    public List<ItemResponse> getAllItems() {
        return catalog.items(null);
    }

    @Override
    @Transactional
    public CategoryQuestionTemplate createTemplate(CategoryQuestionTemplateRequest request) {
        categoryRepository.lockCatalog();
        String normalizedText = validateTemplateRequest(request);
        ensureNoDuplicateTemplate(request.categoryId(), request.itemId(), null);

        CategoryQuestionTemplate template = new CategoryQuestionTemplate();
        template.setCategoryId(request.categoryId());
        template.setItemId(request.itemId());
        template.setTemplateText(normalizedText);
        try {
            return templateRepository.saveAndFlush(template);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Question template already exists for the selected category/item");
        }
    }

    @Override
    @Transactional
    public CategoryQuestionTemplate updateTemplate(UUID id, CategoryQuestionTemplateRequest request) {
        categoryRepository.lockCatalog();
        CategoryQuestionTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Template not found"));
        String normalizedText = validateTemplateRequest(request);
        ensureNoDuplicateTemplate(request.categoryId(), request.itemId(), id);

        template.setCategoryId(request.categoryId());
        template.setItemId(request.itemId());
        template.setTemplateText(normalizedText);
        try {
            return templateRepository.saveAndFlush(template);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Question template already exists for the selected category/item");
        }
    }

    @Override
    @Transactional
    public void deleteTemplate(UUID id) {
        categoryRepository.lockCatalog();
        if (!templateRepository.existsById(id)) {
            throw new NotFoundException("Template not found");
        }
        postRepository.clearTemplateReference(id);
        templateRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryQuestionTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    private String validateTemplateRequest(CategoryQuestionTemplateRequest request) {
        if (request == null || request.categoryId() == null) {
            throw new BadRequestException("categoryId is required");
        }
        if (!categoryRepository.existsById(request.categoryId())) {
            throw new NotFoundException("Category not found");
        }
        if (request.itemId() != null) {
            Item item = itemRepository.findById(request.itemId())
                    .orElseThrow(() -> new NotFoundException("Item not found"));
            if (!request.categoryId().equals(item.getCategory().getId())) {
                throw new BadRequestException("itemId does not belong to categoryId");
            }
        }
        String normalized = normalizeTemplateText(request.templateText());
        if (normalized.isBlank()) {
            throw new BadRequestException("templateText is required");
        }
        return normalized;
    }

    private void ensureNoDuplicateTemplate(UUID categoryId, UUID itemId, UUID excludedId) {
        Optional<CategoryQuestionTemplate> existing = itemId != null
                ? templateRepository.findByCategoryIdAndItemId(categoryId, itemId)
                : templateRepository.findByCategoryIdAndItemIdIsNull(categoryId);

        if (existing.isPresent() && (excludedId == null || !existing.get().getId().equals(excludedId))) {
            throw new ConflictException(itemId != null
                    ? "Question template already exists for this category and item"
                    : "Category-level question template already exists for this category");
        }
    }

    public static String normalizeTemplateText(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (text.isEmpty()) {
            return "";
        }
        text = text.replaceAll("\\)\\s+(?=\\*\\*|[0-9]+[\\.\\)]|\\-\\s|[A-ZÀ-Ỹ])", ")\n");
        text = text.replaceAll("\\?\\*\\*\\s+(?=\\*\\*|[0-9]+[\\.\\)])", "?**\n");
        if (!text.contains("\n")) {
            text = text.replaceAll("\\?\\s+(?=[A-ZÀ-Ỹ0-9\\*\\#])", "?\n");
        }
        return text.trim();
    }
}
