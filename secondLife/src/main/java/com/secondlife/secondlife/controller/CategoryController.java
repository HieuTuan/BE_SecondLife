package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CatalogService catalog;

    @GetMapping
    public List<CategoryResponse> getAllCategories() { return catalog.categories(); }

    @GetMapping("/{id}")
    public CategoryResponse getCategory(@PathVariable UUID id) { return catalog.category(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CATALOG_MANAGE')")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return catalog.createCategory(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CATALOG_MANAGE')")
    public CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return catalog.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CATALOG_MANAGE')")
    public void deleteCategory(@PathVariable UUID id) { catalog.deleteCategory(id); }
}
