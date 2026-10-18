package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.dto.response.ItemResponse;
import java.util.List;
import java.util.UUID;

public interface CatalogService {
    List<CategoryResponse> categories();

    CategoryResponse category(UUID id);

    List<ItemResponse> items(UUID categoryId);

    ItemResponse item(UUID id);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(UUID id, CategoryRequest request);

    void deleteCategory(UUID id);

    ItemResponse createItem(ItemRequest request);

    ItemResponse updateItem(UUID id, ItemRequest request);

    void deleteItem(UUID id);

}
