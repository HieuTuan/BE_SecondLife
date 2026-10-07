package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final CategoryRepository categories;
    private final ItemRepository items;
    private final CatalogWriteLock writeLock;
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAll(Sort.by("name", "id")).stream().map(CategoryResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public CategoryResponse category(UUID id) { return CategoryResponse.from(requireCategory(id)); }
    @Transactional(readOnly = true)
    public List<ItemResponse> items(UUID categoryId) {
        if (categoryId != null) requireCategory(categoryId);
        var found = categoryId == null ? items.findAll(Sort.by("name", "id")) : items.findByCategoryId(categoryId);
        return found.stream().sorted(java.util.Comparator.comparing(Item::getName).thenComparing(Item::getId))
                .map(ItemResponse::from).toList();
    }
    @Transactional(readOnly = true)
    public ItemResponse item(UUID id) { return ItemResponse.from(requireItem(id)); }
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        writeLock.lock();
        String name = name(request.name());
        if (categories.nameExists(name, null)) throw new ConflictException("Category name already exists");
        Category c = new Category(); c.setName(name); c.setDescription(request.description());
        return CategoryResponse.from(categories.saveAndFlush(c));
    }
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        writeLock.lock(); Category c = requireCategory(id); String name = name(request.name());
        if (categories.nameExists(name, id)) throw new ConflictException("Category name already exists");
        c.setName(name); c.setDescription(request.description()); return CategoryResponse.from(categories.saveAndFlush(c));
    }
    @Transactional
    public void deleteCategory(UUID id) {
        writeLock.lock(); Category c = requireCategory(id);
        if (items.existsByCategory_Id(id) || referenced("category_id", id))
            throw new ConflictException("Category is in use by items, posts or question templates");
        try { categories.delete(c); categories.flush(); }
        catch (DataIntegrityViolationException ex) { throw new ConflictException("Category is in use and cannot be deleted"); }
    }
    @Transactional
    public ItemResponse createItem(ItemRequest request) {
        writeLock.lock(); Category c = requireCategory(request.categoryId()); String name = name(request.name());
        if (items.nameExists(c.getId(), name, null)) throw new ConflictException("Item name already exists in this category");
        Item i = new Item(); i.setCategory(c); i.setName(name); return ItemResponse.from(items.saveAndFlush(i));
    }
    @Transactional
    public ItemResponse updateItem(UUID id, ItemRequest request) {
        writeLock.lock(); Item i = requireItem(id); Category c = requireCategory(request.categoryId()); String name = name(request.name());
        if (!i.getCategory().getId().equals(c.getId()) && referenced("item_id", id))
            throw new ConflictException("An item used by posts or templates cannot change category");
        if (items.nameExists(c.getId(), name, id)) throw new ConflictException("Item name already exists in this category");
        i.setCategory(c); i.setName(name);
        try { return ItemResponse.from(items.saveAndFlush(i)); }
        catch (DataIntegrityViolationException ex) { throw new ConflictException("Item is in use and cannot change category"); }
    }
    @Transactional
    public void deleteItem(UUID id) {
        writeLock.lock(); Item i = requireItem(id);
        if (referenced("item_id", id)) throw new ConflictException("Item is in use by posts or question templates");
        try { items.delete(i); items.flush(); }
        catch (DataIntegrityViolationException ex) { throw new ConflictException("Item is in use and cannot be deleted"); }
    }

    private Category requireCategory(UUID id) { return categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found")); }
    private Item requireItem(UUID id) { return items.findById(id).orElseThrow(() -> new NotFoundException("Item not found")); }
    private String name(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("Name is required");
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > 255)
            throw new BadRequestException("Name must contain between 1 and 255 characters after normalization");
        return normalized;
    }
    private boolean referenced(String column, UUID id) {
        // column is selected only by the two literal internal call sites above, never request input.
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM posts WHERE " + column
                + " = ?) OR EXISTS(SELECT 1 FROM category_question_templates WHERE " + column + " = ?)", Boolean.class, id, id));
    }
}
