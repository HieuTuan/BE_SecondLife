package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.dto.response.ItemResponse;
import com.secondlife.secondlife.service.impl.CatalogServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@PreAuthorize("hasAuthority('ADMIN_CONFIG_MANAGE')")
public class AdminCatalogController {

    private final CatalogServiceImpl catalogService;

    public AdminCatalogController(CatalogServiceImpl catalogService) {
        this.catalogService = catalogService;
    }

    // Category Endpoints
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@RequestBody CategoryRequest request) {
        return ResponseEntity.ok(catalogService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(@PathVariable UUID id, @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(catalogService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<String> deleteCategory(@PathVariable UUID id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.ok("Category deleted successfully");
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(catalogService.getAllCategories());
    }

    // Item Endpoints
    @PostMapping("/items")
    public ResponseEntity<ItemResponse> createItem(@RequestBody ItemRequest request) {
        return ResponseEntity.ok(catalogService.createItem(request));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ItemResponse> updateItem(@PathVariable UUID id, @RequestBody ItemRequest request) {
        return ResponseEntity.ok(catalogService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<String> deleteItem(@PathVariable UUID id) {
        catalogService.deleteItem(id);
        return ResponseEntity.ok("Item deleted successfully");
    }

    @GetMapping("/items")
    public ResponseEntity<List<ItemResponse>> getAllItems() {
        return ResponseEntity.ok(catalogService.getAllItems());
    }

    @GetMapping("/categories/{categoryId}/items")
    public ResponseEntity<List<ItemResponse>> getItemsByCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(catalogService.getItemsByCategory(categoryId));
    }

    // Template Endpoints
    @PostMapping("/templates")
    public ResponseEntity<CategoryQuestionTemplate> createTemplate(@RequestBody CategoryQuestionTemplateRequest request) {
        return ResponseEntity.ok(catalogService.createTemplate(request));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<CategoryQuestionTemplate> updateTemplate(@PathVariable UUID id, @RequestBody CategoryQuestionTemplateRequest request) {
        return ResponseEntity.ok(catalogService.updateTemplate(id, request));
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<String> deleteTemplate(@PathVariable UUID id) {
        catalogService.deleteTemplate(id);
        return ResponseEntity.ok("Template deleted successfully");
    }

    @GetMapping("/templates")
    public ResponseEntity<List<CategoryQuestionTemplate>> getAllTemplates() {
        return ResponseEntity.ok(catalogService.getAllTemplates());
    }
}
