package com.secondlife.secondlife.controller.admin;

import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.service.CatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@PreAuthorize("hasAuthority('ADMIN_CONFIG_MANAGE')")
public class AdminCatalogController {

    private final CatalogService catalogService;

    public AdminCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    // Category Endpoints
    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(@RequestBody CategoryRequest request) {
        return ResponseEntity.ok(catalogService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Category> updateCategory(@PathVariable UUID id, @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(catalogService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<String> deleteCategory(@PathVariable UUID id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.ok("Category deleted successfully");
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(catalogService.getAllCategories());
    }

    // Item Endpoints
    @PostMapping("/items")
    public ResponseEntity<Item> createItem(@RequestBody ItemRequest request) {
        return ResponseEntity.ok(catalogService.createItem(request));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<Item> updateItem(@PathVariable UUID id, @RequestBody ItemRequest request) {
        return ResponseEntity.ok(catalogService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<String> deleteItem(@PathVariable UUID id) {
        catalogService.deleteItem(id);
        return ResponseEntity.ok("Item deleted successfully");
    }

    @GetMapping("/items")
    public ResponseEntity<List<Item>> getAllItems() {
        return ResponseEntity.ok(catalogService.getAllItems());
    }

    @GetMapping("/categories/{categoryId}/items")
    public ResponseEntity<List<Item>> getItemsByCategory(@PathVariable UUID categoryId) {
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
