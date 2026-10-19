package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.mapper.CatalogMapper;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.CatalogService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service("catalogService")
public class CatalogServiceImpl implements CatalogService {

    private final CategoryRepository categories;
    private final ItemRepository items;
    private final CatalogMapper catalogMapper;

    public CatalogServiceImpl(CategoryRepository categories, ItemRepository items, CatalogMapper catalogMapper) {
        this.categories = categories;
        this.items = items;
        this.catalogMapper = catalogMapper;
    }

    @Cacheable("catalog:categories")
    @Transactional(readOnly = true)
    public List<CategoryResponse> categories() {
        return categories.findAll(Sort.by("name", "id")).stream().map(catalogMapper::toCategoryResponse).toList();
    }
    @Transactional(readOnly = true)
    public CategoryResponse category(UUID id) { return catalogMapper.toCategoryResponse(requireCategory(id)); }
    @Cacheable(value = "catalog:items", key = "#categoryId == null ? 'ALL' : #categoryId.toString()")
    @Transactional(readOnly = true)
    public List<ItemResponse> items(UUID categoryId) {
        if (categoryId != null) requireCategory(categoryId);
        var found = categoryId == null ? items.findAll(Sort.by("name", "id")) : items.findByCategoryId(categoryId);
        return found.stream().sorted(Comparator.comparing(Item::getName).thenComparing(Item::getId))
                .map(catalogMapper::toItemResponse).toList();
    }
    @Transactional(readOnly = true)
    public ItemResponse item(UUID id) { return catalogMapper.toItemResponse(requireItem(id)); }
    @CacheEvict(value = {"catalog:categories", "catalog:items"}, allEntries = true)
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        categories.lockCatalog();
        String name = name(request.name());
        if (categories.nameExists(name, null)) throw new ConflictException("Category name already exists");
        Category c = new Category(); c.setName(name); c.setDescription(request.description());
        return catalogMapper.toCategoryResponse(categories.saveAndFlush(c));
    }
    @CacheEvict(value = {"catalog:categories", "catalog:items"}, allEntries = true)
    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        categories.lockCatalog(); Category c = requireCategory(id); String name = name(request.name());
        if (categories.nameExists(name, id)) throw new ConflictException("Category name already exists");
        c.setName(name); c.setDescription(request.description()); return catalogMapper.toCategoryResponse(categories.saveAndFlush(c));
    }
    @CacheEvict(value = {"catalog:categories", "catalog:items"}, allEntries = true)
    @Transactional
    public void deleteCategory(UUID id) {
        categories.lockCatalog(); Category c = requireCategory(id);
        if (items.existsByCategory_Id(id) || categories.isCategoryReferenced(id))
            throw new ConflictException("Category is in use by items, posts or question templates");
        try { categories.delete(c); categories.flush(); }
        catch (DataIntegrityViolationException ex) { throw new ConflictException("Category is in use and cannot be deleted"); }
    }
    @CacheEvict(value = {"catalog:categories", "catalog:items"}, allEntries = true)
    @Transactional
    public ItemResponse createItem(ItemRequest request) {
        categories.lockCatalog(); Category c = requireCategory(request.categoryId()); String name = name(request.name());
        if (items.nameExists(c.getId(), name, null)) throw new ConflictException("Item name already exists in this category");
        Item i = new Item(); i.setCategory(c); i.setName(name); return catalogMapper.toItemResponse(items.saveAndFlush(i));
    }
    @CacheEvict(value = {"catalog:categories", "catalog:items"}, allEntries = true)
    @Transactional
    public ItemResponse updateItem(UUID id, ItemRequest request) {
        categories.lockCatalog(); Item i = requireItem(id); Category c = requireCategory(request.categoryId()); String name = name(request.name());
        if (!i.getCategory().getId().equals(c.getId()) && items.isItemReferenced(id))
            throw new ConflictException("An item used by posts or templates cannot change category");
        if (items.nameExists(c.getId(), name, id)) throw new ConflictException("Item name already exists in this category");
        i.setCategory(c); i.setName(name);
        try { return catalogMapper.toItemResponse(items.saveAndFlush(i)); }
        catch (DataIntegrityViolationException ex) { throw new ConflictException("Item is in use and cannot change category"); }
    }
    @CacheEvict(value = {"catalog:categories", "catalog:items"}, allEntries = true)
    @Transactional
    public void deleteItem(UUID id) {
        categories.lockCatalog(); Item i = requireItem(id);
        if (items.isItemReferenced(id)) throw new ConflictException("Item is in use by posts or question templates");
        try { items.delete(i); items.flush(); }
        catch (DataIntegrityViolationException ex) { throw new ConflictException("Item is in use and cannot be deleted"); }
    }

    private Category requireCategory(UUID id) { return categories.findById(id).orElseThrow(() -> new NotFoundException("Category not found")); }
    private Item requireItem(UUID id) { return items.findById(id).orElseThrow(() -> new NotFoundException("Item not found")); }
    private String name(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("Name is required");
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
        if (normalized.isBlank() || normalized.length() > 255)
            throw new BadRequestException("Name must contain between 1 and 255 characters after normalization");
        return normalized;
    }
}
